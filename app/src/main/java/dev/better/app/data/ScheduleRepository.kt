package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import java.time.*
import java.util.UUID

data class CommitmentDraft(
    val title: String, val rule: ScheduleRule, val category: String = "PERSONAL", val priority: Int = 2,
    val essential: Boolean = false, val instructions: String = "", val source: String = "",
    val durationSeconds: Int = 1800, val fixed: Boolean = false,
    val reminderLevel: String = "STANDARD", val reminderOffsets: String = "0,15", val criticalReminder: Boolean = false,
) {
    fun validate() {
        require(title.isNotBlank()) { "Enter a title." }
        require(durationSeconds in 0..86400) { "Duration must be 0–1440 minutes." }
        require(priority in 1..3) { "Priority must be 1, 2 or 3." }
        require(reminderLevel in listOf("NONE", "LIGHT", "STANDARD", "PERSISTENT", "ESCALATING"))
        if (reminderLevel == "ESCALATING") {
            val offsets = reminderOffsets.split(',').map { it.trim().toInt() }
            require(offsets.size in 1..5 && offsets.first() >= 0 && offsets.zipWithNext().all { it.first < it.second }) {
                "Choose 1–5 increasing non-negative reminder offsets in minutes."
            }
        }
        rule.validate()
    }
}

class ScheduleRepository(val db: BetterDatabase, val clock: AppClock) {
    val dao = db.schedules()
    var onChanged: suspend () -> Unit = {}
    suspend fun settings() = requireNotNull(db.foundation().settings()) { "Finish setup first." }
    suspend fun preview(draft: CommitmentDraft, startingAt: LocalDate? = null): List<PlannedOccurrence> {
        draft.validate()
        val from = startingAt ?: if (draft.rule.type == RuleType.ONE_OFF) draft.rule.anchor
            else maxOf(draft.rule.anchor, clock.now().atZone(clock.zone()).toLocalDate())
        return ScheduleEngine.preview(draft.rule, from, clock.zone(), LocalTime.parse(settings().dayBoundary))
    }

    suspend fun save(draft: CommitmentDraft, acknowledged: Boolean, commitmentId: String? = null,
                     scope: EditScope = EditScope.THIS_AND_FUTURE, selectedOccurrence: String? = null,
                     reason: String = "Schedule edit", expectedPreview: List<PlannedOccurrence>? = null,
                     conflictsAcknowledged: Boolean = false, extraMoveAcknowledged: Boolean = false): String = db.withTransaction {
        val now = clock.now().toEpochMilli()
        val id = commitmentId ?: UUID.randomUUID().toString()
        val existing = db.foundation().commitments().find { it.id == id }
        val all = dao.occurrences().filter { it.commitmentId == id }
        val decision = resolveEditScope(scope, all.any { it.protected(now) })
        val selected = selectedOccurrence?.let { key -> all.find { it.id == key } ?: error("Occurrence no longer exists.") }
        val from = if (decision.effectiveScope == EditScope.THIS_AND_FUTURE) selected?.plannedDate?.let(LocalDate::parse) else null
        val preview = preview(draft, from)
        require(expectedPreview == null || expectedPreview == preview) { "The dated preview changed. Preview again before saving." }
        require(preview.isNotEmpty()) { "This rule has no occurrences. Review its dates." }
        require(conflictsAcknowledged || planConflicts(preview, draft.durationSeconds, draft.fixed,
            if (scope == EditScope.THIS_OCCURRENCE) null else commitmentId,
            if (scope == EditScope.THIS_OCCURRENCE) selectedOccurrence else null).isEmpty()) {
            "Overlapping events found. Review and acknowledge the overlap before saving."
        }
        require(acknowledged || preview.none { it.resolution.contains("DST") }) {
            "Acknowledge the DST resolution before saving."
        }
        if (existing != null && decision.effectiveScope == EditScope.THIS_OCCURRENCE) {
            val occurrence = requireNotNull(selected) { "Select an occurrence." }
            require(OccurrenceState.valueOf(occurrence.state) !in terminalStates) { "Resolved history requires a correction, not a plan edit." }
            require(reason.isNotBlank()) { "Enter an override reason." }
            val p = preview.first()
            val previous = dao.overrides().find { it.occurrenceId == occurrence.id }
            val sourceVersion = dao.versions().first { it.id == occurrence.versionId }
            val moves = maxOf(db.foundation().logs().count { it.entityId == occurrence.id && it.action in listOf("EXEC_MOVE", "EDIT_MOVE") },
                if (previous != null && previous.originalStart != occurrence.startAt) 1 else 0)
            if (sourceVersion.category == "CAREER" && p.start.toEpochMilli() != occurrence.startAt && moves > 0)
                require(extraMoveAcknowledged) { "You moved this once. Confirm an additional override with a reason." }
            dao.upsertOverride(OverrideRecord(previous?.id ?: UUID.randomUUID().toString(), occurrence.id,
                draft.title, draft.instructions, draft.source, reason, now, previous?.originalStart ?: occurrence.startAt))
            dao.upsertOccurrence(occurrence.copy(startAt = p.start.toEpochMilli(), endAt = p.end.toEpochMilli(),
                closesAt = p.closesAt.toEpochMilli(), operationalDate = p.operationalDate.toString(), zoneId = p.zone,
                resolution = p.resolution, acted = true, reason = reason,
                state = derivedState(OccurrenceState.PENDING, p, clock.now()).name))
            log("Occurrence", occurrence.id, if (p.start.toEpochMilli() != occurrence.startAt) "EDIT_MOVE" else "OVERRIDE", "${occurrence.startAt} → ${p.start}; $reason\n" +
                "Before: ${previous?.title ?: sourceVersion.title}\n${previous?.instructionText ?: sourceVersion.instructionText}\nSource: ${previous?.sourceText ?: sourceVersion.sourceText}\n" +
                "After: ${draft.title}\n${draft.instructions}\nSource: ${draft.source}")
            return@withTransaction id
        }
        val oldVersions = dao.versions().filter { it.commitmentId == id }
        val boundary = if (existing == null || decision.effectiveScope == EditScope.ENTIRE_SERIES)
            draft.rule.anchor.atStartOfDay(if (draft.rule.zoneMode == ZoneMode.LOCAL_FLOATING) clock.zone() else ZoneId.of(draft.rule.zoneId)).toInstant().toEpochMilli()
        else selected?.plannedDate?.let { LocalDate.parse(it).atStartOfDay(
            if (draft.rule.zoneMode == ZoneMode.LOCAL_FLOATING) clock.zone() else ZoneId.of(draft.rule.zoneId)
        ).toInstant().toEpochMilli() } ?: now
        require(existing == null || selected == null || selected.startAt >= now || decision.effectiveScope == EditScope.ENTIRE_SERIES) {
            "Choose an upcoming occurrence for a future edit."
        }
        for (version in oldVersions) {
            if (version.effectiveUntil == null || version.effectiveUntil > boundary)
                dao.updateVersion(version.copy(effectiveUntil = if (decision.effectiveScope == EditScope.ENTIRE_SERIES)
                    version.effectiveFrom else maxOf(version.effectiveFrom, boundary)))
        }
        if (existing == null) db.foundation().insertCommitment(Commitment(id, draft.title, draft.category, draft.essential))
        else dao.updateCommitment(existing.copy(title = draft.title, category = draft.category, essential = draft.essential))
        val ruleId = UUID.randomUUID().toString()
        dao.insertRule(RuleRecord.from(ruleId, draft.rule))
        dao.insertVersion(VersionRecord(UUID.randomUUID().toString(), id, ruleId, boundary, null, now,
            draft.title, draft.category, draft.priority, draft.essential, draft.instructions, draft.source,
            draft.durationSeconds, draft.fixed, draft.reminderLevel, draft.reminderOffsets, draft.criticalReminder))
        log("Commitment", id, if (existing == null) "CREATE" else "VERSION", decision.explanation ?: "${decision.effectiveScope}: ${draft.title}; effective $boundary")
        reconcileInTransaction()
        id
    }.also { onChanged() }

    suspend fun ensureCareerSchedule() = db.withTransaction {
        val settings = db.foundation().settings() ?: return@withTransaction
        val standard = db.foundation().standards().firstOrNull() ?: return@withTransaction
        val career = db.foundation().commitments().firstOrNull { it.id == standard.commitmentId } ?: return@withTransaction
        if (dao.versions().any { it.commitmentId == career.id }) return@withTransaction
        val start = standard.effectiveDate
        val draft = CommitmentDraft("Career Focus", ScheduleRule(RuleType.DAILY, LocalDate.parse(start),
            LocalTime.parse(settings.careerStart), DueKind.WINDOW, LocalTime.parse(settings.careerEnd), zoneId = settings.zoneId),
            category = "CAREER", essential = true)
        // The existing foundation identity and target are retained.
        val ruleId = UUID.randomUUID().toString()
        dao.insertRule(RuleRecord.from(ruleId, draft.rule))
        dao.insertVersion(VersionRecord(UUID.randomUUID().toString(), career.id, ruleId,
            LocalDate.parse(start).atStartOfDay(clock.zone()).toInstant().toEpochMilli(), null, clock.now().toEpochMilli(),
            draft.title, draft.category, 2, true, "", "", 1800, false, "STANDARD", "0,15"))
        log("Commitment", career.id, "ADD_SCHEDULE", "Existing Career standard linked to daily preferred window")
        reconcileInTransaction()
    }

    suspend fun reconcile() = db.withTransaction { reconcileInTransaction() }
    private suspend fun reconcileInTransaction() {
        val settings = settings()
        val now = clock.now()
        val today = now.atZone(clock.zone()).toLocalDate()
        val rules = dao.rules().associateBy { it.id }
        val active = db.foundation().commitments().filter { it.archivedAt == null }.map { it.id }.toSet()
        val existing = dao.occurrences().associateBy { it.id }
        val adaptive = AdaptiveSnapshot(db.adaptive().records())
        val retained = mutableSetOf<String>()
        for (v in dao.versions().filter { it.commitmentId in active }) {
            val rule = requireNotNull(rules[v.ruleId]).rule()
            val history = existing.values.filter { it.commitmentId == v.commitmentId }
            val last = history.filter { OccurrenceState.valueOf(it.state) in terminalStates &&
                (it.state != "SKIPPED_BY_OVERRIDE" || it.protected(now.toEpochMilli())) }.maxByOrNull { it.actualAt ?: it.startAt }
            val from = if (rule.type == RuleType.ONE_OFF) minOf(today, rule.anchor) else today
            val plans = if (rule.type == RuleType.INTERVAL_COMPLETION) {
                if (rule.count != null && history.count { it.state == "COMPLETED" } >= rule.count) emptyList()
                else ScheduleEngine.preview(rule, minOf(from, rule.anchor), clock.zone(), LocalTime.parse(settings.dayBoundary),
                    completedAt = last?.takeIf { it.state == "COMPLETED" }?.actualAt?.let(Instant::ofEpochMilli),
                    completionStopped = last != null && last.state != "COMPLETED")
            } else ScheduleEngine.horizon(rule, from, clock.zone(), LocalTime.parse(settings.dayBoundary))
            for (p in plans) {
                val start = p.start.toEpochMilli()
                if (start < v.effectiveFrom || (v.effectiveUntil != null && start >= v.effectiveUntil)) continue
                val chain = if (rule.type == RuleType.INTERVAL_COMPLETION && last?.state == "COMPLETED") "|after:${last.id}" else ""
                val key = UUID.nameUUIDFromBytes("${v.commitmentId}|${v.id}|${p.calendarDate}|0$chain".toByteArray(Charsets.UTF_8)).toString()
                retained += key
                val old = existing[key]
                if (old?.protected(now.toEpochMilli()) == true) continue
                // A protected old-version instance occupies its original planned date;
                // a future edit cannot generate a second obligation for that same date.
                if (history.any { it.id != key && it.plannedDate == p.calendarDate.toString() && it.protected(now.toEpochMilli()) &&
                    (rule.type != RuleType.INTERVAL_COMPLETION || it.versionId != v.id || it.state != "COMPLETED") }) continue
                val result = adaptive.apply(p, v, LocalTime.parse(settings.dayBoundary))
                val adapted = result.plan
                val date = p.calendarDate.toString()
                val profile = adaptive.profile(date)
                val block = profile?.let { AdaptiveJson.blocks(it.payload).firstOrNull { b -> b.commitmentId == v.commitmentId } }
                dao.upsertOccurrence(OccurrenceRecord(key, v.commitmentId, v.id, p.calendarDate.toString(), adapted.operationalDate.toString(),
                    adapted.start.toEpochMilli(), adapted.end.toEpochMilli(), adapted.closesAt.toEpochMilli(), adapted.zone,
                    (listOf(p.resolution) + result.notes).filter { it.isNotBlank() }.joinToString("\n"),
                    if (result.suppressed) "SKIPPED_BY_OVERRIDE" else derivedState(OccurrenceState.PENDING, adapted, now).name,
                    reason = result.reason.takeIf { it.isNotBlank() }, profileVersionId = profile?.id,
                    modeActivationId = adaptive.mode(date)?.id, dayOverrideId = adaptive.day(date)?.id,
                    blockDurationSeconds = block?.minutes?.times(60), blockFixed = block?.fixed))
            }
        }
        for (o in existing.values) {
            if (o.id !in retained && !o.protected(now.toEpochMilli())) dao.deletePending(o.id)
            else if (o.id !in retained || o.protected(now.toEpochMilli())) {
                val state = derivedState(OccurrenceState.valueOf(o.state), o.plan(), now).name
                if (state != o.state) dao.upsertOccurrence(o.copy(state = state))
            }
        }
    }

    suspend fun planConflicts(plans: List<PlannedOccurrence>, duration: Int, fixed: Boolean,
        excludeCommitment: String? = null, excludeOccurrence: String? = null): List<PlanConflict> {
        val versions = dao.versions().associateBy { it.id }
        val overrides = dao.overrides().associateBy { it.occurrenceId }
        val active = db.foundation().commitments().filter { it.archivedAt == null }.map { it.id }.toSet()
        val old = dao.occurrences().filter { it.commitmentId in active && it.commitmentId != excludeCommitment &&
            it.id != excludeOccurrence && OccurrenceState.valueOf(it.state) !in terminalStates }.map { o ->
            val v = versions.getValue(o.versionId)
            OccupiedInterval(o.id, overrides[o.id]?.title ?: v.title, o.startAt, o.startAt + o.duration(v) * 1000L, o.fixed(v))
        }
        val proposed = plans.mapIndexed { i, p -> OccupiedInterval("preview:$i", "Proposed event", p.start.toEpochMilli(),
            p.start.toEpochMilli() + duration * 1000L, fixed) }
        return conflicts(old + proposed).filter { it.first.id.startsWith("preview:") || it.second.id.startsWith("preview:") }
    }

    suspend fun archive(id: String) = db.withTransaction {
        val c = db.foundation().commitments().first { it.id == id }
        dao.updateCommitment(c.copy(archivedAt = clock.now().toEpochMilli()))
        log("Commitment", id, "ARCHIVE", "Future generation stopped; history retained")
        reconcileInTransaction()
    }.also { onChanged() }
    suspend fun restore(id: String) = db.withTransaction {
        val c = db.foundation().commitments().first { it.id == id }
        dao.updateCommitment(c.copy(archivedAt = null))
        log("Commitment", id, "RESTORE", "Future generation restored")
        reconcileInTransaction()
    }.also { onChanged() }
    private suspend fun log(entity: String, id: String, action: String, summary: String) =
        db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), entity, id, action, summary, clock.now().toEpochMilli()))
}
