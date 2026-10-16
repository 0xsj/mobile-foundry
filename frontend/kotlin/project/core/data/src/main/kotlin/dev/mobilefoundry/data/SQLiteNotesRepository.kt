package dev.mobilefoundry.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.util.Collections
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged

/** Device-local adapter. IO dispatcher + mutex; native helper/cursor handles close after each operation. */
class SQLiteNotesRepository(context: Context, private val databaseName: String = "local-notes.sqlite") : ObservableNotesRepository {
    private val context = context.applicationContext
    private val mutex = Mutex()
    init { require(databaseName.isNotEmpty() && '/' !in databaseName && '\\' !in databaseName) }
    private val observationKey get() = context.getDatabasePath(databaseName).absolutePath
    override fun observe(): Flow<AppResult<List<Note>>> = NotesChanges.observe(observationKey).map { list() }.distinctUntilChanged()

    override suspend fun list(): AppResult<List<Note>> = access { db ->
        val notes = ArrayList<Note>()
        db.rawQuery("SELECT id, title FROM notes ORDER BY position ASC", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0); val title = cursor.getString(1)
                if (id == null || title == null || !Note.valid(id, title)) throw InvalidStoredNote()
                notes += Note(id, title)
            }
        }
        Outcome.Ok(Collections.unmodifiableList(notes))
    }
    override suspend fun create(command: CreateNote): AppResult<Note> = access(notify = true) { db ->
        val note = Note(UUID.randomUUID().toString(), command.title)
        db.insertOrThrow("notes", null, ContentValues().apply { put("id", note.id); put("title", note.title) })
        Outcome.Ok(note)
    }
    override suspend fun update(command: UpdateNote): AppResult<Note> = access(notify = true) { db ->
        val changed = db.update("notes", ContentValues().apply { put("title", command.title) }, "id = ?", arrayOf(command.id))
        if (changed == 1) Outcome.Ok(Note(command.id, command.title)) else Outcome.Err(NotesRepositoryFailure.missing)
    }
    override suspend fun delete(command: DeleteNote): AppResult<Unit> = access(notify = true) { db ->
        if (db.delete("notes", "id = ?", arrayOf(command.id)) == 1) Outcome.Ok(Unit) else Outcome.Err(NotesRepositoryFailure.missing)
    }
    private suspend fun <T> access(notify: Boolean = false, operation: (SQLiteDatabase) -> AppResult<T>): AppResult<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            ensureActive()
            try {
                val answer = NotesOpenHelper(context, databaseName).use { helper ->
                    val db = helper.writableDatabase
                    ensureActive()
                    // Native single-statement CRUD commits atomically; cancellation after commit does not imply rollback.
                    operation(db)
                }
                // Publish after commit/close, even when cancellation prevents the caller receiving its receipt.
                if (notify && answer is Outcome.Ok) NotesChanges.committed(observationKey)
                answer
            } catch (error: UnsupportedNotesSchema) { Outcome.Err(storage("notes.storage_version", "This notes database needs a newer app version."))
            } catch (error: InvalidStoredNote) { Outcome.Err(corrupt)
            } catch (error: SQLiteDatabaseCorruptException) { Outcome.Err(corrupt)
            } catch (error: SQLiteDatabaseLockedException) { Outcome.Err(storage("notes.storage_busy", "Local notes storage is busy. Try again."))
            } catch (error: SQLiteConstraintException) { Outcome.Err(Failure.Conflict(FailureMeta("The local note could not be saved. Refresh your notes.", "notes.storage_conflict")))
            } catch (error: SQLiteException) { Outcome.Err(storage("notes.storage_unavailable", "Local notes storage is unavailable. Try again.")) }
        }
    }
    private class UnsupportedNotesSchema : SQLiteException()
    private class InvalidStoredNote : SQLiteException()
    // Replace Android's default corruption handler, which may delete a corrupt database.
    private class NotesOpenHelper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1,
        DatabaseErrorHandler { throw InvalidStoredNote() }) {
        override fun onCreate(db: SQLiteDatabase) {
            db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'", null).use {
                if (!it.moveToFirst() || it.getInt(0) != 0) throw InvalidStoredNote()
            }
            db.execSQL("CREATE TABLE notes (position INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT NOT NULL UNIQUE, title TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { throw UnsupportedNotesSchema() }
        override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { throw UnsupportedNotesSchema() }
    }
    private companion object {
        fun storage(code: String, message: String) = Failure.Unavailable(FailureMeta(message, code))
        val corrupt get() = storage("notes.storage_corrupt", "Local notes could not be read. Stored data has been kept.")
    }
}
