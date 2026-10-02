package dev.better.app.platform

import android.app.*
import android.content.*
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.work.*
import dev.better.app.BetterApplication
import dev.better.app.MainActivity
import dev.better.app.R
import dev.better.app.data.*
import java.util.concurrent.TimeUnit

class AndroidReminders(private val context: Context,
    private val alarmReceiver: Class<out BroadcastReceiver> = ReminderAlarmReceiver::class.java,
    private val notificationActivity: Class<out Activity> = MainActivity::class.java) : ReminderPlatform {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private var scheduledAt: Long? = null
    private var scheduledKnown = false
    override fun recoveryToken(): String {
        // Detect wall-clock jumps even if an alarm worker wins the race with TIME_SET.
        val prefs = context.getSharedPreferences("reminder-clock-${alarmReceiver.simpleName}", Context.MODE_PRIVATE)
        val offset = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val baseline = prefs.getLong("offset", offset)
        var generation = prefs.getLong("generation", 0)
        if (!prefs.contains("offset") || kotlin.math.abs(offset - baseline) > 2_000) {
            generation++
            // Persist this marker before the Room recovery token; background apply could lose it on process death.
            prefs.edit(commit = true) { putLong("offset", offset); putLong("generation", generation) }
        }
        return "${android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)}|${java.time.ZoneId.systemDefault().id}|${dev.better.app.BuildConfig.VERSION_CODE}|$generation"
    }
    init {
        listOf("commitments" to "Commitments", "career" to "Career", "health" to "User-entered health reminders").forEach { (id, name) ->
            manager.createNotificationChannel(NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Best-effort reminders. Planned and posted times may differ."
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            })
        }
    }
    private fun channel(category: String) = when (category) { "CAREER" -> "career"; "HEALTH" -> "health"; else -> "commitments" }
    override fun allowed(category: String) = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(channel(category))?.importance != NotificationManager.IMPORTANCE_NONE
    override fun schedule(at: Long?) {
        if (scheduledKnown && scheduledAt == at) return
        val pending = PendingIntent.getBroadcast(context, 0, Intent(context, alarmReceiver).setAction("dev.better.ALARM"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.cancel(pending)
        if (at != null) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        scheduledAt = at
        scheduledKnown = true
    }
    override fun cancelNotification(occurrenceId: String) { manager.cancel(occurrenceId, 1) }
    fun openIntent(row: ReminderDelivery, action: String = "OPEN"): PendingIntent {
        val intent = Intent(context, notificationActivity).apply {
            data = "better://reminder/${row.id}/$action".toUri()
            putExtra("deliveryId", row.id); putExtra("reminderAction", action)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    override fun post(row: ReminderDelivery, occurrence: OccurrenceRecord, version: VersionRecord, title: String, private: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Notifications are off")
        val generic = Notification.Builder(context, channel(version.category)).setSmallIcon(R.drawable.ic_better)
            .setContentTitle("BETTER reminder").setContentText("Unlock to view your commitment.").build()
        val notification = Notification.Builder(context, channel(version.category)).setSmallIcon(R.drawable.ic_better)
            .setContentTitle(title).setContentText(if (row.delayed) "Delayed by quiet hours · Open your record" else "Due commitment · Open your record")
            .setVisibility(if (private) Notification.VISIBILITY_PRIVATE else Notification.VISIBILITY_PUBLIC)
            .setPublicVersion(generic).setAutoCancel(true).setContentIntent(openIntent(row))
            .addAction(Notification.Action.Builder(null, "Complete", openIntent(row, "COMPLETE")).build())
            .addAction(Notification.Action.Builder(null, "Move", openIntent(row, "MOVE")).build())
            .addAction(Notification.Action.Builder(null, "Skip", openIntent(row, "SKIP")).build())
            .setCategory(Notification.CATEGORY_REMINDER).setWhen(row.plannedAt).setShowWhen(true).build()
        manager.notify(occurrence.id, 1, notification)
    }
}

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { ReminderWork.enqueue(context, deliver = true) }
}
class ReminderRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED))
            ReminderWork.enqueue(context, recovery = true)
    }
}
object ReminderWork {
    fun enqueue(context: Context, recovery: Boolean = false, deliver: Boolean = false) {
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-reconcile", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("recovery" to recovery, "deliver" to deliver)).build())
    }
    fun periodic(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminder-safety", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).setInputData(workDataOf("deliver" to true)).build())
    }
}
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as BetterApplication).reminders.reconcile(inputData.getBoolean("recovery", false), inputData.getBoolean("deliver", false))
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }
}
