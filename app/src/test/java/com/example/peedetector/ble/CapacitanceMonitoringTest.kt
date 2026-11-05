package com.example.peedetector.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapacitanceMonitoringTest {

    @Test
    fun `解析正负电容值及指数格式`() {
        assertEquals(CapacitanceReading(1, 0.012), CapacitanceLineParser.parse(
            "CH1: 0.012 pF (dC, raw=6291, capdac=1)",
        ))
        assertEquals(CapacitanceReading(2, -0.018), CapacitanceLineParser.parse("ch2: -0.018 pF"))
        assertEquals(CapacitanceReading(4, 0.25), CapacitanceLineParser.parse(" CH4 : 2.5e-1 PF"))
    }

    @Test
    fun `忽略非测量和无效通道文本`() {
        assertNull(CapacitanceLineParser.parse("CAL done"))
        assertNull(CapacitanceLineParser.parse("CH2: INVALID"))
        assertNull(CapacitanceLineParser.parse("CH5: 0.2 pF"))
        assertNull(CapacitanceLineParser.parse("CH1: NaN pF"))
    }

    @Test
    fun `等于阈值不报警且任一通道超限会触发`() {
        val tracker = CapacitanceAlarmTracker()

        assertFalse(tracker.record(CapacitanceReading(1, 1.0)).shouldShowDialog)
        val transition = tracker.record(CapacitanceReading(3, 1.001))

        assertTrue(transition.shouldShowDialog)
        assertEquals(listOf(ChannelAlarm(3, 1.001)), transition.activeChannels)
    }

    @Test
    fun `关闭后保持静默直到四通道都恢复`() {
        val tracker = CapacitanceAlarmTracker()

        assertTrue(tracker.record(CapacitanceReading(1, 1.2)).shouldShowDialog)
        assertFalse(tracker.record(CapacitanceReading(1, 1.3)).shouldShowDialog)

        tracker.record(CapacitanceReading(1, 0.2))
        tracker.record(CapacitanceReading(2, 0.2))
        tracker.record(CapacitanceReading(3, 0.2))
        assertFalse(tracker.record(CapacitanceReading(4, 0.9)).shouldShowDialog)

        assertTrue(tracker.record(CapacitanceReading(4, 1.1)).shouldShowDialog)
    }

    @Test
    fun `窗口裁剪以最新样本为基准`() {
        val samples = listOf(
            CapacitanceSample(1, 0.1, 39_999),
            CapacitanceSample(2, 0.2, 40_000),
            CapacitanceSample(3, 0.3, 100_000),
        )

        val result = trimSamplesToWindow(samples, latestTimestampMillis = 100_000)

        assertEquals(listOf(2, 3), result.map { it.channel })
    }
}
