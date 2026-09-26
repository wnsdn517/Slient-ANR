package io.github.silentanr.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.silentanr.ui.theme.AppTheme
import io.github.silentanr.ui.theme.Space

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val t = AppTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                start = if (onBack != null) Space.xs else Space.xl,
                end = Space.sm,
                top = if (onBack != null) Space.sm else Space.xl,
                bottom = Space.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = t.text) }
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = if (onBack != null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                color = t.text,
            )
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = t.textMuted)
        }
        actions()
    }
}

/** Standard page: status-bar aware column with a header and lazily laid out content. */
@Composable
fun ScreenList(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    itemSpacing: Dp = Space.md,
    content: LazyListScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        ScreenHeader(title, subtitle, onBack, actions)
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.lg, end = Space.lg, top = Space.xs, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(itemSpacing),
            content = content,
        )
    }
}

/** Vertical gap for lists laid out with `itemSpacing = 0.dp`. */
fun LazyListScope.gap(height: Dp = Space.md) = item { Spacer(Modifier.height(height)) }

/** Hairline between rows of a grouped list, indented past the leading icon. */
@Composable
fun RowDivider(indent: Dp = 70.dp) = Divider(indent)
