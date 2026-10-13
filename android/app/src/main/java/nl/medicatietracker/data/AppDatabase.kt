package nl.medicatietracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [TrackedItem::class, LogEntry::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TrackerDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "medicatie.db")
                .addCallback(SeedCallback)
                .build()
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
