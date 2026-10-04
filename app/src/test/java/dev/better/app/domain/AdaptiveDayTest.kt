package dev.better.app.domain

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class AdaptiveDayTest {
    private val date = LocalDate.of(2026,10,5)
    private val base = ScheduleEngine.preview(ScheduleRule(RuleType.ONE_OFF, date, LocalTime.of(9,0), DueKind.WINDOW, LocalTime.of(13,0)), date, ZoneOffset.UTC, LocalTime.of(4,0)).single()
    private val block = ProfileBlock("Work", "10:00", 30, false, "work")
    private fun apply(mode: DayPolicy? = null, day: DayPolicy? = null, essential: Boolean = false, fixed: Boolean = false) =
        adaptDay(base, "work", essential, fixed, block, mode, day, LocalTime.of(4,0))
    @Test fun profileThenModeThenDayHasDeterministicPrecedence() {
        assertEquals(10, apply().plan.start.atZone(ZoneOffset.UTC).hour)
        val mode = DayPolicy(items = listOf(ItemPolicy("work", Disposition.MOVE, "11:00")), reason = "Travel")
        val day = DayPolicy(items = listOf(ItemPolicy("work", Disposition.MOVE, "12:00")), reason = "Exception")
        assertEquals(11, apply(mode).plan.start.atZone(ZoneOffset.UTC).hour)
        assertEquals(12, apply(mode, day).plan.start.atZone(ZoneOffset.UTC).hour)
        assertEquals(apply(mode, day), apply(mode, day))
    }
    @Test fun essentialDefaultsKeepAndExplicitSuppressIsDistinct() {
        val policy = DayPolicy(true, reason = "Rest")
        assertTrue(apply(day = policy).suppressed)
        assertFalse(apply(day = policy, essential = true).suppressed)
        assertTrue(apply(day = policy.copy(items = listOf(ItemPolicy("work", Disposition.SUPPRESS))), essential = true).suppressed)
        assertEquals(0, reviewCounts(listOf("SKIPPED_BY_OVERRIDE" to false)).missed)
    }
    @Test fun keepReversesSuppressionAndFixedEventsNeverMove() {
        val mode = DayPolicy(true, reason = "Away")
        val keep = DayPolicy(items = listOf(ItemPolicy("work", Disposition.KEEP)), reason = "Keep")
        assertFalse(apply(mode, keep).suppressed)
        assertEquals(base, apply(mode, keep).plan)
        assertEquals(base, apply(fixed = true).plan)
    }
    @Test fun invalidMoveDoesNotCorruptTheAllowedWindow() {
        val result = apply(day = DayPolicy(items = listOf(ItemPolicy("work", Disposition.MOVE, "22:00"))))
        assertTrue(result.notes.any { it.contains("outside") })
        assertTrue(result.plan.start <= result.plan.end)
    }
    @Test fun overnightWindowMoveUsesFollowingCalendarDate() {
        val overnight = ScheduleEngine.preview(ScheduleRule(RuleType.ONE_OFF,date,LocalTime.of(23,0),DueKind.WINDOW,LocalTime.of(6,0)),
            date,ZoneOffset.UTC,LocalTime.of(4,0)).single()
        val result = adaptDay(overnight,"work",false,false,null,null,
            DayPolicy(items=listOf(ItemPolicy("work",Disposition.MOVE,"01:00")),reason="Late night"),LocalTime.of(4,0))
        assertEquals(date.plusDays(1),result.plan.start.atZone(ZoneOffset.UTC).toLocalDate())
        assertEquals(1,result.plan.start.atZone(ZoneOffset.UTC).hour)
        assertEquals(date,result.plan.operationalDate)
    }
    @Test fun modeCannotMoveAFixedProfileBlock() {
        val result=adaptDay(base,"work",false,false,block.copy(fixed=true),
            DayPolicy(items=listOf(ItemPolicy("work",Disposition.MOVE,"12:00"))),null,LocalTime.of(4,0))
        assertEquals(10,result.plan.start.atZone(ZoneOffset.UTC).hour)
        assertTrue(result.notes.any {it.contains("fixed event retained")})
    }
}
