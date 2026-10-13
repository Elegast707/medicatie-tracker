package nl.medicatietracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ItemType { MEDICATIE, TAAK }

enum class LogStatus { GEDAAN, OVERGESLAGEN }

/** Iets dat elke dag op een vast tijdstip moet gebeuren: een medicijn of een taak. */
@Entity(tableName = "items")
data class TrackedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ItemType,
    /** Dosis of notitie, optioneel. */
    val details: String = "",
    /** Tijdstip als minuten na middernacht (08:30 = 510). */
    val minuteOfDay: Int,
    /** Elke zoveel minuten opnieuw herinneren tot het is afgevinkt; 0 = niet herhalen. */
    val repeatMinutes: Int = 15,
    /** Overslaan telt als afgevinkt voor de reeks (anders breekt overslaan de reeks). */
    @ColumnInfo(defaultValue = "0") val skipAllowed: Boolean = false,
    val active: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    /** Gezet bij verwijderen, zodat de geschiedenis bewaard blijft. */
    val deletedAt: Long? = null,
)

/** Eén afgevinkt (of overgeslagen) moment op een bepaalde dag. */
@Entity(
    tableName = "logs",
    indices = [
        Index(value = ["itemId", "epochDay"], unique = true),
        Index(value = ["epochDay"]),
    ],
)
data class LogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val epochDay: Long,
    val status: LogStatus,
    val loggedAt: Long,
    // Momentopname van het item, zodat de geschiedenis klopt als het item later wijzigt.
    val itemName: String,
    val itemType: ItemType,
    val scheduledMinute: Int,
)

val DUTCH: Locale = Locale.forLanguageTag("nl-NL")

fun formatMinute(minuteOfDay: Int): String =
    String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

fun Long.formatTime(): String =
    timeFormatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

fun ItemType.doneLabel(): String = if (this == ItemType.MEDICATIE) "Genomen" else "Gedaan"
