package nl.medicatietracker

import nl.medicatietracker.data.StarEntry
import nl.medicatietracker.data.StarReason
import nl.medicatietracker.data.StarTier
import nl.medicatietracker.data.Stars
import nl.medicatietracker.data.StreakInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Speelt na wat [nl.medicatietracker.data.TrackerRepository.syncStars] met de sterrentabel doet. */
class StarsTest {
    private val start = LocalDate.of(2026, 1, 1)
    private val table = mutableListOf<StarEntry>()
    private var nextId = 1L

    private fun held() = table.lastOrNull { it.collectedOn == null }
    private fun collected() = table.filter { it.collectedOn != null }

    /** Streak van [days] dagen die begon op [streakStart], bekeken op [today]. */
    private fun sync(days: Int, today: LocalDate, streakStart: LocalDate? = today.minusDays(days - 1L)) {
        val streak = StreakInfo(days, if (days == 0) null else streakStart, days)
        for (change in Stars.update(streak, held(), today)) {
            val index = table.indexOfFirst { it.id == change.id && change.id != 0L }
            if (index >= 0) table[index] = change else table += change.copy(id = nextId++)
        }
    }

    @Test
    fun tiersFollowTheStreakLength() {
        assertNull(StarTier.forDays(6))
        assertEquals(StarTier.BRONS, StarTier.forDays(7))
        assertEquals(StarTier.ZILVER, StarTier.forDays(30))
        assertEquals(StarTier.GOUD, StarTier.forDays(90))
        assertEquals(StarTier.BLAUW, StarTier.forDays(365))
        assertEquals(StarTier.BLAUW, StarTier.forDays(1000))
    }

    @Test
    fun eachStarIsCollectedWhenReplaced() {
        for (day in 1..400) sync(day, start.plusDays(day - 1L), start)
        assertEquals(StarTier.BLAUW, held()?.tier)
        assertEquals(listOf(StarTier.BRONS, StarTier.ZILVER, StarTier.GOUD), collected().map { it.tier })
        assertTrue(collected().all { it.reason == StarReason.VERVANGEN })
        assertEquals(start.plusDays(29).toEpochDay(), collected()[0].collectedOn)
    }

    @Test
    fun brokenStreakCollectsTheStarAndRemovesIt() {
        for (day in 1..10) sync(day, start.plusDays(day - 1L), start)
        assertEquals(StarTier.BRONS, held()?.tier)
        sync(0, start.plusDays(11))
        assertNull(held())
        assertEquals(StarReason.GEBROKEN, collected().single().reason)
        // Elke dag opnieuw bijwerken voegt niets toe.
        sync(0, start.plusDays(11))
        assertEquals(1, collected().size)
    }

    @Test
    fun newStreakAfterBreakEarnsANewStar() {
        for (day in 1..8) sync(day, start.plusDays(day - 1L), start)
        val restart = start.plusDays(10)
        for (day in 1..7) sync(day, restart.plusDays(day - 1L), restart)
        assertEquals(StarTier.BRONS, held()?.tier)
        assertEquals(restart.plusDays(6).toEpochDay(), held()?.earnedOn)
        assertEquals(1, collected().size)
    }

    @Test
    fun undoingTodayKeepsTheStar() {
        for (day in 1..7) sync(day, start.plusDays(day - 1L), start)
        // Vandaag weer open: reeks is 6 dagen, maar het is dezelfde reeks.
        sync(6, start.plusDays(6), start)
        assertEquals(StarTier.BRONS, held()?.tier)
        assertTrue(collected().isEmpty())
    }

    @Test
    fun skippedTiersAreAlsoCollected() {
        sync(40, start.plusDays(39), start)
        assertEquals(StarTier.ZILVER, held()?.tier)
        assertEquals(listOf(StarTier.BRONS), collected().map { it.tier })
    }
}
