package dev.better.app.domain

import java.time.*

data class AttentionWindow(val start: LocalTime, val end: LocalTime) {
    fun contains(time: LocalTime) = when {
        start == end -> false
        start < end -> time >= start && time < end
        else -> time >= start || time < end
    }
}

data class UsageInterval(val start: Long, val end: Long)
data class ForegroundMark(val at: Long, val activity: String, val kind: Int)

/** A bounded look-back establishes sessions crossing midnight; only the requested day is returned. */
fun foregroundIntervals(marks: List<ForegroundMark>, from: Long, until: Long): List<UsageInterval>? {
    var active: Long? = null
    val completed = mutableListOf<UsageInterval>()
    var known = false
    var incomplete = false
    for (mark in marks.sortedBy { it.at }) {
        when(mark.kind) {
            1 -> {
                known = true
                // Class names are not activity-instance identities. Ambiguous multi-resume
                // sequences cannot safely be counted as extra foreground time.
                if (active != null && mark.at > from) incomplete = true
                if (active == null) active = mark.at
            }
            2 -> {
                known = true
                val start = active
                active = null
                if (start == null && mark.at > from) incomplete = true
                if (start != null && mark.at > from && start < until)
                    completed += UsageInterval(maxOf(start, from), minOf(mark.at, until))
            }
            // A delayed STOPPED can belong to an older instance of this class. Foreground
            // accounting follows RESUMED/PAUSED; an unpaired resume remains unavailable.
        }
    }
    if (!known || incomplete || active != null) return null
    val merged = mutableListOf<UsageInterval>()
    for (interval in completed.sortedBy { it.start }) {
        val previous = merged.lastOrNull()
        if (previous != null && interval.start <= previous.end)
            merged[merged.lastIndex] = previous.copy(end = maxOf(previous.end, interval.end))
        else merged += interval
    }
    return merged
}
data class AttentionDecision(val eligible: Boolean, val reason: String)

/** Threshold is consumed once per eligible window, with durable global day/cooldown bounds. */
fun attentionDecision(enabled: Boolean, available: Boolean, eligibleWindow: Boolean, excluded: Boolean,
    quiet: Boolean, modeDisabled: Boolean, careerRunning: Boolean, careerComplete: Boolean,
    usageMillis: Long, thresholdMinutes: Int, now: Long, lastAttempt: Long?, attemptsToday: Int,
    windowConsumed: Boolean): AttentionDecision {
    val reason = when {
        !enabled -> "Rule off"
        !available -> "Usage unavailable"
        !eligibleWindow || excluded -> "Outside eligible windows or excluded work/commute time"
        quiet -> "Quiet hours"
        modeDisabled -> "Active mode disables Attention"
        careerRunning -> "Career running"
        careerComplete -> "Career minimum complete"
        usageMillis < thresholdMinutes * 60_000L -> "Below threshold"
        windowConsumed -> "This window already prompted"
        attemptsToday >= 2 -> "Daily cap reached"
        lastAttempt != null && (now < lastAttempt || now - lastAttempt < 3_600_000L) -> "60-minute cooldown"
        else -> "Threshold reached"
    }
    return AttentionDecision(reason == "Threshold reached", reason)
}

/** Clip foreground intervals to actual instants, subtracting exclusions minute by minute. */
fun attentionMillis(intervals: List<UsageInterval>, from: Long, until: Long, zone: ZoneId,
    includes: List<AttentionWindow>, excludes: List<AttentionWindow>): Long = intervals.sumOf { interval ->
    var at = maxOf(from, interval.start)
    val end = minOf(until, interval.end)
    var total = 0L
    while (at < end) {
        val next = minOf(end, (at / 60_000 + 1) * 60_000)
        val time = Instant.ofEpochMilli(at).atZone(zone).toLocalTime()
        if (includes.any { it.contains(time) } && excludes.none { it.contains(time) }) total += next - at
        at = next
    }
    total
}
