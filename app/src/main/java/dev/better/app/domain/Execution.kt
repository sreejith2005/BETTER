package dev.better.app.domain

data class OccupiedInterval(val id: String, val title: String, val start: Long, val end: Long, val fixed: Boolean)
data class PlanConflict(val first: OccupiedInterval, val second: OccupiedInterval)

fun conflicts(intervals: List<OccupiedInterval>): List<PlanConflict> {
    val sorted = intervals.filter { it.end > it.start }.sortedBy { it.start }
    return buildList {
        sorted.forEachIndexed { index, a ->
            for (b in sorted.drop(index + 1)) {
                if (b.start >= a.end) break
                if (a.id != b.id) add(PlanConflict(a, b))
            }
        }
    }
}

data class ReviewCounts(val planned: Int, val completed: Int, val late: Int, val skipped: Int,
    val missed: Int, val suppressed: Int, val cancelled: Int, val open: Int)
fun reviewCounts(states: List<Pair<String, Boolean>>) = ReviewCounts(
    states.size, states.count { it.first == "COMPLETED" && !it.second },
    states.count { it.first == "COMPLETED" && it.second }, states.count { it.first == "SKIPPED" },
    states.count { it.first == "MISSED" }, states.count { it.first == "SKIPPED_BY_OVERRIDE" },
    states.count { it.first == "CANCELLED" }, states.count { it.first in listOf("PENDING", "DUE", "OVERDUE") })
