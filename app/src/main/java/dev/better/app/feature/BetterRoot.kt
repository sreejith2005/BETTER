package dev.better.app.feature

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.better.app.domain.*
import dev.better.app.feature.plan.ScheduleViewModel
import dev.better.app.feature.plan.ScheduleScreen
import dev.better.app.feature.plan.TodayExecution
import dev.better.app.feature.plan.ReviewExecution
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val lightPalette = lightColorScheme(
    primary = Color(0xFF235647), onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E9DF), onPrimaryContainer = Color(0xFF173F35),
    background = Color(0xFFF6F5EF), surface = Color(0xFFF6F5EF),
    surfaceVariant = Color(0xFFE9EAE2), onSurface = Color(0xFF1C2924),
)
private val darkPalette = darkColorScheme(primary = Color(0xFFA5D4B9), onPrimary = Color(0xFF123B2A),
    background = Color(0xFF111C18), surface = Color(0xFF111C18),
    primaryContainer = Color(0xFF254C3D), onPrimaryContainer = Color(0xFFD8E9DF))

@Composable
fun BetterRoot(model: AppViewModel, clock: AppClock, schedules: ScheduleViewModel? = null, career: CareerViewModel? = null, rescueRequest: Int = 0) {
    val state by model.state.collectAsStateWithLifecycle()
    val dark = when (state.theme) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) darkPalette else lightPalette) {
        Surface(Modifier.fillMaxSize()) {
            when {
                state.loading -> Box(Modifier.safeDrawingPadding().padding(24.dp)) { Text("Opening your local plan…") }
                state.settings == null -> Onboarding(state, model::onboard, clock.zone().id)
                else -> Home(state, model::setTheme, clock, schedules, career, rescueRequest)
            }
        }
    }
}

@Composable
private fun Onboarding(state: AppState, onSave: (SetupInput) -> Unit, zone: String) {
    var boundary by rememberSaveable { mutableStateOf("04:00") }
    var zoneText by rememberSaveable { mutableStateOf(zone) }
    var workStart by rememberSaveable { mutableStateOf("09:00") }
    var workEnd by rememberSaveable { mutableStateOf("18:00") }
    var careerStart by rememberSaveable { mutableStateOf("19:00") }
    var careerEnd by rememberSaveable { mutableStateOf("21:00") }
    var notifications by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = it }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("BETTER", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text("Make room for\nwhat matters.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Text("Your plan stays on this phone. No account or internet needed.")
        Text("Set your day", style = MaterialTheme.typography.titleLarge)
        TimeField("Day begins", boundary) { boundary = it }
        OutlinedTextField(zoneText, { zoneText = it }, label = { Text("Timezone") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Times use the 24-hour clock. A window ending before it starts continues into the next calendar day.", style = MaterialTheme.typography.bodySmall)
        TimeField("Work starts", workStart) { workStart = it }
        TimeField("Work ends", workEnd) { workEnd = it }
        Text("Career Focus · 30 minutes daily", style = MaterialTheme.typography.titleMedium)
        Text("Choose a preferred window for deliberate career work.")
        TimeField("Career window starts", careerStart) { careerStart = it }
        TimeField("Career window ends", careerEnd) { careerEnd = it }
        Text("Notifications are optional. Your plan remains available when they are off.")
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33) request.launch(Manifest.permission.POST_NOTIFICATIONS)
            else notifications = context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        }, modifier = Modifier.fillMaxWidth()) { Text(if (notifications) "Notifications enabled" else "Request notifications") }
        Text("You can continue without granting permission. Android may delay reminders.", style = MaterialTheme.typography.bodySmall)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { onSave(SetupInput(boundary, zoneText, workStart, workEnd, careerStart, careerEnd, notifications)) }, enabled = !state.saving, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (state.saving) "Saving…" else "Create my local plan")
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) }, placeholder = { Text("HH:mm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Home(state: AppState, setTheme: (String) -> Unit, clock: AppClock, schedules: ScheduleViewModel?, career: CareerViewModel?, rescueRequest: Int = 0) {
    var selected by rememberSaveable { mutableStateOf("Today") }
    LaunchedEffect(rescueRequest) { if (rescueRequest > 0) selected = "Today" }
    var adaptive by rememberSaveable { mutableStateOf(false) }
    val reminderFocus = schedules?.reminderFocus?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(reminderFocus) { if (reminderFocus != null) { adaptive = false; selected = "Plan" } }
    val pageScroll = rememberScrollState()
    val pageScope = rememberCoroutineScope()
    LaunchedEffect(selected) { pageScroll.scrollTo(0) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val manager = context.getSystemService(NotificationManager::class.java)
    var notificationsEnabled by remember { mutableStateOf(manager.areNotificationsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            notificationsEnabled = manager.areNotificationsEnabled()
            schedules?.refresh()
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val settings = requireNotNull(state.settings)
    LaunchedEffect(settings.profileId) { schedules?.refresh() }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycle, schedules) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { schedules?.refresh(); tick++; delay(15_000) }
        }
    }
    val day = remember(tick, settings) { operationalDate(clock.now(), if (settings.timezonePolicy == "LOCAL_FLOATING") clock.zone() else ZoneId.of(settings.zoneId), LocalTime.parse(settings.dayBoundary)) }
    val standard = state.standards.lastOrNull { it.effectiveDate <= day.toString() }
    Scaffold(floatingActionButton = {
        if (schedules != null) FloatingActionButton(onClick = { adaptive = false; selected = "Plan"; schedules.create() }) { Text("+ Add") }
    }, bottomBar = {
        NavigationBar {
            listOf("Today", "Plan", "Review", "Settings").forEachIndexed { index, tab ->
                NavigationBarItem(selected == tab, { selected = tab },
                    icon = { Text(listOf("◉", "≡", "▥", "⚙")[index]) }, label = { Text(tab) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).verticalScroll(pageScroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("BETTER / ${day.format(DateTimeFormatter.ofPattern("EEE, d MMM"))}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(selected, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            when (selected) {
                "Today" -> {
                    if (schedules != null) AdaptiveSummary(schedules, day.toString()) else Text("Local profile · Normal mode")
                    if (schedules != null) OutlinedButton({ adaptive = true; selected = "Plan" }) { Text("Routines / Day override") }
                    if (career != null) CareerToday(career, day, rescueRequest) else Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("DAILY STANDARD", style = MaterialTheme.typography.labelMedium)
                            Text("Career Focus", style = MaterialTheme.typography.titleLarge)
                            Text("0 / ${(standard?.targetSeconds ?: 1800) / 60} min", style = MaterialTheme.typography.displaySmall)
                            Text("Preferred window ${settings.careerStart}–${settings.careerEnd}")
                            Text("${(standard?.targetSeconds ?: 1800) / 60} minutes remaining.")
                        }
                    }
                    if (career != null) Text("Preferred window ${settings.careerStart}–${settings.careerEnd}")
                    Text(if (notificationsEnabled) "Notifications allowed · timing is best effort" else "Notifications are off · your local plan is available", style = MaterialTheme.typography.bodyMedium)
                    if(schedules?.reminders != null) ReminderSummary(schedules)

                    if(schedules!=null) TodayExecution(schedules,day) {adaptive=false;selected="Plan"}
                }
                "Plan" -> {
                    if (schedules != null) {
                        OutlinedButton({ adaptive = !adaptive }) { Text(if (adaptive) "Commitments" else "Routines and adaptive day") }
                        if (adaptive) AdaptiveScreen(schedules) { pageScope.launch { pageScroll.scrollTo(0) } } else ScheduleScreen(schedules)
                    } else {
                    Text("Career Focus", style = MaterialTheme.typography.titleLarge)
                    Text("Daily standard · ${(standard?.targetSeconds ?: 1800) / 60} minutes")
                    Text("Preferred window ${settings.careerStart}–${settings.careerEnd}")
                    Text("Commitment editing will be available after the scheduling gate.")
                    }
                }
                "Review" -> {
                    if (career != null) CareerReview(career, day)
                    if(schedules!=null) ReviewExecution(schedules,day) else Text("Today · In progress")
                }
                "Settings" -> {
                    (context.applicationContext as? dev.better.app.BetterApplication)?.let { OwnershipSettings(it) }
                    if (career != null) CareerSettings(career, day)
                    Text("Stored on this phone", style = MaterialTheme.typography.titleLarge)
                    Text("Day begins ${settings.dayBoundary}\nTimezone ${settings.zoneId}\nWork ${settings.workStart}–${settings.workEnd}\nQuiet hours ${settings.quietStart}–${settings.quietEnd}")
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Column { listOf("System", "Light", "Dark").forEach { theme ->
                        FilterChip(selected = state.theme == theme, onClick = { setTheme(theme) }, label = { Text(theme) })
                    } }
                    Text(if (notificationsEnabled) "Notifications allowed" else "Notifications are off")
                    OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Notification settings") }
                    if(schedules?.reminders != null) ReminderSettings(schedules, settings)
                    Text("BETTER · Attention and data ownership 0.7.0", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(88.dp))
        }
    }
}
