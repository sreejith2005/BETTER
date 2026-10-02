package dev.better.app

import androidx.room.testing.MigrationTestHelper
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

class MutableClock(var instant: Instant = Instant.parse("2026-09-30T12:00:00Z"), var currentZone: ZoneId = ZoneId.of("Asia/Kolkata")) : AppClock {
    override fun now()=instant
    override fun zone()=currentZone
    override fun elapsedMillis()=100_000L
}

@RunWith(AndroidJUnit4::class)
class SchedulingStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-scheduling-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private val clock=MutableClock()
    @get:Rule val migrations=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),BetterDatabase::class.java)
    @After fun cleanup() { if(::db.isInitialized) db.close(); context.deleteDatabase(name) }
    private suspend fun setup(): ScheduleRepository {
        db=BetterDatabase.open(context,name)
        LocalRepository(db,clock).onboard(SetupInput(zone="Asia/Kolkata"))
        return ScheduleRepository(db,clock)
    }
    private fun draft(type:RuleType=RuleType.DAILY)=CommitmentDraft("Original",ScheduleRule(type,LocalDate.of(2026,10,1)),
        instructions="User-entered\ninstructions unchanged",source="Personal entry")

    @Test fun migrationPreservesV1ProfileCareerTargetAndAudit() = runBlocking {
        migrations.createDatabase(name,1).apply {
            execSQL("INSERT INTO app_settings (id,profileId,dayBoundary,zoneId,timezonePolicy,workStart,workEnd,careerStart,careerEnd,notificationsWanted,quietStart,quietEnd,privacyMode,createdAt) VALUES (1,'profile','04:00','Asia/Kolkata','LOCAL_FLOATING','09:00','18:00','19:00','21:00',0,'22:30','07:00',1,1000)")
            execSQL("INSERT INTO commitments (id,title,category,essential,archivedAt) VALUES ('career','Career Focus','CAREER',1,NULL)")
            execSQL("INSERT INTO career_standards (id,commitmentId,effectiveDate,targetSeconds,createdAt) VALUES ('standard','career','2026-09-30',1800,1000)")
            execSQL("INSERT INTO action_log (id,entity,entityId,action,summary,timestamp) VALUES ('log','AppSettings','profile','ONBOARD','Original setup',1000)")
            close()
        }
        migrations.runMigrationsAndValidate(name,2,true,MIGRATION_1_2).close()
        db=BetterDatabase.open(context,name)
        val repo=ScheduleRepository(db,clock)
        assertEquals("profile",db.foundation().settings()!!.profileId)
        assertEquals(1800,db.foundation().standards().single().targetSeconds)
        assertEquals("Original setup",db.foundation().logs().single().summary)
        repo.ensureCareerSchedule(); repo.ensureCareerSchedule()
        assertEquals("career",db.foundation().commitments().single().id)
        assertEquals(1,repo.dao.versions().size)
        assertEquals(45,repo.dao.occurrences().size)
    }
    @Test fun at06PreviewMatchesStoredSequenceAndReconcileDoesNotDuplicate() = runBlocking {
        val repo=setup()
        val d=draft().copy(rule=ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT,LocalDate.of(2026,10,1),offsets=DateStep.parseSequence("3d,2d,1d"),tail=DateStep(2)))
        val preview=repo.preview(d)
        val id=repo.save(d,false)
        val first=repo.dao.occurrences().filter {it.commitmentId==id}
        assertEquals(preview.map {it.start.toEpochMilli()},first.take(10).map {it.startAt})
        assertEquals(listOf(1,4,6,7,9,11,13,15,17,19),first.take(10).map {LocalDate.parse(it.plannedDate).dayOfMonth})
        repo.reconcile(); repo.reconcile()
        assertEquals(first,repo.dao.occurrences().filter {it.commitmentId==id})
    }
    @Test fun at11FutureVersionMovesEarlierWithoutRewritingCompletedHistory() = runBlocking {
        val repo=setup(); val d=draft(); val id=repo.save(d,false)
        val original=repo.dao.occurrences().filter {it.commitmentId==id}
        val first=original.first()
        repo.dao.upsertOccurrence(first.copy(state="COMPLETED",acted=true,actualAt=first.startAt+60_000))
        clock.instant=Instant.parse("2026-10-02T12:00:00Z")
        val boundary=original.first {it.plannedDate=="2026-10-03"}
        repo.save(d.copy(title="Changed",instructions="New instructions",rule=d.rule.copy(time=LocalTime.of(18,0))),false,id,EditScope.ENTIRE_SERIES,boundary.id)
        val after=repo.dao.occurrences().filter {it.commitmentId==id}
        val retained=after.first {it.id==first.id}
        assertEquals("COMPLETED",retained.state)
        val oldVersion=repo.dao.versions().first {it.id==retained.versionId}
        assertEquals("Original",oldVersion.title)
        assertEquals(d.instructions,oldVersion.instructionText)
        val changed=after.single {it.plannedDate=="2026-10-03"}
        assertEquals(LocalTime.of(18,0),Instant.ofEpochMilli(changed.startAt).atZone(clock.zone()).toLocalTime())
        assertEquals("Changed",repo.dao.versions().first {it.id==changed.versionId}.title)
        assertEquals(1,after.count {it.plannedDate=="2026-10-01"})
    }
    @Test fun at11SingleOverrideKeepsIdentityAndOtherDates() = runBlocking {
        val repo=setup(); val d=draft(); val id=repo.save(d,false)
        val before=repo.dao.occurrences().filter {it.commitmentId==id}
        repo.save(d.copy(title="Once only",rule=d.rule.copy(type=RuleType.ONE_OFF,time=LocalTime.of(20,0))),false,id,EditScope.THIS_OCCURRENCE,before[0].id,"One-day change")
        repo.reconcile()
        val after=repo.dao.occurrences().filter {it.commitmentId==id}
        assertEquals(before[0].id,after[0].id)
        assertNotEquals(before[0].startAt,after[0].startAt)
        assertEquals(before.drop(1),after.drop(1))
        assertEquals("Once only",repo.dao.overrides().single().title)
        assertEquals(1,repo.dao.versions().size)
    }
    @Test fun at10TimezoneChangesOnlyUnactedFloatingInstances() = runBlocking {
        val repo=setup(); val floating=repo.save(draft(),false)
        val anchored=repo.save(draft().copy(title="Anchored",rule=draft().rule.copy(zoneMode=ZoneMode.ANCHORED_ZONE)),false,conflictsAcknowledged=true)
        val before=repo.dao.occurrences()
        val due=before.first {it.commitmentId==floating}
        clock.instant=Instant.ofEpochMilli(due.startAt+1000)
        clock.currentZone=ZoneId.of("America/New_York")
        repo.reconcile()
        val after=repo.dao.occurrences()
        assertEquals(due.startAt,after.first {it.id==due.id}.startAt)
        assertEquals("Asia/Kolkata",after.first {it.id==due.id}.zoneId)
        val future=before.first {it.commitmentId==floating && it.plannedDate=="2026-10-02"}
        assertNotEquals(future.startAt,after.first {it.id==future.id}.startAt)
        val fixed=before.first {it.commitmentId==anchored && it.plannedDate=="2026-10-02"}
        assertEquals(fixed.startAt,after.first {it.id==fixed.id}.startAt)
    }
    @Test fun at10UnacknowledgedDstSaveIsAtomicAndChosenOffsetPersists() = runBlocking {
        val repo=setup()
        val d=draft(RuleType.ONE_OFF).copy(rule=ScheduleRule(RuleType.ONE_OFF,LocalDate.of(2026,11,1),LocalTime.of(1,30),
            zoneMode=ZoneMode.ANCHORED_ZONE,zoneId="America/New_York",overlap=OverlapChoice.LATER))
        assertTrue(runCatching {repo.save(d,false)}.isFailure)
        assertTrue(repo.dao.versions().isEmpty())
        assertEquals(1,db.foundation().commitments().size)
        repo.save(d,true)
        assertEquals(Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(),repo.dao.occurrences().single().startAt)
        assertEquals("LATER",repo.dao.rules().single().overlap)
    }
    @Test fun at12CompletionIntervalStopsOnSkipAndCalendarDatesDoNotShift() = runBlocking {
        val repo=setup()
        val d=draft(RuleType.INTERVAL_COMPLETION).copy(rule=draft().rule.copy(type=RuleType.INTERVAL_COMPLETION,step=DateStep(2)))
        val id=repo.save(d,false)
        val first=repo.dao.occurrences().single()
        val actual=Instant.parse("2026-10-02T16:45:00Z").toEpochMilli()
        repo.dao.upsertOccurrence(first.copy(state="COMPLETED",acted=true,actualAt=actual))
        clock.instant=Instant.ofEpochMilli(actual)
        repo.reconcile()
        val next=repo.dao.occurrences().last {it.commitmentId==id}
        assertEquals(Instant.parse("2026-10-04T16:45:00Z").toEpochMilli(),next.startAt)
        repo.dao.upsertOccurrence(next.copy(state="SKIPPED",acted=true,actualAt=next.startAt))
        repo.reconcile()
        assertEquals(2,repo.dao.occurrences().size)
    }
    @Test fun archiveRestoreRetainsHistoryAndStableKeys() = runBlocking {
        val repo=setup(); val id=repo.save(draft(),false)
        val keys=repo.dao.occurrences().map {it.id}
        repo.archive(id)
        assertTrue(repo.dao.occurrences().isEmpty())
        assertTrue(repo.dao.versions().isNotEmpty())
        repo.restore(id)
        assertEquals(keys,repo.dao.occurrences().map {it.id})
    }
    @Test fun stalePreviewCannotSaveAfterTimezoneChange() = runBlocking {
        val repo=setup(); val d=draft()
        val preview=repo.preview(d)
        clock.currentZone=ZoneId.of("Europe/London")
        assertTrue(runCatching {repo.save(d,false,expectedPreview=preview)}.isFailure)
        assertTrue(repo.dao.versions().isEmpty())
        assertEquals(1,db.foundation().commitments().size)
    }
}
