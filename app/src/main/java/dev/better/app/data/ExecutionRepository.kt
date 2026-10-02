package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import org.json.JSONObject
import java.time.*
import java.util.UUID

/** Versioned snapshots live in the existing append-only audit log. No personal-data migration is needed. */
class ExecutionRepository(private val schedules: ScheduleRepository) {
    private val db get() = schedules.db
    private val dao get() = schedules.dao
    private val clock get() = schedules.clock

    suspend fun act(id: String, kind: String, reason: String = "", plan: PlannedOccurrence? = null,
        restart: SkipRestart = SkipRestart.STOP, expected: OccurrenceRecord? = null,
        acknowledged: Boolean = false, extraMove: Boolean = false): String? = db.withTransaction {
        require(kind in listOf("COMPLETE", "SKIP", "CANCEL", "MOVE"))
        val before = requireNotNull(dao.occurrence(id)) { "Occurrence no longer exists." }
        val terminal = OccurrenceState.valueOf(before.state) in terminalStates
        if (terminal && !(before.state == "MISSED" && kind == "COMPLETE")) return@withTransaction null
        require(expected == null || expected == before) { "This occurrence changed. Open the action again." }
        val version = dao.versions().first { it.id == before.versionId }
        require(db.foundation().commitments().first { it.id == before.commitmentId }.archivedAt == null) { "Restore this commitment before recording an action." }
        val rule = dao.rules().first { it.id == version.ruleId }.rule()
        val now = clock.now()
        val oldOverride = dao.overrides().find { it.occurrenceId == id }
        val allBefore = dao.occurrences().map { it.id }.toSet()
        if (kind == "SKIP" || kind == "CANCEL" || kind == "MOVE") require(reason.isNotBlank()) { "Enter a reason." }
        var nextPlan: PlannedOccurrence? = null
        if (kind == "SKIP" && rule.type == RuleType.INTERVAL_COMPLETION) {
            require(restart != SkipRestart.NEEDS_CHOICE) { "Choose how the interval should continue." }
            nextPlan = when (restart) {
                SkipRestart.RESTART_FROM_SKIP -> ScheduleEngine.preview(rule, now.atZone(ZoneId.of(before.zoneId)).toLocalDate(),
                    ZoneId.of(before.zoneId), LocalTime.parse(schedules.settings().dayBoundary), completedAt = now).firstOrNull()
                SkipRestart.MANUAL_NEXT_DATE -> requireNotNull(plan) { "Preview a next date." }
                else -> null
            }
            if (nextPlan != null) {
                require(nextPlan.start > now) { "Choose a future next date." }
                require(rule.endDate == null || nextPlan.calendarDate <= rule.endDate) { "Next date exceeds the series end date." }
                require(rule.count == null || dao.occurrences().count { it.commitmentId == before.commitmentId && it.state == "COMPLETED" } < rule.count) { "This series has reached its completion count." }
                require(acknowledged || !nextPlan.resolution.contains("DST")) { "Acknowledge the DST resolution." }
                require(acknowledged || schedules.planConflicts(listOf(nextPlan), version.durationSeconds, version.fixed, before.commitmentId).isEmpty()) { "Acknowledge the overlap before continuing." }
            }
        }
        val after = when (kind) {
            "COMPLETE" -> before.copy(state = "COMPLETED", actualAt = now.toEpochMilli(), acted = true,
                completedLate = now.toEpochMilli() > before.endAt, reason = reason.ifBlank { null })
            "SKIP" -> before.copy(state = "SKIPPED", actualAt = now.toEpochMilli(), acted = true, reason = reason)
            "CANCEL" -> before.copy(state = "CANCELLED", actualAt = now.toEpochMilli(), acted = true, reason = reason)
            else -> {
                val p = requireNotNull(plan) { "Preview the new time." }
                require(p.start > now) { "Choose a future time." }
                require(acknowledged || !p.resolution.contains("DST")) { "Acknowledge the DST resolution." }
                require(acknowledged || schedules.planConflicts(listOf(p), version.durationSeconds, version.fixed, excludeOccurrence = id).isEmpty()) { "Acknowledge the overlap before moving." }
                val moves = maxOf(db.foundation().logs().count { it.entityId == id && it.action in listOf("EXEC_MOVE", "EDIT_MOVE") },
                    if (oldOverride != null && oldOverride.originalStart != before.startAt) 1 else 0)
                if (version.category == "CAREER" && moves > 0) require(extraMove) { "You moved this once. Confirm an additional override with a reason." }
                dao.upsertOverride(OverrideRecord(oldOverride?.id ?: UUID.randomUUID().toString(), id,
                    oldOverride?.title ?: version.title, oldOverride?.instructionText ?: version.instructionText,
                    oldOverride?.sourceText ?: version.sourceText, reason, now.toEpochMilli(), oldOverride?.originalStart ?: before.startAt))
                before.copy(startAt = p.start.toEpochMilli(), endAt = p.end.toEpochMilli(), closesAt = p.closesAt.toEpochMilli(),
                    operationalDate = p.operationalDate.toString(), zoneId = p.zone, resolution = p.resolution,
                    state = "PENDING", reason = reason, acted = true)
            }
        }
        dao.upsertOccurrence(after)
        if (nextPlan != null) {
            val p = nextPlan
            dao.upsertOccurrence(OccurrenceRecord(UUID.randomUUID().toString(), before.commitmentId, before.versionId,
                p.calendarDate.toString(), p.operationalDate.toString(), p.start.toEpochMilli(), p.end.toEpochMilli(),
                p.closesAt.toEpochMilli(), p.zone, p.resolution, acted = true, reason = "Explicit restart after skip: $reason"))
        }
        schedules.reconcile()
        val generated = dao.occurrences().filter { it.id !in allBefore && it.commitmentId == before.commitmentId }
        val payload = JSONObject().put("format", 1).put("before", before.json()).put("after", after.json())
            .put("overrideBefore", oldOverride?.json() ?: JSONObject.NULL)
            .put("overrideAfter", dao.overrides().find { it.occurrenceId == id }?.json() ?: JSONObject.NULL)
            .put("generated", org.json.JSONArray(generated.map { it.json() })).put("restart", restart.name)
            .put("reason", reason).put("evidence", if (kind == "COMPLETE") "Self-reported" else "User action")
        val logId = UUID.randomUUID().toString()
        db.foundation().appendLog(ActionLog(logId, "Occurrence", id, "EXEC_$kind", payload.toString(), now.toEpochMilli()))
        logId
    }.also { schedules.onChanged() }

    suspend fun undo(actionId: String) = db.withTransaction {
        val logs = db.foundation().logs()
        val action = logs.first { it.id == actionId }
        require(action.action.startsWith("EXEC_") && action.action != "EXEC_UNDO")
        require(logs.none { it.action == "EXEC_UNDO" && it.summary == actionId }) { "Already undone." }
        require(db.foundation().commitments().first { it.id == dao.occurrence(action.entityId)!!.commitmentId }.archivedAt == null) { "Restore the commitment before undoing." }
        val payload = JSONObject(action.summary)
        val after = payload.getJSONObject("after").occurrence()
        val current = requireNotNull(dao.occurrence(action.entityId))
        require(logs.last { it.entityId == action.entityId }.id == actionId &&
            current.copy(state = after.state) == after) { "A later action changed this occurrence. Undo its latest action first." }
        require(dao.overrides().find { it.occurrenceId == action.entityId }?.json()?.toString() ==
            payload.optJSONObject("overrideAfter")?.toString()) { "A later edit changed this occurrence." }
        val children = payload.getJSONArray("generated")
        for (i in 0 until children.length()) {
            val child = children.getJSONObject(i).occurrence()
            val live = dao.occurrence(child.id) ?: continue
            require(live == child && live.state == "PENDING" && live.startAt > clock.now().toEpochMilli() &&
                logs.none { it.entityId == live.id }) { "The next occurrence has started or changed; undo is no longer available." }
            dao.deleteExecutionChild(live.id)
        }
        val old = payload.getJSONObject("before").occurrence()
        dao.upsertOccurrence(old.copy(acted = true, state = derivedState(OccurrenceState.valueOf(old.state), old.plan(), clock.now()).name))
        dao.deleteOverride(action.entityId)
        payload.optJSONObject("overrideBefore")?.let { dao.upsertOverride(it.override()) }
        db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), "Occurrence", action.entityId, "EXEC_UNDO", actionId, clock.now().toEpochMilli()))
        schedules.reconcile()
    }.also { schedules.onChanged() }
}

private fun OccurrenceRecord.json() = JSONObject().put("id", id).put("commitmentId", commitmentId).put("versionId", versionId)
    .put("plannedDate", plannedDate).put("operationalDate", operationalDate).put("startAt", startAt).put("endAt", endAt)
    .put("closesAt", closesAt).put("zoneId", zoneId).put("resolution", resolution).put("state", state)
    .put("actualAt", actualAt ?: JSONObject.NULL).put("reason", reason ?: JSONObject.NULL).put("acted", acted)
    .put("delivered", delivered).put("completedLate", completedLate)
private fun JSONObject.occurrence() = OccurrenceRecord(getString("id"),getString("commitmentId"),getString("versionId"),
    getString("plannedDate"),getString("operationalDate"),getLong("startAt"),getLong("endAt"),getLong("closesAt"),getString("zoneId"),
    getString("resolution"),getString("state"),if(isNull("actualAt")) null else getLong("actualAt"),
    if(isNull("reason")) null else getString("reason"),getBoolean("acted"),getBoolean("delivered"),getBoolean("completedLate"))
private fun OverrideRecord.json() = JSONObject().put("id",id).put("occurrenceId",occurrenceId).put("title",title)
    .put("instructionText",instructionText).put("sourceText",sourceText).put("reason",reason).put("createdAt",createdAt).put("originalStart",originalStart)
private fun JSONObject.override() = OverrideRecord(getString("id"),getString("occurrenceId"),getString("title"),getString("instructionText"),
    getString("sourceText"),getString("reason"),getLong("createdAt"),getLong("originalStart"))

fun actionSummary(log: ActionLog): String = when {
    log.action == "EXEC_UNDO" -> "Correction: previous action undone"
    log.action.startsWith("EXEC_") -> runCatching {
        val p = JSONObject(log.summary)
        buildString {
            append(log.action.removePrefix("EXEC_").lowercase().replaceFirstChar { it.uppercase() })
            append(" · ${p.getString("evidence")}")
            val before = p.getJSONObject("before").occurrence()
            val after = p.getJSONObject("after").occurrence()
            if (log.action == "EXEC_MOVE") append(" · ${Instant.ofEpochMilli(before.startAt).atZone(ZoneId.of(before.zoneId))} → ${Instant.ofEpochMilli(after.startAt).atZone(ZoneId.of(after.zoneId))}")
            if (after.completedLate) append(" · Late")
            if (p.optString("reason").isNotBlank()) append(" · ${p.getString("reason")}")
            if (log.action == "EXEC_SKIP") append(" · ${p.getString("restart").lowercase().replace('_',' ')}")
        }
    }.getOrDefault("Recorded action")
    else -> "${log.action.lowercase().replaceFirstChar { it.uppercase() }} · ${log.summary}"
}
