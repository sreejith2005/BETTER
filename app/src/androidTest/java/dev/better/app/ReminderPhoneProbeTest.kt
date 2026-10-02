package dev.better.app

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.probe.ReminderProbe
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.time.*

/** Explicitly selected manual gate steps, skipped in ordinary suites. No personal plan writes. */
@RunWith(AndroidJUnit4::class)
class ReminderPhoneProbeTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val step get()=InstrumentationRegistry.getArguments().getString("phoneProbe")
    @Test fun prepare()=runBlocking {
        assumeTrue(step=="prepare")
        assertFalse("Clean up the previous probe explicitly first",context.getDatabasePath(ReminderProbe.DATABASE).exists())
        val repo=ReminderProbe.schedules(context)
        LocalRepository(repo.db,repo.clock).onboard(SetupInput(zone=repo.clock.zone().id))
        ReminderProbe.reminders(context).settings("00:00","00:00",true)
        val start=repo.clock.now().plusSeconds(120).atZone(repo.clock.zone())
        for((label,seconds) in listOf("BETTER test: complete" to 0L,"BETTER test: reboot" to 240L)) {
            val at=start.plusSeconds(seconds)
            repo.save(CommitmentDraft(label,ScheduleRule(RuleType.ONE_OFF,at.toLocalDate(),at.toLocalTime(),zoneId=repo.clock.zone().id),
                durationSeconds=0,reminderLevel="ESCALATING",reminderOffsets="0,3,8"),false,conflictsAcknowledged=true)
        }
        println("PROBE prepared at ${repo.clock.now()}; permission=${context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()}")
        printRows(repo)
    }
    @Test fun inspect()=runBlocking {
        assumeTrue(step=="inspect")
        val repo=ReminderProbe.schedules(context)
        printRows(repo)
        println("PROBE recovery events=${repo.db.foundation().logs().count{it.action=="SCHEDULE_CHECKED"}}")
        for(o in repo.dao.occurrences().filter{it.commitmentId!=repo.db.foundation().standards().first().commitmentId})
            println("PROBE occurrence=${o.id} state=${o.state} delivered=${o.delivered} completeActions=${repo.db.foundation().logs().count{it.entityId==o.id&&it.action=="EXEC_COMPLETE"}}")
        val manager=context.getSystemService(NotificationManager::class.java)
        println("PROBE active OS notifications=${manager.activeNotifications.count{n->repo.dao.occurrences().any{it.id==n.tag}}}; allowed=${manager.areNotificationsEnabled()}")
    }
    @Test fun cleanup()=runBlocking {
        assumeTrue(step=="cleanup")
        if(context.getDatabasePath(ReminderProbe.DATABASE).exists()) {
            val repo=ReminderProbe.schedules(context)
            val platform=ReminderProbe.platform(context)
            repo.dao.occurrences().forEach{platform.cancelNotification(it.id)}
            platform.schedule(null)
            ReminderProbe.close()
            assertTrue(context.deleteDatabase(ReminderProbe.DATABASE))
        }
    }
    private suspend fun printRows(repo:ScheduleRepository) {
        val fixtures=repo.db.foundation().commitments().filter{it.title.startsWith("BETTER test:")}.map{it.id}.toSet()
        val ids=repo.dao.occurrences().filter{it.commitmentId in fixtures}.map{it.id}.toSet()
        repo.db.reminders().all().filter{it.occurrenceId in ids}.forEach{
            println("PROBE delivery=${it.id} occurrence=${it.occurrenceId} steps=${it.steps} planned=${Instant.ofEpochMilli(it.plannedAt)} result=${it.result} posted=${it.postedAt?.let(Instant::ofEpochMilli)} opened=${it.observedAt?.let(Instant::ofEpochMilli)}")
        }
    }
}
