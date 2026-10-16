package dev.mobilefoundry.catalog.ui.notes

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mobilefoundry.catalog.composition.LocalNotesProvider
import dev.mobilefoundry.data.SQLiteNotesRepository
import dev.mobilefoundry.services.*
import kotlinx.coroutines.CoroutineScope

class LiveNotesSource(scope: CoroutineScope, writer: NotesRepository, observer: NotesObserver) {
    private val report: (Exception) -> Unit = { Log.e("LiveNotes", "Unexpected notes error", it) }
    val writer = LocalNotesViewModel(writer, report)
    val first = LiveNotesReader(scope, observer, onUnexpectedError = report)
    val second = LiveNotesReader(scope, observer, onUnexpectedError = report)
    var secondPaused = false
    fun resume() { writer.refresh(); first.start(); if (!secondPaused) second.start() }
    fun pause() { writer.stop(); first.pause(); second.pause() }
    fun shutdown() { writer.shutdown(); first.shutdown(); second.shutdown() }
}
/** Route ViewModel retains Memory data across activity recreation and storage branches. */
class LiveNotesCatalogModel(context: Context, private val databaseName: String = "live-notes.sqlite") : ViewModel() {
    private val context = context.applicationContext
    private val sources = mutableMapOf<LocalNotesProvider, LiveNotesSource>()
    fun source(provider: LocalNotesProvider): LiveNotesSource = sources.getOrPut(provider) {
        when (provider) {
            LocalNotesProvider.DEVICE -> LiveNotesSource(viewModelScope, SQLiteNotesRepository(context, databaseName), SQLiteNotesRepository(context, databaseName))
            LocalNotesProvider.MEMORY -> InMemoryNotesRepository().let { LiveNotesSource(viewModelScope, it, it) }
        }
    }
    override fun onCleared() { sources.values.forEach { it.shutdown() } }
}
