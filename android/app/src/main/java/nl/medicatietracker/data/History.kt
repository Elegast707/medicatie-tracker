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
    /** Overslaan telt voor dit item als afgevinkt in de reeks. */
    val skipAllowed: Boolean = false,
) {
    val countsForStreak: Boolean
        get() = status == RowStatus.GEDAAN || (status == RowStatus.OVERGESLAGEN && skipAllowed)
    val breaksStreak: Boolean
        get() = status == RowStatus.GEMIST || (status == RowStatus.OVERGESLAGEN && !skipAllowed)
}

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>) {
    val done: Int get() = rows.count { it.status == RowStatus.GEDAAN }
}

data class ItemStats(val name: String, val type: ItemType, val done: Int, val skipped: Int, val missed: Int) {
    val total: Int get() = done + skipped + missed
    val percentage: Int get() = if (total == 0) 0 else Math.round(done * 100f / total)
}

data class Summary(val done: Int, val skipped: Int, val missed: Int, val perItem: List<ItemStats>) {
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
        val skipAllowedIds = items.filter { it.skipAllowed }.map { it.id }.toSet()
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
                HistoryRow(item.id, item.name, item.type, item.minuteOfDay, statusFor(item.minuteOfDay, log), log?.loggedAt, item.skipAllowed)
            } + dayLogs.values.filter { it.itemId !in expectedIds }.map { log ->
                HistoryRow(
                    log.itemId, log.itemName, log.itemType, log.scheduledMinute,
                    statusFor(log.scheduledMinute, log), log.loggedAt, log.itemId in skipAllowedIds,
                )
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

    /** Samenvatting over de gegeven dagen. */
    fun summarize(days: List<HistoryDay>): Summary {
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
            perItem = perItem,
        )
    }

    private val COUNTED = setOf(RowStatus.GEDAAN, RowStatus.OVERGESLAGEN, RowStatus.GEMIST)

    /** De eerste dag waarvoor iets gepland of gelogd is. */
    fun firstDay(items: List<TrackedItem>, logs: List<LogEntry>): LocalDate? =
        (items.map { it.createdAt.toLocalDate() } + logs.map { LocalDate.ofEpochDay(it.epochDay) }).minOrNull()

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
