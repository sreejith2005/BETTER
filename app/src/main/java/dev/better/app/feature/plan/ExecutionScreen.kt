package dev.better.app.feature.plan

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.better.app.data.*
import dev.better.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

fun readableTime(at: Long, zone: String): String = Instant.ofEpochMilli(at).atZone(ZoneId.of(zone))
    .format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm"))
fun readablePlan(p: PlannedOccurrence): String = readableTime(p.start.toEpochMilli(),p.zone) +
    if(p.start != p.end) " – " + p.end.atZone(ZoneId.of(p.zone)).format(DateTimeFormatter.ofPattern("d MMM HH:mm")) else ""
fun stateLabel(o: OccurrenceRecord, v: VersionRecord) = when {
    o.state == "COMPLETED" && o.completedLate -> "Completed late · Self-reported"
    o.state == "COMPLETED" -> "Completed · Self-reported"
    o.state == "MISSED" && v.category == "HEALTH" -> "Unconfirmed · Review your record"
    o.state == "SKIPPED_BY_OVERRIDE" -> "Suppressed by day override"
    o.state == "PENDING" -> "Upcoming"
    else -> o.state.lowercase().replace('_',' ').replaceFirstChar { it.uppercase() }
}

@Composable
fun ConflictList(conflicts: List<PlanConflict>, zone: String) {
    if(conflicts.isEmpty()) return
    Text("Overlapping events",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.error)
    Text("Blocks use the scheduled start and duration. Move an item or explicitly keep the overlap.",style=MaterialTheme.typography.bodySmall)
    conflicts.take(10).forEach { c ->
        listOf(c.first,c.second).forEach { i ->
            Text("${i.title} · ${if(i.fixed) "Fixed" else "Flexible"}\n${readableTime(i.start,zone)} – ${readableTime(i.end,zone)}",style=MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
    }
    if(conflicts.size>10) Text("${conflicts.size-10} more overlaps. Review individual dates in Plan.")
}

@Composable
fun TodayExecution(model: ScheduleViewModel, day: LocalDate, openPlan: () -> Unit) {
    val data by model.data.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    val active=data.commitments.filter {it.archivedAt==null}.map {it.id}.toSet()
    val items=data.occurrences.filter {it.commitmentId in active}
    val open=items.filter {it.state in listOf("PENDING","DUE","OVERDUE")}
    val next=open.sortedWith(compareBy<OccurrenceRecord> {if(it.state=="PENDING") 1 else 0}.thenBy {it.startAt}).firstOrNull()
    Text("Next action",style=MaterialTheme.typography.titleLarge)
    if(next==null) {
        Text("No open commitments. Add something to your plan.")
        Button(onClick={model.create();openPlan()}) {Text("Create a commitment")}
    } else {
        val v=data.versions.first {it.id==next.versionId}
        Text(data.overrides.find {it.occurrenceId==next.id}?.title ?: v.title,style=MaterialTheme.typography.titleMedium)
        Text("${stateLabel(next,v)} · ${readableTime(next.startAt,next.zoneId)}")
    }
    val today=items.filter {it.operationalDate==day.toString() || it.state in listOf("DUE","OVERDUE")}
    val intervals=today.filter {it.state in listOf("PENDING","DUE","OVERDUE")}.map {o->
        val v=data.versions.first {it.id==o.versionId}
        OccupiedInterval(o.id,data.overrides.find {it.occurrenceId==o.id}?.title ?: v.title,o.startAt,o.startAt+v.durationSeconds*1000L,v.fixed)
    }
    ConflictList(conflicts(intervals),model.repository.clock.zone().id)
    listOf("Due and overdue" to today.filter {it.state in listOf("DUE","OVERDUE")},
        "Upcoming" to open.filter {it.state=="PENDING"}.take(5),
        "Today's record" to today.filter {it.state !in listOf("PENDING","DUE","OVERDUE")}).forEach { (label, list)->
        if(list.isNotEmpty()) {
            Text(label,style=MaterialTheme.typography.titleMedium)
            list.forEach {OccurrenceCard(model,data,it)}
        }
    }
    val missed=items.count {it.state=="MISSED" && it.operationalDate<day.toString()}
    if(missed>0) Text("$missed earlier missed or unconfirmed items. Open Review to inspect them.")
    TextButton(onClick={model.selected.value=null;openPlan()}) {Text("Open full plan")}
}

@Composable
fun ReviewExecution(model: ScheduleViewModel, today: LocalDate) {
    val data by model.data.collectAsStateWithLifecycle()
    var date by rememberSaveable {mutableStateOf(today.toString())}
    var week by rememberSaveable {mutableStateOf(false)}
    var limit by remember(date,week) {mutableIntStateOf(10)}
    val end=LocalDate.parse(date)
    val from=if(week) end.minusDays(6) else end
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick={date=end.minusDays(if(week) 7 else 1).toString()}) {Text("Previous")}
        OutlinedButton(onClick={date=minOf(today,end.plusDays(if(week) 7 else 1)).toString()},enabled=end<today) {Text("Next")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(!week,{week=false},label={Text("Daily")})
        FilterChip(week,{week=true},label={Text("Seven days")})
        TextButton(onClick={date=today.toString()}) {Text("Today")}
    }
    Text(if(week) "$from through $end" else end.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),style=MaterialTheme.typography.titleMedium)
    Text(if(end>=today) "In progress" else "Recorded history")
    val list=data.occurrences.filter {it.operationalDate>=from.toString() && it.operationalDate<=date}
    val counts=reviewCounts(list.map {it.state to it.completedLate})
    Text("Planned ${counts.planned} · Open ${counts.open}")
    Text("Completed on time ${counts.completed}\nCompleted late ${counts.late}\nSkipped ${counts.skipped}\nMissed / unconfirmed ${counts.missed}\nSuppressed by override ${counts.suppressed}\nCancelled ${counts.cancelled}")
    Text("Manual completion adds no timed minutes. Career timer evidence is shown separately.",style=MaterialTheme.typography.bodySmall)
    if(list.isEmpty()) Text("No occurrences recorded for these dates.")
    list.sortedBy {it.startAt}.take(limit).forEach {OccurrenceCard(model,data,it)}
    if(list.size>limit) TextButton(onClick={limit+=10}) {Text("Show 10 more records")}
}

@Composable
fun OccurrenceCard(model: ScheduleViewModel, data: PlanData, o: OccurrenceRecord, initialAction: String = "OPEN") {
    val v=data.versions.firstOrNull {it.id==o.versionId} ?: return
    val override=data.overrides.find {it.occurrenceId==o.id}
    val logs by model.logs.collectAsStateWithLifecycle()
    var action by remember(o.id, initialAction) {mutableStateOf(initialAction.takeIf { it in listOf("COMPLETE", "MOVE", "SKIP") })}
    val deliveries by model.deliveries.collectAsStateWithLifecycle()
    var history by remember(o.id) {mutableStateOf(false)}
    var error by remember(o.id) {mutableStateOf<String?>(null)}
    var busy by remember(o.id) {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val active=data.commitments.find {it.id==o.commitmentId}?.archivedAt==null
    val canAct=active && (OccurrenceState.valueOf(o.state) !in terminalStates || o.state=="MISSED")
    OutlinedCard(Modifier.fillMaxWidth().testTag("occurrence-${o.id}")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(override?.title ?: v.title,style=MaterialTheme.typography.titleMedium)
            Text(stateLabel(o,v),color=MaterialTheme.colorScheme.primary)
            Text(readablePlan(o.plan()))
            Text("${o.zoneId} · ${if(v.fixed) "Fixed" else "Flexible"} · ${v.durationSeconds/60} min",style=MaterialTheme.typography.bodySmall)
            if(v.category=="HEALTH") Text("Follow your clinician’s instructions.")
            (override?.instructionText ?: v.instructionText).takeIf {it.isNotBlank()}?.let {Text(it)}
            (override?.sourceText ?: v.sourceText).takeIf {it.isNotBlank()}?.let {Text("Source: $it",style=MaterialTheme.typography.bodySmall)}
            o.actualAt?.let {Text("Recorded ${readableTime(it,o.zoneId)}",style=MaterialTheme.typography.bodySmall)}
            o.reason?.takeIf {it.isNotBlank()}?.let {Text("Reason: $it")}
            if(o.resolution.isNotBlank()) Text(o.resolution,style=MaterialTheme.typography.bodySmall)
            val alerts = deliveries.filter { it.occurrenceId == o.id }
            if (alerts.isNotEmpty()) {
                Text("Reminders: ${alerts.count { it.result == "PLANNED" }} planned · ${alerts.count { it.postedAt != null }} posted · ${alerts.count { it.observedAt != null }} opened", style=MaterialTheme.typography.bodySmall)
                Text("Posted means the app sent a request to Android. Viewing is unknown until a notification action is used.", style=MaterialTheme.typography.bodySmall)
            }
            if(canAct) {
                Button(onClick={action="COMPLETE"},modifier=Modifier.fillMaxWidth()) {Text(if(o.state=="MISSED") "Record late completion" else "Complete")}
                if(o.state!="MISSED") {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={action="MOVE"}) {Text("Move")}
                        OutlinedButton(onClick={action="SKIP"}) {Text("Skip")}
                        TextButton(onClick={action="CANCEL"}) {Text("Cancel")}
                    }
                }
            }
            TextButton(onClick={history=!history}) {Text(if(history) "Hide history" else "History and corrections")}
            if(history) {
                alerts.forEach { alert ->
                    Text("Alert ${alert.steps}: ${alert.result.lowercase()}${if(alert.delayed) " · delayed by quiet hours" else ""}\nPlanned ${readableTime(alert.plannedAt,o.zoneId)}" +
                        (alert.postedAt?.let { "\nPosted ${readableTime(it,o.zoneId)}" } ?: "") +
                        (alert.observedAt?.let { "\nOpened ${readableTime(it,o.zoneId)}" } ?: "") + "\n${alert.detail}", style=MaterialTheme.typography.bodySmall)
                }
                Text("Operational day ${o.operationalDate} · version ${o.versionId.take(8)}",style=MaterialTheme.typography.bodySmall)
                val actions=logs.filter {it.entityId==o.id}
                if(actions.isEmpty()) Text("No user actions recorded.")
                actions.forEach {Text("${readableTime(it.timestamp,o.zoneId)}\n${actionSummary(it)}",style=MaterialTheme.typography.bodySmall)}
                val last=actions.lastOrNull()
                if(active && last?.action?.startsWith("EXEC_")==true && last.action!="EXEC_UNDO") {
                    OutlinedButton(enabled=!busy,onClick={busy=true;scope.launch {
                        try {model.execution.undo(last.id);error=null}
                        catch(e:CancellationException){throw e}
                        catch(e:Exception){error=e.message}
                        finally{busy=false}
                    }}) {Text("Undo last action")}
                    Text("Undo keeps the original audit record. It stops being available if the next interval has started or changed.",style=MaterialTheme.typography.bodySmall)
                }
            }
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    action?.let {ActionDialog(model,data,o,it,onClose={action=null})}
}

@Composable
private fun ActionDialog(model:ScheduleViewModel,data:PlanData,o:OccurrenceRecord,kind:String,onClose:()->Unit) {
    val version=data.versions.first {it.id==o.versionId}
    val rule=data.rules.first {it.id==version.ruleId}.rule()
    val start=Instant.ofEpochMilli(o.startAt).atZone(ZoneId.of(o.zoneId))
    var date by remember {mutableStateOf(start.toLocalDate().toString())}
    var time by remember {mutableStateOf(start.toLocalTime().withSecond(0).withNano(0).toString())}
    var reason by remember {mutableStateOf("")}
    var restart by remember {mutableStateOf(SkipRestart.STOP)}
    var overlap by remember {mutableStateOf(rule.overlap)}
    var plan by remember {mutableStateOf<PlannedOccurrence?>(null)}
    var clashes by remember {mutableStateOf(emptyList<PlanConflict>())}
    var ack by remember {mutableStateOf(false)}
    var extra by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var busy by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val completionSkip=kind=="SKIP" && rule.type==RuleType.INTERVAL_COMPLETION
    val timed=kind=="MOVE" || (completionSkip && restart in listOf(SkipRestart.MANUAL_NEXT_DATE,SkipRestart.RESTART_FROM_SKIP))
    val needsFields=kind=="MOVE" || (completionSkip && restart==SkipRestart.MANUAL_NEXT_DATE)
    fun invalidate(){plan=null;clashes=emptyList();ack=false;error=null}
    AlertDialog(onDismissRequest={if(!busy) onClose()},title={Text(when(kind){"COMPLETE"->"Record completion";"MOVE"->"Move this occurrence";"SKIP"->"Skip this occurrence";else->"Cancel this occurrence"})},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(data.overrides.find {it.occurrenceId==o.id}?.title ?: version.title,style=MaterialTheme.typography.titleMedium)
            if(kind=="COMPLETE") {
                Text("Record your confirmation now. Evidence: self-reported.")
                if(model.repository.clock.now().toEpochMilli()>o.endAt) Text("This will be recorded as completed late. The original planned day stays in history.")
                if(version.category=="CAREER") Text("This does not add Career timer minutes.")
            } else OutlinedTextField(reason,{reason=it},label={Text("Reason")},modifier=Modifier.fillMaxWidth(),enabled=!busy)
            if(completionSkip) {
                Text("Choose how this completion-based interval continues. No next date is created when stopped.")
                listOf(SkipRestart.STOP to "Stop until I choose a new date",SkipRestart.RESTART_FROM_SKIP to "Restart interval from skip time",SkipRestart.MANUAL_NEXT_DATE to "Choose next date").forEach {(choice,label)->
                    FilterChip(restart==choice,{restart=choice;invalidate()},label={Text(label)},enabled=!busy)
                }
            }
            if(needsFields) {
                OutlinedTextField(date,{date=it;invalidate()},label={Text("New date (YYYY-MM-DD)")},singleLine=true,enabled=!busy)
                OutlinedTextField(time,{time=it;invalidate()},label={Text("New time (HH:mm)")},singleLine=true,enabled=!busy)
                Text("Timezone: ${o.zoneId}. The due window keeps its local length.")
                Row {OverlapChoice.entries.forEach {c->FilterChip(overlap==c,{overlap=c;invalidate()},label={Text("${c.name.lowercase()} DST offset")},enabled=!busy)}}
            }
            if(kind=="MOVE" && version.category=="CAREER") Row {
                Checkbox(extra,{extra=it},enabled=!busy)
                Text("Allow an additional move with this reason",modifier=Modifier.weight(1f))
            }
            if(timed) {
                OutlinedButton(enabled=!busy,onClick={busy=true;scope.launch {
                    try {
                        val zone=ZoneId.of(o.zoneId)
                        val boundary=LocalTime.parse(model.repository.settings().dayBoundary)
                        val p=if(completionSkip && restart==SkipRestart.RESTART_FROM_SKIP) {
                            ScheduleEngine.preview(rule,model.repository.clock.now().atZone(zone).toLocalDate(),zone,boundary,completedAt=model.repository.clock.now()).firstOrNull()
                                ?: error("The interval has no next date within its limits.")
                        } else {
                            val localTime=LocalTime.parse(time)
                            val originalEnd=Instant.ofEpochMilli(o.endAt).atZone(zone).toLocalTime()
                            val windowSeconds=Duration.between(start.toLocalTime(),originalEnd).seconds.let {if(it<=0) it+86400 else it}
                            val shifted=rule.copy(type=RuleType.ONE_OFF,anchor=LocalDate.parse(date),time=localTime,
                                endTime=localTime.plusSeconds(windowSeconds),zoneMode=ZoneMode.ANCHORED_ZONE,zoneId=o.zoneId,
                                overlap=overlap,endDate=null,count=null,absoluteInstant=null,
                                dueKind=if(rule.dueKind==DueKind.ANYTIME) DueKind.EXACT else rule.dueKind)
                            ScheduleEngine.plan(shifted,shifted.anchor,zone,boundary)
                        }
                        require(p.start>model.repository.clock.now()) {"Choose a future time."}
                        plan=p;ack=false
                        clashes=model.repository.planConflicts(listOf(p),version.durationSeconds,version.fixed,
                            if(completionSkip) o.commitmentId else null,if(kind=="MOVE") o.id else null)
                        error=null
                    }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message;plan=null}finally{busy=false}
                }}) {Text("Preview new time")}
                plan?.let {Text(readablePlan(it));if(it.resolution.isNotBlank()) Text(it.resolution)}
                ConflictList(clashes,o.zoneId)
                if(clashes.isNotEmpty() || plan?.resolution?.contains("DST")==true) Row {
                    Checkbox(ack,{ack=it},enabled=!busy);Text("Keep overlap / accept DST resolution",modifier=Modifier.weight(1f))
                }
            }
            if(version.category=="HEALTH") Text("Follow your clinician’s instructions. BETTER records your choice; it does not recommend a treatment change.")
            error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        }},
        confirmButton={TextButton(enabled=!busy && (!timed || plan!=null) &&
            (ack || (clashes.isEmpty() && plan?.resolution?.contains("DST")!=true)),onClick={busy=true;scope.launch {
            try {
                model.execution.act(o.id,kind,reason,plan,restart,o,ack,extra)
                onClose()
            }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}
        }}) {Text(if(busy) "Saving…" else "Confirm ${kind.lowercase()}")}},
        dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Go back")}})
}
