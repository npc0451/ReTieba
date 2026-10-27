package dev.infinityf4p.tiebapure.core.data

import androidx.test.platform.app.InstrumentationRegistry
import dev.infinityf4p.tiebapure.core.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionPreferencesTest {
    @Test fun reopeningStorePreservesSelectionWithoutChangingReadingSettings() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AppSettingsStore(context)
        val original = store.subscriptionFeed.first()
        val reading = store.values.first()
        try {
            val choices = listOf(
                SubscriptionFeedPreferences(sourceKeys = null),
                SubscriptionFeedPreferences(sourceKeys = emptySet(), window = FeedWindow.Day),
                SubscriptionFeedPreferences(FeedSourceKind.User, setOf("forum:android", "user:42"), FeedSort.Replied, FeedWindow.Month),
            )
            choices.forEach { selected ->
                store.setSubscriptionFeed(selected)
                assertEquals(selected, AppSettingsStore(context).subscriptionFeed.first())
                assertEquals(reading, store.values.first())
            }
        } finally { store.setSubscriptionFeed(original) }
    }
}
