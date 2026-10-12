package nl.medicatietracker.data

import java.time.LocalDate
import java.time.LocalDateTime

enum class RowStatus { GEDAAN, OVERGESLAGEN, GEMIST, TE_LAAT, OPEN }

data class HistoryRow(
    val itemId: Long,
    val name: String,
    val type: ItemType,
    val minuteOfDay: Int,
    val status: RowStatus,
    val loggedAt: Long?,
)

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>) {
    val done: Int get() = rows.count { it.status == RowStatus.GEDAAN }
    val handled: Int get() = rows.count { it.status == RowStatus.GEDAAN || it.status == RowStatus.OVERGESLAGEN }
}

data class ItemStats(val name: String, val type: ItemType, val done: Int, val skipped: Int, val missed: Int) {
    val total: Int get() = done + skipped + missed
    val percentage: Int get() = if (total == 0) 0 else Math.round(done * 100f / total)
}

data class Summary(val done: Int, val skipped: Int, val missed: Int, val streak: Int, val perItem: List<ItemStats>) {
    val total: Int get() = done + skipped + missed
    val percentage: Int get() = if (total == 0) 0 else Math.round(done * 100f / total)
}

object History {

    /** Bouwt per dag (nieuwste eerst) op wat er gepland stond en wat ermee gebeurd is. */
    fun build(
        items: List<TrackedItem>,
        logs: List<LogEntry>,
        from: LocalDate,
        to: LocalDate,
        now: LocalDateTime,
    ): List<HistoryDay> {
        val logsByDay = logs.groupBy { it.epochDay }
        val today = now.toLocalDate()
        val nowMinute = now.hour * 60 + now.minute
        val days = mutableListOf<HistoryDay>()
        var date = to
        while (!date.isBefore(from)) {
            val dayLogs = logsByDay[date.toEpochDay()].orEmpty().associateBy { it.itemId }
            val expected = items.filter { isExpected(it, date) }
            val expectedIds = expected.map { it.id }.toSet()

            fun statusFor(minute: Int, log: LogEntry?): RowStatus = when {
                log != null && log.status == LogStatus.GEDAAN -> RowStatus.GEDAAN
                log != null -> RowStatus.OVERGESLAGEN
                date.isBefore(today) -> RowStatus.GEMIST
                date == today && nowMinute >= minute -> RowStatus.TE_LAAT
                else -> RowStatus.OPEN
            }

            val rows = expected.map { item ->
                val log = dayLogs[item.id]
                HistoryRow(item.id, item.name, item.type, item.minuteOfDay, statusFor(item.minuteOfDay, log), log?.loggedAt)
            } + dayLogs.values.filter { it.itemId !in expectedIds }.map { log ->
                HistoryRow(log.itemId, log.itemName, log.itemType, log.scheduledMinute, statusFor(log.scheduledMinute, log), log.loggedAt)
            }
            days += HistoryDay(date, rows.sortedWith(compareBy({ it.minuteOfDay }, { it.name })))
            date = date.minusDays(1)
        }
        return days
    }

    private fun isExpected(item: TrackedItem, date: LocalDate): Boolean {
        if (!item.active) return false
        if (item.createdAt.toLocalDate().isAfter(date)) return false
        val deletedAt = item.deletedAt ?: return true
        return date.isBefore(deletedAt.toLocalDate())
    }

    /**
     * Samenvatting over de gegeven dagen. De reeks (streak) telt opeenvolgende dagen waarop alles
     * is afgehandeld; vandaag telt alleen mee als alles al gedaan is.
     */
    fun summarize(days: List<HistoryDay>, streakDays: List<HistoryDay> = days): Summary {
        val rows = days.flatMap { it.rows }
        val perItem = rows
            .filter { it.status in COUNTED }
            .groupBy { it.itemId }
            .map { (_, itemRows) ->
                val latest = itemRows.first()
                ItemStats(
                    name = latest.name,
                    type = latest.type,
                    done = itemRows.count { it.status == RowStatus.GEDAAN },
                    skipped = itemRows.count { it.status == RowStatus.OVERGESLAGEN },
                    missed = itemRows.count { it.status == RowStatus.GEMIST },
                )
            }
            .sortedBy { it.name }
        return Summary(
            done = rows.count { it.status == RowStatus.GEDAAN },
            skipped = rows.count { it.status == RowStatus.OVERGESLAGEN },
            missed = rows.count { it.status == RowStatus.GEMIST },
            streak = streak(streakDays),
            perItem = perItem,
        )
    }

    private val COUNTED = setOf(RowStatus.GEDAAN, RowStatus.OVERGESLAGEN, RowStatus.GEMIST)

    /** Verwacht dagen met de nieuwste eerst. */
    fun streak(days: List<HistoryDay>): Int {
        var streak = 0
        for (day in days) {
            if (day.rows.isEmpty()) continue
            val allHandled = day.handled == day.rows.size
            if (allHandled) {
                streak++
            } else if (day.rows.any { it.status == RowStatus.GEMIST }) {
                break
            }
            // Vandaag met nog openstaande items: niet meetellen, maar de reeks ook niet breken.
        }
        return streak
    }

    fun toCsv(days: List<HistoryDay>): String = buildString {
        append("Datum;Gepland;Naam;Soort;Status;Gelogd om\n")
        for (day in days.sortedBy { it.date }) {
            for (row in day.rows) {
                append(day.date).append(';')
                append(formatMinute(row.minuteOfDay)).append(';')
                append(csvField(row.name)).append(';')
                append(if (row.type == ItemType.MEDICATIE) "Medicatie" else "Taak").append(';')
                append(row.status.label(row.type)).append(';')
                append(row.loggedAt?.let { "${it.toLocalDate()} ${it.formatTime()}" } ?: "")
                append('\n')
            }
        }
    }

    private fun csvField(value: String): String =
        if (value.any { it == ';' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

fun RowStatus.label(type: ItemType): String = when (this) {
    RowStatus.GEDAAN -> type.doneLabel()
    RowStatus.OVERGESLAGEN -> "Overgeslagen"
    RowStatus.GEMIST -> "Gemist"
    RowStatus.TE_LAAT -> "Nog niet gedaan"
    RowStatus.OPEN -> "Gepland"
}
