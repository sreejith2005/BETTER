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
import dev.better.app.feature.*
import dev.better.app.feature.plan.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExecutionUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-execution-ui-${UUID.randomUUID()}.db"
    private lateinit var db:BetterDatabase
    private val clock=MutableClock(Instant.parse("2026-10-01T08:00:00Z"))
    @After fun cleanup(){if(::db.isInitialized) db.close();context.deleteDatabase(name)}
    private fun setup():ScheduleViewModel {
        db=BetterDatabase.open(context,name)
        runBlocking {LocalRepository(db,clock).onboard(SetupInput(zone="Asia/Kolkata"))}
        return ScheduleViewModel(ScheduleRepository(db,clock))
    }
    private fun click(text:String) {
        if(!text.startsWith("Confirm ")) compose.onNodeWithText(text).performScrollTo()
        compose.waitUntil(7000) { !compose.onNodeWithText(text).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithText(text).performClick()
    }
    private fun state(model:ScheduleViewModel,id:String,value:String) {
        compose.waitUntil(7000) {model.data.value.occurrences.any {it.commitmentId==id && it.state==value}}
        runBlocking {assertEquals(value,db.schedules().occurrences().single {it.commitmentId==id}.state)}
    }
    @Test fun fullAddPreviewDueMoveUndoCompleteSkipCancelFlow() {
        val model=setup()
        compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {ScheduleScreen(model)}}}
        click("Add commitment")
        compose.onNodeWithText("Title").performTextReplacement("Phase 2 flow")
        compose.onNodeWithText("Due time (HH:mm)").performScrollTo().performTextReplacement("14:00")
        click("Preview next ten")
        compose.waitUntil(5000) {model.editor.value.preview!=null}
        click("Save commitment")
        compose.waitUntil(5000) {model.selected.value!=null && model.editor.value.form==null}
        val id=model.selected.value!!
        compose.runOnIdle {clock.instant=Instant.parse("2026-10-01T08:30:00Z");model.refresh()}
        state(model,id,"DUE")
        click("Move")
        compose.onNodeWithText("Reason").performTextReplacement("Changed plan")
        compose.onNodeWithText("New time (HH:mm)").performScrollTo().performTextReplacement("16:00")
        click("Preview new time")
        compose.waitForIdle()
        click("Confirm move")
        state(model,id,"PENDING")
        click("History and corrections")
        click("Undo last action")
        state(model,id,"DUE")
        click("Complete");click("Confirm complete")
        state(model,id,"COMPLETED")
        click("History")
        compose.onNodeWithText("Completed · Self-reported").performScrollTo().assertIsDisplayed()
        click("History and corrections");click("Undo last action")
        state(model,id,"DUE");click("Open occurrences")
        click("Skip")
        compose.onNodeWithText("Reason").performTextReplacement("Unavailable")
        click("Confirm skip");state(model,id,"SKIPPED");click("History")
        compose.onNodeWithText("Reason: Unavailable").performScrollTo().assertIsDisplayed()
        click("History and corrections");click("Undo last action")
        state(model,id,"DUE");click("Open occurrences")
        click("Cancel")
        compose.onNodeWithText("Reason").performTextReplacement("No longer needed")
        click("Confirm cancel");state(model,id,"CANCELLED");click("History")
        compose.onNodeWithText("Cancelled").performScrollTo().assertIsDisplayed()
        runBlocking {assertEquals(4,db.foundation().logs().count {it.action in listOf("EXEC_MOVE","EXEC_COMPLETE","EXEC_SKIP","EXEC_CANCEL")})}
    }
    @Test fun todayAndReviewShowDistinctStatesAndLateHealthEvidence() {
        val model=setup()
        runBlocking {
            val repo=model.repository
            suspend fun add(title:String,time:Int,category:String="PERSONAL"):String = repo.save(CommitmentDraft(title,
                ScheduleRule(RuleType.ONE_OFF,LocalDate.of(2026,10,1),LocalTime.of(time,0),DueKind.WINDOW,LocalTime.of(time+1,0)),category=category),false,conflictsAcknowledged=true)
            add("Due fixture",13);add("Upcoming fixture",16)
            val id=add("Health fixture",10,"HEALTH")
            model.execution.act(repo.dao.occurrences().first {it.commitmentId==id}.id,"COMPLETE")
        }
        val app=AppViewModel(LocalRepository(db,clock),(context.applicationContext as BetterApplication).preferences)
        compose.setContent {BetterRoot(app,clock,model)}
        compose.waitUntil(7000) {compose.onAllNodesWithText("Next action").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("30 minutes remaining.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Local profile · Normal mode").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Due and overdue").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Upcoming fixture").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Completed late · Self-reported").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        compose.waitUntil(7000) {compose.onAllNodesWithText("In progress").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("In progress").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Completed on time 0\nCompleted late 1\nSkipped 0\nMissed / unconfirmed 0\nSuppressed by override 0\nCancelled 0").performScrollTo().assertIsDisplayed()
        runBlocking {assertEquals(1,db.schedules().occurrences().count {it.completedLate})}
    }
    @Test fun completionIntervalSkipShowsExplicitRestartAndPersistsPreview() {
        val model=setup()
        val id=runBlocking {model.repository.save(CommitmentDraft("Interval fixture",ScheduleRule(RuleType.INTERVAL_COMPLETION,
            LocalDate.of(2026,10,1),LocalTime.of(14,0))),false)}
        compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {ScheduleScreen(model)}}}
        compose.waitUntil(5000) {model.data.value.commitments.any {it.id==id}}
        compose.runOnIdle {model.inspect(id)}
        click("Skip")
        compose.onNodeWithText("Stop until I choose a new date").assertIsDisplayed()
        compose.onNodeWithText("Reason").performTextReplacement("Explicit restart")
        click("Choose next date")
        compose.onNodeWithText("New date (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-10-05")
        click("Preview new time");compose.waitForIdle();click("Confirm skip")
        compose.waitUntil(5000) {model.data.value.occurrences.count {it.commitmentId==id}==2}
        runBlocking {
            val next=db.schedules().occurrences().single {it.commitmentId==id && it.state=="PENDING"}
            assertEquals("2026-10-05",next.plannedDate)
        }
        compose.onNodeWithText("Reason: Explicit restart after skip: Explicit restart").performScrollTo().assertIsDisplayed()
    }
}
