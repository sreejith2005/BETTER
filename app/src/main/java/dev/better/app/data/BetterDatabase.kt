package dev.better.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val profileId: String,
    val dayBoundary: String,
    val zoneId: String,
    val timezonePolicy: String = "LOCAL_FLOATING",
    val workStart: String,
    val workEnd: String,
    val careerStart: String,
    val careerEnd: String,
    val notificationsWanted: Boolean,
    val quietStart: String = "22:30",
    val quietEnd: String = "07:00",
    val privacyMode: Boolean = true,
    val createdAt: Long,
)

@Entity(tableName = "commitments")
data class Commitment(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val essential: Boolean,
    val archivedAt: Long? = null,
)

@Entity(tableName = "career_standards", foreignKeys = [ForeignKey(
    entity = Commitment::class, parentColumns = ["id"], childColumns = ["commitmentId"],
    onDelete = ForeignKey.RESTRICT,
)], indices = [Index("commitmentId"), Index(value = ["effectiveDate"], unique = true)])
data class CareerStandard(
    @PrimaryKey val id: String,
    val commitmentId: String,
    val effectiveDate: String,
    val targetSeconds: Int,
    val createdAt: Long,
)

@Entity(tableName = "action_log")
data class ActionLog(
    @PrimaryKey val id: String,
    val entity: String,
    val entityId: String,
    val action: String,
    val summary: String,
    val timestamp: Long,
)

@Dao
interface FoundationDao {
    @Query("SELECT * FROM app_settings WHERE id = 1") fun observeSettings(): Flow<AppSettings?>
    @Query("SELECT * FROM app_settings WHERE id = 1") suspend fun settings(): AppSettings?
    @Insert suspend fun insertSettings(settings: AppSettings)
    @Update suspend fun updateSettings(settings: AppSettings)
    @Insert suspend fun insertCommitment(commitment: Commitment)
    @Insert suspend fun insertStandard(standard: CareerStandard)
    @Insert suspend fun appendLog(log: ActionLog)
    @Query("SELECT * FROM career_standards ORDER BY effectiveDate") fun observeStandards(): Flow<List<CareerStandard>>
    @Query("SELECT * FROM commitments ORDER BY id") suspend fun commitments(): List<Commitment>
    @Query("SELECT * FROM commitments ORDER BY title") fun observeCommitments(): Flow<List<Commitment>>
    @Query("SELECT * FROM career_standards ORDER BY effectiveDate") suspend fun standards(): List<CareerStandard>
    @Query("SELECT * FROM action_log ORDER BY timestamp, rowid") suspend fun logs(): List<ActionLog>
    @Query("SELECT * FROM action_log ORDER BY timestamp, rowid") fun watchLogs(): Flow<List<ActionLog>>
}

@Database(entities = [AppSettings::class, Commitment::class, CareerStandard::class, ActionLog::class,
    RuleRecord::class, VersionRecord::class, OccurrenceRecord::class, OverrideRecord::class,
    ReminderDelivery::class, FocusSession::class, FocusSegment::class], version = 4, exportSchema = true)
abstract class BetterDatabase : RoomDatabase() {
    abstract fun focus(): FocusDao
    abstract fun foundation(): FoundationDao
    abstract fun schedules(): ScheduleDao
    abstract fun reminders(): ReminderDao
    companion object {
        // Version 1 is the initial schema. Each subsequent change must register
        // an explicit migration here and verify preservation with MigrationTestHelper.
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
        fun open(context: Context, name: String = "better.db"): BetterDatabase =
            Room.databaseBuilder(context, BetterDatabase::class.java, name)
                .addMigrations(*MIGRATIONS).build()
    }
}
