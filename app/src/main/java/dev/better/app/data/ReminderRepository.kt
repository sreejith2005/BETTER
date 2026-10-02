package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.ReminderPolicy
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalTime
import java.util.UUID

interface ReminderPlatform {
    fun recoveryToken(): String? = null
    fun allowed(category: String): Boolean
    fun schedule(at: Long?)
    fun cancelNotification(occurrenceId: String)
    fun post(row: ReminderDelivery, occurrence: OccurrenceRecord, version: VersionRecord, title: String, private: Boolean)
}

/** Platform effects are rebuilt from Room. A single alarm wakes the next batch, avoiding OEM alarm-count limits. */
class ReminderRepository(val schedules: ScheduleRepository, private val platform: ReminderPlatform) {
    private val db get() = schedules.db
    private val clock get() = schedules.clock
    private val mutex = Mutex()
    suspend fun reconcile(recovery: Boolean = false, deliver: Boolean = false) = mutex.withLock {
        if (db.foundation().settings() == null) return@withLock
        schedules.ensureCareerSchedule()
        schedules.reconcile()
        val now = clock.now().toEpochMilli()
        val token = platform.recoveryToken()
        val epochChanged = token != null && db.foundation().logs().lastOrNull { it.action == "REMINDER_EPOCH" }?.summary != token
        val recovering = recovery || epochChanged
        db.withTransaction {
            if (epochChanged) db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), "Reminders", "epoch", "REMINDER_EPOCH", token!!, now))
            val settings = schedules.settings()
            val versions = schedules.dao.versions().associateBy { it.id }
            val active = db.foundation().commitments().filter { it.archivedAt == null }.map { it.id }.toSet()
            val occurrences = schedules.dao.occurrences()
            val wanted = mutableMapOf<String, ReminderDelivery>()
            for (o in occurrences.filter { it.commitmentId in active && it.state in OPEN }) {
                val v = versions.getValue(o.versionId)
                for (step in ReminderPolicy.plan(o.startAt, o.closesAt, v.reminderLevel, v.reminderOffsets,
                    clock.zone(), LocalTime.parse(settings.quietStart), LocalTime.parse(settings.quietEnd), v.criticalReminder)) {
                    // Identity is independent of quiet hours. Replanning cannot replay attempted policy steps.
                    val id = UUID.nameUUIDFromBytes("${o.id}|${v.id}|${o.startAt}|${step.steps}".toByteArray()).toString()
                    wanted[id] = ReminderDelivery(id, o.id, v.id, o.startAt, o.operationalDate, step.steps, step.originalAt, step.plannedAt, step.delayed)
                }
            }
            val existing = db.reminders().all()
            val old = existing.associateBy { it.id }
            for (row in existing) {
                if (row.result == "PLANNED" && row.id !in wanted) db.reminders().put(row.copy(result = "CANCELLED", detail = "Plan or state changed"))
                val o = occurrences.find { it.id == row.occurrenceId }
                if (o == null || o.state !in OPEN || o.commitmentId !in active || o.startAt != row.scheduleStart)
                    platform.cancelNotification(row.occurrenceId)
            }
            for ((id, planned) in wanted) {
                val previous = old[id]
                if (previous != null && previous.result !in listOf("PLANNED", "CANCELLED")) continue
                val candidate = if (!recovering && previous?.result == "PLANNED" && previous.detail == "Android delay entered quiet hours" && previous.plannedAt > planned.plannedAt)
                    planned.copy(plannedAt = previous.plannedAt, delayed = true, detail = previous.detail) else planned
                // Quiet-hour regrouping and move/undo cannot reset the daily attempt budget.
                val consumed = existing.any { it.occurrenceId == candidate.occurrenceId && it.scheduleStart == candidate.scheduleStart &&
                    it.result !in listOf("PLANNED", "CANCELLED") && it.steps.split(',').any { s -> s in candidate.steps.split(',') } }
                val v = versions.getValue(candidate.versionId)
                val permittedNow = if (v.criticalReminder) now else ReminderPolicy.permitted(java.time.Instant.ofEpochMilli(now), clock.zone(),
                    LocalTime.parse(settings.quietStart), LocalTime.parse(settings.quietEnd)).toEpochMilli()
                val row = when {
                    consumed -> candidate.copy(result = "COALESCED", detail = "Policy step already accounted for")
                    candidate.plannedAt <= now && (recovering || previous == null || previous.result == "CANCELLED") -> candidate.copy(result = "MISSED", detail = "Past step; no backlog posted")
                    candidate.plannedAt < now - LATE_LIMIT -> candidate.copy(result = "MISSED", detail = "Android delivery window expired")
                    candidate.plannedAt <= now && !platform.allowed(v.category) -> candidate.copy(result = "BLOCKED", detail = "Notifications or channel are off")
                    candidate.plannedAt <= now && permittedNow > now -> candidate.copy(plannedAt = permittedNow, delayed = true, detail = "Android delay entered quiet hours")
                    else -> candidate
                }
                if (row != previous) db.reminders().put(row)
            }
            if (recovering) db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), "Reminders", "recovery", "SCHEDULE_CHECKED",
                "Schedule checked after restart or time change. Past alert steps were not reposted.", now))
        }
        if (deliver) {
            val due = db.reminders().all().filter { it.result == "PLANNED" && it.plannedAt <= now }
            for (batch in due.groupBy { it.occurrenceId }.values) {
                val latest = batch.maxBy { it.plannedAt }
                // Claim durably BEFORE the OS call. An interrupted claim is never retried as a duplicate alert.
                val claim = db.withTransaction {
                    for (older in batch.filter { it.id != latest.id }) db.reminders().put(older.copy(result = "COALESCED", detail = "Android delayed multiple steps; one attempt"))
                    val o = schedules.dao.occurrence(latest.occurrenceId) ?: return@withTransaction null
                    val v = schedules.dao.versions().first { it.id == o.versionId }
                    val cap = ReminderPolicy.offsets(v.reminderLevel, v.reminderOffsets).size
                    val attempts = db.reminders().all().count { it.occurrenceId == o.id && it.operationalDate == o.operationalDate && it.attemptedAt != null }
                    if (o.state !in OPEN || attempts >= cap) {
                        db.reminders().put(latest.copy(result = "CANCELLED", detail = "Resolved or attempt cap reached")); null
                    } else latest.copy(result = "CLAIMED", attemptedAt = now).also { db.reminders().put(it) }
                } ?: continue
                db.withTransaction {
                    val o = schedules.dao.occurrence(claim.occurrenceId)
                    if (o == null || o.state !in OPEN || o.startAt != claim.scheduleStart ||
                        db.foundation().commitments().first { it.id == o.commitmentId }.archivedAt != null) {
                        db.reminders().put(claim.copy(result = "CANCELLED", detail = "Resolved before post"))
                    } else {
                        val v = schedules.dao.versions().first { it.id == o.versionId }
                        if (!platform.allowed(v.category)) db.reminders().put(claim.copy(result = "BLOCKED", detail = "Notifications or channel are off"))
                        else try {
                            val title = schedules.dao.overrides().find { it.occurrenceId == o.id }?.title ?: v.title
                            platform.post(claim, o, v, title, schedules.settings().privacyMode)
                            db.reminders().put(claim.copy(result = "POSTED", postedAt = clock.now().toEpochMilli(), detail = "Post requested; viewing is unknown"))
                            schedules.dao.upsertOccurrence(o.copy(delivered = true))
                        } catch (_: SecurityException) {
                            db.reminders().put(claim.copy(result = "BLOCKED", detail = "Permission changed before post"))
                        } catch (_: RuntimeException) {
                            db.reminders().put(claim.copy(result = "FAILED", detail = "Android rejected the post; attempt not retried"))
                        }
                    }
                }
            }
        }
        val versions = schedules.dao.versions().associateBy { it.id }
        val next = db.reminders().all().filter { it.result == "PLANNED" && platform.allowed(versions.getValue(it.versionId).category) }
            .minOfOrNull { it.plannedAt }
        platform.schedule(next)
    }

    suspend fun observed(deliveryId: String): ReminderDelivery? = db.withTransaction {
        db.reminders().get(deliveryId)?.also { row ->
            if (row.observedAt == null) db.reminders().put(row.copy(observedAt = clock.now().toEpochMilli()))
        }
    }

    suspend fun settings(start: String, end: String, privacy: Boolean) {
        LocalTime.parse(start); LocalTime.parse(end)
        db.withTransaction {
            val old = schedules.settings()
            db.foundation().updateSettings(old.copy(quietStart = start, quietEnd = end, privacyMode = privacy))
            for (row in db.reminders().all()) {
                if (row.result == "PLANNED") db.reminders().put(row.copy(result = "CANCELLED", detail = "Reminder settings changed"))
                platform.cancelNotification(row.occurrenceId)
            }
            db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), "AppSettings", old.profileId, "REMINDER_SETTINGS",
                "Quiet hours $start–$end; lock-screen privacy $privacy", clock.now().toEpochMilli()))
        }
        reconcile()
    }

    companion object {
        val OPEN = setOf("PENDING", "DUE", "OVERDUE")
        const val LATE_LIMIT = 60 * 60 * 1000L
    }
}
