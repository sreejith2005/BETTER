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
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CareerUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-career-ui-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private val clock = FocusClock()
    private lateinit var repo: FocusRepository
    private lateinit var model: CareerViewModel
    @Before fun setup() = runBlocking {
        db=BetterDatabase.open(context,name);LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
        repo=FocusRepository(db,clock,{74});model=CareerViewModel(repo)
    }
    @After fun cleanup() { androidx.lifecycle.ViewModelStore().apply { put("career",model); clear() }; db.close();context.deleteDatabase(name) }
    @Test fun rescueStartPauseContinueStopAndEvidenceAreVisible() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CareerToday(model,LocalDate.of(2026,10,1)) } } }
        compose.onNodeWithText("Rescue").performScrollTo().performClick()
        compose.onNodeWithText("Start five minutes").performScrollTo().assertIsNotEnabled()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Start five minutes").performClick()
        compose.waitUntil(7000) { model.state.value.active != null }
        compose.onNodeWithText("Pause").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.active?.state == "PAUSED" }
        compose.onNodeWithText("Resume").performClick()
        compose.waitUntil(7000) { model.state.value.active?.state == "RUNNING" }
        clock.advance(300000)
        compose.waitUntil(7000) { model.state.value.active?.state == "RESCUE_READY" }
        compose.onNodeWithText("Continue toward daily minimum").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.active?.continued == true }
        clock.advance(1500000)
        compose.onNodeWithText("Stop and save").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.active == null }
        runBlocking { assertEquals(1800000,repo.snapshot().sessionMillis(repo.snapshot().sessions.single().id)) }
        compose.onNodeWithText("Last session saved · 30:00 · timed").assertExists()
    }
    @Test fun recoveryPromptAndOptionalApplicationEvidence() {
        runBlocking { repo.start("Apply",false,true,"Example","Engineer");clock.advance(10000);repo.snapshot();clock.advance(20000) }
        val recovered=FocusRepository(db,clock,{74},"restarted")
        androidx.lifecycle.ViewModelStore().apply { put("old",model);clear() }
        model=CareerViewModel(recovered)
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            CareerToday(model,LocalDate.of(2026,10,1));CareerReview(model,LocalDate.of(2026,10,1))
        } } }
        compose.waitUntil(7000) { model.state.value.active?.state=="REVIEW" }
        compose.onNodeWithText("Work stayed active").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Keep saved checkpoint only").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.active?.state=="PAUSED" }
        compose.onNodeWithText("Stop and save").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.active==null }
        compose.onNodeWithText("Company: Example\nRole: Engineer\nApplication details: self-reported").performScrollTo().assertIsDisplayed()
    }
    @Test fun futureTargetSettingsShowEffectiveHistory() {
        compose.setContent { MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { CareerSettings(model,LocalDate.of(2026,10,1)) } } }
        compose.onNodeWithText("Target minutes").performTextReplacement("40")
        compose.onNodeWithText("Effective date (YYYY-MM-DD)").performTextReplacement("2026-10-03")
        compose.onNodeWithText("Save future target").performScrollTo().performClick()
        compose.waitUntil(7000) { model.state.value.standards.size==2 }
        compose.onNodeWithText("2026-10-03: 40 minutes").assertExists()
        compose.onNodeWithText("2026-10-01: 30 minutes").assertExists()
    }
}
