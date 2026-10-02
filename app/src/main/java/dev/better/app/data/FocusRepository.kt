package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.UUID

data class FocusSnapshot(
    val sessions: List<FocusSession> = emptyList(),
    val segments: List<FocusSegment> = emptyList(),
    val dailyMillis: Map<LocalDate, Long> = emptyMap(),
    val standards: List<CareerStandard> = emptyList(),
    val actions: List<ActionLog> = emptyList(),
) {
    val active get() = sessions.lastOrNull { it.endedAt == null }
    fun sessionMillis(id: String) = segments.filter { it.sessionId == id }.sumOf { it.durationMillis }
    fun target(date: LocalDate) = standards.lastOrNull { it.effectiveDate <= date.toString() }?.targetSeconds ?: 1800
}

/** One application-scoped owner survives activity changes, but never process death. */
class FocusRepository(val db: BetterDatabase, val clock: AppClock, private val bootCount: () -> Int,
    private val owner: String = UUID.randomUUID().toString()) {
    private val mutex = Mutex()
    private val dao get() = db.focus()
    private fun id() = UUID.randomUUID().toString()
    private suspend fun log(session: String, action: String, detail: String) = db.foundation().appendLog(
        ActionLog(id(), "FocusSession", session, "FOCUS_$action", detail, clock.now().toEpochMilli()))
    private suspend fun current() = dao.sessions().lastOrNull { it.endedAt == null }
    private suspend fun newSegment(session: FocusSession) {
        val settings = requireNotNull(db.foundation().settings())
        val zone = if (settings.timezonePolicy == "LOCAL_FLOATING") clock.zone().id else settings.zoneId
        dao.insertSegment(FocusSegment(id(), session.id, clock.now().toEpochMilli(), clock.elapsedMillis(),
            bootCount(), zone, settings.dayBoundary))
    }
    private suspend fun duration(session: FocusSession, segment: FocusSegment): Long {
        val other = dao.segments().filter { it.sessionId == session.id && it.id != segment.id }.sumOf { it.durationMillis }
        return focusDuration(segment.startElapsed, clock.elapsedMillis(), segment.durationMillis,
            if (session.rescue && !session.continued) RESCUE_MILLIS - other else null)
    }
    private suspend fun sample(force: Boolean = false): List<FocusSegment> {
        val s = current() ?: return dao.segments()
        val segment = dao.segments().lastOrNull { it.sessionId == s.id && !it.closed } ?: return dao.segments()
        val sameBoot = segment.boot >= 0 && segment.boot == bootCount() && clock.elapsedMillis() >= segment.startElapsed + segment.durationMillis
        if (s.owner != owner || !sameBoot) {
            val candidate = if (sameBoot) duration(s, segment) else null
            dao.updateSegment(segment.copy(closed = true, candidateMillis = candidate))
            dao.updateSession(s.copy(state = if (sameBoot) "REVIEW" else "INTERRUPTED", owner = owner,
                recovery = if (sameBoot) "Confirm whether work stayed active until BETTER reopened. Time awaiting confirmation is excluded."
                else "Device restarted or clock continuity was lost. Only the last saved checkpoint counts. Resume or stop."))
            log(s.id, "INTERRUPTED", if (sameBoot) "Same boot; uncertain time awaiting confirmation" else "Reboot/unknown boot; checkpoint only")
            return dao.segments()
        }
        val elapsed = duration(s, segment)
        val others = dao.segments().filter { it.sessionId == s.id && it.id != segment.id }.sumOf { it.durationMillis }
        val rescueReady = s.rescue && !s.continued && elapsed + others >= RESCUE_MILLIS
        val settings = requireNotNull(db.foundation().settings())
        val zone = if (settings.timezonePolicy == "LOCAL_FLOATING") clock.zone().id else settings.zoneId
        val clockChanged = kotlin.math.abs(clock.now().toEpochMilli() - (segment.startWall + (clock.elapsedMillis() - segment.startElapsed))) > 2000 || zone != segment.zoneId
        val sampled = segment.copy(durationMillis = elapsed, closed = rescueReady || clockChanged)
        if (force || elapsed - segment.durationMillis >= 5000 || rescueReady || clockChanged) dao.updateSegment(sampled)
        if (rescueReady) {
            dao.updateSession(s.copy(state = "RESCUE_READY"))
            log(s.id, "RESCUE_READY", "Five active minutes saved; waiting for Continue or Stop")
        } else if (clockChanged) {
            log(s.id, "CLOCK_CHANGE", "Duration kept monotonic; next segment uses current wall time and timezone")
            newSegment(s)
        }
        return dao.segments().map { if (it.id == segment.id) sampled else it }
    }
    suspend fun snapshot(): FocusSnapshot = mutex.withLock { db.withTransaction {
        val segments = sample()
        val totals = mutableMapOf<LocalDate, Long>()
        segments.forEach { segment -> allocateFocus(segment.startWall, segment.durationMillis,
            ZoneId.of(segment.zoneId), LocalTime.parse(segment.boundary)).forEach { (date, millis) ->
            totals[date] = (totals[date] ?: 0) + millis
        } }
        val standards = db.foundation().standards()
        // Persist milestones only from saved intervals; an unconfirmed recovery candidate never qualifies.
        val savedTotals = mutableMapOf<LocalDate, Long>()
        dao.segments().forEach { segment -> allocateFocus(segment.startWall, segment.durationMillis,
            ZoneId.of(segment.zoneId), LocalTime.parse(segment.boundary)).forEach { (date, millis) ->
            savedTotals[date] = (savedTotals[date] ?: 0) + millis
        } }
        val milestones = db.foundation().logs().filter { it.action == "FOCUS_MINIMUM_MET" }.map { it.entityId }.toSet()
        savedTotals.forEach { (date, millis) ->
            val target = standards.lastOrNull { it.effectiveDate <= date.toString() }?.targetSeconds ?: 1800
            if (millis >= target * 1000L && date.toString() !in milestones) log(date.toString(), "MINIMUM_MET", "$date: ${target / 60} minute minimum met; timed evidence")
        }
        FocusSnapshot(dao.sessions(), segments, totals, standards, db.foundation().logs().filter { it.action.startsWith("FOCUS_") })
    } }
    suspend fun start(activity: String, rescue: Boolean, confirmedWork: Boolean, company: String = "", role: String = "") = mutex.withLock { db.withTransaction {
        require(activity in careerActivities) { "Choose a Career activity." }
        require(confirmedWork) { "Confirm deliberate Career work; passive scrolling does not count." }
        require(company.length <= 200 && role.length <= 200) { "Use at most 200 characters for company and role." }
        sample(true)
        if (current() != null) return@withTransaction
        val session = FocusSession(id(), activity, company.trim(), role.trim(), rescue, false, "RUNNING", owner, clock.now().toEpochMilli())
        dao.insertSession(session); newSegment(session)
        log(session.id, "START", "$activity; timed, user-confirmed deliberate work; rescue=$rescue; application details are self-reported")
    } }
    suspend fun act(sessionId: String, action: String) = mutex.withLock { db.withTransaction {
        sample(true)
        val s = current()?.takeIf { it.id == sessionId } ?: return@withTransaction
        when (action) {
            "PAUSE", "STOP" -> {
                // Review must be resolved explicitly before stopping, to avoid silently accepting time.
                require(s.state != "REVIEW") { "Confirm or discard uncertain time first." }
                if (action == "PAUSE" && s.state != "RUNNING") return@withTransaction
                dao.segments().filter { it.sessionId == s.id && !it.closed }.forEach { dao.updateSegment(it.copy(closed = true)) }
                dao.updateSession(s.copy(state = if (action == "STOP") "COMPLETED" else "PAUSED",
                    endedAt = if (action == "STOP") clock.now().toEpochMilli() else null))
            }
            "RESUME", "CONTINUE" -> {
                if (action == "CONTINUE" && s.state != "RESCUE_READY") return@withTransaction
                if (action == "RESUME" && s.state !in listOf("PAUSED", "INTERRUPTED")) return@withTransaction
                val next = s.copy(state = "RUNNING", continued = s.continued || action == "CONTINUE", owner = owner, recovery = "")
                dao.updateSession(next); newSegment(next)
            }
            "CONFIRM", "DISCARD" -> {
                if (s.state != "REVIEW") return@withTransaction
                dao.segments().filter { it.sessionId == s.id && it.candidateMillis != null }.forEach {
                    dao.updateSegment(it.copy(durationMillis = if (action == "CONFIRM") it.candidateMillis!! else it.durationMillis, candidateMillis = null))
                }
                val total = dao.segments().filter { it.sessionId == s.id }.sumOf { it.durationMillis }
                dao.updateSession(s.copy(state = if (s.rescue && !s.continued && total >= RESCUE_MILLIS) "RESCUE_READY" else "PAUSED",
                    recovery = "Recovery reviewed. Resume or stop; review time does not count."))
            }
            else -> error("Unknown timer action")
        }
        log(s.id, action, "Timed evidence; user action")
    } }
    suspend fun changeStandard(minutes: Int, effectiveDate: LocalDate) = mutex.withLock { db.withTransaction {
        require(minutes in 1..1440) { "Enter a target from 1 to 1440 minutes." }
        val settings = requireNotNull(db.foundation().settings())
        val zone = if (settings.timezonePolicy == "LOCAL_FLOATING") clock.zone() else ZoneId.of(settings.zoneId)
        val today = operationalDate(clock.now(), zone, LocalTime.parse(settings.dayBoundary))
        require(effectiveDate > today) { "Choose tomorrow or a later operational date to preserve history." }
        val standards = db.foundation().standards()
        require(standards.none { it.effectiveDate == effectiveDate.toString() }) { "A target already starts on that date. Choose another date." }
        val seed = standards.first()
        db.foundation().insertStandard(CareerStandard(id(), seed.commitmentId, effectiveDate.toString(), minutes * 60, clock.now().toEpochMilli()))
        log(seed.commitmentId, "STANDARD", "$minutes minutes from $effectiveDate; prior days unchanged")
    } }
}
