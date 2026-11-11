package nl.medicatietracker.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/**
 * De takenreeks, in kalenderweken en kalendermaanden.
 *
 * Een week (maandag t/m zondag) of maand telt als **compleet** als alle taken die erin stonden gedaan
 * zijn; dagen zonder taken doen niet mee. Staat er alleen op dinsdag en donderdag een taak, dan is de
 * week voltooid zodra die twee gedaan zijn. Een week of maand zonder taken telt niet mee maar breekt de reeks ook niet, net als een week die
 * nog loopt. Eén gemiste taak breekt zowel de week- als de maandreeks.
 */
data class TaskStreak(
    /** Complete weken op rij. */
    val weeks: Int,
    /** Complete kalendermaanden op rij. */
    val months: Int,
    val bestWeeks: Int,
    val bestMonths: Int,
    /** Dag na de laatst gemiste taak (of de eerste takendag): hier begint de huidige reeks. */
    val runStart: LocalDate?,
    /** Gedaan en totaal in de week van vandaag, voor de voortgang naar de volgende week. */
    val weekDone: Int = 0,
    val weekTotal: Int = 0,
) {
    /**
     * Dezelfde reeks uitgedrukt in de dagen die [StarTier] gebruikt, zodat de takenmedailles dezelfde
     * stappen volgen: brons voor een week, zilver voor een maand, goud voor 3 maanden, blauw voor een jaar.
     */
    fun asStarStreak(): StreakInfo = StreakInfo(starDays(weeks, months), runStart, starDays(bestWeeks, bestMonths))

    companion object {
        val NONE = TaskStreak(0, 0, 0, 0, null)

        fun starDays(weeks: Int, months: Int): Int = when {
            months >= 12 -> 365
            months >= 3 -> 90
            months >= 1 -> 30
            weeks >= 1 -> 7
            else -> 0
        }
    }
}

/** Een badge voor de takenreeks: zoveel complete weken of maanden op rij. */
data class TaskBadge(val label: String, val tier: StarTier, val weeks: Int = 0, val months: Int = 0) {
    fun earned(streak: TaskStreak): Boolean =
        if (months > 0) streak.bestMonths >= months else streak.bestWeeks >= weeks

    /** Voortgang van de huidige reeks, bijvoorbeeld "1/3". */
    fun progress(streak: TaskStreak): String =
        if (months > 0) "${minOf(streak.months, months)}/$months" else "${minOf(streak.weeks, weeks)}/$weeks"
}

object TaskStreaks {
    /** Zelfde stappen als de medicatiebadges, maar dan in kalenderweken en -maanden. */
    val BADGES = listOf(
        TaskBadge("Week", StarTier.BRONS, weeks = 1),
        TaskBadge("Maand", StarTier.ZILVER, months = 1),
        TaskBadge("3 mnd", StarTier.GOUD, months = 3),
        TaskBadge("½ jaar", StarTier.GOUD, months = 6),
        TaskBadge("Jaar", StarTier.BLAUW, months = 12),
    )

    private enum class Result { COMPLETE, BROKEN, PENDING, EMPTY }

    private fun HistoryDay.result(): Result = when {
        rows.isEmpty() -> Result.EMPTY
        rows.any { it.breaksStreak } -> Result.BROKEN
        rows.all { it.countsForStreak } -> Result.COMPLETE
        else -> Result.PENDING
    }

    private fun periodResult(days: List<HistoryDay>): Result {
        val results = days.map { it.result() }
        return when {
            Result.BROKEN in results -> Result.BROKEN
            Result.PENDING in results -> Result.PENDING
            results.all { it == Result.EMPTY } -> Result.EMPTY
            else -> Result.COMPLETE
        }
    }

    private data class Run(val current: Int, val best: Int)

    /** Telt complete periodes op rij; verwacht de periodes van oud naar nieuw. */
    private fun run(results: List<Result>): Run {
        var run = 0
        var best = 0
        for (r in results) {
            when (r) {
                Result.COMPLETE -> {
                    run++
                    best = maxOf(best, run)
                }
                Result.BROKEN -> run = 0
                Result.PENDING, Result.EMPTY -> Unit
            }
        }
        return Run(run, best)
    }

    /**
     * Rekent de reeks uit over [days] (in elke volgorde) die moeten doorlopen tot het eind van de huidige
     * week en maand. [firstDay] is de eerste dag met taken.
     */
    fun compute(days: List<HistoryDay>, firstDay: LocalDate, today: LocalDate? = null): TaskStreak {
        if (days.isEmpty()) return TaskStreak.NONE
        val sorted = days.sortedBy { it.date }
        val weeks = sorted.groupBy { it.date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
        val months = sorted.groupBy { YearMonth.from(it.date) }
        val weekRun = run(weeks.values.map { periodResult(it) })
        val monthRun = run(months.values.map { periodResult(it) })
        val lastBroken = sorted.lastOrNull { it.result() == Result.BROKEN }?.date
        val runStart = lastBroken?.plusDays(1) ?: firstDay

        val currentWeek = today?.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekRows = currentWeek?.let { weeks[it] }.orEmpty().flatMap { it.rows }
        return TaskStreak(
            weeks = weekRun.current,
            months = monthRun.current,
            bestWeeks = weekRun.best,
            bestMonths = monthRun.best,
            runStart = runStart,
            weekDone = weekRows.count { it.countsForStreak },
            weekTotal = weekRows.size,
        )
    }

    /**
     * Wat er nu gevierd moet worden, of null. [celebrated] is wat er in deze reeks al gevierd is
     * (weken, maanden); begint er een nieuwe reeks, dan telt alles weer opnieuw.
     */
    fun milestoneToCelebrate(streak: TaskStreak, celebratedWeeks: Int, celebratedMonths: Int): TaskMilestone? = when {
        streak.months > celebratedMonths -> TaskMilestone(streak.weeks, streak.months, monthTitle(streak.months), monthMessage(streak.months))
        streak.weeks > celebratedWeeks -> TaskMilestone(streak.weeks, streak.months, weekTitle(streak.weeks), weekMessage(streak.weeks))
        else -> null
    }

    fun weekTitle(weeks: Int): String = if (weeks == 1) "Een week compleet!" else "$weeks weken op rij compleet!"

    fun monthTitle(months: Int): String = when (months) {
        1 -> "Een maand compleet!"
        6 -> "Een half jaar compleet!"
        12 -> "Een jaar compleet!"
        else -> "$months maanden op rij compleet!"
    }

    private fun weekMessage(weeks: Int): String =
        if (weeks == 1) "Je hebt deze week, van maandag tot zondag, al je taken gedaan." else "$weeks weken op rij al je taken gedaan. Ga zo door!"

    private fun monthMessage(months: Int): String =
        if (months == 1) "Je hebt een hele maand al je taken gedaan." else "$months maanden op rij al je taken gedaan. Ga zo door!"
}

data class TaskMilestone(val weeks: Int, val months: Int, val title: String, val message: String)
