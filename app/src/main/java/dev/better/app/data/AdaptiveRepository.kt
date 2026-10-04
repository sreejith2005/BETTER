package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import java.time.*
import java.util.UUID
import org.json.JSONObject

data class DayPreviewRow(val id: String, val date: String, val title: String, val disposition: String,
    val start: Long, val end: Long, val fixed: Boolean, val detail: String)
data class AdaptivePreview(val rows: List<DayPreviewRow>, val conflicts: List<PlanConflict>, val overlaps: List<String>, val revision: String)

class AdaptiveRepository(val schedules: ScheduleRepository) {
    val db = schedules.db
    val clock = schedules.clock
    val dao = db.adaptive()
    fun today() = clock.now().atZone(clock.zone()).toLocalDate().toString()
    fun record(kind: String, title: String, from: String, until: String = "", payload: String, family: String = UUID.randomUUID().toString()) =
        AdaptiveRecord(UUID.randomUUID().toString(), kind, family, title.trim(), from, until, payload, clock.now().toEpochMilli())
    private suspend fun log(id: String, action: String, summary: String) = db.foundation().appendLog(
        ActionLog(UUID.randomUUID().toString(), "Adaptive day", id, action, summary, clock.now().toEpochMilli()))

    private suspend fun validate(r: AdaptiveRecord) {
        require(r.title.isNotBlank()) { "Enter a name." }
        LocalDate.parse(r.fromDate)
        require(r.fromDate >= today()) { "Choose today or a future date; history cannot be rewritten." }
        if (r.untilDate.isNotBlank()) require(LocalDate.parse(r.untilDate) >= LocalDate.parse(r.fromDate)) { "End must be on or after start." }
        val all = dao.records()
        val json = JSONObject(r.payload)
        require(json.getInt("format") == 1)
        val commitments = db.foundation().commitments().filter { it.archivedAt == null }.map { it.id }
        when (r.kind) {
            "ROUTINE" -> {
                val steps = AdaptiveJson.strings(json.getJSONArray("steps").toString())
                require(steps.size in 1..50 && steps.all { it.isNotBlank() }) { "Enter 1–50 non-empty steps." }
            }
            "PROFILE" -> {
                val blocks = AdaptiveJson.blocks(r.payload)
                require(blocks.isNotEmpty()) { "Add at least one block." }
                require(blocks.filter { it.commitmentId.isNotBlank() }.map { it.commitmentId }.distinct().size == blocks.count { it.commitmentId.isNotBlank() }) { "Link each commitment only once per profile." }
                blocks.forEach {
                    LocalTime.parse(it.time)
                    require(it.title.isNotBlank() && it.minutes in 1..1440 && it.priority in 1..3) { "Check block name, duration and priority." }
                    require(it.commitmentId.isEmpty() || it.commitmentId in commitments) { "Choose an active commitment." }
                    require(it.routineId.isEmpty() || all.any { r -> r.kind == "ROUTINE" && r.family == it.routineId }) { "Choose a saved routine." }
                    require(it.routineId.isEmpty() || it.commitmentId.isNotEmpty()) { "A routine block needs a scheduled commitment for its run." }
                }
            }
            "ASSIGN" -> {
                require(all.any { it.kind == "PROFILE" && it.family == r.family && it.fromDate <= r.fromDate }) { "Save a profile version effective by this date first." }
                require(json.getString("weekdays").split(',').all { it.toInt() in 1..7 }) { "Choose weekdays 1–7." }
            }
            "LINK" -> {
                require(r.family in commitments)
                require(all.any { it.kind == "ROUTINE" && it.family == json.getString("routine") && it.fromDate <= r.fromDate }) { "Choose a routine effective by this date." }
            }
            "MODE", "DAY" -> {
                require(r.untilDate.isNotBlank())
                if (r.kind == "DAY") require(r.fromDate == r.untilDate)
                val p = AdaptiveJson.policy(json)
                require(p.reason.isNotBlank()) { "Enter a reason." }
                require(p.items.map { it.commitmentId }.distinct().size == p.items.size)
                p.items.forEach { require(it.commitmentId in commitments); if (it.disposition == Disposition.MOVE) LocalTime.parse(it.time) }
            }
            else -> error("Unknown adaptive record.")
        }
        if (r.kind in listOf("ROUTINE", "PROFILE")) {
            val previous = all.filter { it.kind == r.kind && it.family == r.family }
            require(previous.isEmpty() || r.fromDate > previous.maxOf { it.fromDate }) { "New versions need a later effective date." }
            require(previous.isEmpty() || r.fromDate > today()) { "Edits take effect tomorrow or later." }
        }
    }

    suspend fun preview(candidate: AdaptiveRecord): AdaptivePreview {
        validate(candidate)
        val records = dao.records()
        val previewRecords = records + candidate + if (candidate.kind == "PROFILE") listOf(candidate.copy(id = "draft-assignment", kind = "ASSIGN",
            payload = JSONObject().put("format", 1).put("weekdays", "1,2,3,4,5,6,7").toString())) else emptyList()
        val snapshot = AdaptiveSnapshot(previewRecords)
        val existing = schedules.dao.occurrences()
        val versions = schedules.dao.versions().associateBy { it.id }
        val rules = schedules.dao.rules().associateBy { it.id }
        val active = db.foundation().commitments().filter { it.archivedAt == null }.map { it.id }.toSet()
        val boundary = LocalTime.parse(schedules.settings().dayBoundary)
        val now = clock.now().toEpochMilli()
        val first = LocalDate.parse(candidate.fromDate)
        val last = if (candidate.untilDate.isBlank()) first.plusDays(44) else minOf(LocalDate.parse(candidate.untilDate), first.plusDays(44))
        val projected = existing.associateBy { it.id }.toMutableMap()
        // Preview dates beyond the materialized horizon without writing synthetic rows.
        for (v in versions.values.filter { it.commitmentId in active }) {
            val rule = rules.getValue(v.ruleId).rule()
            if (rule.type == RuleType.INTERVAL_COMPLETION) continue // Only its next known instance is predictable.
            for (p in ScheduleEngine.horizon(rule, first, clock.zone(), boundary)) {
                val start = p.start.toEpochMilli()
                if (p.calendarDate > last || start < v.effectiveFrom || (v.effectiveUntil != null && start >= v.effectiveUntil)) continue
                val key = UUID.nameUUIDFromBytes("${v.commitmentId}|${v.id}|${p.calendarDate}|0".toByteArray(Charsets.UTF_8)).toString()
                if (existing.any { it.id != key && it.commitmentId == v.commitmentId && it.plannedDate == p.calendarDate.toString() && it.protected(now) }) continue
                projected.putIfAbsent(key, OccurrenceRecord(key, v.commitmentId, v.id, p.calendarDate.toString(), p.operationalDate.toString(),
                    start, p.end.toEpochMilli(), p.closesAt.toEpochMilli(), p.zone, p.resolution))
            }
        }
        val rows = projected.values.filter { it.commitmentId in active && it.plannedDate >= first.toString() && it.plannedDate <= last.toString() }.map { o ->
            val v = versions.getValue(o.versionId)
            val base = if (o.protected(now)) o.plan() else ScheduleEngine.preview(rules.getValue(v.ruleId).rule(),
                LocalDate.parse(o.plannedDate), clock.zone(), boundary).firstOrNull { it.calendarDate.toString() == o.plannedDate } ?: o.plan()
            val result = if (o.protected(now)) AdaptiveResult(o.plan(), o.state == "SKIPPED_BY_OVERRIDE", o.reason.orEmpty(), listOf("Recorded/due item retained"))
                else snapshot.apply(base, v, boundary)
            val block = snapshot.profile(o.plannedDate)?.let { AdaptiveJson.blocks(it.payload).firstOrNull { b -> b.commitmentId == o.commitmentId } }
            val duration = if (o.protected(now)) o.duration(v) else block?.minutes?.times(60) ?: v.durationSeconds
            val fixed = if (o.protected(now)) o.fixed(v) else v.fixed || block?.fixed == true
            DayPreviewRow(o.id, o.plannedDate, v.title, if (o.protected(now)) o.state else if (result.suppressed) "SUPPRESS" else if (result.plan.start != base.start) "MOVE" else "KEEP",
                result.plan.start.toEpochMilli(), result.plan.start.toEpochMilli() + duration * 1000L, fixed,
                (result.notes + result.reason).filter { it.isNotBlank() }.joinToString("\n"))
        }.toMutableList()
        var date = first
        while (date <= last) {
            val profile = snapshot.profile(date.toString())
            profile?.let { p -> AdaptiveJson.blocks(p.payload).filter { it.commitmentId.isEmpty() }.forEachIndexed { index, b ->
                val start = date.atTime(LocalTime.parse(b.time)).atZone(clock.zone()).toInstant().toEpochMilli()
                rows += DayPreviewRow("${p.id}:$date:$index", date.toString(), b.title, "BLOCK", start, start + b.minutes * 60_000L, b.fixed,
                    "Profile ${p.title} · proposed block; no separate commitment")
            } }
            date = date.plusDays(1)
        }
        val intervals = rows.filter { it.disposition !in listOf("SUPPRESS", "SKIPPED_BY_OVERRIDE", "CANCELLED", "SKIPPED", "COMPLETED", "MISSED") }
            .map { OccupiedInterval(it.id, it.title, it.start, it.end, it.fixed) }
        val overlaps = if (candidate.kind == "MODE") records.filter { it.kind == "MODE" && it.fromDate <= candidate.untilDate && it.untilDate >= candidate.fromDate }
            .map { "${it.title}: ${it.fromDate}–${it.untilDate}; newest activation wins only on overlapping dates" } else emptyList()
        val revision = java.security.MessageDigest.getInstance("SHA-256").digest((records.toString() + existing.toString()).toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AdaptivePreview(rows.sortedWith(compareBy<DayPreviewRow> { it.start }.thenBy { it.id }), conflicts(intervals), overlaps, revision)
    }

    suspend fun save(candidate: AdaptiveRecord, expected: AdaptivePreview, acknowledge: Boolean) = db.withTransaction {
        val current = preview(candidate)
        require(current == expected) { "Plan changed. Preview again before saving." }
        require(acknowledge || (current.conflicts.isEmpty() && current.overlaps.isEmpty())) { "Review overlaps and explicitly keep them before saving." }
        dao.insert(candidate)
        log(candidate.id, "ADAPTIVE_${candidate.kind}", "${candidate.title}; ${candidate.fromDate}–${candidate.untilDate}\n${candidate.payload}")
        schedules.reconcile()
    }.also { schedules.onChanged() }

    suspend fun startRun(occurrenceId: String): RoutineRun = db.withTransaction {
        dao.runs().firstOrNull { it.occurrenceId == occurrenceId }?.let { return@withTransaction it }
        val occurrence = requireNotNull(schedules.dao.occurrence(occurrenceId))
        require(OccurrenceState.valueOf(occurrence.state) !in terminalStates) { "This occurrence is already resolved." }
        val routine = requireNotNull(AdaptiveSnapshot(dao.records()).routine(occurrence.commitmentId, occurrence.plannedDate)) { "Attach an effective routine first." }
        val steps = JSONObject(routine.payload).getJSONArray("steps").toString()
        val run = RoutineRun(UUID.randomUUID().toString(), occurrenceId, routine.id, routine.title, steps,
            AdaptiveJson.strings(AdaptiveJson.strings(steps).map { "PENDING" }), clock.now().toEpochMilli(), null)
        dao.insertRun(run)
        schedules.dao.upsertOccurrence(occurrence.copy(acted = true))
        log(run.id, "ROUTINE_START", "${routine.title}; version ${routine.id}; occurrence $occurrenceId")
        log(occurrence.id, "ROUTINE_STARTED", "Run ${run.id}; occurrence history retained")
        run
    }

    suspend fun step(runId: String, index: Int, state: String) = db.withTransaction {
        require(state in listOf("COMPLETED", "SKIPPED"))
        val run = dao.runs().first { it.id == runId }
        val results = AdaptiveJson.strings(run.results).toMutableList()
        require(index in results.indices)
        if (results[index] != "PENDING") return@withTransaction
        results[index] = state
        dao.updateRun(run.copy(results = AdaptiveJson.strings(results), finishedAt = if (results.none { it == "PENDING" }) clock.now().toEpochMilli() else null))
        log(run.id, "ROUTINE_STEP", "Step ${index + 1}: ${AdaptiveJson.strings(run.steps)[index]} → $state; self-reported")
        // Routine evidence and occurrence completion are distinct explicit actions.
    }
}
