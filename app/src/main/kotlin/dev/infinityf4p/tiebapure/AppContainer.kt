package dev.infinityf4p.tiebapure

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.infinityf4p.tiebapure.core.media.MediaCacheFiles
import dev.infinityf4p.tiebapure.core.media.ReaderCachePreferences
import android.content.Context
import android.os.Build
import dev.infinityf4p.tiebapure.core.data.AppSettings
import dev.infinityf4p.tiebapure.core.data.AppSettingsStore
import dev.infinityf4p.tiebapure.core.data.TiebaPureDatabase
import dev.infinityf4p.tiebapure.core.data.TiebaRepositories
import dev.infinityf4p.tiebapure.core.model.ReaderFontFamily
import dev.infinityf4p.tiebapure.core.network.AnonymousReadInterceptor
import dev.infinityf4p.tiebapure.core.network.DefaultTiebaReadService
import dev.infinityf4p.tiebapure.core.network.TiebaDeviceProfile
import dev.infinityf4p.tiebapure.core.network.TiebaHttpClientFactory
import dev.infinityf4p.tiebapure.core.network.TiebaRequestBuilder
import dev.infinityf4p.tiebapure.core.network.TiebaTransport
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val settings = AppSettingsStore(appContext)
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val cacheMaintenance = ReaderCacheMaintenance(applicationScope) { pressure ->
        MediaCacheFiles.maintain(appContext.cacheDir,
            ReaderCachePreferences.imageLimitMb(appContext).toLong() * 1_024 * 1_024, storageLow = pressure)
    }
    val currentSettings = settings.values.stateIn(applicationScope, SharingStarted.Eagerly, AppSettings())
    val database = TiebaPureDatabase.getInstance(appContext)
    val localLibrary = dev.infinityf4p.tiebapure.core.data.LocalLibraryRepository(database)
    val localUsers = dev.infinityf4p.tiebapure.core.data.LocalUserFollowsRepository(database)
    val readerFonts = AppReaderFontStore(appContext)
    private val requestBuilder = TiebaRequestBuilder(deviceProfile(appContext))
    private val transport = TiebaTransport(TiebaHttpClientFactory.create {
        addInterceptor(AnonymousReadInterceptor())
    })
    val repositories = TiebaRepositories.network(DefaultTiebaReadService(transport, requestBuilder))
    internal val subscriptionFeed = AppSubscriptionFeedSource(database, repositories.forum, repositories.user, settings)

    suspend fun subscribe(forum: dev.infinityf4p.tiebapure.core.model.Forum) {
        localLibrary.subscribe(verifiedSubscriptionForum(forum, repositories.forum))
    }
    val savedThreadMedia = AppSavedThreadMediaStore(appContext)
    val savedThreads = AppSavedThreadRepository(database, repositories, { null }, savedThreadMedia, appContext)
    val featureRepositories = AppFeatureRepositories(
        repositories = repositories,
        database = database,
        account = { null },
        settings = { currentSettings.value },
        applicationScope = applicationScope,
    )
    val readerRepositories = AppReaderRepositories(database, repositories)
    val settingsRepository = AppSettingsRepository(settings, database, readerFonts)

    init {
        applicationScope.launch {
            readerFonts.load()
            val persisted = settings.values.first()
            val selected = persisted.reading.fontFamily
            if (selected.importedId != null && readerFonts.entries.value.none { it.id == selected.importedId }) {
                settings.setReadingPreferences(persisted.reading.copy(fontFamily = ReaderFontFamily.System))
            }
        }
        applicationScope.launch { savedThreads.repairStorage() }
        cacheMaintenance.request()
        ContextCompat.registerReceiver(appContext, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_DEVICE_STORAGE_LOW) cacheMaintenance.request(storagePressure = true)
            }
        }, IntentFilter(Intent.ACTION_DEVICE_STORAGE_LOW), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun deviceProfile(context: Context): TiebaDeviceProfile {
        val preferences = context.getSharedPreferences("installation", Context.MODE_PRIVATE)
        val clientId = preferences.getString(CLIENT_ID_KEY, null)
            ?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString(CLIENT_ID_KEY, it).commit())
            }
        val metrics = context.resources.displayMetrics
        return TiebaDeviceProfile(
            clientId = clientId,
            osVersion = Build.VERSION.RELEASE,
            model = Build.MODEL.ifBlank { "Android" },
            brand = Build.BRAND.ifBlank { "Android" },
            screenWidthPixels = metrics.widthPixels.coerceAtLeast(1),
            screenHeightPixels = metrics.heightPixels.coerceAtLeast(1),
            screenDensity = metrics.density.toDouble().coerceAtLeast(0.1),
        )
    }

    private companion object {
        const val CLIENT_ID_KEY = "tieba_client_id_v1"
    }
}
