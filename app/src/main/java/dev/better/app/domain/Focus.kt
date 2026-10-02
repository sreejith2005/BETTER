package dev.better.app.domain

import java.time.*

val careerActivities = listOf("Search", "Apply", "Resume", "Outreach", "Interview prep", "Portfolio/project")
const val RESCUE_MILLIS = 300_000L

/** Wall time allocates dates; only the monotonic duration supplies credit. */
fun allocateFocus(startWall: Long, durationMillis: Long, zone: ZoneId, boundary: LocalTime): Map<LocalDate, Long> {
    require(durationMillis >= 0)
    val result = linkedMapOf<LocalDate, Long>()
    var cursor = Instant.ofEpochMilli(startWall)
    val end = cursor.plusMillis(durationMillis)
    while (cursor < end) {
        val date = operationalDate(cursor, zone, boundary)
        val next = date.plusDays(1).atTime(boundary).atZone(zone).toInstant()
        val until = minOf(end, next)
        require(until > cursor)
        result[date] = (result[date] ?: 0) + Duration.between(cursor, until).toMillis()
        cursor = until
    }
    return result
}

fun focusDuration(startElapsed: Long, nowElapsed: Long, previousMillis: Long, rescueRemaining: Long?): Long {
    require(nowElapsed >= startElapsed) { "Clock continuity lost" }
    val duration = maxOf(previousMillis, nowElapsed - startElapsed)
    return rescueRemaining?.let { minOf(duration, it.coerceAtLeast(0)) } ?: duration
}
