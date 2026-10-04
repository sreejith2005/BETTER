package dev.better.app

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.better.app.data.*
import dev.better.app.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

class FixedClock : AppClock {
    override fun now(): Instant = Instant.parse("2026-09-30T12:00:00Z")
    override fun zone(): ZoneId = ZoneId.of("Asia/Kolkata")
    override fun elapsedMillis(): Long = 100_000L
}

@RunWith(AndroidJUnit4::class)
class FoundationTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-foundation-${UUID.randomUUID()}.db"
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetterDatabase::class.java)
    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun exportedSchemaOpensAndOnboardingSurvivesReopenWithoutDuplication() = runBlocking {
        migrations.createDatabase(name, 1).close()
        var db = BetterDatabase.open(context, name)
        val setup = SetupInput(zone = "Asia/Kolkata", notificationsWanted = false)
        LocalRepository(db, FixedClock()).onboard(setup)
        db.close()
        db = BetterDatabase.open(context, name)
        try {
            LocalRepository(db, FixedClock()).onboard(setup.copy(boundary = "05:00"))
            val dao = db.foundation()
            assertEquals("04:00", dao.settings()!!.dayBoundary)
            assertEquals("Asia/Kolkata", dao.settings()!!.zoneId)
            assertFalse(dao.settings()!!.notificationsWanted)
            assertEquals(1, dao.commitments().size)
            assertEquals("Career Focus", dao.commitments().single().title)
            assertEquals(1800, dao.standards().single().targetSeconds)
            assertEquals("2026-09-30", dao.standards().single().effectiveDate)
            assertEquals(1, dao.logs().size)
        } finally { db.close() }
    }

    @Test fun invalidSetupLeavesNoPartialProfile() = runBlocking {
        val db = BetterDatabase.open(context, name)
        try {
            val result = runCatching { LocalRepository(db, FixedClock()).onboard(SetupInput(boundary = "99:00")) }
            assertTrue(result.isFailure)
            assertNull(db.foundation().settings())
            assertTrue(db.foundation().commitments().isEmpty())
            assertTrue(db.foundation().standards().isEmpty())
            assertTrue(db.foundation().logs().isEmpty())
        } finally { db.close() }
    }

    @Test fun manifestHasNoNetworkOrBroadSensitivePermissions() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
        val permissions = info.requestedPermissions.orEmpty().toSet()
        listOf("android.permission.INTERNET", "android.permission.QUERY_ALL_PACKAGES", "android.permission.READ_CALENDAR").forEach {
            assertFalse("Unexpected permission $it", permissions.contains(it))
        }
        // Phase 6 adds optional, user-granted Usage Access; no broad package inventory.
        assertTrue(permissions.contains("android.permission.PACKAGE_USAGE_STATS"))
        assertEquals(0, context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
