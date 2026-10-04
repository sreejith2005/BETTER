package dev.better.app

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.better.app.data.*
import dev.better.app.domain.*
import dev.better.app.platform.UsageObservation
import kotlinx.coroutines.runBlocking
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OwnershipStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "test-ownership-${UUID.randomUUID()}.db"
    private lateinit var db: BetterDatabase
    private val clock = FocusClock().apply { instant = Instant.parse("2026-10-04T06:00:00Z") }
    private lateinit var schedules: ScheduleRepository
    private lateinit var archive: ArchiveRepository
    @get:Rule val migrations = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), BetterDatabase::class.java)
    @Before fun setup() = runBlocking {
        db = BetterDatabase.open(context,name); LocalRepository(db,clock).onboard(SetupInput(zone="UTC"))
        schedules = ScheduleRepository(db,clock); schedules.ensureCareerSchedule(); archive = ArchiveRepository(context,db)
    }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }
    private suspend fun fixture() {
        val rule = ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT,LocalDate.of(2026,10,4),LocalTime.of(9,0),offsets=listOf(DateStep(3,DateUnit.DAYS),DateStep(2,DateUnit.DAYS),DateStep(1,DateUnit.DAYS)),tail=DateStep(2,DateUnit.DAYS),zoneId="UTC")
        val id = schedules.save(CommitmentDraft("Exact health text",rule,category="HEALTH",instructions="  User entered only.\nKeep EXACT punctuation!  ",source="Own note"),false,conflictsAcknowledged=true)
        val adaptive = AdaptiveRepository(schedules)
        suspend fun save(r: AdaptiveRecord) { adaptive.save(r,adaptive.preview(r),true) }
        val routine = adaptive.record("ROUTINE","Checklist","2026-10-04",payload=JSONObject().put("format",1).put("steps",JSONArray(listOf("First","Middle","Last"))).toString())
        save(routine)
        save(adaptive.record("LINK","Attached","2026-10-04",payload=JSONObject().put("format",1).put("routine",routine.family).toString(),family=id))
        val occurrence = db.schedules().occurrences().first { it.commitmentId == id }
        val run = adaptive.startRun(occurrence.id); adaptive.step(run.id,0,"COMPLETED"); adaptive.step(run.id,1,"SKIPPED")
        save(routine.copy(id=UUID.randomUUID().toString(),fromDate="2026-10-05",payload=JSONObject().put("format",1).put("steps",JSONArray(listOf("New"))).toString()))
        val profile = adaptive.record("PROFILE","Workday","2026-10-04",payload=AdaptiveJson.blocks(listOf(ProfileBlock("Block","10:00",30,false))))
        save(profile); save(profile.copy(id=UUID.randomUUID().toString(),fromDate="2026-10-05",title="New profile"))
        save(adaptive.record("ASSIGN","Weekdays","2026-10-04",payload=JSONObject().put("format",1).put("weekdays","1,2,3,4,5").toString(),family=profile.family))
        save(adaptive.record("MODE","Travel","2026-10-07","2026-10-09",AdaptiveJson.policy(DayPolicy(true,reason="Away")).toString()))
        save(adaptive.record("DAY","Rest","2026-10-10","2026-10-10",AdaptiveJson.policy(DayPolicy(true,reason="Rest")).toString()))
        db.attention().save(AttentionSettings(theme="Dark"))
        val focus = FocusRepository(db,clock,{1},"test-owner")
        focus.start("Search",true,true); clock.advance(120_000); focus.act(focus.snapshot().active!!.id,"STOP")
        db.schedules().upsertOverride(OverrideRecord(UUID.randomUUID().toString(), occurrence.id, "Explicit synthetic override",
            "  User entered only.\nKeep EXACT punctuation!  ", "Own note", "Archive fixture", clock.now().toEpochMilli(), occurrence.startAt))
        val attempted = Instant.parse("2026-10-03T18:20:00Z").toEpochMilli()
        db.attention().insert(AttentionAttempt("synthetic-attempt", "2026-10-03", "synthetic-window", attempted, attempted, 900_000, "com.instagram.android", "POSTED"))
        ReminderRepository(schedules, object : ReminderPlatform {
            override fun allowed(category: String) = false
            override fun schedule(at: Long?) {}
            override fun cancelNotification(occurrenceId: String) {}
            override fun post(row: ReminderDelivery, occurrence: OccurrenceRecord, version: VersionRecord, title: String, private: Boolean) { error("No real notification in archive fixture") }
        }).reconcile()
    }
    @Test fun at29DeleteSyntheticOnlyRestoreEveryTableAndNextTen() = runBlocking {
        fixture()
        val before = archive.export()
        val previews = db.schedules().rules().associate { it.id to ScheduleEngine.preview(it.rule(),LocalDate.of(2026,10,4),ZoneId.of("UTC"),LocalTime.of(4,0)) }
        val counts = archive.preview(before).counts
        assertTrue("Every archive table must have fixture coverage", counts.values.all { it > 0 })
        assertTrue(counts.getValue("routine_runs") > 0); assertTrue(counts.getValue("adaptive_records") >= 8)
        assertTrue(runCatching { archive.deleteLocal("no") }.isFailure)
        archive.deleteLocal("DELETE MY LOCAL DATA")
        assertNull(db.foundation().settings()); assertEquals(0,db.schedules().occurrences().size)
        val preview = archive.preview(before)
        archive.replace(preview,true)
        assertEquals(JSONObject(before).getJSONObject("tables").toString(),JSONObject(archive.export()).getJSONObject("tables").toString())
        assertEquals(previews,db.schedules().rules().associate { it.id to ScheduleEngine.preview(it.rule(),LocalDate.of(2026,10,4),ZoneId.of("UTC"),LocalTime.of(4,0)) })
        assertEquals("  User entered only.\nKeep EXACT punctuation!  ", db.schedules().versions().first { it.category=="HEALTH" }.instructionText)
    }
    @Test fun at29RejectsCorruptIncompatibleDuplicateAndBrokenReferenceWithoutMutation() = runBlocking {
        fixture(); val raw = archive.export(); val baseline = JSONObject(raw).getJSONObject("tables").toString()
        val bad = mutableListOf("broken", JSONObject(raw).put("archiveVersion",999).toString())
        bad += JSONObject(raw).apply { val rows=getJSONObject("tables").getJSONArray("commitments"); rows.put(rows.getJSONObject(0)) }.toString()
        bad += JSONObject(raw).apply { getJSONObject("tables").getJSONArray("commitment_versions").getJSONObject(0).put("ruleId","missing") }.toString()
        bad += JSONObject(raw).apply { getJSONObject("tables").getJSONArray("schedule_rules").getJSONObject(0).put("step","0d") }.toString()
        for(value in bad) { assertTrue(runCatching { archive.preview(value) }.isFailure); assertEquals(baseline,JSONObject(archive.export()).getJSONObject("tables").toString()) }
    }
    @Test fun stalePreviewAndActiveTimerAreRejected() = runBlocking {
        val raw = archive.export(); val preview=archive.preview(raw)
        db.attention().save(AttentionSettings())
        assertTrue(runCatching { archive.replace(preview,true) }.isFailure)
        val focus=FocusRepository(db,clock,{1}); focus.start("Search",false,true)
        assertTrue(runCatching { archive.export() }.isFailure)
        assertTrue(runCatching { archive.deleteLocal("DELETE MY LOCAL DATA") }.isFailure)
    }
    @Test fun at27PersistedThresholdWindowCooldownAndDailyCap() = runBlocking {
        val repo=AttentionRepository(db,clock)
        repo.save(AttentionSettings(enabled=true,windows="18:00-19:00,19:00-20:00,20:00-21:00"))
        var posts=0
        suspend fun check(hour:Int,minute:Int):String {
            clock.instant=LocalDate.of(2026,10,4).atTime(hour,minute).toInstant(ZoneOffset.UTC)
            val end=clock.now().toEpochMilli(); val o=UsageObservation("com.instagram.android",end,end-1_200_000,listOf(UsageInterval(end-1_200_000,end)),"synthetic")
            return repo.evaluate(o,FocusSnapshot(),true) { _,_->posts++ }
        }
        assertTrue(check(10,20).contains("Outside")); assertEquals(0,posts)
        assertTrue(check(18,20).startsWith("POSTED")); assertTrue(check(18,40).contains("already"))
        assertTrue(check(19,15).contains("cooldown")); assertTrue(check(19,20).startsWith("POSTED"))
        assertTrue(check(20,20).contains("cap")); assertEquals(2,posts)
    }
    @Test fun at27CareerQuietModesDeniedAndStaleSuppressOnPhone() = runBlocking {
        val repo = AttentionRepository(db,clock)
        repo.save(AttentionSettings(enabled=true,windows="18:00-23:30",disabledModes="Travel"))
        clock.instant=Instant.parse("2026-10-04T19:20:00Z")
        val now=clock.now().toEpochMilli()
        val observation=UsageObservation("com.instagram.android",now,now-1_200_000,listOf(UsageInterval(now-1_200_000,now)),"synthetic")
        var posts=0
        suspend fun evaluate(o:UsageObservation=observation, focus:FocusSnapshot=FocusSnapshot(), allowed:Boolean=true) = repo.evaluate(o,focus,allowed) { _,_->posts++ }
        assertEquals("Usage unavailable",evaluate(observation.copy(intervals=null)))
        assertEquals("Usage unavailable",evaluate(observation.copy(observedAt=now-120_001)))
        assertEquals("Usage unavailable",evaluate(observation.copy(packageName="com.instagram.lite")))
        assertEquals("Career minimum complete",evaluate(focus=FocusSnapshot(dailyMillis=mapOf(LocalDate.of(2026,10,4) to 1_800_000L))))
        val session=FocusSession("synthetic","Search","","",false,false,"RUNNING","test",now)
        assertEquals("Career running",evaluate(focus=FocusSnapshot(sessions=listOf(session))))
        assertTrue(evaluate(allowed=false).contains("Notifications unavailable"))
        val adaptive=AdaptiveRepository(schedules)
        db.adaptive().insert(adaptive.record("MODE","Travel","2026-10-04","2026-10-04",AdaptiveJson.policy(DayPolicy(reason="Away")).toString()))
        assertEquals("Active mode disables Attention",evaluate())
        clock.instant=Instant.parse("2026-10-04T22:45:00Z")
        assertEquals("Quiet hours",evaluate(observation.copy(observedAt=clock.now().toEpochMilli())))
        assertEquals(0,posts); assertTrue(db.attention().attempts().isEmpty())
    }
    @Test fun migrationFiveToSixRetainsOriginalSettings() {
        val migrationName="test-ownership-migration.db"
        migrations.createDatabase(migrationName,5).apply { execSQL("INSERT INTO app_settings VALUES (1,'keep','04:00','UTC','LOCAL_FLOATING','09:00','18:00','19:00','21:00',0,'22:30','07:00',1,1)");close() }
        migrations.runMigrationsAndValidate(migrationName,6,true,MIGRATION_5_6).apply {
            query("SELECT profileId FROM app_settings").use { assertTrue(it.moveToFirst()); assertEquals("keep",it.getString(0)) };close()
        }; context.deleteDatabase(migrationName)
    }
}
