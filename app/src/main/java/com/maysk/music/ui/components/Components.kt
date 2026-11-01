package com.maysk.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.app.WallpaperManager
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import coil.compose.AsyncImage
import com.maysk.music.data.Track
import com.maysk.music.data.formatTime
import com.maysk.music.ui.theme.LocalMaysk
import com.maysk.music.ui.theme.LocalUi

/** Фон приложения: градиент / аврора / сплошной. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalMaysk.current
    val ui = LocalUi.current
    val ctx = LocalContext.current
    Box(modifier.fillMaxSize()) {
        // Базовый градиент темы
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
                .drawBehind {
                    if (c.background == com.maysk.music.data.BackgroundStyle.AURORA && !c.isLight) {
                        drawCircle(
                            Brush.radialGradient(listOf(c.accent.copy(alpha = 0.28f), Color.Transparent),
                                center = Offset(size.width * 0.15f, size.height * 0.1f), radius = size.width * 0.8f),
                            radius = size.width * 0.8f, center = Offset(size.width * 0.15f, size.height * 0.1f),
                        )
                        drawCircle(
                            Brush.radialGradient(listOf(c.accent3.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width * 0.95f, size.height * 0.45f), radius = size.width * 0.7f),
                            radius = size.width * 0.7f, center = Offset(size.width * 0.95f, size.height * 0.45f),
                        )
                    } else if (c.background == com.maysk.music.data.BackgroundStyle.GRADIENT && !c.isLight) {
                        drawCircle(
                            Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent),
                                center = Offset(size.width * 0.5f, 0f), radius = size.width),
                            radius = size.width, center = Offset(size.width * 0.5f, 0f),
                        )
                    }
                },
        )
        // Обои телефона
        if (c.background == com.maysk.music.data.BackgroundStyle.WALLPAPER) {
            val bmp = remember {
                try {
                    val d = WallpaperManager.getInstance(ctx).drawable
                    (d as? BitmapDrawable)?.bitmap
                } catch (_: Exception) { null }
            }
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                )
                Box(Modifier.matchParentSize().background(c.bgBottom.copy(alpha = 0.45f)))
            }
        }
        // Фото из галереи
        if (c.background == com.maysk.music.data.BackgroundStyle.IMAGE && ui.customBgUri.isNotBlank()) {
            AsyncImage(
                model = Uri.parse(ui.customBgUri),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.55f,
            )
            Box(Modifier.matchParentSize().background(c.bgBottom.copy(alpha = 0.45f)))
        }
        Box(Modifier.fillMaxSize(), content = content)
    }
}

/** Стеклянная карточка. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    highlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalMaysk.current
    val shape = RoundedCornerShape(corner ?: c.corner)
    var m = modifier.clip(shape).background(c.cardBg)
    m = m.border(1.dp, if (highlight) c.accent.copy(alpha = 0.8f) else c.border, shape)
    if (onClick != null || onLongClick != null) {
        m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    }
    Box(m, content = content)
}

/** Обложка с запасным градиентом, если у трека нет картинки. */
@Composable
fun Artwork(model: Any?, modifier: Modifier = Modifier, corner: Dp = 12.dp, seed: Long = 0L, iconSize: Dp = 24.dp) {
    val c = LocalMaysk.current
    val palettes = listOf(
        listOf(c.accent, c.accent2), listOf(c.accent3, c.accent), listOf(c.accent2, c.accent3),
        listOf(Color(0xFF0EA5E9), c.accent), listOf(Color(0xFFF97316), c.accent3),
    )
    val colors = palettes[Math.floorMod(seed.hashCode(), palettes.size)]
    Box(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(colors.map { it.copy(alpha = 0.85f) })),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(iconSize))
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(60.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = LocalMaysk.current.text)
            }
        } else Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (titleContent != null) titleContent()
            else Text(title, style = MaterialTheme.typography.titleLarge, color = LocalMaysk.current.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalMaysk.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                "$action ›",
                color = c.textDim,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp),
            )
        }
    }
}

/** Кнопка с градиентом и мягким свечением. */
@Composable
fun GradientButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalMaysk.current
    val shape = RoundedCornerShape(c.corner.coerceAtMost(28.dp))
    Row(
        modifier
            .then(if (c.glow && enabled) Modifier.shadow(14.dp, shape, ambientColor = c.button, spotColor = c.button) else Modifier)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(c.button, c.accent3.copy(alpha = 0.9f)))
                else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** Строка трека. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    isCurrent: Boolean,
    isPlaying: Boolean,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
) {
    val c = LocalMaysk.current
    val simple = LocalUi.current.simpleMode
    val art = if (simple) 60.dp else 50.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                when {
                    selected -> c.accent.copy(alpha = 0.22f)
                    isCurrent -> c.card.copy(alpha = (c.glassAlpha + 0.04f).coerceAtMost(1f))
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 6.dp, vertical = if (simple) 10.dp else 6.dp)
            .semantics { contentDescription = "${track.title}, ${track.artist}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Box {
            Artwork(track.artUri, Modifier.size(art), corner = 10.dp, seed = track.albumId)
            if (selectionMode && selected) {
                Box(Modifier.size(art).clip(RoundedCornerShape(10.dp)).background(c.accent.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color.White)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (isCurrent) c.accent else c.text,
                style = if (simple) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (simple) track.artist else "${track.artist} · ${formatTime(track.durationMs)}",
                color = c.textDim,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrent) {
            PlayingBars(active = isPlaying, color = c.accent)
            Spacer(Modifier.width(4.dp))
        }
        trailing?.invoke()
        if (onMenu != null && !selectionMode) {
            IconButton(onClick = onMenu) { Icon(Icons.Rounded.MoreVert, "Действия", tint = c.textDim) }
        }
    }
}

/** Маленький анимированный эквалайзер для текущего трека. */
@Composable
fun PlayingBars(active: Boolean, color: Color, modifier: Modifier = Modifier) {
    val anim = LocalUi.current.animationsEnabled && active
    val t = rememberInfiniteTransition(label = "bars")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "p")
    Row(modifier.height(16.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(0.4f, 1f, 0.65f).forEachIndexed { i, base ->
            val h = if (anim) (0.3f + ((p + i * 0.33f) % 1f) * 0.7f) else base
            Box(Modifier.width(3.dp).fillMaxHeightFraction(h).clip(RoundedCornerShape(2.dp)).background(color))
        }
    }
}

private fun Modifier.fillMaxHeightFraction(f: Float): Modifier = this.then(Modifier.height((16 * f).dp))

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalMaysk.current
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodyMedium)
        }
        if (action != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            GradientButton(action, onClick = onAction)
        }
    }
}

/** Плитка с иконкой (папки / разделы «Моя музыка»). */
@Composable
fun IconTile(
    title: String,
    subtitle: String?,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = LocalMaysk.current
    GlassCard(modifier, onClick = onClick, onLongClick = onLongClick) {
        Box(
            Modifier.matchParentSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), Color.Transparent)))
        )
        Column(
            Modifier.align(Alignment.Center).fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, color = c.text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
fun ColumnScope.Gap(h: Dp) = Spacer(Modifier.height(h))
