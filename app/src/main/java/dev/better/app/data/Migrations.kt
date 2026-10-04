package dev.better.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE occurrences ADD COLUMN profileVersionId TEXT")
        db.execSQL("ALTER TABLE occurrences ADD COLUMN modeActivationId TEXT")
        db.execSQL("ALTER TABLE occurrences ADD COLUMN dayOverrideId TEXT")
        db.execSQL("ALTER TABLE occurrences ADD COLUMN blockDurationSeconds INTEGER")
        db.execSQL("ALTER TABLE occurrences ADD COLUMN blockFixed INTEGER")
        db.execSQL("""CREATE TABLE IF NOT EXISTS adaptive_records (
            id TEXT NOT NULL PRIMARY KEY, kind TEXT NOT NULL, family TEXT NOT NULL, title TEXT NOT NULL,
            fromDate TEXT NOT NULL, untilDate TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL)""")
        db.execSQL("CREATE INDEX index_adaptive_records_kind ON adaptive_records(kind)")
        db.execSQL("CREATE INDEX index_adaptive_records_family ON adaptive_records(family)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS routine_runs (
            id TEXT NOT NULL PRIMARY KEY, occurrenceId TEXT NOT NULL, versionId TEXT NOT NULL, title TEXT NOT NULL,
            steps TEXT NOT NULL, results TEXT NOT NULL, startedAt INTEGER NOT NULL, finishedAt INTEGER)""")
        db.execSQL("CREATE UNIQUE INDEX index_routine_runs_occurrenceId ON routine_runs(occurrenceId)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS focus_sessions (
            id TEXT NOT NULL PRIMARY KEY, activity TEXT NOT NULL, company TEXT NOT NULL, role TEXT NOT NULL,
            rescue INTEGER NOT NULL, continued INTEGER NOT NULL, state TEXT NOT NULL, owner TEXT NOT NULL,
            createdAt INTEGER NOT NULL, endedAt INTEGER, recovery TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS focus_segments (
            id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, startWall INTEGER NOT NULL,
            startElapsed INTEGER NOT NULL, boot INTEGER NOT NULL, zoneId TEXT NOT NULL, boundary TEXT NOT NULL,
            durationMillis INTEGER NOT NULL, closed INTEGER NOT NULL, candidateMillis INTEGER,
            FOREIGN KEY(sessionId) REFERENCES focus_sessions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX index_focus_segments_sessionId ON focus_segments(sessionId)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE commitment_versions ADD COLUMN criticalReminder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("""CREATE TABLE IF NOT EXISTS reminder_deliveries (
            id TEXT NOT NULL PRIMARY KEY, occurrenceId TEXT NOT NULL, versionId TEXT NOT NULL,
            scheduleStart INTEGER NOT NULL, operationalDate TEXT NOT NULL, steps TEXT NOT NULL, originalAt INTEGER NOT NULL,
            plannedAt INTEGER NOT NULL, delayed INTEGER NOT NULL, result TEXT NOT NULL,
            attemptedAt INTEGER, postedAt INTEGER, observedAt INTEGER, detail TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX index_reminder_deliveries_occurrenceId ON reminder_deliveries(occurrenceId)")
        db.execSQL("CREATE INDEX index_reminder_deliveries_result ON reminder_deliveries(result)")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS schedule_rules (
            id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, anchor TEXT NOT NULL,
            time TEXT NOT NULL, dueKind TEXT NOT NULL, endTime TEXT NOT NULL,
            step TEXT NOT NULL, weekdays TEXT NOT NULL, offsets TEXT NOT NULL, tail TEXT,
            endDate TEXT, count INTEGER, zoneMode TEXT NOT NULL, zoneId TEXT NOT NULL,
            overlap TEXT NOT NULL, absoluteInstant INTEGER, summary TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS commitment_versions (
            id TEXT NOT NULL PRIMARY KEY, commitmentId TEXT NOT NULL, ruleId TEXT NOT NULL,
            effectiveFrom INTEGER NOT NULL, effectiveUntil INTEGER, createdAt INTEGER NOT NULL,
            title TEXT NOT NULL, category TEXT NOT NULL, priority INTEGER NOT NULL, essential INTEGER NOT NULL,
            instructionText TEXT NOT NULL, sourceText TEXT NOT NULL, durationSeconds INTEGER NOT NULL,
            fixed INTEGER NOT NULL, reminderLevel TEXT NOT NULL, reminderOffsets TEXT NOT NULL,
            FOREIGN KEY(commitmentId) REFERENCES commitments(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(ruleId) REFERENCES schedule_rules(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX index_commitment_versions_commitmentId ON commitment_versions(commitmentId)")
        db.execSQL("CREATE INDEX index_commitment_versions_ruleId ON commitment_versions(ruleId)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS occurrences (
            id TEXT NOT NULL PRIMARY KEY, commitmentId TEXT NOT NULL, versionId TEXT NOT NULL,
            plannedDate TEXT NOT NULL, operationalDate TEXT NOT NULL, startAt INTEGER NOT NULL,
            endAt INTEGER NOT NULL, closesAt INTEGER NOT NULL, zoneId TEXT NOT NULL, resolution TEXT NOT NULL,
            state TEXT NOT NULL, actualAt INTEGER, reason TEXT, acted INTEGER NOT NULL,
            delivered INTEGER NOT NULL, completedLate INTEGER NOT NULL,
            FOREIGN KEY(commitmentId) REFERENCES commitments(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(versionId) REFERENCES commitment_versions(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE INDEX index_occurrences_commitmentId ON occurrences(commitmentId)")
        db.execSQL("CREATE INDEX index_occurrences_versionId ON occurrences(versionId)")
        db.execSQL("CREATE INDEX index_occurrences_startAt ON occurrences(startAt)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS occurrence_overrides (
            id TEXT NOT NULL PRIMARY KEY, occurrenceId TEXT NOT NULL, title TEXT NOT NULL,
            instructionText TEXT NOT NULL, sourceText TEXT NOT NULL, reason TEXT NOT NULL,
            createdAt INTEGER NOT NULL, originalStart INTEGER NOT NULL,
            FOREIGN KEY(occurrenceId) REFERENCES occurrences(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
        db.execSQL("CREATE UNIQUE INDEX index_occurrence_overrides_occurrenceId ON occurrence_overrides(occurrenceId)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS attention_settings (
            id INTEGER NOT NULL PRIMARY KEY, enabled INTEGER NOT NULL, packageName TEXT NOT NULL,
            thresholdMinutes INTEGER NOT NULL, windows TEXT NOT NULL, office TEXT NOT NULL,
            commute TEXT NOT NULL, disabledModes TEXT NOT NULL, theme TEXT NOT NULL)""")
        db.execSQL("""CREATE TABLE IF NOT EXISTS attention_attempts (
            id TEXT NOT NULL PRIMARY KEY, day TEXT NOT NULL, windowKey TEXT NOT NULL,
            attemptedAt INTEGER NOT NULL, observedAt INTEGER NOT NULL, usageMillis INTEGER NOT NULL,
            packageName TEXT NOT NULL, result TEXT NOT NULL)""")
    }
}
