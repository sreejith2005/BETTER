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

class FocusClock : AppClock {
    var instant = Instant.parse("2026-10-02T03:58:00Z")
    var elapsed = 100000L
    var timezone: ZoneId = ZoneOffset.UTC
    override fun now() = instant
    override fun elapsedMillis() = elapsed
    override fun zone() = timezone
    fun advance(millis: Long) { instant = instant.plusMillis(millis); elapsed += millis }
}

@RunWith(AndroidJUnit4::class)
class FocusStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-focus-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private val clock = FocusClock()
    private var boot = 74
    private lateinit var repo: FocusRepository
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetterDatabase::class.java)
    @Before fun setup() = runBlocking {
        db = BetterDatabase.open(context, name)
        LocalRepository(db, clock).onboard(SetupInput(zone = "UTC"))
        repo = FocusRepository(db, clock, { boot }, "first-process")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun start(rescue: Boolean = false): String {
        repo.start("Apply", rescue, true, "Example company", "AI Engineer")
        return repo.snapshot().active!!.id
    }
    @Test fun at22FutureStandardPreservesPastAndRejectsRetroactiveChange() = runBlocking {
        repo.changeStandard(40, LocalDate.of(2026,10,3))
        val state = repo.snapshot()
        assertEquals(1800, state.target(LocalDate.of(2026,10,2)))
        assertEquals(2400, state.target(LocalDate.of(2026,10,3)))
        assertTrue(runCatching { repo.changeStandard(10, LocalDate.of(2026,10,1)) }.isFailure)
        assertTrue(runCatching { repo.changeStandard(10, LocalDate.of(2026,10,3)) }.isFailure)
    }
    @Test fun at23BackgroundPauseResumeAndDateAllocation() = runBlocking {
        val id = start(); clock.advance(300000); repo.act(id,"PAUSE")
        clock.advance(300000); assertEquals(300000,repo.snapshot().sessionMillis(id))
        repo.act(id,"RESUME"); clock.advance(60000); repo.act(id,"STOP"); repo.act(id,"STOP")
        val state=repo.snapshot()
        assertEquals(360000,state.sessionMillis(id)); assertNull(state.active)
        assertEquals(120000L,state.dailyMillis[LocalDate.of(2026,10,1)])
        assertEquals(240000L,state.dailyMillis[LocalDate.of(2026,10,2)])
        assertEquals(1, db.foundation().logs().count { it.action=="FOCUS_STOP" })
    }
    @Test fun at23ProcessDeathRequiresConfirmationAndReviewDelayNeverCounts() = runBlocking {
        val id=start(); clock.advance(60000);repo.snapshot();clock.advance(120000)
        db.close(); db=BetterDatabase.open(context,name)
        repo=FocusRepository(db,clock,{boot},"second-process")
        assertEquals("REVIEW",repo.snapshot().active!!.state)
        assertEquals(60000,repo.snapshot().sessionMillis(id))
        clock.advance(300000);repo.act(id,"CONFIRM")
        assertEquals(180000,repo.snapshot().sessionMillis(id))
        repo.act(id,"CONFIRM");repo.act(id,"RESUME");clock.advance(10000);repo.act(id,"STOP")
        assertEquals(190000,repo.snapshot().sessionMillis(id))
    }
    @Test fun at23RecoveryCanRejectUncertainTimeAndSurvivesAnotherRestart() = runBlocking {
        val id=start();clock.advance(10000);repo.snapshot();clock.advance(20000)
        repo=FocusRepository(db,clock,{boot},"second");repo.snapshot()
        repo=FocusRepository(db,clock,{boot},"third");clock.advance(10000)
        repo.act(id,"DISCARD");repo.act(id,"STOP")
        assertEquals(10000,repo.snapshot().sessionMillis(id))
    }
    @Test fun at23RebootRetainsCheckpointEvenWhenNewUptimeIsLarger() = runBlocking {
        val id=start();clock.advance(60000);repo.snapshot();clock.advance(900000);boot++
        repo=FocusRepository(db,clock,{boot},"rebooted")
        assertEquals("INTERRUPTED",repo.snapshot().active!!.state)
        assertEquals(60000,repo.snapshot().sessionMillis(id))
        repo.act(id,"RESUME");clock.advance(30000);repo.act(id,"STOP")
        assertEquals(90000,repo.snapshot().sessionMillis(id))
    }
    @Test fun at24RescueStopsExactlyAtFiveAndContinueUsesSameSession() = runBlocking {
        val id=start(true);clock.advance(600000)
        assertEquals("RESCUE_READY",repo.snapshot().active!!.state)
        assertEquals(300000,repo.snapshot().sessionMillis(id))
        clock.advance(300000);repo.act(id,"CONTINUE");repo.act(id,"CONTINUE")
        clock.advance(1500000);repo.act(id,"STOP")
        assertEquals(1800000,repo.snapshot().sessionMillis(id));assertEquals(1,db.focus().sessions().size)
    }
    @Test fun at24StoppingRescueLeavesTwentyFiveMinutes() = runBlocking {
        clock.instant=Instant.parse("2026-10-02T12:00:00Z")
        val id=start(true);clock.advance(300000);repo.act(id,"STOP")
        val state=repo.snapshot();val day=LocalDate.of(2026,10,2)
        assertEquals(1500000, state.target(day)*1000L-state.dailyMillis.getValue(day))
    }
    @Test fun at25DeliberateWorkRequiredAndDetailsAreStoredWithoutInventingSubmission() = runBlocking {
        assertTrue(runCatching { repo.start("Search",false,false) }.isFailure)
        assertTrue(runCatching { repo.start("Scrolling",false,true) }.isFailure)
        val id=start();repo.start("Search",false,true)
        assertEquals(1,db.focus().sessions().size)
        val session=repo.snapshot().active!!
        assertEquals(id,session.id);assertEquals("Example company",session.company);assertEquals("AI Engineer",session.role)
        assertFalse(db.foundation().logs().any { it.summary.contains("application submitted",true) })
    }
    @Test fun clockChangeCannotCreateExtraTimeAndTimezoneSnapshotsRemain() = runBlocking {
        val id=start();clock.advance(60000);clock.instant=clock.instant.plusSeconds(3600);repo.snapshot()
        clock.timezone=ZoneId.of("Asia/Kolkata");clock.advance(10000);repo.snapshot()
        clock.advance(10000);repo.act(id,"STOP")
        assertEquals(80000,repo.snapshot().sessionMillis(id))
        assertEquals(listOf("Z","Z","Asia/Kolkata"),db.focus().segments().map { it.zoneId })
    }
    @Test fun migrationThreeToFourPreservesOriginalTables() = runBlocking {
        db.close();context.deleteDatabase(name)
        migrations.createDatabase(name,3).apply {
            execSQL("INSERT INTO commitments VALUES ('old','Career Focus','CAREER',1,NULL)")
            close()
        }
        migrations.runMigrationsAndValidate(name,4,true,MIGRATION_3_4).close()
        db=BetterDatabase.open(context,name)
        assertEquals("old",db.foundation().commitments().single().id)
        assertTrue(db.focus().sessions().isEmpty())
    }
    @Test fun dailyMinimumMilestoneIsDurableAndNeverUsesRecoveryCandidate() = runBlocking {
        clock.instant=Instant.parse("2026-10-02T12:00:00Z")
        val id=start();clock.advance(60000);repo.snapshot();clock.advance(1800000)
        repo=FocusRepository(db,clock,{boot},"restarted")
        repo.snapshot()
        assertFalse(db.foundation().logs().any { it.action=="FOCUS_MINIMUM_MET" })
        repo.act(id,"CONFIRM");repo.snapshot();repo.snapshot()
        assertEquals(1,db.foundation().logs().count { it.action=="FOCUS_MINIMUM_MET" })
        assertEquals(1860000,repo.snapshot().sessionMillis(id))
    }
    @Test fun pausedSessionSurvivesRebootWithoutAddingTime() = runBlocking {
        val id=start();clock.advance(15000);repo.act(id,"PAUSE")
        boot++;clock.elapsed=10;clock.instant=clock.instant.plusSeconds(3600)
        repo=FocusRepository(db,clock,{boot},"rebooted")
        assertEquals("PAUSED",repo.snapshot().active!!.state)
        repo.act(id,"RESUME");clock.advance(5000);repo.act(id,"STOP")
        assertEquals(20000,repo.snapshot().sessionMillis(id))
    }
}
