package dev.better.app.domain

import java.time.*
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

enum class RuleType { ONE_OFF, DAILY, WEEKLY, INTERVAL_CALENDAR, INTERVAL_COMPLETION, SEQUENCE_THEN_REPEAT }
enum class ZoneMode { LOCAL_FLOATING, ANCHORED_ZONE, ABSOLUTE_INSTANT }
enum class DueKind { EXACT, WINDOW, ANYTIME }
enum class DateUnit { DAYS, WEEKS, MONTHS }
enum class OverlapChoice { EARLIER, LATER }
enum class OccurrenceState { PENDING, DUE, OVERDUE, COMPLETED, SKIPPED, CANCELLED, MISSED, SKIPPED_BY_OVERRIDE }
val terminalStates = setOf(OccurrenceState.COMPLETED, OccurrenceState.SKIPPED, OccurrenceState.CANCELLED, OccurrenceState.MISSED, OccurrenceState.SKIPPED_BY_OVERRIDE)

data class DateStep(val amount: Int, val unit: DateUnit = DateUnit.DAYS) {
    init { require(amount in 1..10_000) { "Intervals must be between 1 and 10000." } }
    fun addTo(date: LocalDate, times: Long = 1): LocalDate = when (unit) {
        DateUnit.DAYS -> date.plusDays(amount * times)
        DateUnit.WEEKS -> date.plusWeeks(amount * times)
        DateUnit.MONTHS -> date.plusMonths(amount * times)
    }
    override fun toString(): String = "$amount${when (unit) { DateUnit.DAYS -> "d"; DateUnit.WEEKS -> "w"; DateUnit.MONTHS -> "mo" }}"
    companion object {
        fun parse(text: String): DateStep {
            val match = Regex("\\+?(\\d+)(d|w|mo)").matchEntire(text.trim().lowercase())
                ?: error("Use an interval such as 3d, 2w or 1mo.")
            return DateStep(match.groupValues[1].toInt(), when (match.groupValues[2]) {
                "d" -> DateUnit.DAYS; "w" -> DateUnit.WEEKS; else -> DateUnit.MONTHS
            })
        }
        fun parseSequence(text: String): List<DateStep> = if (text.isBlank()) emptyList() else text.split(',').map(::parse).also {
            require(it.size <= 100) { "A finite sequence can have at most 100 offsets." }
        }
    }
}

data class ScheduleRule(
    val type: RuleType,
    val anchor: LocalDate,
    val time: LocalTime = LocalTime.of(19, 0),
    val dueKind: DueKind = DueKind.EXACT,
    val endTime: LocalTime = LocalTime.of(21, 0),
    val step: DateStep = DateStep(1),
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val offsets: List<DateStep> = emptyList(),
    val tail: DateStep? = null,
    val endDate: LocalDate? = null,
    val count: Int? = null,
    val zoneMode: ZoneMode = ZoneMode.LOCAL_FLOATING,
    val zoneId: String = "Asia/Kolkata",
    val overlap: OverlapChoice = OverlapChoice.EARLIER,
    val absoluteInstant: Instant? = null,
) {
    fun validate() {
        require(zoneId in ZoneId.getAvailableZoneIds()) { "Choose a named IANA timezone." }
        require(count == null || count in 1..100_000) { "Count must be 1–100000." }
        require(endDate == null || endDate >= anchor) { "End date precedes the anchor." }
        require(type != RuleType.WEEKLY || weekdays.isNotEmpty()) { "Select at least one weekday." }
        require(type != RuleType.DAILY || step.unit == DateUnit.DAYS) { "Daily intervals use days." }
        require(type != RuleType.WEEKLY || step.unit == DateUnit.WEEKS) { "Weekly intervals use weeks." }
        require(dueKind != DueKind.WINDOW || time != endTime) { "Window start and end must differ." }
        require(offsets.size <= 100) { "At most 100 sequence offsets are supported." }
        require(zoneMode != ZoneMode.ABSOLUTE_INSTANT || (type == RuleType.ONE_OFF && absoluteInstant != null && dueKind == DueKind.EXACT)) {
            "An absolute instant must be an exact one-off event."
        }
    }
}

fun ScheduleRule.summary(): String {
    val recurrence = when (type) {
        RuleType.ONE_OFF -> "Once on $anchor"
        RuleType.DAILY -> "Every ${step.amount} day(s) from $anchor"
        RuleType.WEEKLY -> "Every ${step.amount} week(s): ${weekdays.sortedBy { it.value }.joinToString { it.name.lowercase() }} from $anchor"
        RuleType.INTERVAL_CALENDAR -> "Every $step from planned anchor $anchor"
        RuleType.INTERVAL_COMPLETION -> "$step after actual completion; first date $anchor"
        RuleType.SEQUENCE_THEN_REPEAT -> "Anchor $anchor, then ${offsets.joinToString()}; ${tail?.let { "repeat $it" } ?: "stop after last offset"}"
    }
    val due = when (dueKind) { DueKind.EXACT -> "at $time"; DueKind.WINDOW -> "window $time–$endTime"; DueKind.ANYTIME -> "anytime in operational day" }
    return "$recurrence; $due; ${if (zoneMode==ZoneMode.LOCAL_FLOATING) "device local timezone" else zoneId}" +
        (endDate?.let { "; ends $it" } ?: "") + (count?.let { "; max $it occurrences" } ?: "")
}

data class ResolvedTime(val instant: Instant, val local: LocalDateTime, val offset: ZoneOffset, val note: String?)
fun resolveLocal(local: LocalDateTime, zone: ZoneId, choice: OverlapChoice): ResolvedTime {
    val offsets = zone.rules.getValidOffsets(local)
    return when (offsets.size) {
        0 -> {
            val transition = requireNotNull(zone.rules.getTransition(local))
            val resolved = transition.dateTimeAfter
            ResolvedTime(resolved.toInstant(transition.offsetAfter), resolved, transition.offsetAfter,
                "DST gap: $local resolves to $resolved ${transition.offsetAfter}")
        }
        1 -> ResolvedTime(local.toInstant(offsets[0]), local, offsets[0], null)
        else -> {
            val offset = if (choice == OverlapChoice.EARLIER) offsets.first() else offsets.last()
            ResolvedTime(local.toInstant(offset), local, offset, "DST overlap: ${choice.name.lowercase()} offset $offset")
        }
    }
}

data class PlannedOccurrence(
    val calendarDate: LocalDate,
    val operationalDate: LocalDate,
    val start: Instant,
    val end: Instant,
    val closesAt: Instant,
    val zone: String,
    val resolution: String,
)

object ScheduleEngine {
    /** Calendar expansion is independent of completion. Completion rules return only the known next item. */
    fun preview(rule: ScheduleRule, from: LocalDate, zone: ZoneId, boundary: LocalTime, limit: Int = 10,
                completedAt: Instant? = null, completionStopped: Boolean = false): List<PlannedOccurrence> {
        rule.validate()
        require(limit in 1..1000)
        val effectiveZone = if (rule.zoneMode == ZoneMode.LOCAL_FLOATING) zone else ZoneId.of(rule.zoneId)
        if (rule.type == RuleType.INTERVAL_COMPLETION && completionStopped) return emptyList()
        if (rule.type == RuleType.INTERVAL_COMPLETION && completedAt != null) {
            val local = completedAt.atZone(effectiveZone)
            val nextDate = rule.step.addTo(local.toLocalDate())
            if (nextDate < from || (rule.endDate != null && nextDate > rule.endDate)) return emptyList()
            val shifted = rule.copy(time = local.toLocalTime(), endTime = local.toLocalTime().plusSeconds(windowSeconds(rule)))
            return listOf(plan(shifted, nextDate, effectiveZone, boundary))
        }
        return dates(rule, from).take(limit).map { plan(rule, it, effectiveZone, boundary) }.toList()
    }

    fun horizon(rule: ScheduleRule, from: LocalDate, zone: ZoneId, boundary: LocalTime): List<PlannedOccurrence> =
        preview(rule, from, zone, boundary, 1000).takeWhile { it.calendarDate < from.plusDays(45) }

    fun plan(rule: ScheduleRule, date: LocalDate, zone: ZoneId, boundary: LocalTime): PlannedOccurrence {
        val local = date.atTime(if (rule.dueKind == DueKind.ANYTIME) boundary else rule.time)
        val start = if (rule.zoneMode == ZoneMode.ABSOLUTE_INSTANT) {
            val instant = requireNotNull(rule.absoluteInstant)
            val zoned = instant.atZone(zone)
            ResolvedTime(instant, zoned.toLocalDateTime(), zoned.offset, "Fixed instant")
        } else resolveLocal(local, zone, rule.overlap)
        val opDay = operationalDate(start.instant, zone, boundary)
        val dayClose = resolveLocal(opDay.plusDays(1).atTime(boundary), zone, rule.overlap)
        val end = when (rule.dueKind) {
            DueKind.EXACT -> start
            DueKind.ANYTIME -> dayClose
            DueKind.WINDOW -> resolveLocal(date.plusDays(if (rule.endTime <= rule.time) 1 else 0).atTime(rule.endTime), zone, rule.overlap)
        }
        require(end.instant >= start.instant) { "DST resolution reverses this window; choose different times/offset." }
        return PlannedOccurrence(date, opDay, start.instant, end.instant, maxOf(dayClose.instant, end.instant), zone.id,
            listOfNotNull(start.note, end.note).distinct().joinToString("; "))
    }

    private fun windowSeconds(rule: ScheduleRule): Long = if (rule.dueKind == DueKind.WINDOW)
        (rule.endTime.toSecondOfDay() - rule.time.toSecondOfDay()).toLong().let { if (it > 0) it else it + 86400 } else 0

    private fun dates(rule: ScheduleRule, from: LocalDate): Sequence<LocalDate> = sequence {
        var ordinal = 0L
        suspend fun SequenceScope<LocalDate>.emit(date: LocalDate): Boolean {
            if (rule.count != null && ordinal >= rule.count) return false
            if (rule.endDate != null && date > rule.endDate) return false
            ordinal++
            if (date >= from) yield(date)
            return true
        }
        when (rule.type) {
            RuleType.ONE_OFF, RuleType.INTERVAL_COMPLETION -> emit(rule.anchor)
            RuleType.DAILY, RuleType.INTERVAL_CALENDAR -> {
                val distance = when (rule.step.unit) {
                    DateUnit.DAYS -> ChronoUnit.DAYS.between(rule.anchor, from)
                    DateUnit.WEEKS -> ChronoUnit.WEEKS.between(rule.anchor, from)
                    DateUnit.MONTHS -> ChronoUnit.MONTHS.between(rule.anchor.withDayOfMonth(1), from.withDayOfMonth(1))
                }
                ordinal = maxOf(0, distance / rule.step.amount)
                while (true) { val next = rule.step.addTo(rule.anchor, ordinal); if (!emit(next)) break }
            }
            RuleType.WEEKLY -> {
                val anchorWeek = rule.anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val selected = rule.weekdays.sortedBy { it.value }
                val partial = selected.count { anchorWeek.plusDays((it.value - 1).toLong()) < rule.anchor }
                var cycle = maxOf(0, ChronoUnit.WEEKS.between(anchorWeek, from) / rule.step.amount)
                ordinal = maxOf(0, cycle * selected.size - partial)
                outer@ while (true) {
                    val week = anchorWeek.plusWeeks(cycle * rule.step.amount)
                    for (weekday in selected) {
                        val next = week.plusDays((weekday.value - 1).toLong())
                        if (next < rule.anchor) continue
                        if (!emit(next)) break@outer
                    }
                    cycle++
                }
            }
            RuleType.SEQUENCE_THEN_REPEAT -> {
                var next = rule.anchor
                if (!emit(next)) return@sequence
                var monthAnchor = next
                var monthlyTotal = 0L
                for (offset in rule.offsets) {
                    next = if (offset.unit == DateUnit.MONTHS) {
                        monthlyTotal += offset.amount
                        monthAnchor.plusMonths(monthlyTotal)
                    } else { offset.addTo(next).also { monthAnchor = it; monthlyTotal = 0 } }
                    if (!emit(next)) return@sequence
                }
                val tail = rule.tail ?: return@sequence
                val tailAnchor = next
                val distance = when (tail.unit) {
                    DateUnit.DAYS -> ChronoUnit.DAYS.between(tailAnchor, from)
                    DateUnit.WEEKS -> ChronoUnit.WEEKS.between(tailAnchor, from)
                    DateUnit.MONTHS -> ChronoUnit.MONTHS.between(tailAnchor.withDayOfMonth(1), from.withDayOfMonth(1))
                }
                var n = maxOf(1L, distance / tail.amount)
                ordinal += n - 1
                while (true) { if (!emit(tail.addTo(tailAnchor, n++))) break }
            }
        }
    }
}

fun derivedState(current: OccurrenceState, plan: PlannedOccurrence, now: Instant): OccurrenceState {
    if (current in terminalStates) return current
    return when {
        now >= plan.closesAt -> OccurrenceState.MISSED
        now < plan.start -> OccurrenceState.PENDING
        now > plan.end -> OccurrenceState.OVERDUE
        else -> OccurrenceState.DUE
    }
}

enum class EditScope { THIS_OCCURRENCE, THIS_AND_FUTURE, ENTIRE_SERIES }
data class EditDecision(val effectiveScope: EditScope, val explanation: String?)
fun resolveEditScope(requested: EditScope, hasProtectedHistory: Boolean): EditDecision =
    if (requested == EditScope.ENTIRE_SERIES && hasProtectedHistory)
        EditDecision(EditScope.THIS_AND_FUTURE, "History is retained. This edit applies to this and future occurrences.")
    else EditDecision(requested, null)

enum class SkipRestart { NEEDS_CHOICE, RESTART_FROM_SKIP, MANUAL_NEXT_DATE, STOP }
fun completionIntervalAfterSkip(choice: SkipRestart, skippedAt: Instant, selected: Instant?, step: DateStep, zone: ZoneId): Instant? = when (choice) {
    SkipRestart.NEEDS_CHOICE, SkipRestart.STOP -> null
    SkipRestart.MANUAL_NEXT_DATE -> requireNotNull(selected) { "Choose the next date explicitly." }
    SkipRestart.RESTART_FROM_SKIP -> skippedAt.atZone(zone).let {
        resolveLocal(step.addTo(it.toLocalDate()).atTime(it.toLocalTime()), zone, OverlapChoice.EARLIER).instant
    }
}
