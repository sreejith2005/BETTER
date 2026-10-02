package dev.better.app.data

import androidx.room.*
import dev.better.app.domain.*
import kotlinx.coroutines.flow.Flow
import java.time.*

@Entity(tableName = "schedule_rules")
data class RuleRecord(
    @PrimaryKey val id: String,
    val type: String, val anchor: String, val time: String, val dueKind: String, val endTime: String,
    val step: String, val weekdays: String, val offsets: String, val tail: String?,
    val endDate: String?, val count: Int?, val zoneMode: String, val zoneId: String,
    val overlap: String, val absoluteInstant: Long?, val summary: String,
) {
    fun rule() = ScheduleRule(RuleType.valueOf(type), LocalDate.parse(anchor), LocalTime.parse(time),
        DueKind.valueOf(dueKind), LocalTime.parse(endTime), DateStep.parse(step),
        weekdays.split(',').filter { it.isNotBlank() }.map { DayOfWeek.of(it.toInt()) }.toSet(),
        DateStep.parseSequence(offsets), tail?.let(DateStep::parse), endDate?.let(LocalDate::parse), count,
        ZoneMode.valueOf(zoneMode), zoneId, OverlapChoice.valueOf(overlap), absoluteInstant?.let(Instant::ofEpochMilli))
    companion object {
        fun from(id: String, r: ScheduleRule) = RuleRecord(id, r.type.name, r.anchor.toString(), r.time.toString(),
            r.dueKind.name, r.endTime.toString(), r.step.toString(), r.weekdays.sortedBy { it.value }.joinToString(",") { it.value.toString() },
            r.offsets.joinToString(","), r.tail?.toString(), r.endDate?.toString(), r.count, r.zoneMode.name,
            r.zoneId, r.overlap.name, r.absoluteInstant?.toEpochMilli(), r.summary())
    }
}

@Entity(tableName = "commitment_versions", foreignKeys = [
    ForeignKey(entity = Commitment::class, parentColumns = ["id"], childColumns = ["commitmentId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = RuleRecord::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("commitmentId"), Index("ruleId")])
data class VersionRecord(
    @PrimaryKey val id: String, val commitmentId: String, val ruleId: String,
    val effectiveFrom: Long, val effectiveUntil: Long?, val createdAt: Long,
    val title: String, val category: String, val priority: Int, val essential: Boolean,
    val instructionText: String, val sourceText: String, val durationSeconds: Int,
    val fixed: Boolean, val reminderLevel: String, val reminderOffsets: String,
    @ColumnInfo(defaultValue = "0") val criticalReminder: Boolean = false,
)

@Entity(tableName = "occurrences", foreignKeys = [
    ForeignKey(entity = Commitment::class, parentColumns = ["id"], childColumns = ["commitmentId"], onDelete = ForeignKey.RESTRICT),
    ForeignKey(entity = VersionRecord::class, parentColumns = ["id"], childColumns = ["versionId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index("commitmentId"), Index("versionId"), Index("startAt")])
data class OccurrenceRecord(
    @PrimaryKey val id: String, val commitmentId: String, val versionId: String,
    val plannedDate: String, val operationalDate: String, val startAt: Long, val endAt: Long,
    val closesAt: Long, val zoneId: String, val resolution: String,
    val state: String = "PENDING", val actualAt: Long? = null, val reason: String? = null,
    val acted: Boolean = false, val delivered: Boolean = false, val completedLate: Boolean = false,
) {
    fun plan() = PlannedOccurrence(LocalDate.parse(plannedDate), LocalDate.parse(operationalDate),
        Instant.ofEpochMilli(startAt), Instant.ofEpochMilli(endAt), Instant.ofEpochMilli(closesAt), zoneId, resolution)
    fun protected(now: Long) = acted || delivered || state != "PENDING" || startAt <= now
}

@Entity(tableName = "occurrence_overrides", foreignKeys = [
    ForeignKey(entity = OccurrenceRecord::class, parentColumns = ["id"], childColumns = ["occurrenceId"], onDelete = ForeignKey.RESTRICT),
], indices = [Index(value = ["occurrenceId"], unique = true)])
data class OverrideRecord(
    @PrimaryKey val id: String, val occurrenceId: String, val title: String, val instructionText: String,
    val sourceText: String, val reason: String, val createdAt: Long, val originalStart: Long,
)

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedule_rules") suspend fun rules(): List<RuleRecord>
    @Query("SELECT * FROM commitment_versions ORDER BY effectiveFrom, createdAt") suspend fun versions(): List<VersionRecord>
    @Query("SELECT * FROM occurrences ORDER BY startAt, id") suspend fun occurrences(): List<OccurrenceRecord>
    @Query("SELECT * FROM occurrences WHERE id = :id") suspend fun occurrence(id: String): OccurrenceRecord?
    @Query("SELECT * FROM occurrence_overrides") suspend fun overrides(): List<OverrideRecord>
    @Query("SELECT * FROM commitment_versions ORDER BY effectiveFrom, createdAt") fun watchVersions(): Flow<List<VersionRecord>>
    @Query("SELECT * FROM occurrences ORDER BY startAt, id") fun watchOccurrences(): Flow<List<OccurrenceRecord>>
    @Query("SELECT * FROM schedule_rules") fun watchRules(): Flow<List<RuleRecord>>
    @Query("SELECT * FROM occurrence_overrides") fun watchOverrides(): Flow<List<OverrideRecord>>
    @Insert suspend fun insertRule(rule: RuleRecord)
    @Insert suspend fun insertVersion(version: VersionRecord)
    @Update suspend fun updateVersion(version: VersionRecord)
    @Update suspend fun updateCommitment(commitment: Commitment)
    @Upsert suspend fun upsertOccurrence(occurrence: OccurrenceRecord)
    @Upsert suspend fun upsertOverride(override: OverrideRecord)
    @Query("DELETE FROM occurrence_overrides WHERE occurrenceId = :id") suspend fun deleteOverride(id: String)
    @Query("DELETE FROM occurrences WHERE id = :id") suspend fun deleteExecutionChild(id: String)
    @Query("DELETE FROM occurrences WHERE id = :id AND acted = 0 AND delivered = 0 AND state = 'PENDING'") suspend fun deletePending(id: String)
}
