package dev.better.app.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

interface AppClock {
    fun now(): Instant
    fun zone(): ZoneId
    fun elapsedMillis(): Long
}

fun operationalDate(instant: Instant, zone: ZoneId, boundary: LocalTime): LocalDate {
    val local = instant.atZone(zone)
    return local.toLocalDate().minusDays(if (local.toLocalTime() < boundary) 1 else 0)
}

data class SetupInput(
    val boundary: String = "04:00",
    val zone: String = ZoneId.systemDefault().id,
    val workStart: String = "09:00",
    val workEnd: String = "18:00",
    val careerStart: String = "19:00",
    val careerEnd: String = "21:00",
    val notificationsWanted: Boolean = false,
) {
    fun validate(): String? {
        val times = listOf(boundary, workStart, workEnd, careerStart, careerEnd)
        if (times.any { !it.matches(Regex("\\d{2}:\\d{2}")) }) return "Use 24-hour times in HH:mm format."
        if (times.any { runCatching { LocalTime.parse(it) }.isFailure }) return "Enter valid times between 00:00 and 23:59."
        if (runCatching { ZoneId.of(zone) }.isFailure) return "Enter a valid timezone, such as Asia/Kolkata."
        if (workStart == workEnd || careerStart == careerEnd) return "A window must have different start and end times."
        return null // A start after end is an explicit overnight window.
    }
}
