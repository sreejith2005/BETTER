package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class SchedulingTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val boundary = LocalTime.of(4, 0)
    private val anchor = LocalDate.of(2026, 10, 1)
    private fun preview(r: ScheduleRule, from: LocalDate = r.anchor, count: Int = 10) = ScheduleEngine.preview(r, from, zone, boundary, count)

    @Test fun at06SequenceUsesPreviousPlannedDates() {
        val r = ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT, anchor, offsets = DateStep.parseSequence("+3d,+2d,+1d"), tail = DateStep(2))
        assertEquals(listOf(1,4,6,7,9,11,13,15,17,19), preview(r).map { it.calendarDate.dayOfMonth })
        assertEquals(preview(r), preview(r))
    }
    @Test fun at08FiniteSequenceEndsAndAnchorOnlyIsValid() {
        val r = ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT, anchor, offsets = DateStep.parseSequence("3d,2d,1d"))
        assertEquals(listOf(1,4,6,7), preview(r).map { it.calendarDate.dayOfMonth })
        assertEquals(1, preview(r.copy(offsets = emptyList())).size)
        for (invalid in listOf("0d", "-1d", "3", "x", "1.5d", "10001d")) assertTrue(invalid, runCatching { DateStep.parse(invalid) }.isFailure)
    }
    @Test fun at05DailyWeeklyAndFarFutureAreBounded() {
        val daily = ScheduleRule(RuleType.DAILY, anchor)
        assertEquals(31, preview(daily, count = 31).map { it.calendarDate }.distinct().size)
        val weekly = daily.copy(type = RuleType.WEEKLY, step = DateStep(1, DateUnit.WEEKS), weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        assertEquals(listOf(5,7,12,14,19,21,26,28), preview(weekly, count = 8).map { it.calendarDate.dayOfMonth })
        assertTrue(preview(daily.copy(anchor = LocalDate.of(1900,1,1)), LocalDate.of(9000,1,1)).first().calendarDate.year == 9000)
        assertEquals(45, ScheduleEngine.horizon(daily, anchor, zone, boundary).size)
    }
    @Test fun weeklyCountAndAnchorPartialWeekAreCorrect() {
        val r = ScheduleRule(RuleType.WEEKLY, anchor, step = DateStep(2, DateUnit.WEEKS), weekdays = setOf(DayOfWeek.MONDAY,DayOfWeek.FRIDAY), count = 3)
        assertEquals(listOf(2,12,16), preview(r).map { it.calendarDate.dayOfMonth })
        assertEquals(listOf(12,16), preview(r, anchor.plusDays(8)).map { it.calendarDate.dayOfMonth })
        assertTrue(preview(r, anchor.plusMonths(2)).isEmpty())
    }
    @Test fun at07MonthsClampThenReturnToOriginalAnchorDay() {
        val r = ScheduleRule(RuleType.INTERVAL_CALENDAR, LocalDate.of(2026,1,31), step = DateStep(1,DateUnit.MONTHS))
        assertEquals(listOf("2026-01-31","2026-02-28","2026-03-31"), preview(r, count = 3).map { it.calendarDate.toString() })
        assertEquals(LocalDate.of(2028,2,29), DateStep.parse("1mo").addTo(LocalDate.of(2028,1,31)))
        assertEquals(listOf("2026-02-28","2026-03-31"), preview(r, LocalDate.of(2026,2,1), 2).map { it.calendarDate.toString() })
    }
    @Test fun monthlySequenceDoesNotDriftAndTailFastForwards() {
        val r = ScheduleRule(RuleType.SEQUENCE_THEN_REPEAT, LocalDate.of(2026,1,31), offsets = DateStep.parseSequence("1mo,1mo"), tail = DateStep(1, DateUnit.MONTHS))
        assertEquals(listOf(31,28,31,30,31), preview(r, count = 5).map { it.calendarDate.dayOfMonth })
        assertEquals(LocalDate.of(2040,1,31), preview(r, LocalDate.of(2040,1,1)).first().calendarDate)
    }
    @Test fun at09ExactWindowAnytimeBoundaries() {
        val exact = preview(ScheduleRule(RuleType.ONE_OFF, anchor, LocalTime.of(2,0))).single()
        assertEquals(anchor.minusDays(1), exact.operationalDate)
        assertEquals(OccurrenceState.PENDING, derivedState(OccurrenceState.PENDING, exact, exact.start.minusMillis(1)))
        assertEquals(OccurrenceState.DUE, derivedState(OccurrenceState.PENDING, exact, exact.start))
        assertEquals(OccurrenceState.OVERDUE, derivedState(OccurrenceState.PENDING, exact, exact.start.plusMillis(1)))
        assertEquals(OccurrenceState.MISSED, derivedState(OccurrenceState.PENDING, exact, exact.closesAt))
        val anytime = preview(ScheduleRule(RuleType.ONE_OFF, anchor, dueKind = DueKind.ANYTIME)).single()
        assertEquals(anchor, anytime.operationalDate)
        assertEquals(OccurrenceState.DUE, derivedState(OccurrenceState.PENDING, anytime, anytime.end.minusMillis(1)))
        assertEquals(OccurrenceState.MISSED, derivedState(OccurrenceState.PENDING, anytime, anytime.end))
        val window = preview(ScheduleRule(RuleType.ONE_OFF, anchor, LocalTime.of(23,0), DueKind.WINDOW, LocalTime.of(6,0))).single()
        assertEquals(window.end, window.closesAt)
        assertEquals(OccurrenceState.DUE, derivedState(OccurrenceState.PENDING, window, window.end.minusMillis(1)))
        assertEquals(OccurrenceState.COMPLETED, derivedState(OccurrenceState.COMPLETED, window, window.end.plusSeconds(60)))
    }
    @Test fun at10GapChoosesFirstValidInstantAndOverlapHonorsChoice() {
        val ny = ZoneId.of("America/New_York")
        val gap = resolveLocal(LocalDateTime.of(2026,3,8,2,30), ny, OverlapChoice.EARLIER)
        assertEquals(LocalDateTime.of(2026,3,8,3,0), gap.local)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z"), gap.instant)
        assertNotNull(gap.note)
        val local = LocalDateTime.of(2026,11,1,1,30)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), resolveLocal(local,ny,OverlapChoice.EARLIER).instant)
        assertEquals(Instant.parse("2026-11-01T06:30:00Z"), resolveLocal(local,ny,OverlapChoice.LATER).instant)
    }
    @Test fun anchoredAndAbsoluteIgnoreDeviceZone() {
        val r = ScheduleRule(RuleType.ONE_OFF, anchor, zoneMode = ZoneMode.ANCHORED_ZONE, zoneId = "Europe/London")
        assertEquals(preview(r), ScheduleEngine.preview(r,anchor,ZoneId.of("America/New_York"),boundary))
        val fixed = r.copy(zoneMode = ZoneMode.ABSOLUTE_INSTANT, absoluteInstant = Instant.parse("2026-10-01T12:00:00Z"))
        assertEquals(fixed.absoluteInstant, preview(fixed).single().start)
    }
    @Test fun at12CompletionUsesActualTimestampAndSkipRequiresChoice() {
        val daily = ScheduleRule(RuleType.DAILY, LocalDate.of(2026,10,5), step = DateStep(2))
        val actual = Instant.parse("2026-10-06T16:45:00Z")
        assertEquals(LocalDate.of(2026,10,7), ScheduleEngine.preview(daily,daily.anchor,zone,boundary,completedAt=actual)[1].calendarDate)
        val completion = daily.copy(type=RuleType.INTERVAL_COMPLETION)
        val next = ScheduleEngine.preview(completion, daily.anchor, zone,boundary,completedAt=actual).single()
        assertEquals(Instant.parse("2026-10-08T16:45:00Z"),next.start)
        assertTrue(ScheduleEngine.preview(completion,daily.anchor,zone,boundary,completionStopped=true).isEmpty())
        assertNull(completionIntervalAfterSkip(SkipRestart.NEEDS_CHOICE,actual,null,DateStep(2),zone))
        assertEquals(next.start,completionIntervalAfterSkip(SkipRestart.RESTART_FROM_SKIP,actual,null,DateStep(2),zone))
    }
    @Test fun at11EntireSeriesFallsForwardWhenHistoryExists() {
        assertEquals(EditScope.ENTIRE_SERIES,resolveEditScope(EditScope.ENTIRE_SERIES,false).effectiveScope)
        assertEquals(EditScope.THIS_AND_FUTURE,resolveEditScope(EditScope.ENTIRE_SERIES,true).effectiveScope)
        assertNotNull(resolveEditScope(EditScope.ENTIRE_SERIES,true).explanation)
    }
    @Test fun endAndCountShortenWithoutDuplicates() {
        val r = ScheduleRule(RuleType.DAILY,anchor,count=3,endDate=anchor.plusDays(1))
        assertEquals(2,preview(r).size)
        assertTrue(preview(r,anchor.plusDays(3)).isEmpty())
        assertTrue(runCatching { preview(r.copy(count=0)) }.isFailure)
        assertTrue(runCatching { preview(r.copy(type=RuleType.WEEKLY,step=DateStep(1,DateUnit.WEEKS),weekdays=emptySet())) }.isFailure)
    }
}
