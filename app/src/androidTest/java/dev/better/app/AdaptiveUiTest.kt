package dev.better.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.feature.*
import dev.better.app.feature.plan.ScheduleViewModel
import dev.better.app.feature.plan.ReviewExecution
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AdaptiveUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-adaptive-ui-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private lateinit var repo: AdaptiveRepository
    private var showReview by mutableStateOf(false)
    private val clock = MutableClock(Instant.parse("2026-10-03T06:00:00Z"), ZoneId.of("UTC"))
    @After fun cleanup() { if (::db.isInitialized) db.close(); context.deleteDatabase(name) }
    private fun setup(): String {
        db = BetterDatabase.open(context,name)
        val id = runBlocking {
            LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
            val s=ScheduleRepository(db,clock);repo=AdaptiveRepository(s)
            s.save(CommitmentDraft("Morning work",ScheduleRule(RuleType.DAILY,LocalDate.of(2026,10,3),LocalTime.of(9,0),DueKind.WINDOW,LocalTime.of(13,0),zoneId="UTC")),false)
        }
        val model=ScheduleViewModel(repo.schedules)
        compose.setContent { MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
            if (showReview) ReviewExecution(model, LocalDate.of(2026,10,3)) else AdaptiveScreen(model)
        }} }
        return id
    }
    private fun click(text: String) {
        compose.onNodeWithText(text).performScrollTo()
        compose.waitUntil(7000) { !compose.onNodeWithText(text).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled) }
        compose.onNodeWithText(text).performClick()
    }
    private fun fill(label:String,value:String) {compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)}
    private fun save() {
        click("Preview change")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Confirm adaptive change").fetchSemanticsNodes().isNotEmpty() }
        click("Confirm adaptive change")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Saved. Existing history retained.").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun at16At17CreateAttachRunSkipAndEditKeepsVisibleOldSteps() {
        val id=setup()
        click("New routine");fill("Name","Morning routine");fill("Ordered steps (one per line)","First\nMiddle\nLast");save()
        click("Attach to Morning work");click("Morning routine");save()
        click("Runs and history");click("Open Morning routine · 2026-10-03")
        compose.waitUntil(7000) {compose.onAllNodesWithText("Complete step 1").fetchSemanticsNodes().isNotEmpty()}
        click("Complete step 1");click("Skip step 2");click("Complete step 3")
        compose.onNodeWithText("2. Middle · Skipped").performScrollTo().assertIsDisplayed()
        val old=runBlocking {db.adaptive().runs().single()}
        assertEquals(listOf("COMPLETED","SKIPPED","COMPLETED"),AdaptiveJson.strings(old.results))
        click("Routines");click("Edit Morning routine");fill("Ordered steps (one per line)","Last changed\nFirst");save()
        click("Runs and history")
        compose.onNodeWithText("2. Middle · Skipped").performScrollTo().assertIsDisplayed()
        runBlocking { assertEquals(old,db.adaptive().runs().single());assertEquals(2,db.adaptive().records().count {it.kind=="ROUTINE"});assertEquals(id,repo.schedules.dao.occurrence(old.occurrenceId)!!.commitmentId) }
    }
    @Test fun at18CreateProfilePreviewFixedOverlapAndAssignWeekdays() {
        setup();click("Profiles");click("New profile");fill("Name","Workday")
        click("Add block");fill("Block 1 title","Fixed A")
        click("Add block");fill("Block 2 title","Fixed B")
        // Fixed flag toggle is checked through its labelled row's checkbox semantics.
        compose.onAllNodes(isToggleable()).onFirst().performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).onLast().performScrollTo().performClick()
        click("Preview change")
        compose.waitUntil(10000) {compose.onAllNodesWithText("Keep these overlaps; latest mode wins").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Keep these overlaps; latest mode wins").performScrollTo()
        compose.onAllNodes(isToggleable()).onLast().performClick()
        click("Confirm adaptive change")
        compose.waitUntil(10000) {compose.onAllNodesWithText("Assign Workday").fetchSemanticsNodes().isNotEmpty()}
        click("Assign Workday");fill("Effective date (YYYY-MM-DD)","2026-10-05");click("Preview change")
        compose.waitUntil(10000) {compose.onAllNodesWithText("Keep these overlaps; latest mode wins").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Keep these overlaps; latest mode wins").performScrollTo()
        compose.onAllNodes(isToggleable()).onLast().performClick();click("Confirm adaptive change")
        compose.waitUntil(10000) {compose.onAllNodesWithText("Assign Workday").fetchSemanticsNodes().isNotEmpty()}
        runBlocking {val records=db.adaptive().records();assertEquals("Workday",AdaptiveSnapshot(records).profile("2026-10-05")!!.title);assertNull(AdaptiveSnapshot(records).profile("2026-10-04"))}
    }
    @Test fun at20At21TravelAndOneDayOverrideShowSuppressedReview() {
        val id=setup();click("Modes and overrides");click("Configure Travel")
        fill("End date inclusive (YYYY-MM-DD)","2026-10-05");fill("Reason","Three days away");save()
        runBlocking {assertEquals("SKIPPED_BY_OVERRIDE",repo.schedules.dao.occurrences().first {it.commitmentId==id}.state)}
        click("Day override");fill("Reason","Rest today");save()
        compose.onNodeWithText("One-day override: 2026-10-03–2026-10-03\nRest today").performScrollTo().assertIsDisplayed()
        runBlocking {
            clock.instant=Instant.parse("2026-10-04T05:00:00Z");repo.schedules.reconcile()
            val old=repo.schedules.dao.occurrences().single {it.commitmentId==id && it.plannedDate=="2026-10-03"}
            assertEquals("SKIPPED_BY_OVERRIDE",old.state);assertEquals("Day override: Rest today",old.reason)
        }
        compose.runOnIdle { showReview = true }
        compose.onNodeWithText("Suppressed by day override").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Suppressed by override 1", substring = true).performScrollTo().assertIsDisplayed()
    }
}
