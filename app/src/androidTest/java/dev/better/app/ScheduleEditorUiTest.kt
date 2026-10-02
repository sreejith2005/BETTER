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
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScheduleEditorUiTest {
    @get:Rule val compose=createComposeRule()
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val name="test-editor-${UUID.randomUUID()}.db"
    private lateinit var db:BetterDatabase
    @After fun cleanup() {if(::db.isInitialized) db.close();context.deleteDatabase(name)}

    @Test fun at03FixedEventInvalidIntervalThenWindowPreviewMatchesSavedDetail() {
        db=BetterDatabase.open(context,name)
        val clock=MutableClock()
        runBlocking {LocalRepository(db,clock).onboard(SetupInput(zone="Asia/Kolkata"))}
        val model=ScheduleViewModel(ScheduleRepository(db,clock))
        compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {ScheduleScreen(model)}}}
        compose.onNodeWithText("Add fixed event").performClick()
        compose.onNodeWithText("Title").performTextReplacement("Exact source test")
        compose.onNodeWithText("Instructions (copied exactly)").performScrollTo().performTextReplacement("Original line 1\nOriginal line 2")
        compose.onNodeWithText("Source").performScrollTo().performTextReplacement("My source")
        compose.onNodeWithText("one off").performScrollTo().performClick()
        compose.onNodeWithText("daily").performClick()
        compose.onNodeWithText("Interval (e.g. 2d, 1w, 1mo)").performScrollTo().performTextReplacement("0d")
        compose.onNodeWithText("Preview next ten").performScrollTo().performClick()
        compose.waitUntil(5000) {model.editor.value.error!=null}
        compose.onNodeWithText("Intervals must be between 1 and 10000.").performScrollTo().assertIsDisplayed()
        runBlocking {assertEquals(1,db.foundation().commitments().size)}
        compose.onNodeWithText("daily").performScrollTo().performClick()
        compose.onNodeWithText("one off").performClick()
        compose.onNodeWithText("exact").performScrollTo().performClick()
        compose.onNodeWithText("window").performClick()
        compose.onNodeWithText("none").performScrollTo().performClick()
        compose.onNodeWithText("light").performClick()
        compose.onNodeWithText("Preview next ten").performScrollTo().performClick()
        compose.waitUntil(5000) {model.editor.value.preview!=null}
        val preview=requireNotNull(model.editor.value.preview).single()
        compose.onNodeWithText("Dated preview").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep these overlaps").performScrollTo()
        compose.onNodeWithTag("toggle-Keep these overlaps").performScrollTo().performClick()
        compose.onNodeWithText("Save commitment").performScrollTo().performClick()
        compose.waitUntil(5000) {model.editor.value.form==null && model.selected.value!=null}
        compose.onNodeWithText("Reminder: Light. Android timing is best effort.").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Original line 1\nOriginal line 2").onFirst().performScrollTo().assertIsDisplayed()
        runBlocking {
            val id=requireNotNull(model.selected.value)
            val version=db.schedules().versions().single {it.commitmentId==id}
            val occurrence=db.schedules().occurrences().single {it.commitmentId==id}
            assertEquals("Original line 1\nOriginal line 2",version.instructionText)
            assertEquals("My source",version.sourceText)
            assertEquals("LIGHT",version.reminderLevel)
            assertTrue(version.fixed)
            assertEquals("APPOINTMENT",version.category)
            assertEquals(preview.start.toEpochMilli(),occurrence.startAt)
            assertEquals(preview.end.toEpochMilli(),occurrence.endAt)
            assertEquals(DueKind.WINDOW,db.schedules().rules().first {it.id==version.ruleId}.rule().dueKind)
        }
    }
}
