package nl.medicatietracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {
    @Query("SELECT * FROM items WHERE deletedAt IS NULL ORDER BY minuteOfDay, name")
    fun observeItems(): Flow<List<TrackedItem>>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND active = 1 ORDER BY minuteOfDay, name")
    fun observeActiveItems(): Flow<List<TrackedItem>>

    /** Ook verwijderde items, nodig om de geschiedenis op te bouwen. */
    @Query("SELECT * FROM items")
    fun observeAllItems(): Flow<List<TrackedItem>>

    @Query("SELECT * FROM items")
    suspend fun allItems(): List<TrackedItem>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND active = 1")
    suspend fun activeItems(): List<TrackedItem>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun item(id: Long): TrackedItem?

    @Insert
    suspend fun insert(item: TrackedItem): Long

    @Update
    suspend fun update(item: TrackedItem)

    @Query("SELECT * FROM logs WHERE epochDay = :epochDay")
    fun observeLogsForDay(epochDay: Long): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs")
    fun observeAllLogs(): Flow<List<LogEntry>>

    @Query("SELECT * FROM logs")
    suspend fun allLogs(): List<LogEntry>

    @Query("SELECT * FROM logs WHERE itemId = :itemId AND epochDay = :epochDay")
    suspend fun log(itemId: Long, epochDay: Long): LogEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLog(log: LogEntry)

    @Query("DELETE FROM logs WHERE itemId = :itemId AND epochDay = :epochDay")
    suspend fun deleteLog(itemId: Long, epochDay: Long)
}
