package com.maysk.design

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Фон экрана: градиент, аврора, аврора с силуэтом гор или сплошной цвет. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = LocalMaysk.current
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                when (c.backdrop) {
                    Backdrop.SOLID -> {}
                    Backdrop.GRADIENT -> if (!c.isLight) drawCircle(
                        Brush.radialGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width * 0.5f, 0f), radius = size.width),
                        radius = size.width, center = Offset(size.width * 0.5f, 0f),
                    )
                    Backdrop.AURORA, Backdrop.AURORA_MOUNTAINS -> {
                        val a1 = if (c.isLight) 0.18f else 0.30f
                        val a2 = if (c.isLight) 0.12f else 0.20f
                        drawCircle(
                            Brush.radialGradient(listOf(c.accent.copy(alpha = a1), Color.Transparent),
                                center = Offset(size.width * 0.2f, 0f), radius = size.width * 0.9f),
                            radius = size.width * 0.9f, center = Offset(size.width * 0.2f, 0f),
                        )
                        drawCircle(
                            Brush.radialGradient(listOf(c.accent3.copy(alpha = a2), Color.Transparent),
                                center = Offset(size.width, size.height * 0.08f), radius = size.width * 0.7f),
                            radius = size.width * 0.7f, center = Offset(size.width, size.height * 0.08f),
                        )
                        if (c.backdrop == Backdrop.AURORA_MOUNTAINS && !c.isLight) {
                            val h = size.height * 0.16f
                            val path = Path().apply {
                                moveTo(0f, h)
                                lineTo(size.width * 0.18f, h * 0.55f); lineTo(size.width * 0.3f, h * 0.8f)
                                lineTo(size.width * 0.48f, h * 0.35f); lineTo(size.width * 0.66f, h * 0.78f)
                                lineTo(size.width * 0.82f, h * 0.5f); lineTo(size.width, h * 0.72f)
                                lineTo(size.width, h * 1.25f); lineTo(0f, h * 1.25f); close()
                            }
                            drawPath(path, Brush.verticalGradient(listOf(c.accent2.copy(alpha = 0.20f), Color.Transparent), startY = h * 0.3f, endY = h * 1.25f))
                        }
                    }
                }
            },
        content = content,
    )
}

/** Стеклянная карточка. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    corner: Dp? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalMaysk.current
    val shape = RoundedCornerShape(corner ?: c.corner)
    var m = modifier.clip(shape).background(c.cardBg).border(1.dp, if (highlight) c.accent.copy(alpha = 0.85f) else c.border, shape)
    if (onClick != null || onLongClick != null) m = m.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    Box(m, content = content)
}

/** Верхняя панель экрана. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    titleContent: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад", tint = c.text) }
        else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (titleContent != null) titleContent()
            else Text(title, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = c.textDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalMaysk.current
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text("$action ›", color = c.textDim, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
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
            .background(if (enabled) Brush.horizontalGradient(listOf(c.button, c.accent2)) else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = LocalMaysk.current
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(c.accent.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c.accent, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = c.text, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) { Spacer(Modifier.height(6.dp)); Text(subtitle, color = c.textDim, style = MaterialTheme.typography.bodyMedium) }
        if (action != null && onAction != null) { Spacer(Modifier.height(18.dp)); GradientButton(action, onClick = onAction) }
    }
}

/** Ряд переключателей-«таблеток». */
@Composable
fun ChoiceChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMaysk.current
    Row(modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Text(
                label,
                color = if (sel) Color.White else c.textDim,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 8.dp).clip(RoundedCornerShape(50))
                    .background(if (sel) Brush.horizontalGradient(listOf(c.accent, c.accent2)) else Brush.horizontalGradient(listOf(c.cardBg, c.cardBg)))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            )
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String = "",
    label: String = "Название",
    confirm: String = "OK",
    password: Boolean = false,
    message: String? = null,
    selectNameOnly: Boolean = false,
    singleLine: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (message != null) { Text(message, style = MaterialTheme.typography.bodySmall); Spacer(Modifier.height(8.dp)) }
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, label = { Text(label) }, singleLine = singleLine,
                    visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                    modifier = Modifier.fillMaxWidth().then(if (singleLine) Modifier else Modifier.heightIn(min = 160.dp, max = 320.dp)),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String = "Удалить", danger: Boolean = true, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else LocalMaysk.current.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Отметка выделения поверх элемента. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    val c = LocalMaysk.current
    if (selected) Icon(Icons.Rounded.CheckCircle, "Выбрано", tint = c.accent, modifier = modifier.background(Color.White, CircleShape))
}
