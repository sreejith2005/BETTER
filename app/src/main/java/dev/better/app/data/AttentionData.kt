package dev.better.app.data

import androidx.room.*

@Entity(tableName = "attention_settings")
data class AttentionSettings(@PrimaryKey val id: Int = 1, val enabled: Boolean = false,
    val packageName: String = "com.instagram.android", val thresholdMinutes: Int = 15,
    val windows: String = "18:00-22:30", val office: String = "09:00-18:00",
    val commute: String = "", val disabledModes: String = "", val theme: String = "System")

@Entity(tableName = "attention_attempts")
data class AttentionAttempt(@PrimaryKey val id: String, val day: String, val windowKey: String,
    val attemptedAt: Long, val observedAt: Long, val usageMillis: Long, val packageName: String,
    val result: String)

@Dao
interface AttentionDao {
    @Query("SELECT * FROM attention_settings WHERE id=1") fun watchSettings(): kotlinx.coroutines.flow.Flow<AttentionSettings?>
    @Query("SELECT * FROM attention_settings WHERE id=1") suspend fun settings(): AttentionSettings?
    @Upsert suspend fun save(value: AttentionSettings)
    @Query("SELECT * FROM attention_attempts ORDER BY attemptedAt") suspend fun attempts(): List<AttentionAttempt>
    @Insert suspend fun insert(value: AttentionAttempt)
    @Update suspend fun update(value: AttentionAttempt)
}
