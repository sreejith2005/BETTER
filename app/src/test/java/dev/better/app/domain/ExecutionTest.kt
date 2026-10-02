package dev.better.app.domain

import org.junit.Assert.*
import org.junit.Test

class ExecutionTest {
    @Test fun overlapUsesHalfOpenIntervalsWithoutMovingFixedEvents() {
        val a=OccupiedInterval("a","Fixed",100,200,true)
        val b=OccupiedInterval("b","Flexible",150,250,false)
        val c=OccupiedInterval("c","Adjacent",250,300,true)
        val zero=OccupiedInterval("d","No occupied duration",160,160,false)
        val result=conflicts(listOf(c,b,zero,a))
        assertEquals(listOf(PlanConflict(a,b)),result)
        assertEquals(100L,a.start)
        assertTrue(conflicts(listOf(a,a)).isEmpty())
    }
    @Test fun reviewSeparatesLateSuppressedCancelledAndOpen() {
        val c=reviewCounts(listOf("COMPLETED" to false,"COMPLETED" to true,"SKIPPED" to false,
            "MISSED" to false,"SKIPPED_BY_OVERRIDE" to false,"CANCELLED" to false,"DUE" to false,"PENDING" to false))
        assertEquals(ReviewCounts(8,1,1,1,1,1,1,2),c)
    }
}
