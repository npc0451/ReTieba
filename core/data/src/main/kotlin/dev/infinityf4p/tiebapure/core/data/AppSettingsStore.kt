package dev.infinityf4p.tiebapure.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.infinityf4p.tiebapure.core.model.ReaderFontSize
import dev.infinityf4p.tiebapure.core.model.ReaderFontFamily
import dev.infinityf4p.tiebapure.core.model.ReaderLineSpacing
import dev.infinityf4p.tiebapure.core.model.ReaderMediaLoadingPolicy
import dev.infinityf4p.tiebapure.core.model.ReadingPreferences
import dev.infinityf4p.tiebapure.core.model.ThreadReplySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

enum class AppAppearance { System, Light, Dark }

private const val DEFAULT_POSTING_ENABLED = false
private const val DEFAULT_REPLYING_ENABLED = false
private const val DEFAULT_LIKING_ENABLED = false

data class AppSettings(
    val communityColors: Boolean = true,
    val appearance: AppAppearance = AppAppearance.System,
    val postingEnabled: Boolean = DEFAULT_POSTING_ENABLED,
    val replyingEnabled: Boolean = DEFAULT_REPLYING_ENABLED,
    val likingEnabled: Boolean = DEFAULT_LIKING_ENABLED,
    val autoSignEnabled: Boolean = false,
    val submissionRiskAcknowledged: Boolean = false,
    val reading: ReadingPreferences = ReadingPreferences(),
)

class AppSettingsStore(private val context: Context) {
    val subscriptionFeed: Flow<dev.infinityf4p.tiebapure.core.model.SubscriptionFeedPreferences> =
        context.settingsDataStore.data.map { preferences ->
            dev.infinityf4p.tiebapure.core.model.SubscriptionFeedPreferences(
                kind = preferences.enumValue(Keys.feedKind, dev.infinityf4p.tiebapure.core.model.FeedSourceKind.All),
                sourceKeys = preferences[Keys.feedSources],
                sort = preferences.enumValue(Keys.feedSort, dev.infinityf4p.tiebapure.core.model.FeedSort.Published),
                window = preferences.enumValue(Keys.feedWindow, dev.infinityf4p.tiebapure.core.model.FeedWindow.Week),
            )
        }

    suspend fun setSubscriptionFeed(value: dev.infinityf4p.tiebapure.core.model.SubscriptionFeedPreferences) = edit {
        it[Keys.feedKind] = value.kind.name
        it[Keys.feedSort] = value.sort.name
        it[Keys.feedWindow] = value.window.name
        value.sourceKeys?.let { keys -> it[Keys.feedSources] = keys } ?: it.remove(Keys.feedSources)
    }

    val values: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            communityColors = preferences[Keys.communityColors] ?: true,
            appearance = runCatching {
                AppAppearance.valueOf(preferences[Keys.appearance] ?: AppAppearance.System.name)
            }.getOrDefault(AppAppearance.System),
            postingEnabled = preferences[Keys.posting] ?: DEFAULT_POSTING_ENABLED,
            replyingEnabled = preferences[Keys.replying] ?: DEFAULT_REPLYING_ENABLED,
            likingEnabled = preferences[Keys.liking] ?: DEFAULT_LIKING_ENABLED,
            autoSignEnabled = preferences[Keys.autoSign] ?: false,
            submissionRiskAcknowledged = preferences[Keys.submissionRiskV2] ?: false,
            reading = ReadingPreferences(
                fontSize = preferences.enumValue(Keys.readerFontSize, ReaderFontSize.Standard),
                fontFamily = ReaderFontFamily.fromRaw(preferences[Keys.readerFontFamily]),
                lineSpacing = preferences.enumValue(Keys.readerLineSpacing, ReaderLineSpacing.Standard),
                defaultReplySort = preferences.enumValue(Keys.defaultReplySort, ThreadReplySort.Hot),
                mediaLoading = preferences.enumValue(Keys.mediaLoading, ReaderMediaLoadingPolicy.Automatic),
            ),
        )
    }

    suspend fun update(value: AppSettings) {
        context.settingsDataStore.edit { preferences ->
            preferences[Keys.communityColors] = value.communityColors
            preferences[Keys.appearance] = value.appearance.name
            preferences[Keys.posting] = value.postingEnabled
            preferences[Keys.replying] = value.replyingEnabled
            preferences[Keys.liking] = value.likingEnabled
            preferences[Keys.autoSign] = value.autoSignEnabled
            preferences[Keys.submissionRiskV2] = value.submissionRiskAcknowledged
            preferences[Keys.readerFontSize] = value.reading.fontSize.name
            preferences[Keys.readerFontFamily] = value.reading.fontFamily.rawValue
            preferences[Keys.readerLineSpacing] = value.reading.lineSpacing.name
            preferences[Keys.defaultReplySort] = value.reading.defaultReplySort.name
            preferences[Keys.mediaLoading] = value.reading.mediaLoading.name
        }
    }

    suspend fun setAppearance(value: AppAppearance) = edit { it[Keys.appearance] = value.name }
    suspend fun setCommunityColors(value: Boolean) = edit { it[Keys.communityColors] = value }

    suspend fun setPostingEnabled(value: Boolean) = edit { it[Keys.posting] = value }

    suspend fun setReplyingEnabled(value: Boolean) = edit { it[Keys.replying] = value }

    suspend fun setLikingEnabled(value: Boolean) = edit { it[Keys.liking] = value }

    suspend fun setAutoSignEnabled(value: Boolean) = edit { it[Keys.autoSign] = value }

    suspend fun acknowledgeSubmissionRisk() = edit { it[Keys.submissionRiskV2] = true }

    suspend fun setReadingPreferences(value: ReadingPreferences) = edit {
        it[Keys.readerFontSize] = value.fontSize.name
        it[Keys.readerFontFamily] = value.fontFamily.rawValue
        it[Keys.readerLineSpacing] = value.lineSpacing.name
        it[Keys.defaultReplySort] = value.defaultReplySort.name
        it[Keys.mediaLoading] = value.mediaLoading.name
    }

    private suspend fun edit(transform: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(transform)
    }

    private object Keys {
        val communityColors = booleanPreferencesKey("community_colors")
        val feedKind = stringPreferencesKey("feed_kind")
        val feedSort = stringPreferencesKey("feed_sort")
        val feedWindow = stringPreferencesKey("feed_window")
        val feedSources = androidx.datastore.preferences.core.stringSetPreferencesKey("feed_sources")
        val appearance = stringPreferencesKey("appearance")
        val posting = booleanPreferencesKey("posting_enabled")
        val replying = booleanPreferencesKey("replying_enabled")
        val liking = booleanPreferencesKey("liking_enabled")
        val autoSign = booleanPreferencesKey("auto_sign_enabled")
        val submissionRiskV2 = booleanPreferencesKey("content_submission_risk_acknowledged_v2")
        val readerFontSize = stringPreferencesKey("reader_font_size")
        val readerFontFamily = stringPreferencesKey("reader_font_family")
        val readerLineSpacing = stringPreferencesKey("reader_line_spacing")
        val defaultReplySort = stringPreferencesKey("reader_default_reply_sort")
        val mediaLoading = stringPreferencesKey("reader_media_loading")
    }
}

private inline fun <reified T : Enum<T>> androidx.datastore.preferences.core.Preferences.enumValue(
    key: androidx.datastore.preferences.core.Preferences.Key<String>,
    defaultValue: T,
): T = enumValues<T>().firstOrNull { it.name == this[key] } ?: defaultValue
