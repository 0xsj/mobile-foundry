package dev.mobilefoundry.data

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseErrorHandler
import android.database.sqlite.*
import dev.mobilefoundry.kernel.*
import dev.mobilefoundry.services.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Separate backup-excluded read cache; never opens or resets the command journal. */
class SQLiteNotesReadCache(private val file: File, private val maxScopes: Int = 32) : NotesReadCache {
    constructor(context: Context, databaseName: String = "notes-read-cache.sqlite", maxScopes: Int = 32) :
        this(File(context.applicationContext.noBackupFilesDir, databaseName.also { require(it.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }), maxScopes)
    init { require(file.isAbsolute && maxScopes in 1..32) }
    private val mutex = Mutex()
    override suspend fun load(scope: PendingNotesScope, nowMilliseconds: Long): AppResult<NotesReadSnapshot?> = access { db ->
        val snapshot = read(db, scope)
        if (snapshot != null && !snapshot.isUsable(nowMilliseconds)) { remove(db, scope); null } else snapshot
    }
    override suspend fun save(snapshot: NotesReadSnapshot): AppResult<Unit> = access { db ->
        val bytes = snapshot.encoded()
        if (bytes.size > NotesReadSnapshot.maxBytes) throw InvalidStore()
        val current = read(db, snapshot.scope)
        if (current == null || current.savedAtMilliseconds <= snapshot.savedAtMilliseconds) {
            val values = ContentValues().apply { put("saved_at", snapshot.savedAtMilliseconds); put("payload", bytes) }
            if (db.update("notes_cache", values, "authority=? AND principal=? AND account=?", args(snapshot.scope)) == 0) {
                values.put("authority", snapshot.scope.authorityId); values.put("principal", snapshot.scope.principalId); values.put("account", snapshot.scope.accountId)
                db.insertOrThrow("notes_cache", null, values)
            }
        }
        val expired = db.rawQuery("SELECT authority,principal,account FROM notes_cache ORDER BY saved_at DESC,authority,principal,account LIMIT -1 OFFSET $maxScopes", null).use { rows ->
            buildList { while (rows.moveToNext()) add(arrayOf(rows.getString(0), rows.getString(1), rows.getString(2))) }
        }
        expired.forEach { db.delete("notes_cache", "authority=? AND principal=? AND account=?", it) }
    }
    override suspend fun remove(scope: PendingNotesScope): AppResult<Unit> = access { remove(it, scope) }
    private fun remove(db: SQLiteDatabase, scope: PendingNotesScope) { db.delete("notes_cache", "authority=? AND principal=? AND account=?", args(scope)) }
    private suspend fun <T> access(operation: (SQLiteDatabase) -> T): AppResult<T> = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            try {
                val parent = file.parentFile ?: throw IOException()
                if (!parent.isDirectory && !parent.mkdirs()) throw IOException()
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                    DatabaseErrorHandler { throw InvalidStore() }).use { db ->
                    db.rawQuery("PRAGMA journal_mode=DELETE", null).use { if (!it.moveToFirst() || it.getString(0) != "delete") throw InvalidStore() }
                    db.execSQL("PRAGMA synchronous=FULL")
                    db.rawQuery("PRAGMA busy_timeout=2000", null).use { if (!it.moveToFirst() || it.getLong(0) != 2_000L) throw InvalidStore() }
                    if (scalar(db, "PRAGMA synchronous") != 2L) throw InvalidStore()
                    db.beginTransaction()
                    try {
                        schema(db); currentCoroutineContext().ensureActive()
                        val value = operation(db); db.setTransactionSuccessful(); Outcome.Ok(value)
                    } finally { db.endTransaction() }
                }
            } catch (e: Refused) { Outcome.Err(e.failure)
            } catch (_: InvalidStore) { Outcome.Err(NotesCacheFailure.invalid)
            } catch (_: SQLiteDatabaseCorruptException) { Outcome.Err(NotesCacheFailure.invalid)
            } catch (_: SQLiteException) { Outcome.Err(NotesCacheFailure.storage)
            } catch (_: IOException) { Outcome.Err(NotesCacheFailure.storage)
            } catch (_: SecurityException) { Outcome.Err(NotesCacheFailure.storage) }
        }
    }
    private fun schema(db: SQLiteDatabase) {
        if (db.version == 0) {
            if (scalar(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") != 0L) throw InvalidStore()
            db.execSQL("CREATE TABLE cache_store (kind TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("INSERT INTO cache_store(kind) VALUES ('notes-read-cache.v1')")
            db.execSQL("CREATE TABLE notes_cache (authority TEXT NOT NULL, principal TEXT NOT NULL, account TEXT NOT NULL, saved_at INTEGER NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(authority,principal,account)) WITHOUT ROWID")
            db.version = 1
        } else if (db.version != 1) throw Refused(NotesCacheFailure.schema)
        db.rawQuery("SELECT kind FROM cache_store", null).use {
            if (!it.moveToFirst() || it.getString(0) != "notes-read-cache.v1" || it.moveToNext()) throw InvalidStore()
        }
    }
    private fun read(db: SQLiteDatabase, scope: PendingNotesScope): NotesReadSnapshot? = db.rawQuery(
        "SELECT typeof(payload), length(payload), CASE WHEN typeof(payload)='blob' AND length(payload) BETWEEN 1 AND 65536 THEN payload ELSE NULL END,saved_at FROM notes_cache WHERE authority=? AND principal=? AND account=?", args(scope)).use {
        if (!it.moveToFirst()) null
        else {
            if (it.getString(0) != "blob" || it.getLong(1) !in 1L..65_536L || it.getType(2) != android.database.Cursor.FIELD_TYPE_BLOB || it.getType(3) != android.database.Cursor.FIELD_TYPE_INTEGER) throw InvalidStore()
            val snapshot = NotesReadSnapshot.decode(it.getBlob(2), scope).admitted()
            if (snapshot.savedAtMilliseconds != it.getLong(3)) throw InvalidStore()
            snapshot
        }
    }
    private fun scalar(db: SQLiteDatabase, sql: String): Long = db.rawQuery(sql, null).use { if (!it.moveToFirst()) throw InvalidStore(); it.getLong(0) }
    private fun args(scope: PendingNotesScope) = arrayOf(scope.authorityId, scope.principalId, scope.accountId)
    private fun <T> AppResult<T>.admitted(): T = when (this) { is Outcome.Ok -> value; is Outcome.Err -> throw Refused(error) }
    private class Refused(val failure: Failure) : RuntimeException()
    private class InvalidStore : SQLiteException()
}
