package dev.infinityf4p.tiebapure.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed interface ReaderState {
    data class Loading(val message: String = "正在加载") : ReaderState
    data class Empty(val title: String, val message: String? = null) : ReaderState
    data class Error(val title: String, val message: String, val retryLabel: String = "重试") : ReaderState
}


@Composable
fun ReaderPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp)
        .background(MaterialTheme.colorScheme.background)
        .padding(start = if (onBack == null) 16.dp else 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { back -> IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }
        Text(title, Modifier.weight(1f).padding(vertical = 8.dp), style = MaterialTheme.typography.titleMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

/** Shared content surface for both reader themes. */
@Composable
fun ReaderCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    onClick: (() -> Unit)? = null,
    border: BorderStroke? = null,
    content: @Composable () -> Unit,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(cornerRadius))
    Surface(
        modifier = if (onClick == null) cardModifier else cardModifier.clickable(
            role = Role.Button,
            onClick = onClick,
        ),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = border,
        shape = RoundedCornerShape(cornerRadius),
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** A non-sticky section band used between reader content groups. */
@Composable
fun ReaderSectionBand(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        content()
    }
}

/** A shallow edge between groups; real nested replies use a quieter hairline. */
@Composable
fun ReaderContentDivider(modifier: Modifier = Modifier, nested: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val edge = colors.outlineVariant.copy(alpha = if (nested) .5f else .65f)
    val highlight = colors.surface.copy(alpha = .8f)
    Box(modifier.fillMaxWidth().height(if (nested) 1.dp else 6.dp)
        .background(colors.surfaceContainerLow).drawBehind {
            drawLine(edge, Offset(0f, .5.dp.toPx()), Offset(size.width, .5.dp.toPx()), strokeWidth = .5.dp.toPx())
            if (!nested) drawLine(highlight, Offset(0f, size.height - .5.dp.toPx()), Offset(size.width, size.height - .5.dp.toPx()), strokeWidth = 1.dp.toPx())
        })
}

fun threadIdentityAvatarUrl(thread: dev.infinityf4p.tiebapure.core.model.ThreadSummary): String? =
    if (thread.forumRoute() != null) thread.forumAvatarUrl else thread.author.portrait

@Composable
fun ReaderInteractionStats(
    comments: Int?,
    likes: Int?,
    modifier: Modifier = Modifier,
    onCommentsClick: (() -> Unit)? = null,
    alignEnd: Boolean = false,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.spacedBy(8.dp)) {
        likes?.takeIf { it > 0 }?.let {
            ReaderInteractionStat(it, "点赞数", ReaderIcons.Upvote, null)
        }
        comments?.let {
            ReaderInteractionStat(it, "评论", ReaderIcons.Comment, onCommentsClick)
        }
    }
}

@Composable
fun ReaderShareAction(onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "分享帖子" }, contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Icon(ReaderIcons.Share, null, Modifier.padding(horizontal = 10.dp, vertical = 4.dp).size(20.dp))
        }
    }
}

@Composable
fun ReaderCommunityRow(
    name: String,
    time: String?,
    onOpen: () -> Unit,
    avatar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subscribed: Boolean = false,
    onSubscribe: (() -> Unit)? = null,
    more: @Composable () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.weight(1f).heightIn(min = 48.dp).clickable(onClick = onOpen), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            avatar()
            Text(name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            time?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(Modifier.offset(x = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            onSubscribe?.let { action ->
                Box(Modifier.heightIn(min = 48.dp).clickable(onClick = action), contentAlignment = Alignment.Center) {
                    Surface(shape = RoundedCornerShape(50), color = if (subscribed) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.inverseSurface) {
                        Text(if (subscribed) "已订阅" else "订阅", Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (subscribed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.inverseOnSurface)
                    }
                }
            }
            more()
        }
    }
}

@Composable
private fun ReaderInteractionStat(value: Int, label: String, icon: ImageVector, onClick: (() -> Unit)?) {
    val base = Modifier.heightIn(min = 48.dp).semantics {
        contentDescription = if (label == "评论") "评论，当前${value}条评论" else "$label，当前${value}个赞"
    }
    Box(if (onClick == null) base else base.clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Surface(modifier = Modifier.heightIn(min = 30.dp), shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(horizontal = 11.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text(compactInteractionCount(value), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

fun compactInteractionCount(value: Int): String {
    val count = value.coerceAtLeast(0)
    return when {
        count < 1_000 -> count.toString()
        count < 10_000 -> compactDecimal(count / 1_000.0) + "k"
        else -> compactDecimal(count / 10_000.0) + "w"
    }
}

internal fun interactionStatCenterFractions(count: Int): List<Float> = when {
    count <= 0 -> emptyList()
    count == 1 -> listOf(0.5f)
    else -> (1..count).map { index -> index.toFloat() / (count + 1).toFloat() }
}

private fun compactDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else "%.1f".format(java.util.Locale.US, rounded)
}

@Composable
fun ReaderStatePane(
    state: ReaderState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (state) {
                is ReaderState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    Text(state.message, style = MaterialTheme.typography.bodyMedium)
                }
                is ReaderState.Empty -> {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    state.message?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                is ReaderState.Error -> {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    onRetry?.let { retry -> TextButton(onClick = retry) { Text(state.retryLabel) } }
                }
            }
        }
    }
}

@Composable
fun ReaderSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ReaderNavigationRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: String? = null,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(enabled = enabled, role = Role.Button, onClick = onClick,
                onLongClick = onLongClick, onLongClickLabel = if (onLongClick == null) null else "更多操作")
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            Icon(it, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(12.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null,
            modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(modifier = Modifier.padding(start = if (icon == null) 16.dp else 50.dp))
}
