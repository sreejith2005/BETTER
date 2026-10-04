package dev.better.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.BetterDatabase
import dev.better.app.data.ScheduleRepository
import dev.better.app.platform.AndroidClock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicitly invoked upgrade check; never changes personal records except normal clock reconciliation. */
@RunWith(AndroidJUnit4::class)
class InstalledDataPreservationTest {
    @Test fun allOriginalRecordsSurviveInPlaceUpdate()=runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyPersonalData")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val snapshot=InstrumentationRegistry.getArguments().getString("preservationSnapshot") ?: "phase2-preservation"
        require(snapshot in listOf("phase2-preservation", "phase3-preservation", "phase4-preservation", "phase5-preservation", "phase6-preservation"))
        val saved=File(context.filesDir,"$snapshot/better.db")
        assertTrue("Phone-local pre-update snapshot required",saved.isFile)
        assertTrue(context.getDatabasePath("better.db").isFile)
        val db=BetterDatabase.open(context)
        try {
            val repo=ScheduleRepository(db,AndroidClock())
            repo.ensureCareerSchedule();repo.reconcile()
            // SQLite may update shared-memory read marks even for a read-only connection.
            // Compare a temporary phone-private copy, leaving preservation snapshots byte-exact.
            val comparison = File(context.cacheDir,"preservation-comparison-${java.util.UUID.randomUUID()}").also { it.mkdir() }
            listOf("better.db", "better.db-wal", "better.db-shm").forEach { part ->
                File(saved.parentFile, part).takeIf { it.exists() }?.copyTo(File(comparison,part))
            }
            val before=SQLiteDatabase.openDatabase(File(comparison,"better.db").path,null,SQLiteDatabase.OPEN_READONLY)
            try {
                val current=db.openHelper.readableDatabase
                var preserved=0
                val tables = listOf("app_settings","commitments","career_standards","schedule_rules","commitment_versions","occurrences","occurrence_overrides","action_log") +
                    (if (snapshot in listOf("phase4-preservation", "phase5-preservation", "phase6-preservation")) listOf("reminder_deliveries") else emptyList()) +
                    (if (snapshot in listOf("phase5-preservation", "phase6-preservation")) listOf("focus_sessions", "focus_segments") else emptyList()) +
                    (if (snapshot == "phase6-preservation") listOf("adaptive_records", "routine_runs") else emptyList())
                for(table in tables) {
                    before.rawQuery("SELECT * FROM $table",null).use {old->
                        while(old.moveToNext()) {
                            val id=old.getString(old.getColumnIndexOrThrow("id"))
                            current.query("SELECT * FROM $table WHERE id = ?",arrayOf(id)).use {now->
                                assertTrue("Missing original row in $table",now.moveToFirst())
                                for(column in old.columnNames) {
                                    // A pending/due row can advance with the real clock; all identity/content/evidence remains exact.
                                    if(table=="occurrences" && column=="state" && old.getString(old.getColumnIndexOrThrow(column)) in listOf("PENDING","DUE","OVERDUE")) continue
                                    // A live, originally pending alert can reach its due time while
                                    // notifications remain denied during a long verification session.
                                    if (table=="reminder_deliveries" && column in listOf("result","detail") &&
                                        old.getString(old.getColumnIndexOrThrow("result"))=="PLANNED" &&
                                        now.getString(now.getColumnIndexOrThrow("result"))=="BLOCKED") {
                                        assertTrue(old.getLong(old.getColumnIndexOrThrow("plannedAt")) < System.currentTimeMillis())
                                        assertFalse(context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled())
                                        assertEquals("Notifications or channel are off", now.getString(now.getColumnIndexOrThrow("detail")))
                                        listOf("attemptedAt","postedAt","observedAt").forEach { field ->
                                            assertTrue(old.isNull(old.getColumnIndexOrThrow(field)))
                                            assertTrue(now.isNull(now.getColumnIndexOrThrow(field)))
                                        }
                                        continue
                                    }
                                    // A later recheck may follow the original alert's due time. Recovery is
                                    // required to mark an unposted, expired step MISSED instead of replaying it.
                                    if (table=="reminder_deliveries" && column in listOf("result","detail") &&
                                        old.getString(old.getColumnIndexOrThrow("result"))=="PLANNED" &&
                                        now.getString(now.getColumnIndexOrThrow("result"))=="MISSED") {
                                        assertTrue("Only elapsed reminder steps may advance", old.getLong(old.getColumnIndexOrThrow("plannedAt")) < System.currentTimeMillis())
                                        continue
                                    }
                                    assertEquals("Changed original field in $table.$column",old.getString(old.getColumnIndexOrThrow(column)),now.getString(now.getColumnIndexOrThrow(column)))
                                }
                                preserved++
                            }
                        }
                    }
                }
                println("PRESERVATION: $preserved original rows checked across ${tables.size} tables; private snapshot remains on phone. Open occurrence states and expired PLANNED-to-MISSED reminder states may advance; other original fields remain exact.")
                assertEquals(6,current.version)
            }finally{before.close();comparison.deleteRecursively()}
        }finally{db.close()}
    }
}
