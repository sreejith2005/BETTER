package dev.better.app.data

import android.content.Context
import android.database.Cursor
import androidx.room.withTransaction
import dev.better.app.domain.*
import org.json.*
import java.time.*
import java.util.UUID

/** Format 1 is a complete typed-row archive. No DB file, permissions or platform notification IDs. */
class ArchiveRepository(private val context: Context, val db: BetterDatabase) {
    companion object {
        const val MAX_BYTES = 20 * 1024 * 1024
        val tables = listOf("app_settings", "commitments", "career_standards", "schedule_rules", "commitment_versions",
            "occurrences", "occurrence_overrides", "action_log", "reminder_deliveries", "focus_sessions", "focus_segments", "adaptive_records", "routine_runs", "attention_settings", "attention_attempts")
    }
    class Preview internal constructor(internal val raw: String, internal val baseline: String, val counts: Map<String, Int>, val exportedAt: String)
    suspend fun export(): String = db.withTransaction {
        require(db.focus().sessions().none { it.endedAt == null }) { "Stop or finish the current Career session before exporting." }
        envelope(readRows()).toString().also { require(it.toByteArray().size <= MAX_BYTES) { "Archive exceeds 20 MiB." } }
    }
    private fun envelope(rows: JSONObject) = JSONObject().put("format", "BETTER").put("archiveVersion", 1).put("roomVersion", 6)
        .put("exportedAt", Instant.now().toString()).put("tables", rows)
    private fun readRows(): JSONObject {
        val out = JSONObject()
        tables.forEach { table ->
            val rows = JSONArray()
            db.openHelper.readableDatabase.query("SELECT * FROM $table ORDER BY rowid").use { c ->
                while (c.moveToNext()) {
                    val row = JSONObject()
                    c.columnNames.forEachIndexed { index, column -> row.put(column, when(c.getType(index)) {
                        Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(index)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(index)
                        Cursor.FIELD_TYPE_STRING -> c.getString(index)
                        else -> error("Unsupported archive value")
                    }) }
                    rows.put(row)
                }
            }
            out.put(table, rows)
        }
        return out
    }
    private fun signature() = java.security.MessageDigest.getInstance("SHA-256").digest(readRows().toString().toByteArray()).joinToString("") { "%02x".format(it) }
    suspend fun preview(raw: String): Preview {
        require(raw.toByteArray().size <= MAX_BYTES) { "Archive exceeds 20 MiB." }
        val parser = JSONTokener(raw)
        val root = parser.nextValue() as? JSONObject ?: error("Invalid JSON archive.")
        require(parser.nextClean() == '\u0000') { "Trailing content in archive." }
        require(root.getString("format") == "BETTER" && root.getInt("archiveVersion") == 1 && root.getInt("roomVersion") == 6) { "Incompatible archive version." }
        Instant.parse(root.getString("exportedAt"))
        val rows = root.getJSONObject("tables")
        require(rows.keys().asSequence().toSet() == tables.toSet()) { "Missing or unknown archive tables." }
        val name = "archive-stage-${UUID.randomUUID()}.db"
        val staging = BetterDatabase.open(context, name)
        try {
            staging.withTransaction { insertRows(staging, rows); validate(staging) }
        } finally { staging.close(); context.deleteDatabase(name) }
        return db.withTransaction { Preview(raw, signature(), tables.associateWith { rows.getJSONArray(it).length() }, root.getString("exportedAt")) }
    }
    suspend fun replace(preview: Preview, confirmed: Boolean) {
        require(confirmed) { "Confirm replacement after reviewing the archive." }
        // Revalidate immutable bytes before touching the target. No partial import on any failure.
        val checked = preview(preview.raw)
        db.withTransaction {
            require(signature() == preview.baseline) { "Local data changed. Select the archive again for a fresh preview." }
            require(db.focus().sessions().none { it.endedAt == null }) { "Stop Career before importing." }
            val rows = JSONObject(checked.raw).getJSONObject("tables")
            tables.reversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
            insertRows(db, rows)
            validate(db)
        }
        db.invalidationTracker.refreshAsync()
    }
    suspend fun deleteLocal(confirmation: String) {
        require(confirmation == "DELETE MY LOCAL DATA") { "Type DELETE MY LOCAL DATA exactly." }
        db.withTransaction {
            require(db.focus().sessions().none { it.endedAt == null }) { "Stop Career before deleting." }
            tables.reversed().forEach { db.openHelper.writableDatabase.execSQL("DELETE FROM $it") }
        }
        // Deliberately never touch files/, preservation snapshots, or external documents.
        db.invalidationTracker.refreshAsync()
    }
    private fun insertRows(target: BetterDatabase, rows: JSONObject) {
        val sql = target.openHelper.writableDatabase
        var count = 0
        tables.forEach { table ->
            val columns = mutableListOf<Triple<String, String, Boolean>>()
            sql.query("PRAGMA table_info($table)").use { c -> while(c.moveToNext()) columns += Triple(c.getString(1), c.getString(2), c.getInt(3) == 1) }
            val array = rows.getJSONArray(table)
            count += array.length(); require(count <= 200_000) { "Archive has too many rows." }
            val statement = sql.compileStatement("INSERT INTO $table (${columns.joinToString { it.first }}) VALUES (${columns.joinToString { "?" }})")
            try { for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                require(row.keys().asSequence().toSet() == columns.map { it.first }.toSet()) { "Invalid columns in $table." }
                statement.clearBindings()
                columns.forEachIndexed { index, (column, type, required) ->
                    val value = row.get(column)
                    when {
                        value == JSONObject.NULL -> { require(!required) { "Missing $table.$column" }; statement.bindNull(index + 1) }
                        type == "INTEGER" -> { require(value is Int || value is Long) { "Invalid number in $table.$column" }
                            if (column in setOf("enabled", "notificationsWanted", "privacyMode", "essential", "fixed", "criticalReminder", "acted", "delivered", "completedLate", "blockFixed", "delayed", "rescue", "continued", "closed")) require((value as Number).toLong() in 0..1)
                            statement.bindLong(index + 1, (value as Number).toLong()) }
                        type == "TEXT" -> { require(value is String) { "Invalid text in $table.$column" }; statement.bindString(index + 1, value) }
                        else -> error("Unsupported column")
                    }
                }
                statement.executeInsert()
            } } finally { statement.close() }
        }
    }
    private suspend fun validate(target: BetterDatabase) {
        target.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { require(!it.moveToFirst()) { "Broken archive references." } }
        val settings = requireNotNull(target.foundation().settings()) { "Archive has no settings." }
        require(settings.id == 1)
        target.openHelper.readableDatabase.query("SELECT count(*) FROM app_settings").use { it.moveToFirst(); require(it.getInt(0) == 1) }
        target.openHelper.readableDatabase.query("SELECT count(*) FROM attention_settings WHERE id != 1").use { it.moveToFirst(); require(it.getInt(0) == 0) }
        require(settings.timezonePolicy in listOf("LOCAL_FLOATING", "ANCHORED_ZONE"))
        ZoneId.of(settings.zoneId)
        listOf(settings.dayBoundary, settings.workStart, settings.workEnd, settings.careerStart, settings.careerEnd, settings.quietStart, settings.quietEnd).forEach(LocalTime::parse)
        val rules = target.schedules().rules().associateBy { it.id }
        rules.values.forEach { it.rule().validate() }
        val commitments = target.foundation().commitments().map { it.id }.toSet()
        val versions = target.schedules().versions().associateBy { it.id }
        versions.values.forEach {
            require(it.commitmentId in commitments && it.ruleId in rules && it.durationSeconds >= 0 && it.priority in 1..3)
            require(it.effectiveUntil == null || it.effectiveUntil >= it.effectiveFrom)
            CommitmentDraft(it.title, rules.getValue(it.ruleId).rule(), it.category, it.priority, it.essential,
                it.instructionText, it.sourceText, it.durationSeconds, it.fixed, it.reminderLevel, it.reminderOffsets, it.criticalReminder).validate()
            require(it.category in listOf("GENERAL", "CAREER", "HEALTH", "PERSONAL", "WORK", "FITNESS", "APPOINTMENT"))
        }
        val occurrences = target.schedules().occurrences().associateBy { it.id }
        occurrences.values.forEach { o ->
            o.plan(); require(versions[o.versionId]?.commitmentId == o.commitmentId && o.endAt >= o.startAt)
            require(o.state in listOf("PENDING", "DUE", "OVERDUE", "COMPLETED", "SKIPPED", "CANCELLED", "MISSED", "SKIPPED_BY_OVERRIDE"))
        }
        target.foundation().standards().forEach { LocalDate.parse(it.effectiveDate); require(it.targetSeconds > 0) }
        val records = target.adaptive().records()
        val routines = records.filter { it.kind == "ROUTINE" }.map { it.family }.toSet()
        val profiles = records.filter { it.kind == "PROFILE" }.map { it.family }.toSet()
        records.forEach { r ->
            LocalDate.parse(r.fromDate); if (r.untilDate.isNotEmpty()) require(LocalDate.parse(r.untilDate) >= LocalDate.parse(r.fromDate))
            val json = JSONObject(r.payload); require(json.getInt("format") == 1)
            when(r.kind) {
                "ROUTINE" -> { val steps = AdaptiveJson.strings(json.getJSONArray("steps").toString()); require(steps.size in 1..50 && steps.all { it.isNotBlank() }) }
                "PROFILE" -> AdaptiveJson.blocks(r.payload).also { require(it.isNotEmpty()) }.forEach { b -> LocalTime.parse(b.time); require(b.minutes in 1..1440 && b.priority in 1..3); require(b.commitmentId.isEmpty() || b.commitmentId in commitments); require(b.routineId.isEmpty() || b.routineId in routines) }
                "ASSIGN" -> { require(r.family in profiles); require(json.getString("weekdays").split(',').all { it.toInt() in 1..7 }) }
                "LINK" -> { require(r.family in commitments); require(json.getString("routine") in routines) }
                "MODE", "DAY" -> {
                    require(r.untilDate.isNotEmpty()); if (r.kind == "DAY") require(r.fromDate == r.untilDate)
                    val policy = AdaptiveJson.policy(json); require(policy.reason.isNotBlank())
                    policy.items.forEach { require(it.commitmentId in commitments); if (it.disposition == Disposition.MOVE) LocalTime.parse(it.time) }
                }
                else -> error("Unknown adaptive record")
            }
        }
        target.adaptive().runs().forEach { r ->
            require(r.occurrenceId in occurrences && records.any { it.id == r.versionId && it.kind == "ROUTINE" })
            val steps = AdaptiveJson.strings(r.steps); val results = AdaptiveJson.strings(r.results)
            require(steps.size == results.size && results.all { it in listOf("PENDING", "COMPLETED", "SKIPPED") })
        }
        require(target.focus().sessions().none { it.endedAt == null }) { "Archive contains an unfinished timer; finish it on the source phone before export." }
        target.focus().sessions().forEach { require(it.activity in careerActivities && it.state in listOf("COMPLETED", "ABANDONED")) }
        target.focus().segments().forEach { require(it.closed && it.durationMillis >= 0); ZoneId.of(it.zoneId); LocalTime.parse(it.boundary) }
        target.attention().settings()?.let(::validateAttention)
        target.reminders().all().forEach { require(it.versionId in versions); LocalDate.parse(it.operationalDate) }
    }
}

