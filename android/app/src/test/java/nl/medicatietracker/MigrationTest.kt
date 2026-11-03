package nl.medicatietracker

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import nl.medicatietracker.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Controleert dat oudere databases netjes worden bijgewerkt naar de huidige versie. Room valideert
 * na de migratie het hele schema en gooit een fout als iets niet klopt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val name = "migratie-test.db"

    /** Haalt weg wat versie 6 toevoegde: de takentabellen en de reeks-kolom van de sterren. */
    private fun downgradeTo5(raw: SQLiteDatabase) {
        raw.execSQL("DROP TABLE `tasks`")
        raw.execSQL("DROP TABLE `task_logs`")
        raw.execSQL("DROP TABLE `stars`")
        raw.execSQL(
            "CREATE TABLE stars (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tier TEXT NOT NULL, " +
                "earnedOn INTEGER NOT NULL, collectedOn INTEGER, reason TEXT)",
        )
    }

    @Test
    fun migratesFromVersion3To4AndKeepsData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)

        // Maak een verse database en zet die terug naar versie 3 door de tabellen van het blobje te verwijderen.
        AppDatabase.build(context, name).apply {
            openHelper.writableDatabase
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            listOf("pet", "pet_food", "pet_owned", "food_rewards").forEach { raw.execSQL("DROP TABLE `$it`") }
            raw.execSQL("INSERT INTO items (name, type, details, minuteOfDay, repeatMinutes, active, createdAt) VALUES ('Lophlex', 'MEDICATIE', '', 480, 30, 1, 0)")
            raw.execSQL("INSERT INTO logs (itemId, epochDay, status, loggedAt, itemName, itemType, scheduledMinute) VALUES (1, 20000, 'GEDAAN', 0, 'Lophlex', 'MEDICATIE', 480)")
            downgradeTo5(raw)
            raw.version = 3
        }

        val db = AppDatabase.build(context, name)
        val sql = db.openHelper.writableDatabase // voert de migratie en de schemacontrole uit
        fun count(table: String) = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(1, count("items"))
        assertEquals(1, count("logs"))
        assertEquals(0, count("pet"))
        assertEquals(0, count("stars"))
        assertEquals(6, sql.version)
        db.close()
    }

    @Test
    fun migratesFromVersion4To5AndKeepsThePet() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)

        AppDatabase.build(context, name).apply {
            openHelper.writableDatabase
            close()
        }
        // Zet de tabel van het blobje terug zoals hij in versie 4 was, met een bestaand blobje erin.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            raw.execSQL("DROP TABLE `pet`")
            raw.execSQL(
                "CREATE TABLE `pet` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                    "`fullness` INTEGER NOT NULL, `fullnessAt` INTEGER NOT NULL, `coins` INTEGER NOT NULL, " +
                    "`head` TEXT, `eyes` TEXT, `neck` TEXT, `hand` TEXT, PRIMARY KEY(`id`))",
            )
            raw.execSQL("INSERT INTO pet (id, name, fullness, fullnessAt, coins, head) VALUES (1, 'Bolle', 100, 1000, 123, 'PET')")
            downgradeTo5(raw)
            raw.version = 4
        }

        val before = System.currentTimeMillis()
        val db = AppDatabase.build(context, name)
        val sql = db.openHelper.writableDatabase
        assertEquals(6, sql.version)
        sql.query("SELECT name, coins, head, fullnessAt, happiness, happinessAt FROM pet").use {
            it.moveToFirst()
            assertEquals("Bolle", it.getString(0))
            assertEquals(123, it.getInt(1))
            assertEquals("PET", it.getString(2))
            assertEquals(1000L, it.getLong(3))
            assertEquals(80, it.getInt(4))
            assertTrue(it.getLong(5) >= before)
        }
        db.close()
    }

    @Test
    fun migratesFromVersion5To6AndKeepsStarsAsMedicationStars() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)

        AppDatabase.build(context, name).apply {
            openHelper.writableDatabase
            close()
        }
        // Zet de database terug naar versie 5: geen takentabellen en sterren zonder reeks-kolom.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { raw ->
            downgradeTo5(raw)
            raw.execSQL("INSERT INTO stars (tier, earnedOn) VALUES ('BRONS', 20000)")
            raw.version = 5
        }

        val db = AppDatabase.build(context, name)
        val sql = db.openHelper.writableDatabase
        assertEquals(6, sql.version)
        sql.query("SELECT tier, track FROM stars").use {
            it.moveToFirst()
            assertEquals("BRONS", it.getString(0))
            assertEquals("MEDICATIE", it.getString(1))
        }
        sql.query("SELECT COUNT(*) FROM tasks").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        db.close()
    }
}
