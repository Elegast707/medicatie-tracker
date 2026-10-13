package nl.medicatietracker.data

import java.time.LocalDate

data class StreakInfo(
    /** Aantal complete dagen op rij tot en met vandaag (of gisteren als vandaag nog loopt). */
    val current: Int,
    /** Eerste dag van de huidige reeks, of null als er geen reeks loopt. */
    val currentStart: LocalDate?,
    /** Langste reeks ooit. */
    val best: Int,
)

data class Badge(val days: Int, val label: String)

object Streaks {
    val BADGES = listOf(
        Badge(7, "Week"),
        Badge(30, "Maand"),
        Badge(90, "3 mnd"),
        Badge(180, "½ jaar"),
        Badge(365, "Jaar"),
    )

    private val SPECIAL_MILESTONES = BADGES.map { it.days }.toSet()

    private enum class DayResult { COMPLETE, BROKEN, PENDING, EMPTY }

    private fun HistoryDay.result(): DayResult = when {
        rows.isEmpty() -> DayResult.EMPTY
        rows.any { it.breaksStreak } -> DayResult.BROKEN
        rows.all { it.countsForStreak } -> DayResult.COMPLETE
        // Alleen vandaag kan nog openstaande items hebben: telt niet mee, maar breekt ook niet.
        else -> DayResult.PENDING
    }

    /** Verwacht dagen met de nieuwste eerst, zoals [History.build] ze teruggeeft. */
    fun compute(days: List<HistoryDay>): StreakInfo {
        var current = 0
        var start: LocalDate? = null
        for (day in days) {
            when (day.result()) {
                DayResult.COMPLETE -> {
                    current++
                    start = day.date
                }
                DayResult.BROKEN -> break
                DayResult.PENDING, DayResult.EMPTY -> Unit
            }
        }

        var best = 0
        var run = 0
        for (day in days.asReversed()) {
            when (day.result()) {
                DayResult.COMPLETE -> {
                    run++
                    best = maxOf(best, run)
                }
                DayResult.BROKEN -> run = 0
                DayResult.PENDING, DayResult.EMPTY -> Unit
            }
        }
        return StreakInfo(current, start, best)
    }

    /** Mijlpalen: elke week (7, 14, 21, ...) plus 30, 90, 180 en 365 dagen. */
    fun isMilestone(days: Int): Boolean = days > 0 && (days % 7 == 0 || days in SPECIAL_MILESTONES)

    fun highestMilestoneUpTo(days: Int): Int? = (days downTo 1).firstOrNull { isMilestone(it) }

    fun title(milestone: Int): String = when (milestone) {
        7 -> "Een week compleet!"
        30 -> "Een maand compleet!"
        90 -> "3 maanden compleet!"
        180 -> "Een half jaar compleet!"
        365 -> "Een jaar compleet!"
        else -> "$milestone dagen op rij!"
    }

    fun message(milestone: Int): String = "$milestone dagen op rij alles afgevinkt. Ga zo door!"
}
