package nl.medicatietracker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import nl.medicatietracker.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Een nieuwe installatie begint leeg: geen voorgeladen medicatie of herinneringen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class FreshInstallTest {
    @Test
    fun freshDatabaseHasNoItems() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "verse-installatie-test.db"
        context.deleteDatabase(name)

        val db = AppDatabase.build(context, name)
        val sql = db.openHelper.writableDatabase
        sql.query("SELECT COUNT(*) FROM items").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }
}
