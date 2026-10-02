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
        require(snapshot in listOf("phase2-preservation", "phase3-preservation", "phase4-preservation"))
        val saved=File(context.filesDir,"$snapshot/better.db")
        assertTrue("Phone-local pre-update snapshot required",saved.isFile)
        assertTrue(context.getDatabasePath("better.db").isFile)
        val db=BetterDatabase.open(context)
        try {
            val repo=ScheduleRepository(db,AndroidClock())
            repo.ensureCareerSchedule();repo.reconcile()
            val before=SQLiteDatabase.openDatabase(saved.path,null,SQLiteDatabase.OPEN_READONLY)
            try {
                val current=db.openHelper.readableDatabase
                var preserved=0
                val tables = listOf("app_settings","commitments","career_standards","schedule_rules","commitment_versions","occurrences","occurrence_overrides","action_log") +
                    if (snapshot == "phase4-preservation") listOf("reminder_deliveries") else emptyList()
                for(table in tables) {
                    before.rawQuery("SELECT * FROM $table",null).use {old->
                        while(old.moveToNext()) {
                            val id=old.getString(old.getColumnIndexOrThrow("id"))
                            current.query("SELECT * FROM $table WHERE id = ?",arrayOf(id)).use {now->
                                assertTrue("Missing original row in $table",now.moveToFirst())
                                for(column in old.columnNames) {
                                    // A pending/due row can advance with the real clock; all identity/content/evidence remains exact.
                                    if(table=="occurrences" && column=="state" && old.getString(old.getColumnIndexOrThrow(column)) in listOf("PENDING","DUE","OVERDUE")) continue
                                    assertEquals("Changed original field in $table.$column",old.getString(old.getColumnIndexOrThrow(column)),now.getString(now.getColumnIndexOrThrow(column)))
                                }
                                preserved++
                            }
                        }
                    }
                }
                println("PRESERVATION: $preserved original rows checked across ${tables.size} tables; private snapshot remains on phone.")
                assertEquals(4,current.version)
            }finally{before.close()}
        }finally{db.close()}
    }
}
