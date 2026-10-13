package nl.medicatietracker

import nl.medicatietracker.data.Celebration
import nl.medicatietracker.data.History
import nl.medicatietracker.data.ItemType
import nl.medicatietracker.data.LogEntry
import nl.medicatietracker.data.LogStatus
import nl.medicatietracker.data.StreakInfo
import nl.medicatietracker.data.Streaks
import nl.medicatietracker.data.TrackedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Speelt na wat [nl.medicatietracker.data.TrackerRepository] doet bij elke keer afvinken. */
class StreakMilestoneTest {
    private val start = LocalDate.of(2026, 1, 1)

    private fun millis(date: LocalDate, hour: Int = 0) =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private val pill = TrackedItem(id = 1, name = "Pil", type = ItemType.MEDICATIE, minuteOfDay = 8 * 60, createdAt = millis(start))
    private val items = listOf(pill)

    private val logs = mutableMapOf<Long, LogEntry>()
    private var last: Celebration? = null
    private val fired = mutableListOf<Int>()

    private fun day(n: Int): LocalDate = start.plusDays(n.toLong())

    /** Vinkt de pil af voor [date] terwijl het op het toestel [now] is, en viert zoals de app. */
    private fun log(date: LocalDate, status: LogStatus = LogStatus.GEDAAN, now: LocalDateTime = date.atTime(9, 0)) {
        logs[date.toEpochDay()] = LogEntry(
            itemId = pill.id, epochDay = date.toEpochDay(), status = status, loggedAt = millis(date, 9),
            itemName = pill.name, itemType = pill.type, scheduledMinute = pill.minuteOfDay,
        )
        val streak = streakAt(now)
        Streaks.milestoneToCelebrate(streak, last)?.let {
            last = Celebration(it, now.toLocalDate().toEpochDay())
            fired += it
        }
    }

    private fun streakAt(now: LocalDateTime): StreakInfo =
        Streaks.compute(History.build(items, logs.values.toList(), start, now.toLocalDate(), now))

    private fun logDays(range: IntRange) = range.forEach { log(day(it)) }

    @Test
    fun weekMonthAndThreeMonthsFireExactlyOnceEach() {
        logDays(0 until 100)
        val expected = (1..14).map { it * 7 } + listOf(30, 90)
        assertEquals(expected.sorted(), fired)
        listOf(7, 30, 90).forEach { assertEquals("$it", 1, fired.count { m -> m == it }) }
        assertEquals("Een week compleet!", Streaks.title(7))
        assertEquals("3 maanden compleet!", Streaks.title(90))
    }

    @Test
    fun notBeforeTheSeventhCompleteDay() {
        logDays(0 until 6)
        assertEquals(emptyList<Int>(), fired)
        log(day(6))
        assertEquals(listOf(7), fired)
    }

    @Test
    fun undoAndRedoDoesNotFireAgain() {
        logDays(0 until 7)
        // Ongedaan maken (log weg) en opnieuw afvinken, en ook nog eens als overgeslagen en weer gedaan.
        logs.remove(day(6).toEpochDay())
        log(day(6))
        log(day(6), LogStatus.OVERGESLAGEN)
        log(day(6))
        assertEquals(listOf(7), fired)
    }

    @Test
    fun missedDayStartsANewStreakThatCelebratesAgain() {
        logDays(0 until 10)
        // Dag 10 vergeten: de reeks begint op dag 11 opnieuw.
        logDays(11 until 18)
        assertEquals(listOf(7, 7), fired)
        assertEquals(7, streakAt(day(17).atTime(9, 0)).current)
    }

    @Test
    fun skippedMedicationBreaksTheStreakLikeAMissedDay() {
        logDays(0 until 5)
        log(day(5), LogStatus.OVERGESLAGEN)
        logDays(6 until 12)
        assertEquals(emptyList<Int>(), fired)
        log(day(12))
        assertEquals(listOf(7), fired)
    }

    @Test
    fun editingAPastDayDoesNotRepeatAMilestone() {
        logDays(0 until 20)
        assertEquals(listOf(7, 14), fired)
        // Achteraf dag 5 als overgeslagen markeren: de reeks is nu 14 dagen (6 t/m 19).
        log(day(5), LogStatus.OVERGESLAGEN, now = day(19).atTime(21, 0))
        assertEquals(14, streakAt(day(19).atTime(21, 0)).current)
        // En weer terugzetten.
        log(day(5), now = day(19).atTime(21, 5))
        assertEquals(listOf(7, 14), fired)
        logDays(20 until 21)
        assertEquals(listOf(7, 14, 21), fired)
    }

    @Test
    fun loggingYesterdayAfterMidnightStillCounts() {
        logDays(0 until 6)
        // Dag 6 pas na middernacht afgevinkt; vandaag (dag 7) staat nog open.
        log(day(6), now = day(7).atTime(0, 30))
        assertEquals(listOf(7), fired)
        log(day(7))
        assertEquals(listOf(7), fired)
    }

    @Test
    fun missedMilestoneIsCaughtUpOnce() {
        // Bijv. omdat het laatste open item van dag 6 verwijderd werd in plaats van afgevinkt.
        logDays(0 until 6)
        logs[day(6).toEpochDay()] = logs.getValue(day(5).toEpochDay()).copy(epochDay = day(6).toEpochDay())
        log(day(7))
        assertEquals(listOf(7), fired)
        logDays(8 until 13)
        assertEquals(listOf(7), fired)
    }

    @Test
    fun timezoneShiftBackADayDoesNotFireAgain() {
        logDays(0 until 8)
        assertEquals(listOf(7), fired)
        // Na een vlucht naar het westen is het op het toestel weer dag 6: de log van dag 7 valt even weg.
        val west = day(6).atTime(22, 0)
        assertEquals(7, streakAt(west).current)
        log(day(6), now = west)
        // De volgende dagen in de nieuwe tijdzone telt alles gewoon door.
        logDays(8 until 13)
        assertEquals(listOf(7), fired)
        log(day(13))
        assertEquals(listOf(7, 14), fired)
    }

    @Test
    fun timezoneShiftForwardKeepsTheStreak() {
        logDays(0 until 6)
        // Op dag 6 vroeg afgevinkt, daarna naar het oosten gevlogen: het is al dag 7, 06:00.
        // Dag 7 staat nog open en breekt de reeks niet.
        log(day(6), now = day(7).atTime(6, 0))
        assertEquals(listOf(7), fired)
        assertEquals(7, streakAt(day(7).atTime(6, 0)).current)
    }

    @Test
    fun nothingToCelebrateWithoutAStreak() {
        assertNull(Streaks.milestoneToCelebrate(StreakInfo(current = 0, currentStart = null, best = 30), null))
        assertNull(Streaks.milestoneToCelebrate(StreakInfo(current = 6, currentStart = start, best = 6), null))
    }
}
