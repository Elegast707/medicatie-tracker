package nl.medicatietracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackedItem::class, LogEntry::class, StarEntry::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "medicatie.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(SeedCallback)
                .build()

        /** Versie 2: instelling per item of overslaan de reeks breekt. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE items ADD COLUMN skipAllowed INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Versie 3: verdiende sterren en de sterrencollectie. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS stars (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tier TEXT NOT NULL, " +
                        "earnedOn INTEGER NOT NULL, collectedOn INTEGER, reason TEXT)",
                )
            }
        }
    }

    /** Vult bij de eerste start het schema uit de oude web-app in. */
    private object SeedCallback : Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            val seed = listOf(
                Triple("Lophlex", 8 * 60, 30),
                Triple("Lophlex", 12 * 60, 30),
                Triple("Lophlex", 18 * 60, 30),
                Triple("Calciumtabletten", 20 * 60, 15),
            )
            for ((name, minute, repeat) in seed) {
                db.execSQL(
                    "INSERT INTO items (name, type, details, minuteOfDay, repeatMinutes, active, createdAt) " +
                        "VALUES (?, ?, '', ?, ?, 1, ?)",
                    arrayOf<Any>(name, ItemType.MEDICATIE.name, minute, repeat, now),
                )
            }
        }
    }
}
