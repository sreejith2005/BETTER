package dev.better.app.feature.plan

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.better.app.data.*
import dev.better.app.domain.*
import java.time.*

@Composable
fun ScheduleScreen(model: ScheduleViewModel) {
    val data by model.data.collectAsStateWithLifecycle()
    val editor by model.editor.collectAsStateWithLifecycle()
    val selected by model.selected.collectAsStateWithLifecycle()
    val error by model.error.collectAsStateWithLifecycle()
    val focus by model.reminderFocus.collectAsStateWithLifecycle()
    Column(verticalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.fillMaxWidth()) {
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        when {
            focus != null -> {
                val target = data.occurrences.find { it.id == focus!!.occurrenceId }
                TextButton(onClick = { model.reminderFocus.value = null }) { Text("Back to plan") }
                if (focus!!.stale) Text("This reminder is no longer current. Your recorded history is shown below.")
                if (target != null) OccurrenceCard(model, data, target, focus!!.action)
                else Text("Opening the occurrence…")
            }
            editor.form!=null -> RuleEditor(model,editor,data)
            selected!=null -> CommitmentDetail(model,data,requireNotNull(selected))
            else -> {
                Button(onClick=model::create) { Text("Add commitment") }
                OutlinedButton(onClick=model::createFixed) { Text("Add fixed event") }
                data.commitments.forEach { c ->
                    OutlinedCard(onClick={ model.inspect(c.id) },modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(c.title,style=MaterialTheme.typography.titleMedium)
                            Text(if(c.archivedAt!=null) "Archived · history retained" else c.category.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitmentDetail(model: ScheduleViewModel, data: PlanData, id: String) {
    val c=data.commitments.firstOrNull { it.id==id } ?: return
    val v=data.versions.lastOrNull { it.commitmentId==id && it.effectiveUntil==null }
    var archivePrompt by remember { mutableStateOf(false) }
    TextButton(onClick={model.selected.value=null}) { Text("All commitments") }
    Text(c.title,style=MaterialTheme.typography.titleLarge)
    if(v==null) { Text("Schedule is being prepared."); return }
    val rule=data.rules.firstOrNull { it.id==v.ruleId }?.rule()
    Text("${v.category.lowercase().replaceFirstChar { it.uppercase() }} · priority ${v.priority} · ${v.durationSeconds/60} min · ${if(v.fixed) "Fixed" else "Flexible"}")
    Text("Reminder: ${v.reminderLevel.lowercase().replaceFirstChar { it.uppercase() }}. Android timing is best effort.")
    if(v.category=="HEALTH") Text("Follow your clinician’s instructions.")
    if(v.instructionText.isNotEmpty()) Text(v.instructionText)
    if(v.sourceText.isNotEmpty()) Text("Source: ${v.sourceText}")
    Text("${rule?.type?.name?.lowercase()?.replace('_',' ')} · ${rule?.zoneMode?.name?.lowercase()?.replace('_',' ')} · ${rule?.zoneId}")
    Text(data.rules.firstOrNull {it.id==v.ruleId}?.summary.orEmpty())
    if(c.archivedAt==null) {
        Button(onClick={model.edit(id)}) { Text("Edit schedule") }
        OutlinedButton(onClick={archivePrompt=true}) { Text("Archive commitment") }
    } else Button(onClick={model.restore(id)}) { Text("Restore commitment") }
    if(archivePrompt) AlertDialog(onDismissRequest={archivePrompt=false},title={Text("Archive this commitment?")},
        text={Text("Future generation stops. Existing history is retained, and you can restore it.")},
        confirmButton={TextButton(onClick={archivePrompt=false;model.archive(id)}) {Text("Archive")}},
        dismissButton={TextButton(onClick={archivePrompt=false}) {Text("Keep")}})
    Text("Occurrences and history",style=MaterialTheme.typography.titleMedium)
    var history by remember(id) { mutableStateOf(false) }
    var limit by remember(id,history) { mutableIntStateOf(10) }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(!history,{history=false},label={Text("Open occurrences")})
        FilterChip(history,{history=true},label={Text("History")})
    }
    val items=data.occurrences.filter { it.commitmentId==id &&
        (if(history) OccurrenceState.valueOf(it.state) in terminalStates else OccurrenceState.valueOf(it.state) !in terminalStates) }
        .let {if(history) it.sortedByDescending {o->o.startAt} else it}
    if(items.isEmpty()) Text(if(history) "No resolved history yet." else "No open occurrences. For a stopped interval, edit the schedule and choose a new anchor date to restart.")
    items.take(limit).forEach { o ->
        OccurrenceCard(model,data,o)
        if(!history && c.archivedAt==null) TextButton(onClick={model.edit(id,o.id);model.oneOccurrence()}) {Text("Edit this occurrence")}
    }
    if(items.size>limit) TextButton(onClick={limit+=10}) {Text("Show 10 more occurrences")}
    Text("Versions: ${data.versions.count { it.commitmentId==id }}. Past snapshots are retained.",style=MaterialTheme.typography.bodySmall)
}

@Composable
private fun RuleEditor(model: ScheduleViewModel, state: EditorState, data: PlanData) {
    val f=requireNotNull(state.form)
    val one=f.commitmentId!=null && f.scope==EditScope.THIS_OCCURRENCE
    Text(if(f.commitmentId==null) "New commitment" else "Edit commitment",style=MaterialTheme.typography.titleLarge)
    Field("Title",f.title) { model.update { form->form.copy(title=it) } }
    if(!one) {
        Choice("Category",listOf("PERSONAL","CAREER","HEALTH","APPOINTMENT"),f.category) {model.update {form->form.copy(category=it)}}
        Field("Priority (1 high, 2 normal, 3 low)",f.priority) {model.update {form->form.copy(priority=it)}}
        Toggle("Essential",f.essential) {model.update {form->form.copy(essential=it)}}
        Field("Duration in minutes",f.duration) {model.update {form->form.copy(duration=it)}}
        Toggle("Fixed event",f.fixed) {model.update {form->form.copy(fixed=it)}}
        Choice("Reminder policy",listOf("NONE","LIGHT","STANDARD","PERSISTENT","ESCALATING"),f.reminder) {model.update {form->form.copy(reminder=it)}}
        Toggle("Allow this reminder during quiet hours",f.criticalReminder) {model.update {form->form.copy(criticalReminder=it)}}
        Text("This is your quiet-hours exception. It does not bypass Android Do Not Disturb or guarantee exact timing.",style=MaterialTheme.typography.bodySmall)
        if(f.reminder=="ESCALATING") Field("Alert offsets in minutes (max 5)",f.reminderOffsets) {model.update {form->form.copy(reminderOffsets=it)}}
    }
    if(f.category=="HEALTH") Text("Follow your clinician’s instructions.")
    Field("Instructions (copied exactly)",f.instructions,false) {model.update {form->form.copy(instructions=it)}}
    Field("Source",f.source,false) {model.update {form->form.copy(source=it)}}
    if(f.commitmentId!=null) {
        Choice("Edit scope",EditScope.entries.map {it.name},f.scope.name) {
            model.selectScope(EditScope.valueOf(it))
        }
        val target=data.occurrences.find { it.id==f.selected }
        Text("Boundary occurrence: ${target?.let { readableTime(it.startAt,it.zoneId) } ?: "now"}")
        Text("Entire series preserves protected history and becomes this-and-future when necessary.")
    }
    if(one) Text("This override changes only this occurrence’s title, instructions, source and due time. Series policy stays unchanged.")
    if(!one) Choice("Recurrence",RuleType.entries.map {it.name},f.type.name) {model.update {form->form.copy(type=RuleType.valueOf(it))}}
    Field("Anchor date (YYYY-MM-DD)",f.anchor) {model.update {form->form.copy(anchor=it)}}
    Text("The anchor is the first due date. Sequence offsets are applied after it.",style=MaterialTheme.typography.bodySmall)
    if(!one) TextButton(onClick=model::calendarFollowUp) {Text("Resolve one calendar month after this date")}
    Choice("Due definition",DueKind.entries.map {it.name},f.dueKind.name) {model.update {form->form.copy(dueKind=DueKind.valueOf(it))}}
    if(f.dueKind!=DueKind.ANYTIME) Field("Due time (HH:mm)",f.time) {model.update {form->form.copy(time=it)}}
    if(f.dueKind==DueKind.WINDOW) Field("Window ends (HH:mm)",f.endTime) {model.update {form->form.copy(endTime=it)}}
    if(f.type in listOf(RuleType.DAILY,RuleType.WEEKLY,RuleType.INTERVAL_CALENDAR,RuleType.INTERVAL_COMPLETION))
        Field(if(f.type==RuleType.WEEKLY) "Interval (weeks, e.g. 2w)" else "Interval (e.g. 2d, 1w, 1mo)",f.interval) {model.update {form->form.copy(interval=it)}}
    if(f.type==RuleType.WEEKLY) DayOfWeek.entries.forEach { day->Toggle(day.name,day in f.weekdays) {checked->model.update {form->form.copy(weekdays=if(checked) form.weekdays+day else form.weekdays-day)}} }
    if(f.type==RuleType.SEQUENCE_THEN_REPEAT) {
        Field("Sequence offsets (e.g. 3d,2d,1d)",f.offsets) {model.update {form->form.copy(offsets=it)}}
        Field("Repeating tail (blank means stop)",f.tail) {model.update {form->form.copy(tail=it)}}
    }
    if(f.type==RuleType.INTERVAL_COMPLETION) Text("Only the next date is known. Completion starts the next interval; skipping requires an explicit restart choice.")
    if(!one) {
        Field("End date (optional)",f.endDate) {model.update {form->form.copy(endDate=it)}}
        Field("Occurrence count (optional)",f.count) {model.update {form->form.copy(count=it)}}
    }
    Choice("Timezone behavior",ZoneMode.entries.map {it.name},f.zoneMode.name) {model.update {form->form.copy(zoneMode=ZoneMode.valueOf(it))}}
    if(f.zoneMode==ZoneMode.LOCAL_FLOATING) Text("Device timezone: ${model.repository.clock.zone()}")
    else Field("IANA timezone",f.zoneId) {model.update {form->form.copy(zoneId=it)}}
    if(f.zoneMode==ZoneMode.ABSOLUTE_INSTANT) Field("Fixed UTC instant (e.g. 2026-10-01T12:00:00Z)",f.instant) {model.update {form->form.copy(instant=it)}}
    Choice("DST overlap offset",OverlapChoice.entries.map {it.name},f.overlap.name) {model.update {form->form.copy(overlap=OverlapChoice.valueOf(it))}}
    if(one) Field("Override reason",f.reason) {model.update {form->form.copy(reason=it)}}
    if(one && f.category=="CAREER") Toggle("Allow an additional move with this reason",state.extraMoveAcknowledged,model::acknowledgeExtraMove)
    state.notice?.let {Text(it)}
    state.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
    Button(onClick=model::preview,enabled=!state.busy) {Text("Preview next ten")}
    state.preview?.let { list->
        Text("Dated preview",style=MaterialTheme.typography.titleMedium)
        if(list.isEmpty()) Text("No occurrences. Review the rule.")
        list.forEachIndexed { i,p->
            Text("${i+1}. ${readablePlan(p)}\n${p.zone} · Operational day ${p.operationalDate}")
            if(p.resolution.isNotBlank()) Text(p.resolution,color=MaterialTheme.colorScheme.primary)
        }
        ConflictList(state.conflicts,model.repository.clock.zone().id)
        if(state.conflicts.isNotEmpty()) Toggle("Keep these overlaps",state.conflictsAcknowledged,model::acknowledgeConflicts)
        val needsAck=list.any {it.resolution.contains("DST")}
        if(needsAck) Toggle("I accept these DST resolutions",state.acknowledged,model::acknowledge)
        Button(onClick=model::save,enabled=list.isNotEmpty() && !state.busy && (!needsAck || state.acknowledged) && (state.conflicts.isEmpty() || state.conflictsAcknowledged)) {Text("Save commitment")}
    }
    TextButton(onClick=model::closeEditor,enabled=!state.busy) {Text("Cancel editing")}
}

@Composable private fun Field(label: String,value: String,singleLine: Boolean=true,onChange:(String)->Unit) {
    OutlinedTextField(value,onChange,label={Text(label)},singleLine=singleLine,modifier=Modifier.fillMaxWidth())
}
@Composable private fun Toggle(label:String,value:Boolean,onChange:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,modifier=Modifier.weight(1f).padding(top=12.dp)); Switch(value,onChange,modifier=Modifier.testTag("toggle-$label"))
    }
}
@Composable private fun Choice(label:String,options:List<String>,selected:String,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Column {
        Text(label,style=MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick={expanded=true}) {Text(selected.lowercase().replace('_',' '))}
            DropdownMenu(expanded,onDismissRequest={expanded=false}) {options.forEach {value->
                DropdownMenuItem(text={Text(value.lowercase().replace('_',' '))},onClick={expanded=false;onSelect(value)})
            }}
        }
    }
}
