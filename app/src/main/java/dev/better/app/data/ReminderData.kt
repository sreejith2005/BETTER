package dev.better.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// No FK: cancelled delivery history outlives regenerated, unacted future occurrences.
@Entity(tableName = "reminder_deliveries", indices = [Index("occurrenceId"), Index("result")])
data class ReminderDelivery(
    @PrimaryKey val id: String, val occurrenceId: String, val versionId: String,
    val scheduleStart: Long, val operationalDate: String, val steps: String, val originalAt: Long, val plannedAt: Long,
    val delayed: Boolean, val result: String = "PLANNED", val attemptedAt: Long? = null,
    val postedAt: Long? = null, val observedAt: Long? = null, val detail: String = "",
)

@Dao interface ReminderDao {
    @Query("SELECT * FROM reminder_deliveries ORDER BY plannedAt") suspend fun all(): List<ReminderDelivery>
    @Query("SELECT * FROM reminder_deliveries ORDER BY plannedAt") fun watch(): Flow<List<ReminderDelivery>>
    @Query("SELECT * FROM reminder_deliveries WHERE id = :id") suspend fun get(id: String): ReminderDelivery?
    @Upsert suspend fun put(row: ReminderDelivery)
}
