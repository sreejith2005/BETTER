package dev.better.app.domain

import java.time.*

enum class Disposition { KEEP, MOVE, SUPPRESS }
data class ItemPolicy(val commitmentId: String, val disposition: Disposition, val time: String = "")
data class DayPolicy(val essentialOnly: Boolean = false, val items: List<ItemPolicy> = emptyList(), val reason: String = "")
data class ProfileBlock(val title: String, val time: String, val minutes: Int, val fixed: Boolean,
    val commitmentId: String = "", val routineId: String = "", val priority: Int = 2)
data class AdaptiveResult(val plan: PlannedOccurrence, val suppressed: Boolean, val reason: String, val notes: List<String>)

/** No wall-clock reads or persistence: the same snapshot always gives the same preview. */
fun adaptDay(base: PlannedOccurrence, commitmentId: String, essential: Boolean, fixed: Boolean,
    block: ProfileBlock?, mode: DayPolicy?, day: DayPolicy?, boundary: LocalTime): AdaptiveResult {
    var plan = base
    var suppressed = false
    var reason = ""
    val notes = mutableListOf<String>()
    fun move(time: String, source: String, locked: Boolean = fixed) {
        if (locked) { notes += "$source: fixed event retained; move it explicitly in occurrence detail."; return }
        var local = LocalDateTime.of(base.calendarDate, LocalTime.parse(time))
        val zone = ZoneId.of(base.zone)
        if (local < base.start.atZone(zone).toLocalDateTime() && base.end.atZone(zone).toLocalDate() > base.calendarDate)
            local = local.plusDays(1)
        val offsets = ZoneId.of(base.zone).rules.getValidOffsets(local)
        if (offsets.size != 1) { notes += "$source move has a DST gap/overlap; previous plan retained. Resolve in occurrence editor."; return }
        val start = local.atZone(ZoneId.of(base.zone)).toInstant()
        // Profiles and policies propose starts inside the existing allowed window.
        if (start < base.start || start > base.end) { notes += "$source move is outside the allowed window; previous plan retained. Edit the occurrence explicitly."; return }
        plan = base.copy(start = start, operationalDate = operationalDate(start, ZoneId.of(base.zone), boundary))
    }
    block?.let { move(it.time, "Profile") }
    fun apply(policy: DayPolicy?, source: String) {
        if (policy == null) return
        val item = policy.items.lastOrNull { it.commitmentId == commitmentId }
        // An absent rule inherits the previous layer. KEEP explicitly restores baseline.
        val disposition = item?.disposition ?: if (policy.essentialOnly) {
            if (essential) Disposition.KEEP else Disposition.SUPPRESS
        } else null
        when (disposition) {
            Disposition.KEEP -> { suppressed = false; if (!fixed && block?.fixed != true) plan = base; reason = "$source: ${policy.reason}" }
            Disposition.SUPPRESS -> { suppressed = true; reason = "$source: ${policy.reason}" }
            Disposition.MOVE -> { suppressed = false; move(requireNotNull(item).time, source, fixed || block?.fixed == true); reason = "$source: ${policy.reason}" }
            null -> Unit
        }
    }
    apply(mode, "Life Mode"); apply(day, "Day override")
    return AdaptiveResult(plan, suppressed, reason, notes)
}
