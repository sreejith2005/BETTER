package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AttentionTest {
    @Test fun midnightCarryInAndOverlappingActivitiesAreClippedWithoutDoubleCounting() {
        val marks = listOf(ForegroundMark(50,"A",1),ForegroundMark(120,"B",1),ForegroundMark(150,"A",2),ForegroundMark(180,"B",2))
        assertNull(foregroundIntervals(marks,100,200))
        assertEquals(listOf(UsageInterval(100,180)),foregroundIntervals(listOf(ForegroundMark(50,"A",1),ForegroundMark(180,"B",2)),100,200))
        assertNull(foregroundIntervals(listOf(ForegroundMark(150,"A",2)),100,200))
        assertNull(foregroundIntervals(listOf(ForegroundMark(150,"A",1)),100,200))
        assertNull(foregroundIntervals(emptyList(),100,200))
        assertEquals(listOf(UsageInterval(110,180)),foregroundIntervals(listOf(ForegroundMark(110,"A",1),ForegroundMark(120,"A",23),ForegroundMark(180,"A",2)),100,200))
    }
    private fun decision(now: Long = 10_000_000L, last: Long? = null, count: Int = 0, consumed: Boolean = false,
        excluded: Boolean = false, running: Boolean = false, complete: Boolean = false, available: Boolean = true,
        quiet: Boolean = false, mode: Boolean = false, usage: Long = 900_000) = attentionDecision(true, available, true,
        excluded, quiet, mode, running, complete, usage, 15, now, last, count, consumed)
    @Test fun officeCareerQuietModeAndUnknownSuppress() {
        assertFalse(decision(excluded = true).eligible); assertFalse(decision(running = true).eligible)
        assertFalse(decision(complete = true).eligible); assertFalse(decision(available = false).eligible)
        assertFalse(decision(quiet = true).eligible); assertFalse(decision(mode = true).eligible)
    }
    @Test fun thresholdCooldownCapAndWindowAreBounded() {
        assertFalse(decision(usage = 899_999).eligible); assertTrue(decision().eligible)
        assertFalse(decision(last = 6_400_001).eligible); assertTrue(decision(last = 6_400_000).eligible)
        assertFalse(decision(count = 2).eligible); assertFalse(decision(consumed = true).eligible)
        assertFalse(decision(now = 1, last = 2).eligible)
    }
    @Test fun clippedWindowsSubtractOfficeAndCommuteIncludingOvernight() {
        val start = Instant.parse("2026-10-04T17:30:00Z").toEpochMilli()
        val end = start + 7_200_000
        val windows = listOf(AttentionWindow(LocalTime.of(17,0), LocalTime.of(21,0)))
        val excluded = listOf(AttentionWindow(LocalTime.of(9,0),LocalTime.of(18,0)), AttentionWindow(LocalTime.of(18,0),LocalTime.of(18,30)))
        assertEquals(3_600_000, attentionMillis(listOf(UsageInterval(start,end)),start,end,ZoneId.of("UTC"),windows,excluded))
        val overnight = AttentionWindow(LocalTime.of(23,0),LocalTime.of(2,0))
        assertTrue(overnight.contains(LocalTime.of(1,0))); assertFalse(overnight.contains(LocalTime.of(12,0)))
    }
}
