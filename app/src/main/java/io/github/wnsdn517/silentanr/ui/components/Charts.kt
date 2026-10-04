package io.github.wnsdn517.silentanr.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.wnsdn517.silentanr.ui.theme.AppTheme
import io.github.wnsdn517.silentanr.ui.theme.Figures
import io.github.wnsdn517.silentanr.ui.theme.Space

data class ChartSlice(val label: String, val value: Int, val color: Color)

/** Vertical bar chart with rounded tops. Labels are thinned out to avoid overlap. */
@Composable
fun BarChart(
    values: List<Int>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.tokens.accent,
    height: Dp = 140.dp,
    labelEvery: Int = 1,
    highlightLast: Boolean = true,
) {
    val t = AppTheme.tokens
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.58f).coerceAtMost(22.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                val radius = CornerRadius(barW / 2, barW / 2)
                // Empty days keep a faint full-height track so the week still reads as a row.
                drawRoundRect(t.surfaceMuted, Offset(x, 0f), Size(barW, size.height), radius)
                if (v > 0) {
                    val h = (size.height * v / max).coerceAtLeast(barW)
                    val c = if (highlightLast && i == values.lastIndex) color else color.copy(alpha = 0.55f)
                    drawRoundRect(c, Offset(x, size.height - h), Size(barW, h), radius)
                }
            }
        }
        Spacer(Modifier.height(Space.xs))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, l ->
                Text(
                    if (i % labelEvery == 0 || i == labels.lastIndex) l else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = t.textMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

/** Donut chart with legend on the right. */
@Composable
fun DonutChart(slices: List<ChartSlice>, centerLabel: String, centerValue: String, modifier: Modifier = Modifier) {
    val t = AppTheme.tokens
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(120.dp)) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(t.surfaceMuted, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                var start = -90f
                val gap = if (slices.count { it.value > 0 } > 1) 14f else 0f
                slices.filter { it.value > 0 }.forEach { s ->
                    val sweep = 360f * s.value / total
                    drawArc(
                        s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                    start += sweep
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerValue, style = MaterialTheme.typography.titleLarge.merge(Figures), color = t.text)
                Text(centerLabel, style = MaterialTheme.typography.labelSmall, color = t.textMuted)
            }
        }
        Spacer(Modifier.width(Space.xl))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(s.color)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(s.label, style = MaterialTheme.typography.bodySmall, color = t.text, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${s.value}", style = MaterialTheme.typography.labelMedium.merge(Figures), color = t.textMuted)
                }
            }
        }
    }
}

/** Ranked horizontal bars (e.g. top apps). */
@Composable
fun RankedBars(
    items: List<Pair<String, Int>>,
    modifier: Modifier = Modifier,
    color: Color = AppTheme.tokens.accent,
    leading: (@Composable (index: Int) -> Unit)? = null,
    onClick: ((index: Int) -> Unit)? = null,
) {
    val t = AppTheme.tokens
    val max = (items.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        items.forEachIndexed { i, (label, value) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier)
                    .padding(vertical = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leading != null) {
                    leading(i)
                    Spacer(Modifier.width(Space.md))
                }
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = t.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$value", style = MaterialTheme.typography.labelLarge.merge(Figures), color = t.text)
                    }
                    Spacer(Modifier.height(Space.xs))
                    ProgressBar(value.toFloat() / max, color, height = 6.dp)
                }
            }
        }
    }
}

/** Circular usage gauge (memory). */
@Composable
fun RingGauge(fraction: Float, color: Color, modifier: Modifier = Modifier, size: Dp = 120.dp, content: @Composable () -> Unit) {
    val t = AppTheme.tokens
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(t.surfaceMuted, 135f, 270f, false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(color, 135f, 270f * fraction.coerceIn(0f, 1f), false, Offset(inset, inset), arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        content()
    }
}
