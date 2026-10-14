package nl.medicatietracker

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import nl.medicatietracker.data.AppDatabase
import nl.medicatietracker.data.PetRepository
import nl.medicatietracker.data.TrackerRepository
import nl.medicatietracker.reminders.Notifications

class MedApp : Application() {
    /** Voor werk dat moet doorlopen, ook als een scherm sluit. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.build(this) }
    val petRepository: PetRepository by lazy { PetRepository(database) }
    val repository: TrackerRepository by lazy { TrackerRepository(this, database.dao(), petRepository) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
        appScope.launch { repository.rescheduleAll() }
    }
}
