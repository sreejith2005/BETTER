package dev.better.app.data

import androidx.room.*

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey val id: String,
    val activity: String,
    val company: String,
    val role: String,
    val rescue: Boolean,
    val continued: Boolean,
    val state: String,
    val owner: String,
    val createdAt: Long,
    val endedAt: Long? = null,
    val recovery: String = "",
)

@Entity(tableName = "focus_segments", foreignKeys = [ForeignKey(
    entity = FocusSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.RESTRICT
)], indices = [Index("sessionId")])
data class FocusSegment(
    @PrimaryKey val id: String,
    val sessionId: String,
    val startWall: Long,
    val startElapsed: Long,
    val boot: Int,
    val zoneId: String,
    val boundary: String,
    val durationMillis: Long = 0,
    val closed: Boolean = false,
    val candidateMillis: Long? = null,
)

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY createdAt, rowid") suspend fun sessions(): List<FocusSession>
    @Query("SELECT * FROM focus_segments ORDER BY startWall, rowid") suspend fun segments(): List<FocusSegment>
    @Insert suspend fun insertSession(value: FocusSession)
    @Update suspend fun updateSession(value: FocusSession)
    @Insert suspend fun insertSegment(value: FocusSegment)
    @Update suspend fun updateSegment(value: FocusSegment)
}
