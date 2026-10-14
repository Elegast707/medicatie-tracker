package nl.medicatietracker

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import nl.medicatietracker.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Controleert dat een bestaande database (versie 3, zonder blobje) netjes wordt bijgewerkt naar
 * versie 4. Room valideert na de migratie het hele schema en gooit een fout als iets niet klopt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MigrationTest {
    private val name = "migratie-test.db"

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
            raw.execSQL("INSERT INTO logs (itemId, epochDay, status, loggedAt, itemName, itemType, scheduledMinute) VALUES (1, 20000, 'GEDAAN', 0, 'Lophlex', 'MEDICATIE', 480)")
            raw.version = 3
        }

        val db = AppDatabase.build(context, name)
        val sql = db.openHelper.writableDatabase // voert de migratie en de schemacontrole uit
        fun count(table: String) = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
        assertEquals(4, count("items"))
        assertEquals(1, count("logs"))
        assertEquals(0, count("pet"))
        assertEquals(0, count("stars"))
        assertEquals(4, sql.version)
        db.close()
    }
}
