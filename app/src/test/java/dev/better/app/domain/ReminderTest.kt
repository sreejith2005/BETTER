package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderTest {
    @Test fun at13PoliciesHaveDefinedOffsetsAndCaps() {
        assertEquals(emptyList<Int>(), ReminderPolicy.offsets("NONE", ""))
        assertEquals(listOf(0), ReminderPolicy.offsets("LIGHT", ""))
        assertEquals(listOf(0,15), ReminderPolicy.offsets("STANDARD", ""))
        assertEquals(listOf(0,30,60,90), ReminderPolicy.offsets("PERSISTENT", ""))
        assertEquals(listOf(0,2,5,10,30), ReminderPolicy.offsets("ESCALATING", "0,2,5,10,30"))
        for (invalid in listOf("", "-1", "0,0", "3,2", "0,1,2,3,4,5"))
            assertTrue(runCatching { ReminderPolicy.offsets("ESCALATING", invalid) }.isFailure)
    }
    @Test fun quietHoursCoalesceMarkDelayAndExpireAtDayClose() {
        val due=Instant.parse("2026-10-01T02:00:00Z").toEpochMilli()
        val steps=ReminderPolicy.plan(due,due+8*3600000,"PERSISTENT","",ZoneOffset.UTC,LocalTime.of(22,30),LocalTime.of(7,0))
        assertEquals(1,steps.size); assertEquals("0,1,2,3",steps.single().steps)
        assertTrue(steps.single().delayed)
        assertEquals(Instant.parse("2026-10-01T07:00:00Z").toEpochMilli(),steps.single().plannedAt)
        assertTrue(ReminderPolicy.plan(due,due+2*3600000,"STANDARD","",ZoneOffset.UTC,LocalTime.of(22,30),LocalTime.of(7,0)).isEmpty())
        assertEquals(4,ReminderPolicy.plan(due,due+8*3600000,"PERSISTENT","",ZoneOffset.UTC,LocalTime.of(22,30),LocalTime.of(7,0),true).size)
    }
    @Test fun quietHoursUseCalendarTimeAcrossDstAndSupportDaytimeAndOff() {
        val at=Instant.parse("2026-03-08T06:00:00Z")
        assertEquals(Instant.parse("2026-03-08T11:00:00Z"),ReminderPolicy.permitted(at,ZoneId.of("America/New_York"),LocalTime.of(22,0),LocalTime.of(7,0)))
        assertEquals(at,ReminderPolicy.permitted(at,ZoneOffset.UTC,LocalTime.NOON,LocalTime.NOON))
        assertEquals(Instant.parse("2026-03-08T08:00:00Z"),ReminderPolicy.permitted(at,ZoneOffset.UTC,LocalTime.of(5,0),LocalTime.of(8,0)))
    }
}
