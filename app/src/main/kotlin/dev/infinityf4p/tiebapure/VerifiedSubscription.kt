package dev.infinityf4p.tiebapure

import dev.infinityf4p.tiebapure.core.data.ForumRepository
import dev.infinityf4p.tiebapure.core.data.localForumKey
import dev.infinityf4p.tiebapure.core.model.Forum
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun verifiedSubscriptionForum(forum: Forum, repository: ForumRepository): Forum {
    requireNotNull(localForumKey(forum.name)) { "贴吧名称无效。" }
    val name = forum.name.trim().removeSuffix("吧").trim()
    val info = try {
        withTimeoutOrNull(12_000) { repository.info(name) }
            ?: throw IOException("验证超时，请检查网络后重试。")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        throw IOException("暂时无法确认该贴吧，未添加订阅。${error.message.orEmpty()}", error)
    }
    require(info.forumId > 0) { "未找到这个贴吧，未添加订阅。请检查吧名。" }
    return forum.copy(id = info.forumId, name = name, displayName = "${name}吧")
}
