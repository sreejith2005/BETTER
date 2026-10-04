package dev.better.app

import androidx.compose.material3.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import dev.better.app.feature.plan.ScheduleViewModel
import dev.better.app.feature.plan.ScheduleScreen
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.feature.plan.stateLabel
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HealthOwnershipUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun at28ExactEnteredInstructionsAndMissedIsUnconfirmed() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test-health-ownership-${UUID.randomUUID()}.db"
        val db=BetterDatabase.open(context,name)
        try {
            val clock=MutableClock(Instant.parse("2026-10-04T06:00:00Z"),ZoneId.of("UTC"))
            val text="  Exact user text.\nNo inferred changes!  "
            val model = ScheduleViewModel(ScheduleRepository(db,clock))
            val id = runBlocking {
                LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
                val schedules=ScheduleRepository(db,clock)
                val id=schedules.save(CommitmentDraft("User instructions",ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT,LocalDate.of(2026,10,4),LocalTime.of(7,0),offsets=listOf(DateStep(3),DateStep(2),DateStep(1)),zoneId="UTC"),category="HEALTH",instructions=text),false)
                clock.instant=Instant.parse("2026-10-05T05:00:00Z");schedules.reconcile()
                val o=db.schedules().occurrences().first {it.commitmentId==id}
                val v=db.schedules().versions().first {it.commitmentId==id}
                assertEquals("MISSED",o.state);assertEquals(text,v.instructionText)
                assertEquals("Unconfirmed · Review your record", stateLabel(o,v))
                id
            }
            model.selected.value = id
            compose.setContent {MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {ScheduleScreen(model)}}}
            compose.waitUntil(7000) { model.data.value.versions.any { it.commitmentId == id } }
            compose.onNodeWithText("History").performScrollTo().performClick()
            compose.onNodeWithText("Unconfirmed · Review your record").performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText(text)[0].performScrollTo().assertIsDisplayed()
            compose.onAllNodesWithText("Follow your clinician’s instructions.")[0].performScrollTo().assertIsDisplayed()
        } finally {db.close();context.deleteDatabase(name)}
    }
}
