package dev.better.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.feature.plan.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ReminderUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-reminder-ui-${UUID.randomUUID()}.db"
    private lateinit var db:BetterDatabase
    @After fun cleanup(){if(::db.isInitialized)db.close();context.deleteDatabase(name)}
    @Test fun notificationCompleteConfirmationCancelsFutureAndStaleLinkShowsResolvedRecord() {
        val clock=MutableClock(Instant.parse("2026-10-01T12:00:00Z"),ZoneOffset.UTC)
        db=BetterDatabase.open(context,name)
        val repo=ScheduleRepository(db,clock)
        val platform=RecordingReminders()
        val reminders=ReminderRepository(repo,platform)
        repo.onChanged={reminders.reconcile()}
        val o=runBlocking {
            LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
            val id=repo.save(CommitmentDraft("Notification fixture",ScheduleRule(RuleType.ONE_OFF,LocalDate.of(2026,10,1),LocalTime.of(13,0),zoneId="UTC")),false)
            repo.dao.occurrences().first{it.commitmentId==id}
        }
        val model=ScheduleViewModel(repo,reminders)
        runBlocking {
            clock.instant=Instant.ofEpochMilli(o.startAt);reminders.reconcile(deliver=true)
            model.openReminder(platform.posts.first{it.occurrenceId==o.id}.id,"COMPLETE")
        }
        compose.setContent {MaterialTheme{Column(Modifier.verticalScroll(rememberScrollState())){ScheduleScreen(model)}}}
        compose.onNodeWithText("Record completion").assertIsDisplayed()
        compose.onNodeWithText("Confirm complete").performClick()
        compose.waitUntil(7000){runBlocking{repo.dao.occurrence(o.id)!!.state=="COMPLETED" &&
            db.reminders().all().none{it.occurrenceId==o.id&&it.result=="PLANNED"}}}
        runBlocking {
            model.openReminder(platform.posts.first{it.occurrenceId==o.id}.id,"COMPLETE")
            assertFalse(db.reminders().all().any{it.occurrenceId==o.id&&it.result=="PLANNED"})
            assertEquals(1,db.foundation().logs().count{it.entityId==o.id&&it.action=="EXEC_COMPLETE"})
        }
        compose.onNodeWithText("This reminder is no longer current. Your recorded history is shown below.").assertExists()
        compose.onNodeWithText("Record completion").assertDoesNotExist()
        compose.onNodeWithText("Completed · Self-reported").assertExists()
    }
}
