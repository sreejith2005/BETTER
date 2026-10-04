package dev.better.app

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.better.app.feature.*
import dev.better.app.feature.plan.ScheduleViewModel
import dev.better.app.data.ScheduleRepository

open class MainActivity : ComponentActivity() {
    protected open fun scheduleRepository(app: BetterApplication) = app.schedules
    protected open fun reminderRepository(app: BetterApplication) = app.reminders
    protected open fun focusRepository(app: BetterApplication) = app.focus
    private var rescueRequest by mutableIntStateOf(0)
    private var reminderRequest by mutableStateOf<Pair<String, String>?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readReminder(intent)
    }
    private fun readReminder(intent: Intent) {
        if (intent.getBooleanExtra("attentionRescue", false)) { rescueRequest++; intent.removeExtra("attentionRescue") }
        reminderRequest = intent.getStringExtra("deliveryId")?.let { it to (intent.getStringExtra("reminderAction") ?: "OPEN") }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as BetterApplication
        val scheduleRepo = scheduleRepository(app)
        val reminderRepo = reminderRepository(app)
        val focusRepo = focusRepository(app)
        readReminder(intent)
        setContent {
            val model: AppViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(dev.better.app.data.LocalRepository(scheduleRepo.db, app.clock), app.preferences) as T
            })
            val schedules: ScheduleViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScheduleViewModel(scheduleRepo, reminderRepo) as T
            })
            LaunchedEffect(reminderRequest) {
                reminderRequest?.let { schedules.openReminder(it.first, it.second) }
                reminderRequest = null
            }
            val career: CareerViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = CareerViewModel(focusRepo) as T
            })
            BetterRoot(model, app.clock, schedules, career, rescueRequest)
        }
    }
}
