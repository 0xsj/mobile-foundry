package dev.mobilefoundry.catalog.composition

import android.content.Context
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.services.InMemoryNotesRepository
import dev.mobilefoundry.services.NotesRepository

enum class LocalNotesProvider(val label: String) { DEVICE("Device storage"), MEMORY("Memory") }
object LocalNotesComposition {
    fun repository(context: Context, provider: LocalNotesProvider): NotesRepository = when (provider) {
        LocalNotesProvider.DEVICE -> SQLiteNotesRepository(context)
        LocalNotesProvider.MEMORY -> InMemoryNotesRepository()
    }
}
