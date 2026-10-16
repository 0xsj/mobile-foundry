package dev.mobilefoundry.services

import dev.mobilefoundry.kernel.AppResult
import kotlinx.coroutines.flow.Flow

/** Cold current-state observation. The collector owns cancellation; intermediate states may coalesce. */
interface NotesObserver { fun observe(): Flow<AppResult<List<Note>>> }
interface ObservableNotesRepository : NotesRepository, NotesObserver
