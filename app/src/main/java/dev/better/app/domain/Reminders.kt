package dev.better.app.domain

import java.time.*

data class AlertStep(val steps: String, val originalAt: Long, val plannedAt: Long, val delayed: Boolean)

object ReminderPolicy {
    fun offsets(level: String, configured: String): List<Int> = when (level) {
        "NONE" -> emptyList()
        "LIGHT" -> listOf(0)
        "STANDARD" -> listOf(0, 15)
        "PERSISTENT" -> listOf(0, 30, 60, 90)
        "ESCALATING" -> configured.split(',').map { it.trim().toInt() }.also { values ->
            require(values.size in 1..5 && values.first() >= 0 && values.zipWithNext().all { it.first < it.second })
        }
        else -> error("Unknown reminder policy")
    }

    fun permitted(at: Instant, zone: ZoneId, start: LocalTime, end: LocalTime): Instant {
        if (start == end) return at // Equal endpoints explicitly disable quiet hours.
        val local = at.atZone(zone)
        val time = local.toLocalTime()
        val inside = if (start < end) time >= start && time < end else time >= start || time < end
        if (!inside) return at
        val date = if (start > end && time >= start) local.toLocalDate().plusDays(1) else local.toLocalDate()
        return date.atTime(end).atZone(zone).toInstant()
    }

    fun plan(due: Long, closes: Long, level: String, configured: String, zone: ZoneId,
        quietStart: LocalTime, quietEnd: LocalTime, critical: Boolean = false): List<AlertStep> =
        offsets(level, configured).mapIndexedNotNull { index, minutes ->
            val original = due + minutes * 60_000L
            val allowed = if (critical) original else permitted(Instant.ofEpochMilli(original), zone, quietStart, quietEnd).toEpochMilli()
            if (allowed >= closes) null else AlertStep(index.toString(), original, allowed, allowed != original)
        }.groupBy { it.plannedAt }.values.map { group ->
            group.first().copy(steps = group.joinToString(",") { it.steps }, delayed = group.any { it.delayed })
        }.sortedBy { it.plannedAt }
}
