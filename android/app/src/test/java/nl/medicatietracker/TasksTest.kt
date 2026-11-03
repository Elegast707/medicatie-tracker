package nl.medicatietracker

import nl.medicatietracker.data.StarTier
import nl.medicatietracker.data.StarTrack
import nl.medicatietracker.data.Stars
import nl.medicatietracker.data.TaskItem
import nl.medicatietracker.data.TaskLog
import nl.medicatietracker.data.TaskRecurrence
import nl.medicatietracker.data.TaskRules
import nl.medicatietracker.data.Weekdays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TasksTest {
    /** Donderdag 1 januari 2026. */
    private val start = LocalDate.of(2026, 1, 1)

    private fun millis(date: LocalDate) = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        recurrence: TaskRecurrence,
        startDay: LocalDate = start,
        weekdays: Int = 0,
        intervalDays: Int = 2,
        deletedOn: LocalDate? = null,
    ) = TaskItem(
        id = id,
        name = "Taak $id",
        recurrence = recurrence,
        startDay = startDay.toEpochDay(),
        weekdays = weekdays,
        intervalDays = intervalDays,
        createdAt = millis(start),
        deletedAt = deletedOn?.let { millis(it) + 3_600_000 },
    )

    private fun done(id: Long, vararg dates: LocalDate) = dates.map { TaskLog(id, it.toEpochDay(), millis(it), "Taak $id") }

    @Test
    fun oneOffTaskOnlyOnItsDay() {
        val t = task(1, TaskRecurrence.EENMALIG, startDay = start.plusDays(3))
        assertFalse(t.occursOn(start))
        assertTrue(t.occursOn(start.plusDays(3)))
        assertFalse(t.occursOn(start.plusDays(4)))
    }

    @Test
    fun weekdayTaskFollowsChosenDays() {
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.THURSDAY)
        val t = task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask)
        assertTrue(t.occursOn(start)) // donderdag
        assertFalse(t.occursOn(start.plusDays(1))) // vrijdag
        assertTrue(t.occursOn(start.plusDays(4))) // maandag
    }

    @Test
    fun intervalTaskCountsFromStart() {
        val t = task(1, TaskRecurrence.INTERVAL, intervalDays = 3)
        assertTrue(t.occursOn(start))
        assertFalse(t.occursOn(start.plusDays(1)))
        assertTrue(t.occursOn(start.plusDays(3)))
        assertTrue(t.occursOn(start.plusDays(6)))
    }

    @Test
    fun taskDoesNotCountBeforeCreationOrAfterDeletion() {
        val t = task(1, TaskRecurrence.DAGELIJKS, startDay = start.minusDays(10), deletedOn = start.plusDays(2))
        assertFalse(t.occursOn(start.minusDays(1)))
        assertTrue(t.occursOn(start.plusDays(1)))
        assertFalse(t.occursOn(start.plusDays(2)))
    }

    @Test
    fun streakCountsDaysWithAllTasksDoneAndSkipsEmptyDays() {
        // Maandag en donderdag een taak; daartussen dagen zonder taken.
        val mask = Weekdays.bit(DayOfWeek.MONDAY) or Weekdays.bit(DayOfWeek.THURSDAY)
        val tasks = listOf(task(1, TaskRecurrence.WEEKDAGEN, weekdays = mask))
        val logs = done(1, start, start.plusDays(4), start.plusDays(7))
        val now = start.plusDays(8).atTime(LocalTime.NOON) // vrijdag, geen taken
        val streak = TaskRules.streak(tasks, logs, now)
        assertEquals(3, streak.current)
        assertEquals(start, streak.currentStart)
    }

    @Test
    fun missedTaskBreaksStreakButTodayStaysOpen() {
        val tasks = listOf(task(1, TaskRecurrence.DAGELIJKS), task(2, TaskRecurrence.EENMALIG, startDay = start.plusDays(1)))
        // Dag 2 is taak 2 niet gedaan: die dag breekt de reeks.
        val logs = done(1, start, start.plusDays(1), start.plusDays(2))
        val now = start.plusDays(3).atTime(LocalTime.NOON) // vandaag nog niets gedaan
        val streak = TaskRules.streak(tasks, logs, now)
        assertEquals(1, streak.current)
        assertEquals(start.plusDays(2), streak.currentStart)
        assertEquals(1, streak.best)
    }

    @Test
    fun noTasksMeansNoStreak() {
        val streak = TaskRules.streak(emptyList(), emptyList(), start.atTime(LocalTime.NOON))
        assertEquals(0, streak.current)
        assertNull(streak.currentStart)
    }

    @Test
    fun taskStarsAreKeptOnTheirOwnTrack() {
        val streak = TaskRules.streak(
            listOf(task(1, TaskRecurrence.DAGELIJKS)),
            done(1, *(0L..6L).map { start.plusDays(it) }.toTypedArray()),
            start.plusDays(6).atTime(LocalTime.NOON),
        )
        assertEquals(7, streak.current)
        val changes = Stars.update(streak, null, start.plusDays(6), StarTrack.TAKEN)
        assertEquals(1, changes.size)
        assertEquals(StarTier.BRONS, changes.single().tier)
        assertEquals(StarTrack.TAKEN, changes.single().track)
    }

    @Test
    fun nextOccurrenceFindsTheNextDay() {
        val t = task(1, TaskRecurrence.INTERVAL, intervalDays = 5)
        assertEquals(start.plusDays(5), TaskRules.nextOccurrence(t, start.plusDays(1)))
        assertNull(TaskRules.nextOccurrence(task(2, TaskRecurrence.EENMALIG), start.plusDays(1)))
    }
}
