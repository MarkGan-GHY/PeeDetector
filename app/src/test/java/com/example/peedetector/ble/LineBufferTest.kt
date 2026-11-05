package com.example.peedetector.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LineBufferTest {

    private fun bytes(s: String): ByteArray = s.toByteArray(Charsets.ISO_8859_1)

    @Test
    fun `提取单个完整行`() {
        val buffer = LineBuffer()
        val lines = buffer.append(bytes("CH1: 0.012 pF (dC, raw=6291, capdac=1)\r\n"))
        assertEquals(listOf("CH1: 0.012 pF (dC, raw=6291, capdac=1)"), lines)
    }

    @Test
    fun `跨通知拆分的CRLF`() {
        val buffer = LineBuffer()
        assertEquals(emptyList<String>(), buffer.append(bytes("CH1: 0.012 pF\r")))
        assertEquals(listOf("CH1: 0.012 pF"), buffer.append(bytes("\nCH2: -0.018 pF")))
    }

    @Test
    fun `一个通知包含多行`() {
        val buffer = LineBuffer()
        val lines = buffer.append(bytes("CH1: 1\r\nCH2: 2\r\nCH3: 3\r\n"))
        assertEquals(listOf("CH1: 1", "CH2: 2", "CH3: 3"), lines)
    }

    @Test
    fun `无换行数据不产生行`() {
        val buffer = LineBuffer()
        assertEquals(emptyList<String>(), buffer.append(bytes("partial line without newline")))
    }

    @Test
    fun `reset清除半行缓存`() {
        val buffer = LineBuffer()
        assertEquals(emptyList<String>(), buffer.append(bytes("partial\r")))
        buffer.reset()
        assertEquals(listOf("new"), buffer.append(bytes("new\r\n")))
    }

    @Test
    fun `空数据不产生行`() {
        val buffer = LineBuffer()
        assertEquals(emptyList<String>(), buffer.append(ByteArray(0)))
    }

    @Test
    fun `超长无换行输入内存有界`() {
        val buffer = LineBuffer(maxPendingBytes = 1024)
        val chunk = ByteArray(1024) { 'A'.code.toByte() }
        repeat(10) { buffer.append(chunk) }
        val lines = buffer.append(bytes("\r\n"))
        assertEquals(1, lines.size)
        assertTrue(lines[0].length <= 1024)
    }

    @Test
    fun `连续完整行后接半行`() {
        val buffer = LineBuffer()
        assertEquals(listOf("A", "B"), buffer.append(bytes("A\r\nB\r\n")))
        assertEquals(emptyList<String>(), buffer.append(bytes("C")))
        assertEquals(listOf("C"), buffer.append(bytes("\r\n")))
    }

    @Test
    fun `空行也被提取`() {
        val buffer = LineBuffer()
        assertEquals(listOf("A", "", "B"), buffer.append(bytes("A\r\n\r\nB\r\n")))
    }
}
