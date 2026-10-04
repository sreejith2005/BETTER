package dev.better.app.feature

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.better.app.BetterApplication
import dev.better.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.time.Instant

@Composable
fun OwnershipSettings(app: BetterApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current
    var section by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(AttentionSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var observation by remember { mutableStateOf("Unavailable · Attention off") }
    var reason by remember { mutableStateOf("") }
    var careerComparison by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<ArchiveRepository.Preview?>(null) }
    var confirmation by remember { mutableStateOf("") }
    val archive = remember { ArchiveRepository(context, app.database) }
    fun work(block: suspend () -> Unit) { scope.launch { busy = true; message = ""; try { withContext(Dispatchers.IO) { block() } }
        catch(e: CancellationException) { throw e } catch(e: Exception) { message = e.message ?: "Operation failed; existing data retained." } finally { busy = false } } }
    LaunchedEffect(Unit) { config = withContext(Dispatchers.IO) { app.attention.repository.settings() }; loaded = true }
    LaunchedEffect(lifecycle, section) { lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        while(true) {
            if (section == "Attention") {
                val sampled = withContext(Dispatchers.IO) { app.attention.sample() }
                val o = sampled.first
                observation = if (o.totalMillis == null) "Unavailable · ${o.reason}" else "Instagram ${o.totalMillis!! / 60_000} min ${o.totalMillis!! / 1000 % 60} sec\nObserved ${Instant.ofEpochMilli(o.observedAt)}\nSelected package: ${o.packageName}"
                reason = sampled.second
                val focus = withContext(Dispatchers.IO) { app.focus.snapshot() }
                val settings = withContext(Dispatchers.IO) { app.database.foundation().settings() }
                if (settings != null) {
                    val zone = if (settings.timezonePolicy == "LOCAL_FLOATING") app.clock.zone() else java.time.ZoneId.of(settings.zoneId)
                    val day = dev.better.app.domain.operationalDate(app.clock.now(), zone, java.time.LocalTime.parse(settings.dayBoundary))
                    careerComparison = "Career ${(focus.dailyMillis[day] ?: 0) / 60_000} / ${focus.target(day) / 60} min · timed this operational day"
                }
            }
            delay(15_000)
        }
    } }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if(uri != null) work {
            app.database.attention().save(app.attention.repository.settings().copy(theme = app.preferences.theme.first()))
            val raw = archive.export()
            requireNotNull(context.contentResolver.openOutputStream(uri, "wt")).use { it.write(raw.toByteArray(Charsets.UTF_8)) }
            message = "Archive exported. The selected document is outside BETTER's control."
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) work {
            preview = null
            val bytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while(true) { val n = input.read(buffer); if(n < 0) break; require(out.size() + n <= ArchiveRepository.MAX_BYTES) { "Archive exceeds 20 MiB." }; out.write(buffer, 0, n) }; out.toByteArray()
            }
            preview = archive.preview(bytes.toString(Charsets.UTF_8))
            message = "Validated archive. Review below; no data replaced yet."
        }
    }
    Text("Attention and data ownership", style = MaterialTheme.typography.titleLarge)
    OutlinedButton({ section = if(section == "Attention") "" else "Attention" }) { Text("Attention / Usage Access") }
    OutlinedButton({ section = if(section == "Archive") "" else "Archive" }) { Text("Archive and privacy") }
    if(section == "Attention" && loaded) {
        Text(observation)
        Text(reason)
        Text(careerComparison)
        Text("Selected package: ${config.packageName}")
        Text("Optional Android foreground usage, stored locally. Raw events are discarded; only bounded intervention evidence is retained. Missing or incomplete data is unavailable, never assumed zero.")
        Text("Checks run on refresh and best-effort background work. Android can delay checks; no real-time blocking or overlay.")
        OutlinedButton({ context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) { Text("Open Usage Access settings") }
        OutlinedTextField(config.packageName, { config = config.copy(packageName = it) }, label = { Text("Instagram package") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.thresholdMinutes.toString(), { config = config.copy(thresholdMinutes = it.toIntOrNull() ?: 0) }, label = { Text("Threshold minutes (1–240)") })
        OutlinedTextField(config.windows, { config = config.copy(windows = it) }, label = { Text("Eligible windows HH:mm-HH:mm") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.office, { config = config.copy(office = it) }, label = { Text("Office exclusions") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.commute, { config = config.copy(commute = it) }, label = { Text("Commute exclusions (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.disabledModes, { config = config.copy(disabledModes = it) }, label = { Text("Disable in mode titles, comma separated") }, modifier = Modifier.fillMaxWidth())
        Text("Separate windows with commas. One prompt per window, maximum 2 per operational day, 60-minute cooldown. Quiet hours and Career running/complete suppress prompts.")
        Row { Checkbox(config.enabled, { config = config.copy(enabled = it) }); Text("Opt in to selected-package usage and interventions") }
        Button(enabled = !busy, onClick = { work {
            require(!config.enabled || app.attention.usage.allowed()) { "Grant Usage Access before opting in." }
            app.attention.repository.save(config.copy(theme = app.preferences.theme.first()))
            message = "Attention settings saved."
        } }) { Text("Save Attention settings") }
    }
    if(section == "Archive") {
        Text("No account, analytics SDK, internet permission or automatic cloud backup. Core features work offline. Usage Access is optional and can be revoked in Android settings.")
        Text("Archives include your instructions, history and settings in readable JSON. Choose a local device folder for offline storage. External documents remain outside app control. Permission grants are never imported.")
        Button(enabled = !busy, onClick = { exportPicker.launch("BETTER-${java.time.LocalDate.now()}.json") }) { Text("Export JSON archive") }
        OutlinedButton(enabled = !busy, onClick = { importPicker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Choose archive to preview") }
        preview?.let { p ->
            Text("Import preview · ${p.exportedAt}", style = MaterialTheme.typography.titleMedium)
            Text(p.counts.entries.joinToString("\n") { "${it.key}: ${it.value}" })
            Text("Replace all local plan records with this archive. This is not a merge. Stop any active Career session first. Android permissions will be checked again and alerts rebuilt.")
            Button(enabled = !busy, onClick = { work {
                archive.replace(p, true)
                config = app.attention.repository.settings()
                context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
                app.reminders.reconcile(recovery = true)
                preview = null; message = "Archive restored. Permissions rechecked; future alerts rebuilt."
            } }) { Text("Confirm replace local records") }
            OutlinedButton({ preview = null }) { Text("Cancel import") }
        }
        HorizontalDivider()
        Text("Delete local records", style = MaterialTheme.typography.titleMedium)
        Text("Deletes the local plan and history. Export first if you need recovery. External archives and phone-private development preservation snapshots remain untouched.")
        OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Type DELETE MY LOCAL DATA") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(enabled = confirmation == "DELETE MY LOCAL DATA" && !busy, onClick = { work {
            archive.deleteLocal(confirmation)
            context.getSystemService(android.app.NotificationManager::class.java).cancelAll()
            dev.better.app.platform.AndroidReminders(context).schedule(null)
            app.preferences.setTheme("System")
            confirmation = ""; message = "Local records deleted."
        } }) { Text("Confirm delete local records") }
    }
    if(busy) Text("Working on this phone…")
    if(message.isNotEmpty()) Text(message)
}
