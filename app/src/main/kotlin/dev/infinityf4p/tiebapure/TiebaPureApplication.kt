package dev.infinityf4p.tiebapure

import android.app.Application
import android.content.ComponentCallbacks2
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

internal val readerMemoryTrimRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

class TiebaPureApplication : Application() {
    private val lazyContainer = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(this)
    }
    val container: AppContainer get() = lazyContainer.value

    @Suppress("DEPRECATION") // Older Android versions still deliver running-low levels.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) readerMemoryTrimRequests.tryEmit(Unit)
    }

    internal fun noteCacheInteraction(active: Boolean) {
        if (lazyContainer.isInitialized()) lazyContainer.value.cacheMaintenance.interaction(active)
    }

    internal fun requestCacheMaintenance() {
        if (lazyContainer.isInitialized()) lazyContainer.value.cacheMaintenance.request()
    }
}
