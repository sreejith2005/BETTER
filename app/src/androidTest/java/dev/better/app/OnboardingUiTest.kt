package dev.better.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.feature.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OnboardingUiTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-ui-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    @After fun cleanup() { if (::db.isInitialized) db.close(); context.deleteDatabase(name) }

    @Test fun localSetupWithoutPermissionShowsPersistedCareerStandardAndNavigation() {
        db = BetterDatabase.open(context, name)
        val model = AppViewModel(LocalRepository(db, FixedClock()), (context.applicationContext as BetterApplication).preferences)
        compose.setContent { BetterRoot(model, FixedClock()) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Day begins").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Day begins").performTextReplacement("25:00")
        compose.onNodeWithText("Create my local plan").performScrollTo().performClick()
        compose.onNodeWithText("Enter valid times between 00:00 and 23:59.").assertIsDisplayed()
        runBlocking { assertNull(db.foundation().settings()) }
        compose.onNodeWithText("Day begins").performScrollTo().performTextReplacement("04:00")
        compose.onNodeWithText("Create my local plan").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("0 / 30 min").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("0 / 30 min").assertIsDisplayed()
        compose.onNodeWithText("30 minutes remaining.").assertIsDisplayed()
        compose.onNodeWithText("Local profile · Normal mode").assertIsDisplayed()
        runBlocking {
            assertFalse(db.foundation().settings()!!.notificationsWanted)
            assertEquals(1800, db.foundation().standards().single().targetSeconds)
            assertEquals("04:00", db.foundation().settings()!!.dayBoundary)
        }
        compose.onNodeWithText("Plan").performClick()
        compose.onNodeWithText("Daily standard · 30 minutes").assertIsDisplayed()
        compose.onNodeWithText("Review").performClick()
        compose.onNodeWithText("Today · In progress").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Stored on this phone").assertIsDisplayed()
    }
}
