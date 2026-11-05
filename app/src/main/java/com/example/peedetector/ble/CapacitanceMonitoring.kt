package com.example.peedetector.ble

/** 当前版本仅提供默认报警规则，后续可在此扩展其他选项。 */
enum class AlarmRule(
    val displayName: String,
    val thresholdPf: Double,
) {
    DEFAULT_CAPACITANCE(
        displayName = "默认电容报警",
        thresholdPf = 1.0,
    ),
}

data class CapacitanceReading(
    val channel: Int,
    val valuePf: Double,
)

data class CapacitanceSample(
    val channel: Int,
    val valuePf: Double,
    val receivedAtMillis: Long,
)

data class ChannelAlarm(
    val channel: Int,
    val valuePf: Double,
)

data class AlarmDialogState(
    val rule: AlarmRule,
    val channels: List<ChannelAlarm>,
    val triggeredAtMillis: Long,
)

data class AlarmTransition(
    val shouldShowDialog: Boolean,
    val activeChannels: List<ChannelAlarm>,
)

/** 从硬件文本行中提取 CH1～CH4 的电容值。 */
object CapacitanceLineParser {
    private val pattern = Regex(
        pattern = """^\s*CH([1-4])\s*:\s*([+-]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][+-]?\d+)?)\s*pF\b""",
        option = RegexOption.IGNORE_CASE,
    )

    fun parse(line: String): CapacitanceReading? {
        val match = pattern.find(line) ?: return null
        val channel = match.groupValues[1].toIntOrNull() ?: return null
        val value = match.groupValues[2].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return null
        return CapacitanceReading(channel, value)
    }
}

/**
 * 报警在首次超限时触发；关闭弹窗后保持静默，直到四个通道都收到合法低值。
 */
class CapacitanceAlarmTracker(
    private val rule: AlarmRule = AlarmRule.DEFAULT_CAPACITANCE,
) {
    private val latestValues = mutableMapOf<Int, Double>()
    private var armed = true

    fun record(reading: CapacitanceReading): AlarmTransition {
        latestValues[reading.channel] = reading.valuePf
        val activeChannels = latestValues
            .filterValues { it > rule.thresholdPf }
            .toSortedMap()
            .map { (channel, value) -> ChannelAlarm(channel, value) }

        val shouldTrigger = armed && reading.valuePf > rule.thresholdPf
        if (shouldTrigger) {
            armed = false
        } else if (!armed && allChannelsRecovered()) {
            armed = true
        }

        return AlarmTransition(
            shouldShowDialog = shouldTrigger,
            activeChannels = activeChannels,
        )
    }

    fun reset() {
        latestValues.clear()
        armed = true
    }

    private fun allChannelsRecovered(): Boolean =
        (1..CHANNEL_COUNT).all { latestValues[it]?.let { value -> value <= rule.thresholdPf } == true }

    companion object {
        const val CHANNEL_COUNT = 4
    }
}

/** 以最新样本为基准，仅保留最近一分钟的数据。 */
fun trimSamplesToWindow(
    samples: List<CapacitanceSample>,
    latestTimestampMillis: Long,
    windowMillis: Long = SIXTY_SECONDS_MILLIS,
): List<CapacitanceSample> {
    val cutoff = latestTimestampMillis - windowMillis
    return samples
        .filter { it.receivedAtMillis in cutoff..latestTimestampMillis }
        .sortedBy { it.receivedAtMillis }
}

const val SIXTY_SECONDS_MILLIS: Long = 60_000L
