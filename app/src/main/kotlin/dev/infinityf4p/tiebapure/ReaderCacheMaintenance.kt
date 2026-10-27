package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The supplied application scope runs on IO; requests never do file work on the caller's thread. */
internal class ReaderCacheMaintenance(
    scope: CoroutineScope,
    nowMilliseconds: () -> Long = { System.nanoTime() / 1_000_000 },
    clean: suspend (storageLow: Boolean) -> Unit,
) {
    private val storageLow = AtomicBoolean(false)
    private val interacting = MutableStateFlow(false)
    private val requests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        scope.launch {
            val mutex = Mutex()
            var lastRun: Long? = null
            var lastFailure: Long? = null
            merge(requests, MediaCacheFiles.maintenanceRequests, interacting.drop(1).map { Unit })
                .collectLatest {
                    mutex.withLock {
                        if (!storageLow.get()) {
                            interacting.first { active -> !active }
                            delay(5_000)
                            lastRun?.let { previous ->
                                delay((60_000 - (nowMilliseconds() - previous)).coerceIn(0, 60_000))
                            }
                        }
                        if (storageLow.get()) lastFailure?.let { previous ->
                            delay((5_000 - (nowMilliseconds() - previous)).coerceIn(0, 5_000))
                        }
                        val pressure = storageLow.getAndSet(false)
                        var completed = false
                        try {
                            clean(pressure)
                            completed = true
                            lastFailure = null
                        } catch (error: CancellationException) {
                            if (pressure && !completed) storageLow.set(true)
                            throw error
                        } catch (error: Exception) {
                            lastFailure = nowMilliseconds()
                            if (pressure) storageLow.set(true)
                            System.err.println("Cache maintenance failed: ${error.javaClass.simpleName}")
                        } finally { lastRun = nowMilliseconds() }
                    }
                }
        }
    }

    fun interaction(active: Boolean) { interacting.value = active }

    fun request(storagePressure: Boolean = false) {
        if (storagePressure) storageLow.set(true)
        requests.tryEmit(Unit)
    }
}
