package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.data.BrowsingHistoryEntity
import dev.infinityf4p.tiebapure.core.data.TiebaPureDatabase
import dev.infinityf4p.tiebapure.core.data.TiebaRepositories
import dev.infinityf4p.tiebapure.core.model.BrowsingHistoryEntry
import dev.infinityf4p.tiebapure.core.model.OwnThreadDeletionTarget
import dev.infinityf4p.tiebapure.core.model.TiebaContentFilterPolicy
import dev.infinityf4p.tiebapure.core.model.UserSummary
import dev.infinityf4p.tiebapure.feature.account.BrowsingHistoryRepository
import dev.infinityf4p.tiebapure.feature.account.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

class AppReaderRepositories(
    private val database: TiebaPureDatabase,
    private val repositories: TiebaRepositories,
) {
    private val historyEntries: Flow<List<BrowsingHistoryEntry>> = combine(
        database.browsingHistoryDao().observeAll(),
        database.blocklistDao().observeAll(),
    ) { entities, blocklist ->
        val snapshot = blocklist.toBlocklistSnapshot()
        entities.map(BrowsingHistoryEntity::toBrowsingHistoryEntry)
            .filter { TiebaContentFilterPolicy.shouldKeep(it.thread, snapshot) }
    }

    val browsingHistory: BrowsingHistoryRepository = object : BrowsingHistoryRepository {
        override val entries: Flow<List<BrowsingHistoryEntry>> = historyEntries

        override suspend fun removeEntries(threadIds: Set<Long>) {
            threadIds.toList().sorted().forEach { database.browsingHistoryDao().remove(it) }
        }

        override suspend fun clear() {
            database.browsingHistoryDao().clear()
        }
    }

    val userProfile: UserProfileRepository = object : UserProfileRepository {
        override suspend fun loadForums(user: UserSummary, page: Int) = repositories.user.followedForums(user.id, page).let { result ->
            val blocked = database.blocklistDao().observeAll().first().toBlocklistSnapshot()
            result.copy(forums = result.forums.filter { TiebaContentFilterPolicy.shouldKeep(it, blocked) })
        }
        override suspend fun loadProfile(user: UserSummary) = repositories.user.profile(user, account = null).let { profile ->
            val blocked = database.blocklistDao().observeAll().first().toBlocklistSnapshot()
            profile.copy(
                isCurrentUser = false,
                isFollowed = false,
                followedForums = profile.followedForums.filter { TiebaContentFilterPolicy.shouldKeep(it, blocked) },
            )
        }

        override suspend fun loadThreads(user: UserSummary, page: Int) =
            repositories.user.threads(user.id, page.coerceAtLeast(1), account = null).let { result ->
                val blocked = database.blocklistDao().observeAll().first().toBlocklistSnapshot()
                result.copy(
                    threads = result.threads.filter { TiebaContentFilterPolicy.shouldKeep(it, blocked) },
                    deletionTargetsByThreadId = emptyMap(),
                )
            }

        override suspend fun setFollow(user: UserSummary, followed: Boolean): Boolean =
            throw UnsupportedOperationException("只读客户端不提供账号关注。")

        override suspend fun deleteOwnThread(target: OwnThreadDeletionTarget): Unit =
            throw UnsupportedOperationException("只读客户端不提供删帖。")
    }
}
