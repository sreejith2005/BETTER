package dev.better.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AdaptiveStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-adaptive-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private val clock = MutableClock(Instant.parse("2026-10-03T06:00:00Z"), ZoneId.of("UTC"))
    private lateinit var schedules: ScheduleRepository
    private lateinit var repo: AdaptiveRepository
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetterDatabase::class.java)
    @Before fun setup() = runBlocking {
        db = BetterDatabase.open(context, name); LocalRepository(db, clock).onboard(SetupInput(zone = "UTC"))
        schedules = ScheduleRepository(db, clock); repo = AdaptiveRepository(schedules)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun add(title: String = "Work", essential: Boolean = false, fixed: Boolean = false) = schedules.save(
        CommitmentDraft(title, ScheduleRule(RuleType.DAILY, LocalDate.of(2026,10,3), LocalTime.of(9,0), DueKind.WINDOW, LocalTime.of(13,0), zoneId = "UTC"), essential = essential, fixed = fixed), false, conflictsAcknowledged = true)
    private suspend fun save(r: AdaptiveRecord, ack: Boolean = true) { repo.save(r, repo.preview(r), ack) }
    private fun policy(kind: String, first: String = "2026-10-03", last: String = first, p: DayPolicy = DayPolicy(true, reason = "Rest"), title: String = "Travel") =
        repo.record(kind, title, first, last, AdaptiveJson.policy(p).toString())
    private fun profile(id: String, title: String = "Workday", time: String = "10:00", date: String = "2026-10-03", family: String = UUID.randomUUID().toString()) =
        repo.record("PROFILE", title, date, payload = AdaptiveJson.blocks(listOf(ProfileBlock("Work", time, 30, false, id))), family = family)
    private fun assign(p: AdaptiveRecord, date: String = p.fromDate, oneDay: Boolean = false) = repo.record("ASSIGN", p.title, date,
        if (oneDay) date else "", JSONObject().put("format",1).put("weekdays","1,2,3,4,5,6,7").toString(), p.family)
    private suspend fun occurrence(id: String, date: String = "2026-10-03") = schedules.dao.occurrences().single { it.commitmentId == id && it.plannedDate == date }

    @Test fun at16And17RunResultsAndOldStepOrderSurviveVersionEditAndReopen() = runBlocking {
        val id = add()
        val routine = repo.record("ROUTINE", "Morning", "2026-10-03", payload = JSONObject().put("format",1).put("steps", org.json.JSONArray(listOf("One","Two","Three"))).toString())
        save(routine)
        save(repo.record("LINK", "Attached", "2026-10-03", payload = JSONObject().put("format",1).put("routine",routine.family).toString(), family = id))
        val run = repo.startRun(occurrence(id).id)
        repo.step(run.id,0,"COMPLETED"); repo.step(run.id,1,"SKIPPED"); repo.step(run.id,2,"COMPLETED")
        repo.step(run.id,1,"COMPLETED")
        val finished = repo.dao.runs().single()
        assertEquals(listOf("COMPLETED","SKIPPED","COMPLETED"), AdaptiveJson.strings(finished.results)); assertNotNull(finished.finishedAt)
        save(routine.copy(id = UUID.randomUUID().toString(), fromDate = "2026-10-04", payload = JSONObject().put("format",1).put("steps",org.json.JSONArray(listOf("Three changed","One"))).toString()))
        assertEquals(finished, repo.dao.runs().single())
        val next = repo.startRun(occurrence(id,"2026-10-04").id)
        assertEquals(listOf("Three changed","One"), AdaptiveJson.strings(next.steps))
        db.close(); db = BetterDatabase.open(context, name)
        assertEquals(finished, db.adaptive().runs().first { it.id == run.id })
        assertEquals(3, db.foundation().logs().count { it.action == "ROUTINE_STEP" })
    }
    @Test fun at18ProfilesKeepStableIdsAndFlagFixedOverlaps() = runBlocking {
        val id = add(); val unrelated = add("Unrelated", fixed = true)
        val original = occurrence(id)
        val p = profile(id); save(p); save(assign(p))
        assertEquals(original.id, occurrence(id).id); assertEquals(10, occurrence(id).plan().start.atZone(clock.zone()).hour)
        assertEquals(9, occurrence(unrelated).plan().start.atZone(clock.zone()).hour)
        val other = repo.record("PROFILE","Workday + Run","2026-10-03", payload = AdaptiveJson.blocks(listOf(
            ProfileBlock("Run","09:00",60,true),ProfileBlock("Appointment","09:30",60,true))))
        val preview = repo.preview(other)
        assertTrue(preview.conflicts.any { it.first.fixed && it.second.fixed })
        assertTrue(runCatching { repo.save(other,preview,false) }.isFailure)
        repo.save(other,preview,true); save(assign(other,"2026-10-04",true))
        assertEquals(other.id, AdaptiveSnapshot(repo.dao.records()).profile("2026-10-04")!!.id)
        assertEquals(p.id, AdaptiveSnapshot(repo.dao.records()).profile("2026-10-05")!!.id)
        assertEquals(45, schedules.dao.occurrences().count { it.commitmentId == id })
    }
    @Test fun at19MondayVersionPreservesPriorDaysAndUnrelatedWork() = runBlocking {
        val id = add(); val other = add("Other")
        val p = profile(id); save(p); save(assign(p))
        clock.instant = Instant.parse("2026-10-03T10:30:00Z"); schedules.reconcile()
        val historical = occurrence(id)
        save(profile(id,"Workday + Run","11:00","2026-10-05",p.family))
        assertEquals(historical,occurrence(id))
        assertEquals(11,occurrence(id,"2026-10-05").plan().start.atZone(clock.zone()).hour)
        assertEquals(9,occurrence(other,"2026-10-05").plan().start.atZone(clock.zone()).hour)
        assertEquals(p.id,AdaptiveSnapshot(repo.dao.records()).profile("2026-10-04")!!.id)
    }
    @Test fun at20TravelPreviewExpiryOverlapResolutionAndLatestWins() = runBlocking {
        val moved = add("Move"); val suppressed = add("Suppress"); val essential = add("Essential",true)
        val travel = policy("MODE", last = "2026-10-05", p = DayPolicy(true,listOf(ItemPolicy(moved,Disposition.MOVE,"11:00")),"Away"))
        val preview = repo.preview(travel)
        assertEquals("MOVE",preview.rows.first { it.id == occurrence(moved).id }.disposition)
        assertEquals("SUPPRESS",preview.rows.first { it.id == occurrence(suppressed).id }.disposition)
        assertEquals("KEEP",preview.rows.first { it.id == occurrence(essential).id }.disposition)
        repo.save(travel,preview,true)
        assertEquals("SKIPPED_BY_OVERRIDE",occurrence(suppressed).state)
        assertEquals("PENDING",occurrence(suppressed,"2026-10-06").state)
        val normal = policy("MODE",last="2026-10-04",p=DayPolicy(reason="Back"),title="Normal")
        assertTrue(repo.preview(normal).overlaps.isNotEmpty())
        assertTrue(runCatching { save(normal,false) }.isFailure); save(normal)
        assertEquals("PENDING",occurrence(suppressed).state)
        assertEquals("SKIPPED_BY_OVERRIDE",occurrence(suppressed,"2026-10-05").state)
    }
    @Test fun at21SuppressionNeverBecomesMissedAndEssentialCanBeExplicitlyChanged() = runBlocking {
        val ordinary = add(); val essential = add("Health",true)
        save(policy("DAY"))
        assertEquals("SKIPPED_BY_OVERRIDE",occurrence(ordinary).state); assertEquals("PENDING",occurrence(essential).state)
        save(policy("DAY",p=DayPolicy(true,listOf(ItemPolicy(essential,Disposition.SUPPRESS)),"Explicit instruction review")))
        clock.instant = Instant.parse("2026-10-04T05:00:00Z"); schedules.reconcile(); schedules.reconcile()
        assertEquals("SKIPPED_BY_OVERRIDE",occurrence(ordinary).state)
        assertEquals("SKIPPED_BY_OVERRIDE",occurrence(essential).state)
        val counts=reviewCounts(listOf(occurrence(ordinary).state to false,occurrence(essential).state to false))
        assertEquals(2,counts.suppressed);assertEquals(0,counts.missed)
        assertTrue(occurrence(ordinary).reason!!.contains("Explicit instruction review"))
    }
    @Test fun occurrenceOverrideAndCompletedStateWinOverEveryModifier() = runBlocking {
        val id = add(); val p=profile(id);save(p);save(assign(p))
        save(policy("MODE",p=DayPolicy(items=listOf(ItemPolicy(id,Disposition.MOVE,"11:00")),reason="Mode")))
        save(policy("DAY",p=DayPolicy(items=listOf(ItemPolicy(id,Disposition.MOVE,"12:00")),reason="Day")))
        assertEquals(12,occurrence(id).plan().start.atZone(clock.zone()).hour)
        val execution=ExecutionRepository(schedules)
        execution.act(occurrence(id).id,"COMPLETE")
        val completed=occurrence(id); save(policy("DAY"));assertEquals(completed,occurrence(id))
        save(policy("DAY",p=DayPolicy(reason="Reset day")))
        val second=add("Move once")
        val o=occurrence(second)
        val moved=o.plan().copy(start=o.plan().start.plusSeconds(2700))
        execution.act(o.id,"MOVE","Explicit",plan=moved,acknowledged=true)
        val explicit=occurrence(second);save(policy("DAY"));assertEquals(explicit,occurrence(second))
        assertEquals("PENDING",explicit.state);assertEquals(45,explicit.plan().start.atZone(clock.zone()).minute)
    }
    @Test fun suppressedRemindersCancelAndRestorationDoesNotDuplicate() = runBlocking {
        val id=add();val platform=RecordingReminders();val reminders=ReminderRepository(schedules,platform)
        schedules.onChanged={reminders.reconcile()};reminders.reconcile()
        val o=occurrence(id);assertTrue(db.reminders().all().any {it.occurrenceId==o.id && it.result=="PLANNED"})
        save(policy("DAY"));assertFalse(db.reminders().all().any {it.occurrenceId==o.id && it.result=="PLANNED"})
        save(policy("DAY",p=DayPolicy(reason="Restored")));reminders.reconcile();reminders.reconcile()
        assertEquals(2,db.reminders().all().count {it.occurrenceId==o.id && it.result=="PLANNED"})
    }
    @Test fun stalePreviewAndRetroactiveEditAreRejectedAtomically() = runBlocking {
        add();val r=policy("DAY");val preview=repo.preview(r);add("New work")
        assertTrue(runCatching {repo.save(r,preview,true)}.isFailure)
        assertFalse(repo.dao.records().any {it.id==r.id})
        assertTrue(runCatching {repo.preview(policy("DAY","2026-10-02"))}.isFailure)
    }
    @Test fun futureCompletionIntervalSuppressionIsStableAndReversible() = runBlocking {
        val id=schedules.save(CommitmentDraft("Completion interval",ScheduleRule(RuleType.INTERVAL_COMPLETION,
            LocalDate.of(2026,10,3),LocalTime.of(9,0),zoneId="UTC")),false)
        val original=occurrence(id)
        save(policy("DAY"))
        repeat(3) { schedules.reconcile();assertEquals("SKIPPED_BY_OVERRIDE",occurrence(id).state);assertEquals(original.id,occurrence(id).id) }
        save(policy("DAY",p=DayPolicy(reason="Restore")))
        assertEquals("PENDING",occurrence(id).state);assertEquals(original.id,occurrence(id).id)
        assertEquals(1,schedules.dao.occurrences().count {it.commitmentId==id})
    }
    @Test fun profileProvenanceSurvivesUndoAndStartedRunBlocksEarlierMoveUndo() = runBlocking {
        val id=add();val p=profile(id);save(p);save(assign(p))
        val execution=ExecutionRepository(schedules)
        val o=occurrence(id)
        val completion=execution.act(o.id,"COMPLETE")!!;execution.undo(completion)
        val restored=occurrence(id)
        assertEquals(p.id,restored.profileVersionId);assertEquals(o.blockDurationSeconds,restored.blockDurationSeconds)
        val move=execution.act(o.id,"MOVE","Later",plan=restored.plan().copy(start=restored.plan().start.plusSeconds(600)),acknowledged=true)!!
        val routine=repo.record("ROUTINE","Steps","2026-10-03",payload=JSONObject().put("format",1).put("steps",org.json.JSONArray(listOf("One"))).toString())
        save(routine);save(repo.record("LINK","Attached","2026-10-03",payload=JSONObject().put("format",1).put("routine",routine.family).toString(),family=id))
        repo.startRun(o.id)
        assertTrue(runCatching {execution.undo(move)}.isFailure)
        assertEquals(10,occurrence(id).plan().start.atZone(clock.zone()).minute)
    }
    @Test fun migrationFourToFiveKeepsFocusAndLogRows() = runBlocking {
        db.close();context.deleteDatabase(name)
        migrations.createDatabase(name,4).apply {
            execSQL("INSERT INTO focus_sessions VALUES ('session','Apply','Company','Role',0,0,'COMPLETED','owner',123,456,'')")
            execSQL("INSERT INTO focus_segments VALUES ('segment','session',123,100,1,'UTC','04:00',333,1,NULL)")
            execSQL("INSERT INTO action_log VALUES ('log','Focus','session','FOCUS_STOP','Original evidence',456)")
            close()
        }
        migrations.runMigrationsAndValidate(name,5,true,MIGRATION_4_5).apply {
            query("SELECT durationMillis FROM focus_segments").use {assertTrue(it.moveToFirst());assertEquals(333,it.getLong(0))}
            query("SELECT summary FROM action_log").use {assertTrue(it.moveToFirst());assertEquals("Original evidence",it.getString(0))}
            close()
        }
        db=BetterDatabase.open(context,name);assertTrue(db.adaptive().records().isEmpty())
    }
}
