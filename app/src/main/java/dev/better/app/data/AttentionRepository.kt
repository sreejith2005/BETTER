package dev.better.app.data

import androidx.room.withTransaction
import dev.better.app.domain.*
import dev.better.app.platform.UsageObservation
import java.time.*
import java.util.UUID

fun parseAttentionWindows(raw: String): List<AttentionWindow> = raw.split(',').filter { it.isNotBlank() }.map {
    val parts = it.trim().split('-')
    require(parts.size == 2) { "Use HH:mm-HH:mm, separated by commas." }
    AttentionWindow(LocalTime.parse(parts[0]), LocalTime.parse(parts[1])).also { w -> require(w.start != w.end) { "Window endpoints must differ." } }
}
fun validateAttention(s: AttentionSettings) {
    require(s.packageName.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Enter a valid package name." }
    require(s.thresholdMinutes in 1..240) { "Threshold must be 1–240 minutes." }
    require(parseAttentionWindows(s.windows).isNotEmpty()) { "Add an eligible window." }
    parseAttentionWindows(s.office); parseAttentionWindows(s.commute)
    require(s.theme in listOf("System", "Light", "Dark"))
}

class AttentionRepository(val db: BetterDatabase, val clock: AppClock) {
    suspend fun settings(): AttentionSettings = db.attention().settings() ?: db.foundation().settings().let {
        val end = if (it != null && it.quietStart == it.workEnd) it.workStart else it?.quietStart ?: "22:30"
        AttentionSettings(windows = "${it?.workEnd ?: "18:00"}-$end", office = "${it?.workStart ?: "09:00"}-${it?.workEnd ?: "18:00"}")
    }
    suspend fun save(s: AttentionSettings) { validateAttention(s); db.withTransaction {
        db.attention().save(s)
        db.foundation().appendLog(ActionLog(UUID.randomUUID().toString(), "Attention", "1", "ATTENTION_SETTINGS", "Rule ${if(s.enabled) "on" else "off"}; selected package ${s.packageName}", clock.now().toEpochMilli()))
    } }
    suspend fun evaluate(observation: UsageObservation, focus: FocusSnapshot, notificationsAllowed: Boolean,
        post: (String, Boolean) -> Unit): String = db.withTransaction {
        val s = settings()
        val app = db.foundation().settings() ?: return@withTransaction "Setup required"
        val now = clock.now()
        val zone = if (app.timezonePolicy == "LOCAL_FLOATING") clock.zone() else ZoneId.of(app.zoneId)
        val local = now.atZone(zone)
        val day = operationalDate(now, zone, LocalTime.parse(app.dayBoundary))
        val windows = parseAttentionWindows(s.windows)
        val exclusions = parseAttentionWindows(s.office) + parseAttentionWindows(s.commute)
        val window = windows.firstOrNull { it.contains(local.toLocalTime()) }
        val windowDate = if (window != null && window.start > window.end && local.toLocalTime() < window.end) local.toLocalDate().minusDays(1) else local.toLocalDate()
        val key = "$windowDate|${window?.start}|${window?.end}"
        val from = window?.let { windowDate.atTime(it.start).atZone(zone).toInstant().toEpochMilli() } ?: now.toEpochMilli()
        val usage = attentionMillis(observation.intervals.orEmpty(), from, now.toEpochMilli(), zone, listOfNotNull(window), exclusions)
        val attempts = db.attention().attempts()
        val mode = AdaptiveSnapshot(db.adaptive().records()).mode(local.toLocalDate().toString())
        val complete = (focus.dailyMillis[day] ?: 0) >= focus.target(day) * 1000L
        val decision = attentionDecision(s.enabled, observation.intervals != null && observation.packageName == s.packageName && now.toEpochMilli() - observation.observedAt in 0..120_000,
            window != null, exclusions.any { it.contains(local.toLocalTime()) }, AttentionWindow(LocalTime.parse(app.quietStart), LocalTime.parse(app.quietEnd)).contains(local.toLocalTime()),
            mode != null && mode.title in s.disabledModes.split(',').map { it.trim() }, focus.active?.state == "RUNNING", complete,
            usage, s.thresholdMinutes, now.toEpochMilli(), attempts.maxOfOrNull { it.attemptedAt }, attempts.count { it.day == day.toString() }, attempts.any { it.windowKey == key })
        if (!decision.eligible) return@withTransaction decision.reason
        if (!notificationsAllowed) return@withTransaction "Notifications unavailable; no intervention posted"
        val attempt = AttentionAttempt(UUID.randomUUID().toString(), day.toString(), key, now.toEpochMilli(), observation.observedAt, usage, s.packageName, "CLAIMED")
        db.attention().insert(attempt)
        // Commit claim before posting via the separate method below; caller receives claim identity.
        attempt.id
    }.let { result ->
        val attempt = db.attention().attempts().firstOrNull { it.id == result } ?: return@let result
        val app = requireNotNull(db.foundation().settings())
        val outcome = try { post("Instagram ${attempt.usageMillis / 60_000} min in this window. Start five minutes of Career.", app.privacyMode); "POSTED" }
            catch (_: Exception) { "FAILED" }
        db.attention().update(attempt.copy(result = outcome))
        "$outcome · ${attempt.attemptedAt} · not proof the alert was seen"
    }
}
