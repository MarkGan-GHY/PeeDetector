package com.example.peedetector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.peedetector.ble.AlarmRule
import com.example.peedetector.ble.CapacitanceSample
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

private val ChannelColors = listOf(
    Color(0xFF1565C0),
    Color(0xFF2E7D32),
    Color(0xFFF57C00),
    Color(0xFF8E24AA),
)

@Composable
fun CapacitanceChart(
    samples: List<CapacitanceSample>,
    alarmRule: AlarmRule,
    windowMillis: Long,
    onWindowChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("四通道电容曲线", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            WindowSlider(windowMillis, onWindowChange)
            Spacer(Modifier.height(4.dp))

            if (samples.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "暂无电容数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                    )
                }
                return@Column
            }

            val latestTimestamp = samples.maxOf { it.receivedAtMillis }
            val windowStart = latestTimestamp - windowMillis
            val visibleSamples = samples.filter { it.receivedAtMillis >= windowStart }
            val range = calculateYRange(visibleSamples)

            Text(
                "共同纵轴 ${formatValue(range.start)}～${formatValue(range.endInclusive)} pF",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "四条横轴同步显示最近 ${formatWindowLabel(windowMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
            )
            Spacer(Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (channel in 1..4) {
                    ChannelTrack(
                        channel = channel,
                        samples = visibleSamples.filter { it.channel == channel },
                        windowStartMillis = windowStart,
                        windowEndMillis = latestTimestamp,
                        windowMillis = windowMillis,
                        yStart = range.start,
                        yEnd = range.endInclusive,
                        alarmRule = alarmRule,
                        color = ChannelColors[channel - 1],
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelTrack(
    channel: Int,
    samples: List<CapacitanceSample>,
    windowStartMillis: Long,
    windowEndMillis: Long,
    windowMillis: Long,
    yStart: Float,
    yEnd: Float,
    alarmRule: AlarmRule,
    color: Color,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text("CH$channel", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            Text(
                samples.lastOrNull()?.let { formatCapacitance(it.valuePf) } ?: "等待数据",
                style = MaterialTheme.typography.labelMedium,
                color = if (samples.isEmpty()) Color.Gray else color,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            val left = 2.dp.toPx()
            val right = size.width - 2.dp.toPx()
            val top = 4.dp.toPx()
            val bottom = size.height - 5.dp.toPx()
            val axisColor = Color.Gray.copy(alpha = 0.45f)

            drawLine(
                color = axisColor,
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = 1.dp.toPx(),
            )
            repeat(5) { index ->
                val x = left + (right - left) * index / 4f
                drawLine(
                    color = axisColor,
                    start = Offset(x, bottom),
                    end = Offset(x, bottom + 3.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            fun yFor(value: Float): Float {
                val fraction = (value - yStart) / (yEnd - yStart)
                return bottom - fraction.coerceIn(0f, 1f) * (bottom - top)
            }

            fun xFor(timestamp: Long): Float {
                val window = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
                val fraction = (timestamp - windowStartMillis).toFloat() / window
                return left + fraction.coerceIn(0f, 1f) * (right - left)
            }

            if (0f in yStart..yEnd) {
                val zeroY = yFor(0f)
                drawLine(
                    color = axisColor.copy(alpha = 0.25f),
                    start = Offset(left, zeroY),
                    end = Offset(right, zeroY),
                    strokeWidth = 1.dp.toPx(),
                )
            }

            val threshold = alarmRule.thresholdPf.toFloat()
            if (threshold in yStart..yEnd) {
                val thresholdY = yFor(threshold)
                drawLine(
                    color = Color(0xFFC62828).copy(alpha = 0.65f),
                    start = Offset(left, thresholdY),
                    end = Offset(right, thresholdY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(6.dp.toPx(), 4.dp.toPx()),
                    ),
                )
            }

            if (samples.isNotEmpty()) {
                if (samples.size == 1) {
                    drawCircle(
                        color = color,
                        radius = 3.dp.toPx(),
                        center = Offset(xFor(samples.first().receivedAtMillis), yFor(samples.first().valuePf.toFloat())),
                    )
                } else {
                    val path = Path()
                    samples.forEachIndexed { index, sample ->
                        val point = Offset(
                            x = xFor(sample.receivedAtMillis),
                            y = yFor(sample.valuePf.toFloat()),
                        )
                        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Text("−${formatWindowLabel(windowMillis)}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Spacer(Modifier.weight(1f))
            Text("现在", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@Composable
private fun WindowSlider(windowMillis: Long, onWindowChange: (Long) -> Unit) {
    val windowSeconds = (windowMillis / 1000L).coerceIn(60L, 600L).toFloat()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("横轴宽度", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(8.dp))
        Slider(
            value = windowSeconds,
            onValueChange = { seconds ->
                val stepMillis = 30_000L
                val snapped = ((seconds * 1000L).roundToLong() / stepMillis) * stepMillis
                onWindowChange(snapped)
            },
            valueRange = 60f..600f,
            steps = 17,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Text(formatWindowLabel(windowMillis), style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatWindowLabel(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        seconds == 0L -> "${minutes} 分钟"
        minutes == 0L -> "${seconds} 秒"
        else -> "${minutes} 分 ${seconds} 秒"
    }
}

private fun calculateYRange(samples: List<CapacitanceSample>): ClosedFloatingPointRange<Float> {
    val minimum = samples.minOf { it.valuePf }.toFloat()
    val maximum = samples.maxOf { it.valuePf }.toFloat()
    val span = maximum - minimum
    val padding = if (span > 0.0001f) {
        span * 0.15f
    } else {
        maxOf(abs(maximum) * 0.1f, 0.01f)
    }
    return (minimum - padding)..(maximum + padding)
}

internal fun formatCapacitance(valuePf: Double): String =
    "${formatValue(valuePf.toFloat())} pF"

private fun formatValue(value: Float): String =
    String.format(Locale.US, "%.3f", value)
