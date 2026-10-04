package dev.better.app

import android.app.Application
import dev.better.app.data.*
import dev.better.app.platform.AndroidClock
import kotlinx.coroutines.launch

class BetterApplication : Application() {
    val clock by lazy { AndroidClock() }
    val database by lazy { BetterDatabase.open(this) }
    val repository by lazy { LocalRepository(database, clock) }
    val preferences by lazy { UiPreferences(this, database) }
    val attention by lazy { dev.better.app.platform.AndroidAttention(this) }
    val focus by lazy { FocusRepository(database, clock, { android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1) }) }
    private val focusScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val schedules: ScheduleRepository by lazy { ScheduleRepository(database, clock).also { repo -> repo.onChanged = { reminders.reconcile() } } }
    val reminders: ReminderRepository by lazy { ReminderRepository(schedules, dev.better.app.platform.AndroidReminders(this)) }
    override fun onCreate() {
        super.onCreate()
        dev.better.app.platform.ReminderWork.periodic(this)
        focusScope.launch {
            while (true) {
                try { focus.snapshot() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { /* UI exposes storage errors. */ }
                kotlinx.coroutines.delay(5000)
            }
        }
    }
}
