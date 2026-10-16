package dev.mobilefoundry.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** Process-local invalidations for one admitted native database path. */
internal object NotesChanges {
    private val lock = Any()
    private val listeners = mutableMapOf<String, MutableMap<Any, () -> Unit>>()
    fun observe(key: String): Flow<Unit> = callbackFlow {
        val id = Any()
        synchronized(lock) { listeners.getOrPut(key) { mutableMapOf() }[id] = { trySend(Unit); Unit } }
        trySend(Unit) // Register before the initial read.
        awaitClose {
            synchronized(lock) { listeners[key]?.remove(id); if (listeners[key]?.isEmpty() == true) listeners.remove(key) }
        }
    }.buffer(Channel.CONFLATED)
    fun committed(key: String) {
        val callbacks = synchronized(lock) { listeners[key]?.values?.toList().orEmpty() }
        callbacks.forEach { it() }
    }
}
