package io.github.wnsdn517.silentanr.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import io.github.wnsdn517.silentanr.common.Contract
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Figures
import io.github.wnsdn517.silentanr.ui.theme.Radius
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.AppInfoCache
import io.github.wnsdn517.silentanr.util.Labels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Borderless rounded card, one step above the page background. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = Space.lg,
    color: Color = AppTheme.tokens.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = Radius.lg, color = color) {
        Column(Modifier.padding(padding), content = content)
    }
}

/**
 * Background for one row of a long lazy list, so consecutive rows read as a single card:
 * the first row rounds its top corners, the last its bottom ones.
 */
@Composable
fun Modifier.groupItem(index: Int, count: Int): Modifier {
    val r = 22.dp
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp,
        topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == count - 1) r else 0.dp,
        bottomEnd = if (index == count - 1) r else 0.dp,
    )
    return this
        .fillMaxWidth()
        .clip(shape)
        .background(AppTheme.tokens.surface)
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Space.xs, top = Space.lg, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.tokens.text,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Title at the top of a card. */
@Composable
fun CardTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = AppTheme.tokens.text)
    Spacer(Modifier.height(Space.md))
}

data class StatCell(val label: String, val value: String, val emphasis: Color? = null)

/** A card of big figures side by side. */
@Composable
fun StatStrip(cells: List<StatCell>, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Panel(modifier, padding = Space.sm) {
        Row {
            cells.forEach { cell ->
                Column(
                    Modifier
                        .weight(1f)
                        .padding(vertical = Space.md, horizontal = Space.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        cell.value,
                        style = MaterialTheme.typography.headlineSmall.merge(Figures),
                        color = cell.emphasis ?: t.text,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(cell.label, style = MaterialTheme.typography.bodySmall, color = t.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Small soft-filled pill. */
@Composable
fun Tag(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Radius.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

/** Icon in a soft circle of its color. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 44.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** App icon; falls back to the first letter of the label while loading or when missing. */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { AppInfoCache.iconBitmap(context, packageName) }.getOrNull()
        }
    }
    val t = AppTheme.tokens
    val shape = RoundedCornerShape(size * 0.28f)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(shape)
                .background(t.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                AppInfoCache.label(context, packageName).take(1).uppercase(),
                color = t.accent,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false,
    subtitleColor: Color? = null,
) {
    val t = AppTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(Space.md + 2.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: t.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            trailing()
        }
        if (showChevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = t.textMuted)
        }
    }
}

/** Row that shows its current value on the right and opens a picker on tap. */
@Composable
fun ValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    highlight: Boolean = false,
) {
    val t = AppTheme.tokens
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = leading,
        onClick = onClick,
        showChevron = !highlight,
        trailing = {
            when {
                value.isEmpty() -> Unit
                highlight -> Tag(value, t.accent)
                else -> Text(value, style = MaterialTheme.typography.bodyMedium, color = t.textMuted, maxLines = 1)
            }
        },
    )
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    ListRow(
        title = title,
        subtitle = subtitle,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}

@Composable
fun Divider(indent: Dp = Space.lg) {
    HorizontalDivider(Modifier.padding(start = indent), thickness = 1.dp, color = AppTheme.tokens.outline)
}

@Composable
fun EmptyState(title: String, subtitle: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl, horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = t.text)
        Spacer(Modifier.height(Space.xs))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = t.textMuted)
    }
}

/** Pill-shaped segmented control. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.pill)
            .background(t.surfaceMuted)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(Radius.pill)
                    .background(if (active) t.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (active) t.accent else t.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    TextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = t.textMuted) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, "Clear", tint = t.textMuted) }
        },
        singleLine = true,
        shape = Radius.pill,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = t.surface,
            unfocusedContainerColor = t.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val t = AppTheme.tokens
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Radius.pill)
            .background(t.surfaceMuted)
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(Radius.pill)
                .background(color)
        )
    }
}

/**
 * Bottom sheet listing the ANR actions. With [defaultLabel] set, a "follow the default" option
 * ([Contract.MODE_DEFAULT]) is offered first. [killWarning] replaces the Kill description.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSheet(
    title: String,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    defaultLabel: String? = null,
    header: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    killWarning: String? = null,
) {
    val t = AppTheme.tokens
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = t.surface) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = Space.lg)
        ) {
            Box(Modifier.padding(horizontal = Space.lg)) {
                if (header != null) header() else Text(title, style = MaterialTheme.typography.titleLarge, color = t.text)
            }
            Spacer(Modifier.height(Space.sm))
            val options = buildList {
                if (defaultLabel != null) add(Triple(Contract.MODE_DEFAULT, defaultLabel, "Use the rule above this one"))
                Labels.modes.forEach { add(Triple(it.id, it.title, it.description)) }
            }
            options.forEach { (id, label, desc) ->
                val warn = id == Contract.MODE_KILL && killWarning != null
                ListRow(
                    title = label,
                    subtitle = if (warn) killWarning else desc,
                    subtitleColor = if (warn) t.danger else null,
                    onClick = { onSelect(id); onDismiss() },
                    trailing = {
                        if (selected == id) Icon(Icons.Filled.Check, null, tint = t.accent)
                        else Spacer(Modifier.size(24.dp))
                    },
                )
            }
            if (footer != null) Box(Modifier.padding(horizontal = Space.lg)) { footer() }
        }
    }
}

const val SYSTEM_KILL_WARNING = "System app: closing it can restart the screen or break phone features"

/** Asks before stopping a system app; stopping one can restart the UI or break phone features. */
@Composable
fun SystemKillDialog(appLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val t = AppTheme.tokens
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = t.surface,
        title = { Text("Stop a system app?") },
        text = {
            Text("$appLabel is part of the system. Stopping it can restart the screen, drop calls or break other features until it starts again.")
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text("Stop anyway", color = t.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
