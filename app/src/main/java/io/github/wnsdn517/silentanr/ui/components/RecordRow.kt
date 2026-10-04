package io.github.wnsdn517.silentanr.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.wnsdn517.silentanr.ui.LabeledSummary
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Figures
import io.github.wnsdn517.silentanr.ui.theme.Space
import io.github.wnsdn517.silentanr.util.Format
import io.github.wnsdn517.silentanr.util.Labels

/** Two lines: app and time, then ANR type and what was done about it. */
@Composable
fun RecordRow(item: LabeledSummary, onClick: () -> Unit) {
    val t = AppTheme.tokens
    val r = item.s
    val outcomeColor = Labels.actionColor(r.action)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(r.packageName)
        Spacer(Modifier.width(Space.md + 2.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = t.text,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(Format.relative(r.timestamp), style = MaterialTheme.typography.labelSmall.merge(Figures), color = t.textMuted)
            }
            Text(
                buildAnnotatedString {
                    append(Labels.reasonCategory(r.reason))
                    append(" · ")
                    withStyle(SpanStyle(color = outcomeColor)) {
                        append(Labels.userAction(r.userAction) ?: Labels.action(r.action))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = t.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
