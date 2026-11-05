package com.example.peedetector.export

import com.example.peedetector.ble.CapacitanceSample
import org.junit.Assert.assertEquals
import org.junit.Test

class CsvExporterTest {

    @Test
    fun emptySamples_returnsHeaderOnly() {
        assertEquals(
            "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf",
            CsvExporter.samplesToWideCsv(emptyList()),
        )
    }

    @Test
    fun fullRow_fillsAllFourChannels() {
        val samples = listOf(
            CapacitanceSample(channel = 1, valuePf = 0.512, receivedAtMillis = 1000L),
            CapacitanceSample(channel = 2, valuePf = 0.503, receivedAtMillis = 1000L),
            CapacitanceSample(channel = 3, valuePf = 0.498, receivedAtMillis = 1000L),
            CapacitanceSample(channel = 4, valuePf = 0.501, receivedAtMillis = 1000L),
        )
        assertEquals(
            "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf\n1000,0.512,0.503,0.498,0.501",
            CsvExporter.samplesToWideCsv(samples),
        )
    }

    @Test
    fun missingChannel_emitsEmptyCell() {
        val samples = listOf(
            CapacitanceSample(channel = 1, valuePf = 0.5, receivedAtMillis = 1000L),
            CapacitanceSample(channel = 3, valuePf = 0.7, receivedAtMillis = 1000L),
        )
        assertEquals(
            "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf\n1000,0.5,,0.7,",
            CsvExporter.samplesToWideCsv(samples),
        )
    }

    @Test
    fun sortsTimestampsAscending() {
        val samples = listOf(
            CapacitanceSample(channel = 1, valuePf = 0.1, receivedAtMillis = 2000L),
            CapacitanceSample(channel = 1, valuePf = 0.2, receivedAtMillis = 1000L),
        )
        assertEquals(
            "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf\n1000,0.2,,,\n2000,0.1,,,",
            CsvExporter.samplesToWideCsv(samples),
        )
    }

    @Test
    fun distinctTimestamps_formSeparateRows() {
        val samples = listOf(
            CapacitanceSample(channel = 1, valuePf = 0.1, receivedAtMillis = 1000L),
            CapacitanceSample(channel = 2, valuePf = 0.2, receivedAtMillis = 2000L),
            CapacitanceSample(channel = 1, valuePf = 0.3, receivedAtMillis = 2000L),
        )
        assertEquals(
            "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf\n1000,0.1,,,\n2000,0.3,0.2,,",
            CsvExporter.samplesToWideCsv(samples),
        )
    }
}
