package dev.mobilefoundry.product.notes

import android.content.Context
import android.util.Log
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.services.ObservableNotesRepository

/** Product-owned selection; swap this for InMemoryNotesRepository without changing consumers. */
object NotesComposition {
    fun repository(context: Context): ObservableNotesRepository = SQLiteNotesRepository(context, "local-notes.sqlite")
    fun unexpected(error: Exception) { Log.e("LocalNotes", "Unexpected notes error", error) }
}
