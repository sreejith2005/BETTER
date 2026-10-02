package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimeTest {
    @Test fun operationalBoundaryUsesLocalCalendar() {
        val zone = ZoneId.of("Asia/Kolkata")
        assertEquals(LocalDate.of(2026, 9, 29), operationalDate(Instant.parse("2026-09-29T20:30:00Z"), zone, LocalTime.of(4, 0)))
        assertEquals(LocalDate.of(2026, 9, 30), operationalDate(Instant.parse("2026-09-29T22:30:00Z"), zone, LocalTime.of(4, 0)))
    }
    @Test fun dstBoundaryDoesNotAssume24Hours() {
        val zone = ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026, 3, 7), operationalDate(Instant.parse("2026-03-08T07:30:00Z"), zone, LocalTime.of(4, 0)))
        assertEquals(LocalDate.of(2026, 3, 8), operationalDate(Instant.parse("2026-03-08T08:00:00Z"), zone, LocalTime.of(4, 0)))
    }
    @Test fun rejectsInvalidSetupWithoutCoercion() {
        assertNotNull(SetupInput(boundary = "24:00").validate())
        assertNotNull(SetupInput(boundary = "4:00").validate())
        assertNotNull(SetupInput(zone = "Not/AZone").validate())
        assertNotNull(SetupInput(careerStart = "19:00", careerEnd = "19:00").validate())
        assertNull(SetupInput(workStart = "22:00", workEnd = "06:00").validate())
        assertNull(SetupInput().validate())
    }
}
