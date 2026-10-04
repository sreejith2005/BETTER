package dev.better.app.feature

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.feature.plan.ScheduleViewModel
import dev.better.app.feature.plan.ConflictList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun AdaptiveSummary(model: ScheduleViewModel, date: String) {
    val records by model.repository.db.adaptive().watchRecords().collectAsState(initial = emptyList())
    val snapshot = AdaptiveSnapshot(records)
    val data by model.data.collectAsState()
    val profile = snapshot.profile(date)
    val mode = snapshot.mode(date)
    Text("${profile?.title ?: "Local profile"} · ${mode?.title ?: "Normal"} mode")
    snapshot.day(date)?.let { Text("Day override: ${AdaptiveJson.policy(JSONObject(it.payload)).reason}") }
    profile?.let { p ->
        Text("Profile version from ${p.fromDate}", style = MaterialTheme.typography.bodySmall)
        AdaptiveJson.blocks(p.payload).filter { it.commitmentId.isEmpty() }.sortedBy { it.time }.forEach {
            Text("${it.time} · ${it.title} · ${it.minutes} min · ${if (it.fixed) "Fixed" else "Flexible"} proposed block")
        }
        val standalone = AdaptiveJson.blocks(p.payload).filter { it.commitmentId.isEmpty() }.mapIndexed { i, b ->
            val start = LocalDate.parse(date).atTime(LocalTime.parse(b.time)).atZone(model.repository.clock.zone()).toInstant().toEpochMilli()
            OccupiedInterval("block:$i", b.title, start, start + b.minutes * 60_000L, b.fixed)
        }
        val scheduled = data.occurrences.filter { it.plannedDate == date && it.state in listOf("PENDING", "DUE", "OVERDUE") }.mapNotNull { o ->
            data.versions.find { it.id == o.versionId }?.let { v -> OccupiedInterval(o.id, v.title, o.startAt, o.startAt + o.duration(v) * 1000L, o.fixed(v)) }
        }
        ConflictList(conflicts(standalone + scheduled).filter { it.first.id.startsWith("block:") || it.second.id.startsWith("block:") }, model.repository.clock.zone().id)
    }
}

@Composable
fun AdaptiveScreen(model: ScheduleViewModel, onNavigate: () -> Unit = {}) {
    val repo = remember(model) { AdaptiveRepository(model.repository) }
    val records by repo.dao.watchRecords().collectAsState(initial = emptyList())
    val runs by repo.dao.watchRuns().collectAsState(initial = emptyList())
    val data by model.data.collectAsState()
    val logs by model.logs.collectAsState()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var section by remember { mutableStateOf("Routines") }
    var kind by remember { mutableStateOf<String?>(null) }
    var family by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var from by remember { mutableStateOf(repo.today()) }
    var until by remember { mutableStateOf(repo.today()) }
    var steps by remember { mutableStateOf("") }
    var weekdays by remember { mutableStateOf("1,2,3,4,5") }
    var oneDay by remember { mutableStateOf(false) }
    var essentialOnly by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    var policies by remember { mutableStateOf(emptyList<ItemPolicy>()) }
    var blocks by remember { mutableStateOf(emptyList<ProfileBlock>()) }
    var linkRoutine by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<AdaptivePreview?>(null) }
    var candidate by remember { mutableStateOf<AdaptiveRecord?>(null) }
    var ack by remember { mutableStateOf(false) }
    var previewPage by remember { mutableIntStateOf(0) }
    var runPage by remember { mutableIntStateOf(0) }
    fun invalidate() { preview = null; candidate = null; ack = false; message = null }
    fun work(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch { try { block() } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Could not update the local plan." } finally { busy = false } }
    }
    fun begin(type: String, existing: AdaptiveRecord? = null, duplicate: Boolean = false) {
        kind = type; family = if (duplicate) "" else existing?.family.orEmpty()
        title = existing?.title.orEmpty(); from = if (existing != null) LocalDate.parse(repo.today()).plusDays(1).toString() else repo.today()
        until = from; steps = if (type == "ROUTINE" && existing != null) AdaptiveJson.strings(JSONObject(existing.payload).getJSONArray("steps").toString()).joinToString("\n") else ""
        blocks = if (type == "PROFILE" && existing != null) AdaptiveJson.blocks(existing.payload) else emptyList()
        essentialOnly = false; policies = emptyList(); reason = ""; linkRoutine = ""; oneDay = false
        error = null; invalidate()
        onNavigate()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text("Routines and adaptive day", style = MaterialTheme.typography.titleLarge)
    Text("Changes keep recorded history. Review dates and overlaps before saving. Fixed events stay in place; move one explicitly from its occurrence detail.")
    if (kind == null) {
        listOf("Routines", "Profiles", "Modes and overrides", "Runs and history").forEach { tab ->
            FilterChip(section == tab, { section = tab; onNavigate() }, label = { Text(tab) })
        }
        when (section) {
            "Routines" -> {
                Button({ begin("ROUTINE") }) { Text("New routine") }
                records.filter { it.kind == "ROUTINE" }.groupBy { it.family }.values.forEach { versions ->
                    val r = versions.last()
                    Text("${r.title} · ${versions.size} version(s)")
                    versions.forEach { v -> Text("From ${v.fromDate}: " + AdaptiveJson.strings(JSONObject(v.payload).getJSONArray("steps").toString()).joinToString(" → ")) }
                    OutlinedButton({ begin("ROUTINE", r) }) { Text("Edit ${r.title}") }
                }
                Text("Attach routine to commitment", style = MaterialTheme.typography.titleMedium)
                data.commitments.filter { it.archivedAt == null }.forEach { c ->
                    OutlinedButton({ begin("LINK"); family = c.id; title = "Routine for ${c.title}" }) { Text("Attach to ${c.title}") }
                }
            }
            "Profiles" -> {
                Button({ begin("PROFILE") }) { Text("New profile") }
                records.filter { it.kind == "PROFILE" }.groupBy { it.family }.values.forEach { versions ->
                    val p = versions.last()
                    Text("${p.title} · ${versions.size} version(s)")
                    versions.forEach { Text("Version from ${it.fromDate}: ${AdaptiveJson.blocks(it.payload).size} blocks") }
                    OutlinedButton({ begin("PROFILE", p) }) { Text("Edit ${p.title}") }
                    OutlinedButton({ begin("PROFILE", p, true); title = "${p.title} copy" }) { Text("Duplicate ${p.title}") }
                    OutlinedButton({ begin("ASSIGN", p); title = p.title }) { Text("Assign ${p.title}") }
                }
                records.filter { it.kind == "ASSIGN" }.forEach { Text("${it.title}: from ${it.fromDate}${if (it.untilDate.isNotEmpty()) " through ${it.untilDate}" else ""}; weekdays ${JSONObject(it.payload).getString("weekdays")}") }
            }
            "Modes and overrides" -> {
                listOf("Normal", "Travel", "Vacation", "Sick", "Late Night", "Recovery Day", "High Focus", "Custom").forEach { name ->
                    OutlinedButton({ begin("MODE"); title = name; essentialOnly = name in listOf("Travel", "Vacation", "Sick", "Recovery Day") }) { Text("Configure $name") }
                }
                Button({ begin("DAY"); title = "One-day override"; essentialOnly = true }) { Text("Day override") }
                Text("Presets keep essential items by default. All rules are editable. Normal restores baseline policy for its dates. To reverse a pending change, preview a replacement assignment, mode or day override.")
                records.filter { it.kind in listOf("MODE", "DAY") }.takeLast(20).forEach {
                    Text("${it.title}: ${it.fromDate}–${it.untilDate}\n${AdaptiveJson.policy(JSONObject(it.payload)).reason}")
                }
            }
            "Runs and history" -> {
                Text("Routine results are self-reported. Finish the occurrence separately when appropriate. Leaving this screen retains each checked step.")
                val snapshot = AdaptiveSnapshot(records)
                val available = data.occurrences.filter { it.state in listOf("PENDING", "DUE", "OVERDUE") && snapshot.routine(it.commitmentId, it.plannedDate) != null }
                available.drop(runPage * 10).take(10).forEach { o ->
                    val r = snapshot.routine(o.commitmentId, o.plannedDate)!!
                    OutlinedButton({ work { repo.startRun(o.id) } }, enabled = !busy) { Text("Open ${r.title} · ${o.plannedDate}") }
                }
                if (runPage > 0) OutlinedButton({ runPage-- }) { Text("Earlier runs") }
                if ((runPage + 1) * 10 < available.size) OutlinedButton({ runPage++ }) { Text("Later runs") }
                runs.reversed().forEach { run ->
                    Text("${run.title} · ${if (run.finishedAt == null) "Active / resumable" else "Finished"}", style = MaterialTheme.typography.titleMedium)
                    Text("Routine version from ${records.find { it.id == run.versionId }?.fromDate.orEmpty()} · occurrence ${data.occurrences.find { it.id == run.occurrenceId }?.plannedDate.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    val result = AdaptiveJson.strings(run.results)
                    AdaptiveJson.strings(run.steps).forEachIndexed { i, step ->
                        Text("${i + 1}. $step · ${result[i].lowercase().replaceFirstChar { it.uppercase() }}")
                        logs.lastOrNull { it.entityId == run.id && it.action == "ROUTINE_STEP" && it.summary.startsWith("Step ${i + 1}:") }?.let {
                            Text("Recorded ${adaptiveTime(it.timestamp, repo.clock.zone())} · Self-reported", style = MaterialTheme.typography.bodySmall)
                        }
                        if (result[i] == "PENDING") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ work { repo.step(run.id, i, "COMPLETED") } }, enabled = !busy) { Text("Complete step ${i + 1}") }
                            OutlinedButton({ work { repo.step(run.id, i, "SKIPPED") } }, enabled = !busy) { Text("Skip step ${i + 1}") }
                        }
                    }
                }
                HorizontalDivider()
                Text("Change history", style = MaterialTheme.typography.titleMedium)
                records.takeLast(20).reversed().forEach { Text("${it.kind} · ${it.title} · ${it.fromDate} · saved ${Instant.ofEpochMilli(it.createdAt).atZone(repo.clock.zone())}") }
            }
        }
    } else {
        Text("${kind!!.lowercase().replaceFirstChar { it.uppercase() }} editor", style = MaterialTheme.typography.titleMedium)
        AdaptiveField("Name", title, !busy) { title = it; invalidate() }
        AdaptiveField("Effective date (YYYY-MM-DD)", from, !busy) { from = it; invalidate() }
        if (kind == "ROUTINE") {
            OutlinedTextField(steps, { steps = it; invalidate() }, label = { Text("Ordered steps (one per line)") }, minLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
        if (kind == "LINK") {
            records.filter { it.kind == "ROUTINE" }.distinctBy { it.family }.forEach { r ->
                FilterChip(linkRoutine == r.family, { linkRoutine = r.family; invalidate() }, label = { Text(r.title) })
            }
        }
        if (kind == "ASSIGN") {
            AdaptiveToggle("One-off day assignment", oneDay) { oneDay = it; invalidate() }
            if (!oneDay) AdaptiveField("Weekdays (1=Mon … 7=Sun)", weekdays, !busy) { weekdays = it; invalidate() }
        }
        if (kind == "PROFILE") {
            blocks.forEachIndexed { i, block ->
                Text("Block ${i + 1}", style = MaterialTheme.typography.titleMedium)
                fun change(b: ProfileBlock) { blocks = blocks.toMutableList().also { it[i] = b }; invalidate() }
                AdaptiveField("Block ${i + 1} title", block.title, !busy) { change(block.copy(title = it)) }
                AdaptiveField("Block ${i + 1} time (HH:mm)", block.time, !busy) { change(block.copy(time = it)) }
                AdaptiveField("Block ${i + 1} minutes", block.minutes.toString(), !busy) { change(block.copy(minutes = it.toIntOrNull() ?: 0)) }
                AdaptiveField("Block ${i + 1} priority (1–3)", block.priority.toString(), !busy) { change(block.copy(priority = it.toIntOrNull() ?: 0)) }
                AdaptiveToggle("Block ${i + 1} fixed", block.fixed) { change(block.copy(fixed = it)) }
                Text("Link a commitment. The block sets its proposed duration; an existing fixed commitment stays fixed.")
                FilterChip(block.commitmentId.isEmpty(), { change(block.copy(commitmentId = "", routineId = "")) }, label = { Text("Standalone proposed block") })
                data.commitments.filter { it.archivedAt == null }.forEach { c ->
                    FilterChip(block.commitmentId == c.id, { change(block.copy(commitmentId = c.id)) }, label = { Text(c.title) })
                }
                if (block.commitmentId.isNotEmpty()) {
                    FilterChip(block.routineId.isEmpty(), { change(block.copy(routineId = "")) }, label = { Text("No profile routine") })
                    records.filter { it.kind == "ROUTINE" }.distinctBy { it.family }.forEach { r ->
                        FilterChip(block.routineId == r.family, { change(block.copy(routineId = r.family)) }, label = { Text("Routine: ${r.title}") })
                    }
                }
                OutlinedButton({ blocks = blocks.filterIndexed { index, _ -> index != i }; invalidate() }, enabled = !busy) { Text("Remove block ${i + 1}") }
            }
            OutlinedButton({ blocks = blocks + ProfileBlock("", "09:00", 30, false); invalidate() }, enabled = !busy) { Text("Add block") }
        }
        if (kind in listOf("MODE", "DAY")) {
            if (kind == "MODE") AdaptiveField("End date inclusive (YYYY-MM-DD)", until, !busy) { until = it; invalidate() }
            AdaptiveField("Reason", reason, !busy) { reason = it; invalidate() }
            AdaptiveToggle("Keep essential only", essentialOnly) { essentialOnly = it; invalidate() }
            Text("Per-item exceptions: Keep restores the base schedule; Move stays within the allowed window; Skip suppresses for these dates. Essential health items default to keep. Check your instructions before changing them.")
            data.commitments.filter { it.archivedAt == null }.forEach { c ->
                HorizontalDivider()
                Text("${c.title}${if (c.essential) " · Essential" else ""}", style = MaterialTheme.typography.titleMedium)
                val policy = policies.firstOrNull { it.commitmentId == c.id }
                FilterChip(policy == null, { policies = policies.filter { it.commitmentId != c.id }; invalidate() }, label = { Text("Inherit") })
                Disposition.entries.forEach { d ->
                    FilterChip(policy?.disposition == d, { policies = policies.filter { it.commitmentId != c.id } + ItemPolicy(c.id, d, "19:00"); invalidate() },
                        label = { Text(if (d == Disposition.SUPPRESS) "Skip for these dates" else d.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
                if (policy?.disposition == Disposition.MOVE) AdaptiveField("Move ${c.title} to (HH:mm)", policy.time, !busy) { time ->
                    policies = policies.map { if (it.commitmentId == c.id) it.copy(time = time) else it }; invalidate()
                }
            }
        }
        Button({ work {
            val payload = when (kind) {
                "ROUTINE" -> JSONObject().put("format", 1).put("steps", org.json.JSONArray(steps.lines().map { it.trim() })).toString()
                "PROFILE" -> AdaptiveJson.blocks(blocks)
                "LINK" -> JSONObject().put("format", 1).put("routine", linkRoutine).toString()
                "ASSIGN" -> JSONObject().put("format", 1).put("weekdays", if (oneDay) "1,2,3,4,5,6,7" else weekdays.replace(" ", "")).toString()
                else -> AdaptiveJson.policy(DayPolicy(essentialOnly, policies, reason)).toString()
            }
            val r = repo.record(kind!!, title, from, when { kind == "DAY" || (kind == "ASSIGN" && oneDay) -> from; kind == "MODE" -> until; else -> "" }, payload,
                family.ifEmpty { java.util.UUID.randomUUID().toString() })
            val p = repo.preview(r); candidate = r; preview = p; previewPage = 0; ack = false
        } }, enabled = !busy) { Text("Preview change") }
        preview?.let { p ->
            Text("Preview · ${p.rows.size} items · up to 45 days from $from", style = MaterialTheme.typography.titleMedium)
            if (kind == "ROUTINE") Text(steps.lines().mapIndexed { i, s -> "${i + 1}. $s" }.joinToString("\n"))
            if (kind == "PROFILE") blocks.forEach { Text("${it.time} · ${it.title} · ${it.minutes} min · ${if (it.fixed) "Fixed" else "Flexible"}") }
            p.overlaps.forEach { Text("Mode overlap: $it", color = MaterialTheme.colorScheme.error) }
            p.rows.drop(previewPage * 10).take(10).forEach { row ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(row.title, style = MaterialTheme.typography.titleMedium)
                        Text(when (row.disposition) { "SUPPRESS", "SKIPPED_BY_OVERRIDE" -> "Suppressed"; "KEEP" -> "Kept"; "MOVE" -> "Moved"; "BLOCK" -> "Proposed block"; else -> row.disposition.lowercase().replaceFirstChar { it.uppercase() } }, color = MaterialTheme.colorScheme.primary)
                        Text("${adaptiveTime(row.start, repo.clock.zone())}–${adaptiveTime(row.end, repo.clock.zone())}")
                        Text(if (row.fixed) "Fixed" else "Flexible", style = MaterialTheme.typography.bodySmall)
                        if (row.detail.isNotBlank()) Text(row.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (previewPage > 0) OutlinedButton({ previewPage-- }) { Text("Previous preview items") }
            if ((previewPage + 1) * 10 < p.rows.size) OutlinedButton({ previewPage++ }) { Text("Next preview items") }
            p.conflicts.take(20).forEach { c -> Text("Overlap: ${c.first.title} (${adaptiveTime(c.first.start, repo.clock.zone())}–${adaptiveTime(c.first.end, repo.clock.zone())}, ${if (c.first.fixed) "fixed" else "flexible"}) / ${c.second.title} (${adaptiveTime(c.second.start, repo.clock.zone())}–${adaptiveTime(c.second.end, repo.clock.zone())}, ${if (c.second.fixed) "fixed" else "flexible"})", color = MaterialTheme.colorScheme.error) }
            if (p.conflicts.size > 20) Text("${p.conflicts.size} overlaps total across preview dates.")
            if (p.conflicts.isNotEmpty() || p.overlaps.isNotEmpty()) AdaptiveToggle("Keep these overlaps; latest mode wins", ack) { ack = it }
            Button({ work { repo.save(requireNotNull(candidate), p, ack); kind = null; invalidate(); message = "Saved. Existing history retained."; onNavigate() } }, enabled = !busy) { Text("Confirm adaptive change") }
        }
        OutlinedButton({ kind = null; invalidate(); error = null; onNavigate() }, enabled = !busy) { Text("Cancel change") }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it) }
    }
}

private fun adaptiveTime(value: Long, zone: ZoneId) = Instant.ofEpochMilli(value).atZone(zone).format(DateTimeFormatter.ofPattern("MMM d HH:mm z"))
@Composable private fun AdaptiveField(label: String, value: String, enabled: Boolean, change: (String) -> Unit) {
    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth())
}
@Composable private fun AdaptiveToggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row { Checkbox(value, change); Text(label, Modifier.padding(top = 12.dp)) }
}
