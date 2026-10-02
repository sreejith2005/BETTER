package dev.better.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExecutionStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-execution-${UUID.randomUUID()}.db"
    private lateinit var db:BetterDatabase
    private val clock=MutableClock(Instant.parse("2026-10-01T08:00:00Z"))
    @After fun cleanup(){if(::db.isInitialized) db.close();context.deleteDatabase(name)}
    private suspend fun setup():ScheduleRepository {
        db=BetterDatabase.open(context,name)
        LocalRepository(db,clock).onboard(SetupInput(zone="Asia/Kolkata"))
        return ScheduleRepository(db,clock)
    }
    private fun draft(type:RuleType=RuleType.ONE_OFF, time:LocalTime=LocalTime.of(14,0))=
        CommitmentDraft("Execution fixture",ScheduleRule(type,LocalDate.of(2026,10,1),time),reminderLevel="NONE")
    private suspend fun first(repo:ScheduleRepository,id:String)=repo.dao.occurrences().first {it.commitmentId==id}

    @Test fun completeIsIdempotentAndUndoSurvivesReopen()=runBlocking {
        var repo=setup();val id=repo.save(draft(),false);val o=first(repo,id)
        clock.instant=Instant.ofEpochMilli(o.startAt+60_000)
        repo.reconcile()
        val execution=ExecutionRepository(repo)
        val log=execution.act(o.id,"COMPLETE")!!
        assertNull(execution.act(o.id,"COMPLETE"))
        assertNull(execution.act(o.id,"SKIP","Stale tap"))
        assertEquals(1,db.foundation().logs().count {it.action=="EXEC_COMPLETE"})
        assertTrue(repo.dao.occurrence(o.id)!!.completedLate)
        db.close();db=BetterDatabase.open(context,name);repo=ScheduleRepository(db,clock)
        ExecutionRepository(repo).undo(log)
        assertEquals("OVERDUE",repo.dao.occurrence(o.id)!!.state)
        assertEquals(1,db.foundation().logs().count {it.action=="EXEC_UNDO"})
        assertTrue(runCatching {ExecutionRepository(repo).undo(log)}.isFailure)
    }
    @Test fun missedHealthCanBeCompletedLateWithoutDoubleCounting()=runBlocking {
        val repo=setup();val id=repo.save(draft().copy(category="HEALTH",instructions="Exact entered text"),false)
        val o=first(repo,id);clock.instant=Instant.ofEpochMilli(o.closesAt+60_000);repo.reconcile()
        assertEquals("MISSED",repo.dao.occurrence(o.id)!!.state)
        ExecutionRepository(repo).act(o.id,"COMPLETE")
        val after=repo.dao.occurrence(o.id)!!
        assertEquals("COMPLETED",after.state);assertTrue(after.completedLate)
        assertEquals(o.operationalDate,after.operationalDate)
        assertEquals(0,reviewCounts(listOf(after.state to after.completedLate)).missed)
        assertEquals("Exact entered text",repo.dao.versions().single().instructionText)
    }
    @Test fun moveKeepsIdCalendarAnchorAndUndoRestoresOverride()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.DAILY),false);val o=first(repo,id)
        val other=repo.dao.occurrences().filter {it.commitmentId==id && it.id!=o.id}
        val p=ScheduleEngine.plan(draft().rule.copy(time=LocalTime.of(16,0)),LocalDate.of(2026,10,1),clock.zone(),LocalTime.of(4,0))
        val execution=ExecutionRepository(repo)
        val action=execution.act(o.id,"MOVE","Appointment moved",p)!!
        repo.reconcile()
        assertEquals(p.start.toEpochMilli(),repo.dao.occurrence(o.id)!!.startAt)
        assertEquals(o.plannedDate,repo.dao.occurrence(o.id)!!.plannedDate)
        assertEquals(other,repo.dao.occurrences().filter {it.commitmentId==id && it.id!=o.id})
        assertEquals("Appointment moved",repo.dao.overrides().single().reason)
        execution.undo(action);assertEquals(o.copy(acted=true),repo.dao.occurrence(o.id));assertTrue(repo.dao.overrides().isEmpty())
    }
    @Test fun skipCancelRequireReasonAndStaleMoveCannotOverwriteResolution()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.DAILY),false);val items=repo.dao.occurrences().filter {it.commitmentId==id}
        val e=ExecutionRepository(repo)
        assertTrue(runCatching {e.act(items[0].id,"SKIP")}.isFailure)
        e.act(items[0].id,"SKIP","Unavailable")
        e.act(items[1].id,"CANCEL","No longer needed")
        assertEquals("SKIPPED",repo.dao.occurrence(items[0].id)!!.state)
        assertEquals("CANCELLED",repo.dao.occurrence(items[1].id)!!.state)
        assertNull(e.act(items[0].id,"MOVE","Stale",items[1].plan()))
        assertEquals(2,db.foundation().logs().count {it.action.startsWith("EXEC_")})
    }
    @Test fun completionIntervalMoveDoesNotStopAndLateCompletionUsesActualTime()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.INTERVAL_COMPLETION),false);val o=first(repo,id)
        val e=ExecutionRepository(repo)
        val p=ScheduleEngine.plan(draft().rule.copy(time=LocalTime.of(16,0)),LocalDate.of(2026,10,1),clock.zone(),LocalTime.of(4,0))
        e.act(o.id,"MOVE","Later today",p)
        clock.instant=Instant.parse("2026-10-02T09:30:00Z");repo.reconcile()
        e.act(o.id,"COMPLETE")
        val next=repo.dao.occurrences().single {it.commitmentId==id && it.state=="PENDING"}
        assertEquals(Instant.parse("2026-10-03T09:30:00Z").toEpochMilli(),next.startAt)
        repo.reconcile();assertEquals(2,repo.dao.occurrences().count {it.commitmentId==id})
    }
    @Test fun completionSkipStopRestartAndManualChoiceAreExplicitAndUndoable()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.INTERVAL_COMPLETION),false);val o=first(repo,id)
        val e=ExecutionRepository(repo)
        assertTrue(runCatching {e.act(o.id,"SKIP","Reason",restart=SkipRestart.NEEDS_CHOICE)}.isFailure)
        val stop=e.act(o.id,"SKIP","Reason")!!
        repo.reconcile();assertEquals(1,repo.dao.occurrences().size)
        e.undo(stop)
        val restart=e.act(o.id,"SKIP","Reason",restart=SkipRestart.RESTART_FROM_SKIP)!!
        val next=repo.dao.occurrences().single {it.id!=o.id}
        assertEquals(clock.now().plusSeconds(86400).toEpochMilli(),next.startAt)
        e.undo(restart);assertEquals(1,repo.dao.occurrences().size)
        val p=ScheduleEngine.plan(draft().rule,LocalDate.of(2026,10,5),clock.zone(),LocalTime.of(4,0))
        e.act(o.id,"SKIP","Chosen explicitly",p,SkipRestart.MANUAL_NEXT_DATE)
        repo.reconcile();assertEquals(p.start.toEpochMilli(),repo.dao.occurrences().single {it.id!=o.id}.startAt)
    }
    @Test fun undoCannotDeleteNextOccurrenceOnceItIsDue()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.INTERVAL_COMPLETION),false);val o=first(repo,id)
        val e=ExecutionRepository(repo);val log=e.act(o.id,"COMPLETE")!!
        val next=repo.dao.occurrences().single {it.id!=o.id}
        clock.instant=Instant.ofEpochMilli(next.startAt);repo.reconcile()
        assertTrue(runCatching {e.undo(log)}.isFailure)
        assertEquals("COMPLETED",repo.dao.occurrence(o.id)!!.state)
        assertEquals(2,repo.dao.occurrences().size)
    }
    @Test fun conflictSaveAndMoveRequireAcknowledgementAndNeverReflow()=runBlocking {
        val repo=setup();val fixed=repo.save(draft().copy(fixed=true),false);val original=first(repo,fixed)
        assertTrue(runCatching {repo.save(draft().copy(title="Overlap"),false)}.isFailure)
        assertEquals(1,repo.dao.versions().size)
        repo.save(draft().copy(title="Overlap"),false,conflictsAcknowledged=true)
        val other=repo.save(draft(time=LocalTime.of(16,0)),false);val o=first(repo,other)
        val e=ExecutionRepository(repo)
        assertTrue(runCatching {e.act(o.id,"MOVE","Conflict",original.plan())}.isFailure)
        e.act(o.id,"MOVE","Keep overlap",original.plan(),acknowledged=true)
        assertEquals(original,repo.dao.occurrence(original.id))
    }
    @Test fun careerExtraMoveRequiresExplicitOverride()=runBlocking {
        val repo=setup();val id=repo.save(draft().copy(category="CAREER"),false);val o=first(repo,id)
        val e=ExecutionRepository(repo)
        val p=ScheduleEngine.plan(draft().rule.copy(time=LocalTime.of(16,0)),LocalDate.of(2026,10,1),clock.zone(),LocalTime.of(4,0))
        e.act(o.id,"MOVE","First move",p)
        assertTrue(runCatching {e.act(o.id,"MOVE","Second move",p)}.isFailure)
        e.act(o.id,"MOVE","Explicit second move",p,extraMove=true)
        assertEquals(2,db.foundation().logs().count {it.action=="EXEC_MOVE"})
    }
    @Test fun earlyCompletionStillGeneratesNextWhenItSharesOriginalDate()=runBlocking {
        val repo=setup();val id=repo.save(draft(RuleType.INTERVAL_COMPLETION).copy(rule=draft(RuleType.INTERVAL_COMPLETION).rule.copy(anchor=LocalDate.of(2026,10,2))),false)
        val o=first(repo,id);ExecutionRepository(repo).act(o.id,"COMPLETE")
        repo.reconcile()
        assertEquals(2,repo.dao.occurrences().size)
        assertEquals(1,repo.dao.occurrences().count {it.state=="PENDING"})
    }
}
