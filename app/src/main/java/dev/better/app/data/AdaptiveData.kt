package dev.better.app.data

import androidx.room.*
import dev.better.app.domain.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** Immutable, format-versioned snapshots. family is the stable routine/profile identity. */
@Entity(tableName = "adaptive_records", indices = [Index("kind"), Index("family")])
data class AdaptiveRecord(@PrimaryKey val id: String, val kind: String, val family: String,
    val title: String, val fromDate: String, val untilDate: String, val payload: String, val createdAt: Long)

@Entity(tableName = "routine_runs", indices = [Index(value = ["occurrenceId"], unique = true)])
data class RoutineRun(@PrimaryKey val id: String, val occurrenceId: String, val versionId: String,
    val title: String, val steps: String, val results: String, val startedAt: Long, val finishedAt: Long?)

@Dao
interface AdaptiveDao {
    @Query("SELECT * FROM adaptive_records ORDER BY createdAt, rowid") suspend fun records(): List<AdaptiveRecord>
    @Query("SELECT * FROM adaptive_records ORDER BY createdAt, rowid") fun watchRecords(): Flow<List<AdaptiveRecord>>
    @Insert suspend fun insert(record: AdaptiveRecord)
    @Query("SELECT * FROM routine_runs ORDER BY startedAt") suspend fun runs(): List<RoutineRun>
    @Query("SELECT * FROM routine_runs ORDER BY startedAt") fun watchRuns(): Flow<List<RoutineRun>>
    @Insert suspend fun insertRun(run: RoutineRun)
    @Update suspend fun updateRun(run: RoutineRun)
}

object AdaptiveJson {
    fun strings(values: List<String>) = JSONArray(values).toString()
    fun strings(raw: String): List<String> = JSONArray(raw).let { a -> (0 until a.length()).map(a::getString) }
    fun policy(p: DayPolicy): JSONObject = JSONObject().put("format", 1).put("essentialOnly", p.essentialOnly)
        .put("reason", p.reason).put("items", JSONArray().also { a -> p.items.forEach { a.put(JSONObject()
            .put("id", it.commitmentId).put("disposition", it.disposition.name).put("time", it.time)) } })
    fun policy(o: JSONObject) = DayPolicy(o.optBoolean("essentialOnly"), o.optJSONArray("items")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { v -> ItemPolicy(v.getString("id"), Disposition.valueOf(v.getString("disposition")), v.optString("time")) } }
        } ?: emptyList(), o.optString("reason"))
    fun blocks(blocks: List<ProfileBlock>) = JSONObject().put("format", 1).put("blocks", JSONArray().also { a ->
        blocks.forEach { a.put(JSONObject().put("title", it.title).put("time", it.time).put("minutes", it.minutes)
            .put("fixed", it.fixed).put("commitment", it.commitmentId).put("routine", it.routineId).put("priority", it.priority)) }
    }).toString()
    fun blocks(raw: String): List<ProfileBlock> = JSONObject(raw).getJSONArray("blocks").let { a ->
        (0 until a.length()).map { a.getJSONObject(it).let { b -> ProfileBlock(b.getString("title"), b.getString("time"),
            b.getInt("minutes"), b.getBoolean("fixed"), b.optString("commitment"), b.optString("routine"), b.optInt("priority", 2)) } }
    }
}

data class AdaptiveSnapshot(val records: List<AdaptiveRecord>) {
    fun version(kind: String, family: String, date: String) = records.lastOrNull { it.kind == kind && it.family == family && it.fromDate <= date }
    fun profile(date: String): AdaptiveRecord? {
        val weekday = java.time.LocalDate.parse(date).dayOfWeek.value.toString()
        val candidates = records.filter { it.kind == "ASSIGN" && it.fromDate <= date && (it.untilDate.isEmpty() || it.untilDate >= date) }
        val assignment = candidates.lastOrNull { it.fromDate == date && it.untilDate == date }
            ?: candidates.lastOrNull { JSONObject(it.payload).getString("weekdays").split(',').contains(weekday) }
        return assignment?.let { version("PROFILE", it.family, date) }
    }
    fun mode(date: String) = records.lastOrNull { it.kind == "MODE" && it.fromDate <= date && it.untilDate >= date }
    fun day(date: String) = records.lastOrNull { it.kind == "DAY" && it.fromDate == date }
    fun routine(commitment: String, date: String): AdaptiveRecord? {
        val link = records.lastOrNull { it.kind == "LINK" && it.family == commitment && it.fromDate <= date }
        val family = link?.payload?.let { JSONObject(it).getString("routine") }
            ?: profile(date)?.let { AdaptiveJson.blocks(it.payload).firstOrNull { b -> b.commitmentId == commitment }?.routineId }
        return family?.let { version("ROUTINE", it, date) }
    }
    fun apply(base: dev.better.app.domain.PlannedOccurrence, v: VersionRecord, boundary: java.time.LocalTime): AdaptiveResult {
        val date = base.calendarDate.toString()
        val profile = profile(date)
        val result = adaptDay(base, v.commitmentId, v.essential, v.fixed,
            profile?.let { AdaptiveJson.blocks(it.payload).firstOrNull { b -> b.commitmentId == v.commitmentId } },
            mode(date)?.let { AdaptiveJson.policy(JSONObject(it.payload)) }, day(date)?.let { AdaptiveJson.policy(JSONObject(it.payload)) }, boundary)
        val sources = listOfNotNull(profile?.let { "Profile ${it.title} · version from ${it.fromDate}" }, mode(date)?.let { "Mode ${it.title} · ${it.fromDate}–${it.untilDate}" }, day(date)?.let { "Day override ${it.fromDate}" })
        return result.copy(notes = sources + result.notes)
    }
}
