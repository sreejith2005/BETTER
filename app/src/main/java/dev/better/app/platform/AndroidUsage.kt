package dev.better.app.platform

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.UserManager
import dev.better.app.domain.UsageInterval
import dev.better.app.domain.ForegroundMark
import dev.better.app.domain.foregroundIntervals

 data class UsageObservation(val packageName: String, val observedAt: Long, val from: Long,
    val intervals: List<UsageInterval>?, val reason: String, val dayFrom: Long = from) {
    val totalMillis: Long? get() = intervals?.sumOf { maxOf(0, minOf(it.end, observedAt) - maxOf(it.start, dayFrom)) }
}

class AndroidUsage(private val context: Context) {
    fun allowed(): Boolean = context.getSystemService(AppOpsManager::class.java)
        .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    @Suppress("DEPRECATION")
    fun observe(packageName: String, from: Long, until: Long): UsageObservation {
        fun unknown(reason: String) = UsageObservation(packageName, until, from, null, reason)
        if (!allowed()) return unknown("Usage Access denied or revoked")
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return unknown("Unlock the phone to read usage")
        return try {
            // A bounded prior-day look-back is needed only to reconstruct a session crossing midnight.
            // Other packages are immediately discarded, and returned aggregates are clipped to today.
            val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from - 86_400_000L, until)
                ?: return unknown("Android usage data unavailable")
            val marks = mutableListOf<ForegroundMark>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.packageName != packageName) continue
                if (event.eventType in listOf(1, 2)) marks += ForegroundMark(event.timeStamp, event.className ?: "package", event.eventType)
            }
            if (!allowed()) return unknown("Usage Access revoked")
            // Missing events are not proof of zero use. An unclosed session is conservatively unknown.
            val intervals = foregroundIntervals(marks, from, until)
            if (intervals == null) unknown("No complete current-day event coverage; usage unavailable")
            else UsageObservation(packageName, until, from, intervals, "Android foreground events; observed, not proof of attention")
        } catch (_: SecurityException) { unknown("Usage Access unavailable") }
          catch (_: RuntimeException) { unknown("Android usage data unavailable") }
    }
}
