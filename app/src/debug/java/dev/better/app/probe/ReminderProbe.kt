package dev.better.app.probe

import android.content.*
import androidx.work.*
import dev.better.app.*
import dev.better.app.data.*
import dev.better.app.platform.*

/** Debug-only physical-device fixture. Uses the production engine/adapters with a separate private DB. */
object ReminderProbe {
    const val DATABASE = "phase3-reminder-probe.db"
    private var db: BetterDatabase? = null
    private var repo: ScheduleRepository? = null
    private var engine: ReminderRepository? = null
    @Synchronized fun schedules(context: Context): ScheduleRepository {
        if (repo == null) {
            db = BetterDatabase.open(context.applicationContext, DATABASE)
            repo = ScheduleRepository(db!!, AndroidClock())
            engine = ReminderRepository(repo!!, platform(context))
            repo!!.onChanged = { engine!!.reconcile() }
        }
        return repo!!
    }
    fun platform(context: Context) = AndroidReminders(context.applicationContext, ReminderProbeReceiver::class.java, ReminderProbeActivity::class.java)
    fun reminders(context: Context): ReminderRepository { schedules(context); return engine!! }
    @Synchronized fun close() { db?.close(); db=null; repo=null; engine=null }
}
class ReminderProbeActivity : MainActivity() {
    override fun scheduleRepository(app: BetterApplication) = ReminderProbe.schedules(app)
    override fun reminderRepository(app: BetterApplication) = ReminderProbe.reminders(app)
}
class ReminderProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!context.getDatabasePath(ReminderProbe.DATABASE).exists()) return
        WorkManager.getInstance(context).enqueueUniqueWork("reminder-probe", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ReminderProbeWorker>().setInputData(workDataOf("recovery" to (intent.action != "dev.better.ALARM"))).build())
    }
}
class ReminderProbeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getDatabasePath(ReminderProbe.DATABASE).exists()) return Result.success()
        val recovery=inputData.getBoolean("recovery",false)
        ReminderProbe.reminders(applicationContext).reconcile(recovery=recovery,deliver=!recovery)
        return Result.success()
    }
}
