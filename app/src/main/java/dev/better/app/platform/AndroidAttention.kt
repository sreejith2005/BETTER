package dev.better.app.platform

import android.app.*
import android.content.*
import dev.better.app.*
import dev.better.app.data.*
import java.time.*

class AndroidAttention(private val app: BetterApplication) {
    val usage = AndroidUsage(app)
    val repository = AttentionRepository(app.database, app.clock)
    private val manager = app.getSystemService(NotificationManager::class.java)
    init { manager.createNotificationChannel(NotificationChannel("attention", "Optional Attention", NotificationManager.IMPORTANCE_DEFAULT)) }
    fun allowed() = manager.areNotificationsEnabled() && manager.getNotificationChannel("attention").importance != NotificationManager.IMPORTANCE_NONE
    suspend fun sample(): Pair<UsageObservation, String> {
        val settings = repository.settings()
        val now = app.clock.now()
        val from = now.atZone(app.clock.zone()).toLocalDate().atStartOfDay(app.clock.zone()).toInstant().toEpochMilli()
        val local = now.atZone(app.clock.zone())
        val currentWindow = parseAttentionWindows(settings.windows).firstOrNull { it.contains(local.toLocalTime()) }
        val windowDate = if (currentWindow != null && currentWindow.start > currentWindow.end && local.toLocalTime() < currentWindow.end) local.toLocalDate().minusDays(1) else local.toLocalDate()
        val coverageFrom = minOf(from, currentWindow?.let { windowDate.atTime(it.start).atZone(app.clock.zone()).toInstant().toEpochMilli() } ?: from)
        val observation = if (settings.enabled) usage.observe(settings.packageName, coverageFrom, now.toEpochMilli()).copy(dayFrom = from)
            else UsageObservation(settings.packageName, now.toEpochMilli(), from, null, "Attention off; no usage queried")
        val reason = repository.evaluate(observation, app.focus.snapshot(), allowed()) { text, private -> post(text, private) }
        return observation to reason
    }
    @Suppress("MissingPermission")
    private fun post(text: String, private: Boolean) {
        val intent = Intent(app, MainActivity::class.java).putExtra("attentionRescue", true)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(app, 6100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val generic = Notification.Builder(app, "attention").setSmallIcon(R.drawable.ic_better).setContentTitle("BETTER reminder").setContentText("Unlock to view.").build()
        manager.notify("attention", 6, Notification.Builder(app, "attention").setSmallIcon(R.drawable.ic_better)
            .setContentTitle("Career: start five minutes").setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
            .setVisibility(if(private) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC).setPublicVersion(generic)
            .setContentIntent(open).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Open Rescue", open).build()).build())
    }
}
