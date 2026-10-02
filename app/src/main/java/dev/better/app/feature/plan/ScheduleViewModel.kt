package dev.better.app.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.better.app.data.*
import dev.better.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

data class PlanData(val commitments: List<Commitment> = emptyList(), val versions: List<VersionRecord> = emptyList(),
    val rules: List<RuleRecord> = emptyList(), val occurrences: List<OccurrenceRecord> = emptyList(), val overrides: List<OverrideRecord> = emptyList())

data class RuleForm(
    val commitmentId: String? = null, val title: String = "", val category: String = "PERSONAL",
    val priority: String = "2", val essential: Boolean = false, val instructions: String = "", val source: String = "",
    val duration: String = "30", val fixed: Boolean = false, val reminder: String = "STANDARD", val reminderOffsets: String = "0,15",
    val type: RuleType = RuleType.ONE_OFF, val anchor: String, val time: String = "19:00", val dueKind: DueKind = DueKind.EXACT,
    val endTime: String = "21:00", val interval: String = "1d", val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY),
    val offsets: String = "", val tail: String = "", val endDate: String = "", val count: String = "",
    val zoneMode: ZoneMode = ZoneMode.LOCAL_FLOATING, val zoneId: String, val overlap: OverlapChoice = OverlapChoice.EARLIER,
    val instant: String = "", val scope: EditScope = EditScope.THIS_AND_FUTURE, val selected: String? = null, val reason: String = "",
    val criticalReminder: Boolean = false,
) {
    fun draft(): CommitmentDraft {
        val fixedInstant = if (zoneMode == ZoneMode.ABSOLUTE_INSTANT) Instant.parse(instant) else null
        val date = fixedInstant?.atZone(ZoneId.of(zoneId))?.toLocalDate() ?: LocalDate.parse(anchor)
        val step = when (type) {
            RuleType.DAILY -> DateStep(interval.removeSuffix("d").toInt(), DateUnit.DAYS)
            RuleType.WEEKLY -> DateStep(interval.removeSuffix("w").removeSuffix("d").toInt(), DateUnit.WEEKS)
            RuleType.INTERVAL_CALENDAR, RuleType.INTERVAL_COMPLETION -> DateStep.parse(interval)
            else -> DateStep(1)
        }
        return CommitmentDraft(title, ScheduleRule(type,date,if(dueKind==DueKind.ANYTIME) LocalTime.MIDNIGHT else LocalTime.parse(time),dueKind,
            if(dueKind==DueKind.WINDOW) LocalTime.parse(endTime) else LocalTime.of(21,0),step,weekdays,
            if(type==RuleType.SEQUENCE_THEN_REPEAT) DateStep.parseSequence(offsets) else emptyList(),
            tail.takeIf { type==RuleType.SEQUENCE_THEN_REPEAT && it.isNotBlank() }?.let(DateStep::parse),
            endDate.takeIf { it.isNotBlank() }?.let(LocalDate::parse),count.takeIf { it.isNotBlank() }?.toInt(),zoneMode,zoneId,overlap,fixedInstant),
            category,priority.toInt(),essential,instructions,source,Math.multiplyExact(duration.toInt(),60),fixed,reminder,reminderOffsets,criticalReminder)
    }
}

data class EditorState(val form: RuleForm? = null, val preview: List<PlannedOccurrence>? = null,
    val acknowledged: Boolean = false, val error: String? = null, val busy: Boolean = false, val notice: String? = null,
    val conflicts: List<PlanConflict> = emptyList(), val conflictsAcknowledged: Boolean = false,
    val extraMoveAcknowledged: Boolean = false)

data class ReminderFocus(val occurrenceId: String, val action: String, val stale: Boolean)

class ScheduleViewModel(val repository: ScheduleRepository, val reminders: ReminderRepository? = null) : ViewModel() {
    val deliveries = repository.db.reminders().watch().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminderFocus = MutableStateFlow<ReminderFocus?>(null)
    suspend fun openReminder(id: String, requestedAction: String) {
        val delivery = reminders?.observed(id) ?: return
        val o = repository.dao.occurrence(delivery.occurrenceId) ?: return
        val stale = o.state !in ReminderRepository.OPEN || o.startAt != delivery.scheduleStart
        reminderFocus.value = ReminderFocus(o.id, if (stale) "OPEN" else requestedAction, stale)
        mutableEditor.value = EditorState()
    }
    val execution = ExecutionRepository(repository)
    val logs = repository.db.foundation().watchLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val mutableEditor = MutableStateFlow(EditorState())
    val editor = mutableEditor.asStateFlow()
    val selected = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<String?>(null)
    val data = combine(repository.db.foundation().observeCommitments(),repository.dao.watchVersions(),repository.dao.watchRules(),
        repository.dao.watchOccurrences(),repository.dao.watchOverrides()) { c,v,r,o,x -> PlanData(c,v,r,o,x) }
        .stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),PlanData())

    init { refresh() }
    fun refresh() = action { repository.ensureCareerSchedule(); repository.reconcile(); reminders?.reconcile() }
    fun create() { if(editor.value.busy) return; mutableEditor.value = EditorState(RuleForm(anchor = repository.clock.now().atZone(repository.clock.zone()).toLocalDate().toString(), zoneId=repository.clock.zone().id)); selected.value=null }
    fun inspect(id: String) { if(editor.value.busy) return; selected.value=id; mutableEditor.value=EditorState() }
    fun closeEditor() { if(!editor.value.busy) mutableEditor.value=EditorState() }
    fun update(transform: (RuleForm) -> RuleForm) {
        if(editor.value.busy) return
        mutableEditor.value.form?.let { mutableEditor.value=EditorState(transform(it)) }
    }
    fun acknowledge(value: Boolean) { if(!editor.value.busy) mutableEditor.value=mutableEditor.value.copy(acknowledged=value) }
    fun acknowledgeConflicts(value: Boolean) { if(!editor.value.busy) mutableEditor.value=mutableEditor.value.copy(conflictsAcknowledged=value) }
    fun acknowledgeExtraMove(value: Boolean) { if(!editor.value.busy) mutableEditor.value=mutableEditor.value.copy(extraMoveAcknowledged=value) }
    fun createFixed() { create(); update { it.copy(category="APPOINTMENT", fixed=true, reminder="NONE") } }
    fun edit(id: String, occurrence: String? = null) {
        if(editor.value.busy) return
        val d=data.value
        val v=d.versions.lastOrNull { it.commitmentId==id && it.effectiveUntil==null } ?: return
        val r=d.rules.first { it.id==v.ruleId }.rule()
        val next=occurrence ?: d.occurrences.firstOrNull { it.commitmentId==id && it.startAt>=repository.clock.now().toEpochMilli() }?.id
        mutableEditor.value=EditorState(RuleForm(id,v.title,v.category,v.priority.toString(),v.essential,v.instructionText,v.sourceText,
            (v.durationSeconds/60).toString(),v.fixed,v.reminderLevel,v.reminderOffsets,r.type,r.anchor.toString(),r.time.toString(),r.dueKind,
            r.endTime.toString(),r.step.toString(),r.weekdays,r.offsets.joinToString(","),r.tail?.toString().orEmpty(),r.endDate?.toString().orEmpty(),
            r.count?.toString().orEmpty(),r.zoneMode,r.zoneId,r.overlap,r.absoluteInstant?.toString().orEmpty(),selected=next,criticalReminder=v.criticalReminder))
    }
    fun oneOccurrence() {
        val f=editor.value.form ?: return
        val o=data.value.occurrences.firstOrNull { it.id==f.selected } ?: return
        val v=data.value.versions.first {it.id==o.versionId}
        val override=data.value.overrides.find {it.occurrenceId==o.id}
        val start=Instant.ofEpochMilli(o.startAt).atZone(ZoneId.of(o.zoneId))
        val end=Instant.ofEpochMilli(o.endAt).atZone(ZoneId.of(o.zoneId))
        update { it.copy(title=override?.title ?: v.title,instructions=override?.instructionText ?: v.instructionText,
            source=override?.sourceText ?: v.sourceText,category=v.category,priority=v.priority.toString(),essential=v.essential,
            duration=(v.durationSeconds/60).toString(),fixed=v.fixed,reminder=v.reminderLevel,reminderOffsets=v.reminderOffsets,
            scope=EditScope.THIS_OCCURRENCE,type=RuleType.ONE_OFF,anchor=start.toLocalDate().toString(),
            time=start.toLocalTime().toString(),endTime=end.toLocalTime().toString(),dueKind=if(o.startAt==o.endAt) DueKind.EXACT else DueKind.WINDOW,
            zoneMode=ZoneMode.ANCHORED_ZONE,zoneId=o.zoneId,count="",endDate="") }
    }
    fun selectScope(scope: EditScope) {
        val f=editor.value.form ?: return
        if(scope==EditScope.THIS_OCCURRENCE) { oneOccurrence(); return }
        if(f.scope==EditScope.THIS_OCCURRENCE && f.commitmentId!=null) {
            edit(f.commitmentId,f.selected)
            update {it.copy(scope=scope,title=f.title,instructions=f.instructions,source=f.source)}
        } else update {it.copy(scope=scope)}
    }
    fun calendarFollowUp() {
        try {
            val f=requireNotNull(editor.value.form)
            val resolved=LocalDate.parse(f.anchor).plusMonths(1)
            update { it.copy(anchor=resolved.toString(),type=RuleType.ONE_OFF) }
            mutableEditor.value=editor.value.copy(notice="Resolved to $resolved. This is a separate one-off; review it if the original start changes.")
        } catch (_: Exception) { mutableEditor.value=editor.value.copy(error="Enter a valid anchor date first (YYYY-MM-DD).") }
    }
    fun preview() {
        val f=editor.value.form ?: return
        mutableEditor.value=editor.value.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val history=data.value.occurrences.any { it.commitmentId==f.commitmentId && it.protected(repository.clock.now().toEpochMilli()) }
                val decision=resolveEditScope(f.scope,history)
                val from=if(decision.effectiveScope==EditScope.THIS_AND_FUTURE)
                    data.value.occurrences.find { it.id==f.selected }?.plannedDate?.let(LocalDate::parse) else null
                val p=repository.preview(f.draft(),from)
                val d=f.draft()
                val clashes=repository.planConflicts(p,d.durationSeconds,d.fixed,
                    if(f.scope==EditScope.THIS_OCCURRENCE) null else f.commitmentId,
                    if(f.scope==EditScope.THIS_OCCURRENCE) f.selected else null)
                mutableEditor.value=editor.value.copy(preview=p,busy=false,notice=decision.explanation,conflicts=clashes,conflictsAcknowledged=false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableEditor.value=editor.value.copy(busy=false,error=e.message ?: "Check dates, times and intervals.",preview=null) }
        }
    }
    fun save() {
        val before=editor.value
        val f=before.form ?: return
        if (before.preview==null || before.busy) return
        mutableEditor.value=before.copy(busy=true,error=null)
        viewModelScope.launch {
            try {
                val id=repository.save(f.draft(),before.acknowledged,f.commitmentId,f.scope,f.selected,
                    if(f.scope==EditScope.THIS_OCCURRENCE) f.reason else "Schedule edit",expectedPreview=before.preview,
                    conflictsAcknowledged=before.conflictsAcknowledged,extraMoveAcknowledged=before.extraMoveAcknowledged)
                mutableEditor.value=EditorState(); selected.value=id
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableEditor.value=editor.value.copy(busy=false,error=e.message ?: "Could not save. Your prior plan is retained.") }
        }
    }
    fun archive(id: String) = action { repository.archive(id) }
    fun restore(id: String) = action { repository.restore(id) }
    private fun action(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); error.value=null }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value=e.message ?: "The local plan could not be updated." }
    } }
}
