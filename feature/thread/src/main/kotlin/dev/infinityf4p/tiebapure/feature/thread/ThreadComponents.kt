package dev.infinityf4p.tiebapure.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.infinityf4p.tiebapure.core.designsystem.compactInteractionCount
import dev.infinityf4p.tiebapure.core.media.AvatarImage
import dev.infinityf4p.tiebapure.core.model.ContentBlock
import dev.infinityf4p.tiebapure.core.model.ImageContent
import dev.infinityf4p.tiebapure.core.model.Post
import dev.infinityf4p.tiebapure.core.model.Subpost
import dev.infinityf4p.tiebapure.core.model.TiebaEmoticon
import dev.infinityf4p.tiebapure.core.model.UserSummary
import dev.infinityf4p.tiebapure.core.model.VideoContent
import dev.infinityf4p.tiebapure.core.model.plainText
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun commentBodyInsetDp(availableWidthDp: Float, fontScale: Float): Int =
    if (availableWidthDp < 360f || fontScale >= 1.3f) 24 else 48

/** Flat Tieba replies share one reading canvas without implying a comment tree. */
@Composable
internal fun CommentContainer(
    modifier: Modifier = Modifier,
    inset: Dp = 16.dp,
    verticalPadding: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
        .padding(start = inset, end = inset,
        top = verticalPadding, bottom = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

@Composable
internal fun AuthorLine(
    author: UserSummary,
    floor: Int?,
    likeCount: Int,
    isLiked: Boolean,
    isLikeUpdating: Boolean,
    onToggleLike: (() -> Unit)?,
    onUserClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    isThreadAuthor: Boolean = false,
    isMainPost: Boolean = false,
    isCollapsed: Boolean = false,
    onToggleCollapsed: (() -> Unit)? = null,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(enabled = author.id > 0) { onUserClick(author.id) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AvatarImage(author.portrait, author.resolvedDisplayName, Modifier.size(36.dp))
            androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(author.resolvedDisplayName, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                userLevelBadgeText(author.level, author.levelName)?.let { label ->
                    val colors = MaterialTheme.colorScheme
                    val dark = colors.surface.luminance() < .5f
                    IdentityBadge(label,
                        if (dark) Color(0xFF492C24) else Color(0xFFFFE3D6),
                        if (dark) Color(0xFFFFD7C2) else Color(0xFF4E210E))
                }
                if (isThreadAuthor) IdentityBadge("楼主", MaterialTheme.colorScheme.primary.copy(alpha = .10f), MaterialTheme.colorScheme.primary)
            }
        }
        floor?.takeIf { it > 0 && !isMainPost }?.let {
            Text("${it}楼", modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        onToggleCollapsed?.let { toggle ->
            IconButton(onClick = toggle, modifier = Modifier.testTag("thread-collapse-$floor")
                .semantics { stateDescription = if (isCollapsed) "已折叠" else "已展开" }) {
                Icon(if (isCollapsed) Icons.Outlined.Add else Icons.Outlined.Remove,
                    contentDescription = (if (isCollapsed) "展开" else "折叠") + (floor?.takeIf { it > 0 }?.let { "${it}楼" } ?: "该楼") + "回复")
            }
        }
    }
}


@Composable
private fun IdentityBadge(text: String, background: Color, contentColor: Color) {
    Surface(shape = RoundedCornerShape(4.dp), color = background, contentColor = contentColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PostBody(
    post: Post,
    onReply: ((Post) -> Unit)?,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onImagesClick: (List<ImageContent>, Int) -> Unit,
    onVideoClick: (VideoContent) -> Unit,
    onOpenSubposts: (Post) -> Unit,
    modifier: Modifier = Modifier,
    isMainPost: Boolean = false,
    threadAuthorId: Long? = null,
    showSubpostPreview: Boolean = true,
    showSubpostOpenAction: Boolean = false,
    bodyInset: Dp = 0.dp,
) {
    val hasPreview = showSubpostPreview && (post.previewSubposts.isNotEmpty() || post.subpostCount > 0)
    val placement = threadPostMetadataPlacement(isMainPost, hasPreview)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("thread-post-body-${post.id}")
            .then(if (onReply == null) Modifier else Modifier.clickable { onReply(post) }),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = bodyInset)) {
            RichContent(
                blocks = post.blocks,
                onLinkClick = onLinkClick,
                onUserClick = onUserClick,
                onImagesClick = onImagesClick,
                onVideoClick = onVideoClick,
                modifier = Modifier.fillMaxWidth(),
                textStyle = if (isMainPost) RichContentTextStyle.Body else RichContentTextStyle.Reply,
            )
            MetadataLine(
                createdAtEpochSeconds = post.createdAtEpochSeconds,
                ipAddress = post.ipAddress ?: post.author.ipAddress,
                placement = placement,
                onReply = onReply?.let { reply -> { reply(post) } },
                likeCount = if (isMainPost) 0 else post.likeCount,
                copyText = subpostPreviewText(post.blocks),
                onAuthor = post.author.id.takeIf { it > 0 }?.let { id -> { onUserClick(id) } },
            )
        }
        if (hasPreview) {
            val previewIndent = if (bodyInset > 0.dp) bodyInset - 36.dp else 0.dp
            SubpostPreview(
                post = post,
                threadAuthorId = threadAuthorId,
                onUserClick = onUserClick,
                onOpen = { onOpenSubposts(post) },
                alwaysShowOpenAction = showSubpostOpenAction,
                modifier = Modifier.offset(x = previewIndent.coerceAtMost(0.dp))
                    .padding(start = previewIndent.coerceAtLeast(0.dp)),
            )
        }
    }
}

@Composable
private fun MetadataLine(
    createdAtEpochSeconds: Long?,
    ipAddress: String?,
    placement: ThreadPostMetadataPlacement,
    onReply: (() -> Unit)?,
    likeCount: Int = 0,
    copyText: String = "",
    onAuthor: (() -> Unit)? = null,
) {
    val readingPreferences = LocalReadingPreferences.current
    val textMetrics = readerScaledTextMetrics(
        baseFontSize = 12f,
        baseLineHeight = MaterialTheme.typography.bodySmall.lineHeight.value,
        fontScale = readingPreferences.fontSize.scale,
        lineSpacingMultiplier = readingPreferences.lineSpacing.multiplier,
    )
    val visualHeight = maxOf(48f, placement.visualHeight.toFloat(), textMetrics.lineHeight * androidx.compose.ui.platform.LocalDensity.current.fontScale + 2f)
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = placement.topSpacing.dp, bottom = placement.bottomSpacing.dp)
            .heightIn(min = visualHeight.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = listOfNotNull(
                formatThreadTimestamp(createdAtEpochSeconds),
                normalizeThreadLocation(ipAddress),
            ).joinToString("  "),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = textMetrics.fontSize.sp,
                lineHeight = textMetrics.lineHeight.sp,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (likeCount > 0) {
            Row(Modifier.testTag("thread-like-action").semantics(mergeDescendants = true) { contentDescription = "真实点赞数，${likeCount}个赞" },
                verticalAlignment = Alignment.CenterVertically) {
                Icon(dev.infinityf4p.tiebapure.core.designsystem.ReaderIcons.Upvote, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(compactInteractionCount(likeCount), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp))
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "更多评论操作", Modifier.size(18.dp)) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("复制文本") }, enabled = copyText.isNotBlank(), onClick = { menu = false; clipboard.setText(AnnotatedString(copyText)) })
                onAuthor?.let { open -> DropdownMenuItem(text = { Text("查看作者主页") }, onClick = { menu = false; open() }) }
            }
        }
        if (onReply != null) {
            Row(
                modifier = Modifier
                    .testTag("thread-metadata-reply")
                    .sizeIn(minWidth = 56.dp, minHeight = visualHeight.dp)
                    .clickable(onClick = onReply),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Icon(
                    Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "回复",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = textMetrics.fontSize.sp,
                        lineHeight = textMetrics.lineHeight.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubpostPreview(
    post: Post,
    threadAuthorId: Long?,
    onUserClick: (Long) -> Unit,
    onOpen: () -> Unit,
    alwaysShowOpenAction: Boolean,
    modifier: Modifier = Modifier,
) {
    val visibleSubposts = post.previewSubposts.take(3)
    val readingPreferences = LocalReadingPreferences.current
    val textMetrics = readerScaledTextMetrics(
        baseFontSize = 16f,
        baseLineHeight = 22f,
        fontScale = readingPreferences.fontSize.scale,
        lineSpacingMultiplier = readingPreferences.lineSpacing.multiplier,
    )
    val previewStyle = MaterialTheme.typography.bodyMedium.copy(
        fontFamily = dev.infinityf4p.tiebapure.core.designsystem.readerFontFamily(readingPreferences.fontFamily),
        fontSize = textMetrics.fontSize.sp,
        lineHeight = textMetrics.lineHeight.sp,
    )
    CommentContainer(
        modifier = modifier,
        inset = 0.dp, verticalPadding = 4.dp,
    ) {
        visibleSubposts.forEach { subpost ->
            val authorColor = MaterialTheme.colorScheme.onSurfaceVariant
            val primaryColor = MaterialTheme.colorScheme.primary
            val richPreview = buildAnnotatedString {
                val validAuthorId = subpost.author.id.takeIf { it > 0L }
                if (validAuthorId != null) {
                    pushLink(
                        LinkAnnotation.Clickable(
                            tag = "user-$validAuthorId",
                            styles = TextLinkStyles(style = SpanStyle(color = authorColor, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = previewStyle.fontFamily)),
                            linkInteractionListener = LinkInteractionListener { onUserClick(validAuthorId) },
                        ),
                    )
                } else {
                    pushStyle(SpanStyle(color = authorColor, fontWeight = FontWeight.Medium, fontSize = 15.sp, fontFamily = previewStyle.fontFamily))
                }
                append(subpost.author.resolvedDisplayName)
                pop()
                if (threadAuthorId != null && threadAuthorId > 0L && subpost.author.id == threadAuthorId) {
                    append(" ")
                    appendInlineContent("thread-author", "楼主")
                }
                append(": ${subpostPreviewText(subpost.blocks)}")
            }
            Row(Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AvatarImage(subpost.author.portrait, subpost.author.resolvedDisplayName, Modifier.size(28.dp))
                SelectionContainer {
                    Text(
                        text = richPreview,
                        inlineContent = mapOf("thread-author" to InlineTextContent(
                            Placeholder(36.sp, 22.sp, PlaceholderVerticalAlign.Center),
                        ) { IdentityBadge("楼主", primaryColor.copy(alpha = .10f), primaryColor) }),
                        modifier = Modifier.fillMaxWidth().testTag("thread-preview-text-${subpost.id}"),
                        style = previewStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (alwaysShowOpenAction || post.subpostCount > visibleSubposts.size) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SUBPOST_OPEN_ALL_TOUCH_HEIGHT_DP.dp)
                    .testTag("thread-subpost-open-all")
                    .clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .padding(start = 36.dp)
                        .heightIn(min = SUBPOST_OPEN_ALL_VISUAL_HEIGHT_DP.dp)
                        .testTag("thread-subpost-open-all-visual"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (post.subpostCount > 0) "查看全部${post.subpostCount}条回复" else "查看回复",
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SubpostRow(
    subpost: Subpost,
    onReply: ((Subpost) -> Unit)?,
    onUserClick: (Long) -> Unit,
    onLinkClick: (String) -> Unit,
    onImagesClick: (List<ImageContent>, Int) -> Unit,
    onVideoClick: (VideoContent) -> Unit,
    isLikeUpdating: Boolean,
    onToggleLike: (() -> Unit)?,
    threadAuthorId: Long? = null,
    bodyInset: Dp = 48.dp,
) {
    CommentContainer {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("thread-subpost-body-${subpost.id}")
                .then(if (onReply == null) Modifier else Modifier.clickable { onReply(subpost) }),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AuthorLine(
                author = subpost.author,
                floor = subpost.floor,
                likeCount = subpost.likeCount,
                isLiked = subpost.isLiked,
                isLikeUpdating = isLikeUpdating,
                onToggleLike = onToggleLike,
                onUserClick = onUserClick,
                isThreadAuthor = threadAuthorId != null && threadAuthorId > 0L && subpost.author.id == threadAuthorId,
            )
            Column(Modifier.fillMaxWidth().padding(start = bodyInset)) {
                RichContent(
                    blocks = subpost.blocks,
                    onLinkClick = onLinkClick,
                    onUserClick = onUserClick,
                    onImagesClick = onImagesClick,
                    onVideoClick = onVideoClick,
                    textStyle = RichContentTextStyle.Subpost,
                )
                MetadataLine(
                    subpost.createdAtEpochSeconds,
                    subpost.ipAddress ?: subpost.author.ipAddress,
                    ThreadPostMetadataPlacement.StandaloneReply,
                    onReply?.let { reply -> { reply(subpost) } },
                    likeCount = subpost.likeCount, copyText = subpostPreviewText(subpost.blocks),
                    onAuthor = subpost.author.id.takeIf { it > 0 }?.let { id -> { onUserClick(id) } },
                )
            }
        }
    }
}

internal enum class ThreadPostMetadataPlacement(
    val visualHeight: Int,
    val topSpacing: Int,
    val bottomSpacing: Int,
    val cardBottomPadding: Int,
) {
    MainPost(visualHeight = 28, topSpacing = 0, bottomSpacing = 0, cardBottomPadding = 4),
    StandaloneReply(visualHeight = 20, topSpacing = 0, bottomSpacing = 0, cardBottomPadding = 4),
    BeforeSubpostPreview(visualHeight = 20, topSpacing = 0, bottomSpacing = 0, cardBottomPadding = 4),
}

internal data class ReaderScaledTextMetrics(val fontSize: Float, val lineHeight: Float)

internal fun readerScaledTextMetrics(
    baseFontSize: Float,
    baseLineHeight: Float,
    fontScale: Float,
    lineSpacingMultiplier: Float,
): ReaderScaledTextMetrics {
    val resolvedFontSize = baseFontSize * fontScale.coerceAtLeast(0.5f)
    val resolvedLineHeight = (baseLineHeight * fontScale.coerceAtLeast(0.5f) * lineSpacingMultiplier.coerceAtLeast(0.5f))
        .coerceAtLeast(resolvedFontSize * 1.1f)
    return ReaderScaledTextMetrics(resolvedFontSize, resolvedLineHeight)
}

internal fun replyFilterControlHeight(metrics: ReaderScaledTextMetrics): Float =
    maxOf(48f, metrics.lineHeight + 12f)

internal fun threadPostMetadataPlacement(
    isMainPost: Boolean,
    hasPreviewSubposts: Boolean,
): ThreadPostMetadataPlacement = when {
    isMainPost -> ThreadPostMetadataPlacement.MainPost
    hasPreviewSubposts -> ThreadPostMetadataPlacement.BeforeSubpostPreview
    else -> ThreadPostMetadataPlacement.StandaloneReply
}

internal fun userLevelBadgeText(level: Int?, levelName: String?): String? {
    val name = levelName.orEmpty().filterNot(Char::isISOControl).trim().takeIf { it.isNotEmpty() } ?: return null
    return level?.takeIf { it > 0 }?.let { "$it $name" } ?: name
}

internal fun normalizeThreadLocation(value: String?): String? {
    var normalized = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
    listOf("IP属地：", "IP属地:", "来自").firstOrNull(normalized::startsWith)?.let { prefix ->
        normalized = normalized.removePrefix(prefix).trim()
    }
    return normalized.takeIf(String::isNotEmpty)
}

internal fun subpostPreviewText(blocks: List<ContentBlock>): String = blocks.joinToString("") { block ->
    when (block) {
        is ContentBlock.Emoticon -> TiebaEmoticon.displayText(block.code)
        else -> block.plainText().orEmpty()
    }
}

internal fun formatCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 10_000 -> compactCount(value / 1_000.0, "k")
    else -> compactCount(value / 10_000.0, "w")
}

private fun compactCount(value: Double, suffix: String): String =
    "%.1f%s".format(Locale.US, value, suffix)

internal fun formatThreadTimestamp(
    epochSeconds: Long?,
    nowMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String? {
    val seconds = epochSeconds?.takeIf { it > 0 } ?: return null
    val timestampMillis = seconds * 1_000L
    val elapsedSeconds = ((nowMillis - timestampMillis) / 1_000L).coerceAtLeast(0L)
    if (elapsedSeconds < 60L) return "刚刚"
    if (elapsedSeconds < 3_600L) return "${elapsedSeconds / 60L}分钟前"

    val now = Calendar.getInstance(timeZone, locale).apply { timeInMillis = nowMillis }
    val value = Calendar.getInstance(timeZone, locale).apply { timeInMillis = timestampMillis }
    val sameDay = now.get(Calendar.ERA) == value.get(Calendar.ERA) &&
        now.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return SimpleDateFormat("HH:mm", locale).apply { this.timeZone = timeZone }.format(Date(timestampMillis))

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val wasYesterday = yesterday.get(Calendar.ERA) == value.get(Calendar.ERA) &&
        yesterday.get(Calendar.YEAR) == value.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == value.get(Calendar.DAY_OF_YEAR)
    if (wasYesterday) {
        return "昨天 ${SimpleDateFormat("HH:mm", locale).apply { this.timeZone = timeZone }.format(Date(timestampMillis))}"
    }
    val pattern = if (now.get(Calendar.YEAR) == value.get(Calendar.YEAR)) "MM-dd" else "yyyy-MM-dd"
    return SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(Date(timestampMillis))
}

internal fun floorReplyTitle(floor: Int, count: Int?): String {
    val label = if (floor > 0) "${floor}楼" else "该楼"
    return count?.takeIf { it >= 0 }?.let { "$label · ${it}条回复" } ?: "${label}的回复"
}
