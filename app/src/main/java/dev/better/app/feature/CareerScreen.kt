package dev.better.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.better.app.data.*
import dev.better.app.domain.careerActivities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

class CareerViewModel(val repository: FocusRepository) : ViewModel() {
    val state = MutableStateFlow(FocusSnapshot())
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    init { viewModelScope.launch { while (isActive) { refresh(); delay(1000) } } }
    private suspend fun refresh() {
        try { state.value = repository.snapshot() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error.value = "Could not read Career history. Your data has not been reset." }
    }
    private fun change(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try { block(); error.value = null; refresh() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error.value = e.message ?: "Could not save timer action. Try again." }
            finally { busy.value = false }
        }
    }
    fun start(activity: String, rescue: Boolean, confirmed: Boolean, company: String, role: String) =
        change { repository.start(activity, rescue, confirmed, company, role) }
    fun act(id: String, action: String) = change { repository.act(id, action) }
    fun standard(minutes: String, date: String) = change {
        val target = minutes.toIntOrNull() ?: error("Enter a whole number of minutes.")
        val effective = runCatching { LocalDate.parse(date) }.getOrElse { error("Use a date in YYYY-MM-DD format.") }
        repository.changeStandard(target, effective)
    }
}

fun focusTime(millis: Long): String = "${millis / 60000}:${(millis / 1000 % 60).toString().padStart(2, '0')}"

@Composable
fun CareerToday(model: CareerViewModel, day: LocalDate) {
    val state by model.state.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val today = state.dailyMillis[day] ?: 0
    val target = state.target(day)
    var starting by rememberSaveable { mutableStateOf<String?>(null) }
    var activity by rememberSaveable { mutableStateOf("Search") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var company by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Career Focus", style = MaterialTheme.typography.titleLarge)
        Text("${focusTime(today)} / ${target / 60} min", style = MaterialTheme.typography.headlineMedium)
        Text(if (today >= target * 1000L) "Daily minimum met. More deliberate work is optional."
            else "${focusTime((target * 1000L - today).coerceAtLeast(0))} remaining today.")
        Text("Timed · user-labelled work, not independently verified", style = MaterialTheme.typography.bodySmall)
        val session = state.active
        if (session == null) {
            if (starting == null) {
                Button({ starting = "Career"; confirmed = false }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Start Career") }
                OutlinedButton({ starting = "Rescue"; confirmed = false }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Rescue") }
                if (state.sessions.isNotEmpty()) Text("Last session saved · ${focusTime(state.sessionMillis(state.sessions.last().id))} · timed")
            } else {
                Text(if (starting == "Rescue") "Start with five active minutes." else "Choose deliberate Career work.")
                careerActivities.forEach { label -> FilterChip(activity == label, { activity = label }, label = { Text(label) }) }
                OutlinedTextField(company, { company = it }, label = { Text("Company (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(role, { role = it }, label = { Text("Role (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Application details are self-reported. A timer does not establish submission.", style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(confirmed, { confirmed = it }); Text("I am doing deliberate Career work, not passive scrolling.") }
                Button({ model.start(activity, starting == "Rescue", confirmed, company, role); starting = null },
                    Modifier.fillMaxWidth(), enabled = confirmed && !busy) { Text(if (starting == "Rescue") "Start five minutes" else "Start timer") }
                TextButton({ starting = null }) { Text("Back") }
            }
        } else {
            Text("${session.activity} · ${session.state.lowercase().replace('_', ' ')}", style = MaterialTheme.typography.titleMedium)
            Text("Session ${focusTime(state.sessionMillis(session.id))}", style = MaterialTheme.typography.headlineSmall)
            if (session.recovery.isNotBlank()) Text(session.recovery)
            if (session.state == "REVIEW") {
                val pending = state.segments.filter { it.sessionId == session.id }.sumOf { (it.candidateMillis ?: it.durationMillis) - it.durationMillis }
                Text("Awaiting confirmation: ${focusTime(pending)}. The timer is paused during review.")
                Button({ model.act(session.id, "CONFIRM") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Work stayed active") }
                OutlinedButton({ model.act(session.id, "DISCARD") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Keep saved checkpoint only") }
            } else {
                when (session.state) {
                    "RUNNING" -> Button({ model.act(session.id, "PAUSE") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Pause") }
                    "PAUSED", "INTERRUPTED" -> Button({ model.act(session.id, "RESUME") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Resume") }
                    "RESCUE_READY" -> {
                        Text("Five minutes recorded. Continue this session or stop; waiting time does not count.")
                        Button({ model.act(session.id, "CONTINUE") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Continue toward daily minimum") }
                    }
                }
                OutlinedButton({ model.act(session.id, "STOP") }, Modifier.fillMaxWidth(), enabled = !busy) { Text("Stop and save") }
            }
        }
        Text("Pause when interrupted. Work in other apps counts while active. After a restart, review recovery. Checkpoints save while Android lets BETTER run.", style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }
}

@Composable
fun CareerSettings(model: CareerViewModel, day: LocalDate) {
    val state by model.state.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    var minutes by rememberSaveable { mutableStateOf("30") }
    var date by rememberSaveable { mutableStateOf(day.plusDays(1).toString()) }
    Text("Career daily minimum", style = MaterialTheme.typography.titleLarge)
    Text("Today: ${state.target(day) / 60} minutes. Changes start on a future operational date; history stays unchanged.")
    OutlinedTextField(minutes, { minutes = it }, label = { Text("Target minutes") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    OutlinedTextField(date, { date = it }, label = { Text("Effective date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    Button({ model.standard(minutes, date) }, enabled = !busy) { Text("Save future target") }
    state.standards.forEach { Text("${it.effectiveDate}: ${it.targetSeconds / 60} minutes") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
fun CareerReview(model: CareerViewModel, day: LocalDate) {
    val state by model.state.collectAsStateWithLifecycle()
    var weekly by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var endDate by rememberSaveable { mutableStateOf(day.toString()) }
    val end = LocalDate.parse(endDate)
    Text("Career evidence", style = MaterialTheme.typography.titleLarge)
    Row {
        TextButton({ endDate = end.minusDays(if (weekly) 7 else 1).toString(); page = 0 }) { Text("Earlier Career") }
        TextButton({ endDate = minOf(day, end.plusDays(if (weekly) 7 else 1)).toString(); page = 0 }, enabled = end < day) { Text("Later Career") }
    }
    FilterChip(weekly, { weekly = !weekly; page = 0 }, label = { Text("Seven-day Career view") })
    val dates = (if (weekly) 6 downTo 0 else 0 downTo 0).map { end.minusDays(it.toLong()) }
    dates.forEach { date -> Text("$date · ${focusTime(state.dailyMillis[date] ?: 0)} / ${state.target(date) / 60} min · timed") }
    val sessions = state.sessions.filter { s -> state.segments.any { it.sessionId == s.id &&
        dev.better.app.domain.allocateFocus(it.startWall, maxOf(1, it.durationMillis), java.time.ZoneId.of(it.zoneId), java.time.LocalTime.parse(it.boundary)).keys.any { d -> d in dates } } }.reversed()
    sessions.drop(page * 10).take(10).forEach { s ->
        var expanded by rememberSaveable(s.id) { mutableStateOf(false) }
        Text("${s.activity} · ${focusTime(state.sessionMillis(s.id))} total session · ${s.state.lowercase().replace('_', ' ')} · timed")
        if (s.company.isNotBlank() || s.role.isNotBlank()) Text("Company: ${s.company.ifBlank { "—" }}\nRole: ${s.role.ifBlank { "—" }}\nApplication details: self-reported")
        TextButton({ expanded = !expanded }) { Text(if (expanded) "Hide timer actions" else "Show timer actions") }
        if (expanded) state.actions.filter { it.entityId == s.id }.forEach { action ->
            Text("${java.time.Instant.ofEpochMilli(action.timestamp).atZone(model.repository.clock.zone())}\n${action.action.removePrefix("FOCUS_").lowercase().replace('_', ' ')} · ${action.summary}", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (sessions.isEmpty()) Text("No timed Career sessions in this period.")
    Row { if (page > 0) TextButton({ page-- }) { Text("Previous sessions") }; if ((page + 1) * 10 < sessions.size) TextButton({ page++ }) { Text("More sessions") } }
}
