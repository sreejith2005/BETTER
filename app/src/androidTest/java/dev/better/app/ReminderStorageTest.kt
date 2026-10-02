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

class RecordingReminders : ReminderPlatform {
    var epoch: String? = null
    override fun recoveryToken() = epoch
    var enabled=true
    var next:Long?=null
    val posts=mutableListOf<ReminderDelivery>()
    val cancelled=mutableListOf<String>()
    override fun allowed(category:String)=enabled
    override fun schedule(at:Long?){next=at}
    override fun cancelNotification(occurrenceId:String){cancelled+=occurrenceId}
    override fun post(row:ReminderDelivery,occurrence:OccurrenceRecord,version:VersionRecord,title:String,private:Boolean){posts+=row}
}

@RunWith(AndroidJUnit4::class)
class ReminderStorageTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-reminders-${UUID.randomUUID()}.db"
    private lateinit var db:BetterDatabase
    private val clock=MutableClock(Instant.parse("2026-10-01T12:00:00Z"),ZoneOffset.UTC)
    private lateinit var repo:ScheduleRepository
    private lateinit var reminders:ReminderRepository
    private val platform=RecordingReminders()
    @get:Rule val migrations=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),BetterDatabase::class.java)
    @After fun cleanup(){if(::db.isInitialized)db.close();context.deleteDatabase(name)}
    private suspend fun setup(level:String="PERSISTENT",time:LocalTime=LocalTime.of(13,0)):OccurrenceRecord {
        db=BetterDatabase.open(context,name)
        LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
        repo=ScheduleRepository(db,clock)
        reminders=ReminderRepository(repo,platform)
        repo.onChanged={reminders.reconcile()}
        val id=repo.save(CommitmentDraft("Reminder fixture",ScheduleRule(RuleType.ONE_OFF,LocalDate.of(2026,10,1),time,zoneId="UTC"),reminderLevel=level),false)
        return repo.dao.occurrences().first{it.commitmentId==id}
    }
    @Test fun at13LadderCapsAreDurableAndRepeatedReconcileDoesNotDuplicate()=runBlocking {
        val o=setup()
        for(minutes in listOf(0L,30,60,90,120)){
            clock.instant=Instant.ofEpochMilli(o.startAt+minutes*60000)
            reminders.reconcile(deliver=true);reminders.reconcile(deliver=true)
        }
        assertEquals(4,platform.posts.count{it.occurrenceId==o.id})
        assertEquals(4,db.reminders().all().count{it.occurrenceId==o.id&&it.postedAt!=null})
        assertTrue(db.reminders().all().filter{it.occurrenceId==o.id}.all{it.observedAt==null})
    }
    @Test fun at14CompleteCancelsAndStaleActionsDoNotDuplicateCompletionOrSuccessor()=runBlocking {
        val o=setup();clock.instant=Instant.ofEpochMilli(o.startAt)
        reminders.reconcile(deliver=true)
        val alert=platform.posts.first{it.occurrenceId==o.id}
        reminders.observed(alert.id)
        val execution=ExecutionRepository(repo)
        assertNotNull(execution.act(o.id,"COMPLETE"));assertNull(execution.act(o.id,"COMPLETE"))
        assertEquals("COMPLETED",repo.dao.occurrence(o.id)!!.state)
        assertTrue(o.id in platform.cancelled)
        assertFalse(db.reminders().all().any{it.occurrenceId==o.id&&it.result=="PLANNED"})
        assertNotNull(db.reminders().get(alert.id)!!.observedAt)
        clock.instant=clock.instant.plusSeconds(7200);reminders.reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
    }
    @Test fun at14RebootAndWallTimeRecoveryNeverPostBacklog()=runBlocking {
        val o=setup();clock.instant=Instant.ofEpochMilli(o.startAt+45*60000)
        reminders.reconcile(recovery=true);reminders.reconcile(recovery=true,deliver=true)
        assertTrue(platform.posts.none{it.occurrenceId==o.id})
        val rows=db.reminders().all().filter{it.occurrenceId==o.id}
        assertEquals(2,rows.count{it.result=="MISSED"});assertEquals(2,rows.count{it.result=="PLANNED"})
        clock.instant=Instant.ofEpochMilli(o.startAt+60*60000);reminders.reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
    }
    @Test fun at15DeniedThenGrantedOnlySchedulesFutureAndDoesNotChangeDueState()=runBlocking {
        platform.enabled=false;val o=setup("STANDARD")
        assertNull(platform.next)
        clock.instant=Instant.ofEpochMilli(o.startAt);reminders.reconcile(deliver=true)
        assertTrue(platform.posts.isEmpty());assertEquals("DUE",repo.dao.occurrence(o.id)!!.state)
        platform.enabled=true;reminders.reconcile()
        assertEquals(o.startAt+15*60000,platform.next)
        clock.instant=Instant.ofEpochMilli(o.startAt+15*60000);reminders.reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
    }
    @Test fun quietHourCoalescingAndAndroidDelayNeverProduceBurst()=runBlocking {
        val o=setup()
        reminders.settings("12:30","14:00",true)
        assertEquals(2,db.reminders().all().count{it.occurrenceId==o.id&&it.result=="PLANNED"})
        clock.instant=Instant.ofEpochMilli(o.startAt+90*60000)
        reminders.reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
        assertTrue(platform.posts.first{it.occurrenceId==o.id}.delayed || db.reminders().all().any{it.occurrenceId==o.id&&it.delayed})
    }
    @Test fun moveAndUndoCancelStaleAlarmAndRestoreFutureSteps()=runBlocking {
        val o=setup("STANDARD")
        val execution=ExecutionRepository(repo)
        val moved=o.plan().copy(start=Instant.ofEpochMilli(o.startAt+2*3600000),end=Instant.ofEpochMilli(o.endAt+2*3600000))
        val action=execution.act(o.id,"MOVE","test move",moved,acknowledged=true)!!
        assertFalse(db.reminders().all().any{it.occurrenceId==o.id&&it.scheduleStart==o.startAt&&it.result=="PLANNED"})
        execution.undo(action)
        assertEquals(2,db.reminders().all().count{it.occurrenceId==o.id&&it.scheduleStart==o.startAt&&it.result=="PLANNED"})
    }
    @Test fun delayedOsDeliveryEnteringQuietHoursKeepsItsDeferredTime()=runBlocking {
        val o=setup("LIGHT")
        reminders.settings("13:15","18:00",true)
        clock.instant=Instant.ofEpochMilli(o.startAt+30*60000)
        reminders.reconcile(deliver=true)
        assertTrue(platform.posts.isEmpty())
        clock.instant=Instant.ofEpochMilli(o.startAt+4*3600000)
        reminders.reconcile(deliver=true)
        assertEquals("PLANNED",db.reminders().all().single{it.occurrenceId==o.id}.result)
        clock.instant=Instant.ofEpochMilli(o.startAt+5*3600000)
        reminders.reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
        assertTrue(platform.posts.single{it.occurrenceId==o.id}.delayed)
    }
    @Test fun bootMarkerPreventsWorkerBeforeReceiverFromPostingBacklog()=runBlocking {
        platform.epoch="boot-1";val o=setup("STANDARD")
        platform.epoch="boot-2";clock.instant=Instant.ofEpochMilli(o.startAt+60000)
        reminders.reconcile(deliver=true)
        assertTrue(platform.posts.none{it.occurrenceId==o.id})
        assertEquals("MISSED",db.reminders().all().first{it.occurrenceId==o.id}.result)
    }
    @Test fun advancingClockDoesNotInventAQuietHoursDelay()=runBlocking {
        val o=setup("LIGHT")
        val ticking=object:AppClock {
            override fun now()=clock.instant.also{clock.instant=clock.instant.plusMillis(5)}
            override fun zone()=clock.zone()
            override fun elapsedMillis()=clock.elapsedMillis()
        }
        clock.instant=Instant.ofEpochMilli(o.startAt)
        ReminderRepository(ScheduleRepository(db,ticking),platform).reconcile(deliver=true)
        assertEquals(1,platform.posts.count{it.occurrenceId==o.id})
        assertFalse(platform.posts.single{it.occurrenceId==o.id}.delayed)
    }
    @Test fun migration2to3AddsOnlyReminderStorageAndRetainsSettings()=runBlocking {
        migrations.createDatabase(name,2).apply {
            execSQL("INSERT INTO app_settings VALUES(1,'original','04:00','UTC','LOCAL_FLOATING','09:00','18:00','19:00','21:00',0,'22:30','07:00',1,123)")
            close()
        }
        migrations.runMigrationsAndValidate(name,3,true,MIGRATION_2_3).close()
        db=BetterDatabase.open(context,name)
        assertEquals("original",db.foundation().settings()!!.profileId)
        assertFalse(db.foundation().settings()!!.notificationsWanted)
        assertTrue(db.reminders().all().isEmpty())
    }
}
