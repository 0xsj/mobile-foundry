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

/** Private backup-excluded journal; short owned transactions never contain HTTP. */
class SQLitePendingNotesStore(private val file: File, private val maxScopes: Int = 256) : PendingNotesStore {
    constructor(context: Context, databaseName: String = "pending-notes.sqlite", maxScopes: Int = 256) :
        this(File(context.applicationContext.noBackupFilesDir, databaseName.also { require(it.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }), maxScopes)
    init { require(file.isAbsolute && maxScopes > 0) }
    private val mutex = Mutex()
    override suspend fun load(scope: PendingNotesScope): AppResult<PendingNoteRecord?> = access { read(it, scope) }
    override suspend fun stage(command: RemoteNoteWrite, scope: PendingNotesScope, replacing: String?): AppResult<PendingNoteRecord> = access { db ->
        val current = read(db, scope)
        if (current?.command?.commandId == command.commandId) {
            if (!current.matches(command)) throw Refused(PendingNotesFailure.conflict)
            current
        } else {
            if (current?.command?.commandId != replacing || current?.status == PendingNoteStatus.PENDING) throw Refused(PendingNotesFailure.conflict)
            if (current == null && scalar(db, "SELECT count(*) FROM pending_notes") >= maxScopes) throw Refused(PendingNotesFailure.capacity)
            PendingNoteRecord(scope, command).also { write(db, it) }
        }
    }
    override suspend fun confirm(command: RemoteNoteWrite, scope: PendingNotesScope, receipt: NoteWriteReceipt): AppResult<PendingNoteRecord> = access { db ->
        val current = read(db, scope)?.takeIf { it.matches(command) } ?: throw Refused(PendingNotesFailure.conflict)
        current.confirming(receipt).admitted().also { write(db, it) }
    }
    override suspend fun reject(command: RemoteNoteWrite, scope: PendingNotesScope, reason: PendingNoteRejection): AppResult<PendingNoteRecord> = access { db ->
        val current = read(db, scope)?.takeIf { it.matches(command) } ?: throw Refused(PendingNotesFailure.conflict)
        current.rejecting(reason).admitted().also { write(db, it) }
    }
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
            } catch (_: InvalidStore) { Outcome.Err(PendingNotesFailure.invalid)
            } catch (_: SQLiteDatabaseCorruptException) { Outcome.Err(PendingNotesFailure.invalid)
            } catch (_: SQLiteException) { Outcome.Err(PendingNotesFailure.storage)
            } catch (_: IOException) { Outcome.Err(PendingNotesFailure.storage)
            } catch (_: SecurityException) { Outcome.Err(PendingNotesFailure.storage) }
        }
    }
    private fun schema(db: SQLiteDatabase) {
        if (db.version == 0) {
            if (scalar(db, "SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'") != 0L) throw InvalidStore()
            db.execSQL("CREATE TABLE pending_store (kind TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("INSERT INTO pending_store(kind) VALUES ('pending-notes.v1')")
            db.execSQL("CREATE TABLE pending_notes (authority TEXT NOT NULL, principal TEXT NOT NULL, account TEXT NOT NULL, payload BLOB NOT NULL, PRIMARY KEY(authority,principal,account)) WITHOUT ROWID")
            db.version = 1
        } else if (db.version != 1) throw Refused(PendingNotesFailure.schema)
        db.rawQuery("SELECT kind FROM pending_store", null).use {
            if (!it.moveToFirst() || it.getString(0) != "pending-notes.v1" || it.moveToNext()) throw InvalidStore()
        }
    }
    private fun read(db: SQLiteDatabase, scope: PendingNotesScope): PendingNoteRecord? = db.rawQuery(
        "SELECT typeof(payload), length(payload), CASE WHEN typeof(payload)='blob' AND length(payload) BETWEEN 1 AND 16384 THEN payload ELSE NULL END FROM pending_notes WHERE authority=? AND principal=? AND account=?", args(scope)).use {
        if (!it.moveToFirst()) null
        else {
            if (it.getString(0) != "blob" || it.getLong(1) !in 1L..16_384L || it.getType(2) != android.database.Cursor.FIELD_TYPE_BLOB) throw InvalidStore()
            PendingNoteRecord.decode(it.getBlob(2), scope).admitted()
        }
    }
    private fun write(db: SQLiteDatabase, record: PendingNoteRecord) {
        val values = ContentValues().apply { put("payload", record.encoded()) }
        if (db.update("pending_notes", values, "authority=? AND principal=? AND account=?", args(record.scope)) == 0) {
            values.put("authority", record.scope.authorityId); values.put("principal", record.scope.principalId); values.put("account", record.scope.accountId)
            db.insertOrThrow("pending_notes", null, values)
        }
    }
    private fun scalar(db: SQLiteDatabase, sql: String): Long = db.rawQuery(sql, null).use { if (!it.moveToFirst()) throw InvalidStore(); it.getLong(0) }
    private fun args(scope: PendingNotesScope) = arrayOf(scope.authorityId, scope.principalId, scope.accountId)
    private fun <T> AppResult<T>.admitted(): T = when (this) { is Outcome.Ok -> value; is Outcome.Err -> throw Refused(error) }
    private class Refused(val failure: Failure) : RuntimeException()
    private class InvalidStore : SQLiteException()
}
