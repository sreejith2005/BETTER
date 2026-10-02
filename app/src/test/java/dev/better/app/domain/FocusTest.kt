package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class FocusTest {
    @Test fun activeIntervalSplitsAtOperationalBoundary() {
        val parts = allocateFocus(Instant.parse("2026-10-02T03:58:00Z").toEpochMilli(), 300000, ZoneOffset.UTC, LocalTime.of(4,0))
        assertEquals(mapOf(LocalDate.of(2026,10,1) to 120000L, LocalDate.of(2026,10,2) to 180000L), parts)
    }
    @Test fun dstDayUsesRealBoundaryAndConservesDuration() {
        val zone = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026,3,7).atTime(4,0).atZone(zone).toInstant()
        val parts = allocateFocus(start.toEpochMilli(), 24*3600000L, zone, LocalTime.of(4,0))
        assertEquals(23*3600000L, parts[LocalDate.of(2026,3,7)])
        assertEquals(3600000L, parts[LocalDate.of(2026,3,8)])
    }
    @Test fun rescueCapsWithoutRoundingOrDoubleCounting() {
        assertEquals(120000, focusDuration(1000, 900000, 10000, 120000))
        assertEquals(899000, focusDuration(1000, 900000, 10000, null))
    }
    @Test fun emptyIntervalAllocatesNothing() { assertTrue(allocateFocus(0,0,ZoneOffset.UTC,LocalTime.of(4,0)).isEmpty()) }
    @Test(expected = IllegalArgumentException::class) fun elapsedResetCannotAwardTime() { focusDuration(100,90,0,null) }
}
