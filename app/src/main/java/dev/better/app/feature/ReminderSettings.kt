package dev.better.app.feature

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.better.app.data.AppSettings
import dev.better.app.feature.plan.ScheduleViewModel
import kotlinx.coroutines.launch

@Composable fun ReminderSummary(model: ScheduleViewModel) {
    val alerts by model.deliveries.collectAsStateWithLifecycle()
    val logs by model.logs.collectAsStateWithLifecycle()
    val missed = alerts.count { it.result in listOf("MISSED", "BLOCKED", "FAILED", "CLAIMED") }
    Text("Best-effort timing · exact alarm access is not requested. Battery restrictions and force-stop can delay alerts; reopen BETTER after a force-stop.", style = MaterialTheme.typography.bodySmall)
    if (missed > 0) Text("$missed alert steps missed, blocked or unconfirmed. The plan is unchanged; inspect occurrence history.", style = MaterialTheme.typography.bodySmall)
    logs.lastOrNull { it.action == "SCHEDULE_CHECKED" }?.let { Text(it.summary, style = MaterialTheme.typography.bodySmall) }
}

@Composable fun ReminderSettings(model: ScheduleViewModel, settings: AppSettings) {
    var start by remember(settings.quietStart) { mutableStateOf(settings.quietStart) }
    var end by remember(settings.quietEnd) { mutableStateOf(settings.quietEnd) }
    var privacy by remember(settings.privacyMode) { mutableStateOf(settings.privacyMode) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { model.refresh() }
    Text("Reminders", style = MaterialTheme.typography.titleLarge)
    Text("Allow notifications to receive future commitment alerts. Denying keeps your due list available. If Android no longer prompts, use Notification settings above.")
    if(Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick={request.launch(Manifest.permission.POST_NOTIFICATIONS)}) { Text("Request reminder permission") }
    Text("Channels: Commitments, Career, and User-entered health reminders. Android notification settings controls sound and channel availability.")
    OutlinedTextField(start, {start=it}, label={Text("Quiet hours start (HH:mm)")}, singleLine=true, modifier=Modifier.fillMaxWidth())
    OutlinedTextField(end, {end=it}, label={Text("Quiet hours end (HH:mm)")}, singleLine=true, modifier=Modifier.fillMaxWidth())
    Text("Uses the current phone timezone. Equal start and end disables quiet hours. Alerts that pass day close stay missed; they are not sent the next morning.", style=MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Checkbox(privacy, {privacy=it}); Text("Hide titles on the lock screen", modifier=Modifier.weight(1f))
    }
    Text("Instructions and source text appear only inside the app.", style=MaterialTheme.typography.bodySmall)
    Button(onClick={scope.launch {
        try { model.reminders?.settings(start,end,privacy); message="Reminder settings saved." }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){message="Enter valid 24-hour times (HH:mm)."}
    }}) { Text("Save reminder settings") }
    message?.let {Text(it)}
    ReminderSummary(model)
}
