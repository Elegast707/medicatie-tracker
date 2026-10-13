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

/** De laatst gevierde mijlpaal en de dag (epochDay) waarop die gevierd is. */
data class Celebration(val milestone: Int, val epochDay: Long)

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

    /**
     * De mijlpaal die nu gevierd moet worden, of null. Elke mijlpaal wordt per reeks maar één keer
     * gevierd, ook als je iets ongedaan maakt en opnieuw afvinkt of een eerdere dag aanpast.
     */
    fun milestoneToCelebrate(streak: StreakInfo, last: Celebration?): Int? {
        val start = streak.currentStart ?: return null
        val milestone = highestMilestoneUpTo(streak.current) ?: return null
        // Een reeks die al liep toen er voor het laatst gevierd werd, is nog dezelfde reeks: ook als
        // hij achteraf naar achteren is verlengd, of ingekort doordat een eerdere dag is aangepast.
        val sameStreak = last != null && start.toEpochDay() <= last.epochDay
        return if (sameStreak && milestone <= last!!.milestone) null else milestone
    }

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
