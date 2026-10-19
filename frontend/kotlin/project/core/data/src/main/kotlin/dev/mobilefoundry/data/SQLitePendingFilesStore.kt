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

/** One private atomic slot; bounded reads avoid Android CursorWindow's large-row limit. */
class SQLitePendingFilesStore(private val file: File) : PendingFilesStore {
    constructor(context: Context, databaseName: String = "pending-files.sqlite") :
        this(File(context.applicationContext.noBackupFilesDir,databaseName.also { require(it.matches(Regex("[A-Za-z0-9._-]{1,100}"))) }))
    init { require(file.isAbsolute) }
    private val mutex=Mutex()
    override suspend fun load(): AppResult<PendingFileIntent?> = access { read(it) }
    override suspend fun stage(intent: PendingFileIntent): AppResult<Unit> = access { db ->
        val current=read(db)
        if (current!=null) {
            if (!current.matches(intent)) throw Refused(PendingFilesFailure.conflict)
        } else db.insertOrThrow("pending_files",null,ContentValues().apply { put("slot",1);put("metadata",intent.metadata());put("content",intent.bytes()) })
        Unit
    }
    override suspend fun remove(intent: PendingFileIntent): AppResult<Unit> = access { db ->
        val current=read(db)
        if (current!=null) {
            if (!current.matches(intent)) throw Refused(PendingFilesFailure.conflict)
            db.delete("pending_files","slot=1",null)
        }
        Unit
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
            } catch (_: InvalidStore) { Outcome.Err(PendingFilesFailure.invalid)
            } catch (_: SQLiteDatabaseCorruptException) { Outcome.Err(PendingFilesFailure.invalid)
            } catch (_: SQLiteException) { Outcome.Err(PendingFilesFailure.storage)
            } catch (_: IOException) { Outcome.Err(PendingFilesFailure.storage)
            } catch (_: SecurityException) { Outcome.Err(PendingFilesFailure.storage) }
        }
    }
    private fun schema(db: SQLiteDatabase) {
        if (db.version==0) {
            if (scalar(db,"SELECT count(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'")!=0L) throw InvalidStore()
            db.execSQL("CREATE TABLE pending_store (kind TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("INSERT INTO pending_store(kind) VALUES ('pending-files.v1')")
            db.execSQL("CREATE TABLE pending_files (slot INTEGER PRIMARY KEY CHECK(slot=1), metadata BLOB NOT NULL, content BLOB NOT NULL)")
            db.version=1
        } else if (db.version!=1) throw Refused(PendingFilesFailure.schema)
        db.rawQuery("SELECT kind FROM pending_store",null).use {
            if (!it.moveToFirst() || it.getString(0)!="pending-files.v1" || it.moveToNext()) throw InvalidStore()
        }
    }
    private fun read(db: SQLiteDatabase): PendingFileIntent? {
        val sizes=db.rawQuery("SELECT slot,typeof(metadata),length(metadata),typeof(content),length(content) FROM pending_files",null).use {
            if (!it.moveToFirst()) return null
            if (it.getLong(0)!=1L || it.getString(1)!="blob" || it.getLong(2) !in 1L..16_384L || it.getString(3)!="blob" || it.getLong(4) !in 0L..10*1024*1024L) throw InvalidStore()
            val sizes=it.getInt(2) to it.getInt(4)
            if (it.moveToNext()) throw InvalidStore();sizes
        }
        fun bounded(column: String,size: Int): ByteArray {
            val bytes=ByteArray(size);var offset=0
            while (offset<size) {
                val count=minOf(256*1024,size-offset)
                // Only constant owned column names reach this query; the cursor never holds a 10 MiB row.
                db.rawQuery("SELECT substr($column,?,?) FROM pending_files WHERE slot=1",arrayOf((offset+1).toString(),count.toString())).use {
                    if (!it.moveToFirst() || it.getType(0)!=android.database.Cursor.FIELD_TYPE_BLOB) throw InvalidStore()
                    val chunk=it.getBlob(0);if (chunk.size!=count) throw InvalidStore();chunk.copyInto(bytes,offset)
                }
                offset+=count
            };return bytes
        }
        return PendingFileIntent.decode(bounded("metadata",sizes.first),bounded("content",sizes.second)).admitted()
    }
    private fun scalar(db: SQLiteDatabase,sql: String): Long = db.rawQuery(sql,null).use { if (!it.moveToFirst()) throw InvalidStore();it.getLong(0) }
    private fun <T> AppResult<T>.admitted(): T = when (this) { is Outcome.Ok -> value;is Outcome.Err -> throw Refused(error) }
    private class Refused(val failure: Failure) : RuntimeException()
    private class InvalidStore : SQLiteException()
}
