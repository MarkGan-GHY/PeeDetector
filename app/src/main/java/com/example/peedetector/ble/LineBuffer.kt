package com.example.peedetector.ble

/**
 * CRLF 字节流分行器。
 *
 * 蓝牙通知边界不等于文本行边界：一行可跨多个通知，一个通知也可含多行。
 * 本类缓存原始字节，按 CRLF（0x0D 0x0A）提取完整行，并限制未完成缓存大小，
 * 防止异常字节或超长无换行输入导致内存无限增长。
 */
class LineBuffer(
    private val maxPendingBytes: Int = DEFAULT_MAX_PENDING_BYTES,
) {
    private var buffer = ByteArray(0)

    /**
     * 追加一段字节，返回本次新提取出的完整行（不含行尾 CRLF）。
     */
    fun append(data: ByteArray): List<String> {
        if (data.isEmpty()) return emptyList()
        buffer = concat(buffer, data)
        if (buffer.size > maxPendingBytes) {
            // 超长无换行：丢弃最旧字节，保留最新的 maxPendingBytes 字节，保证内存有界。
            buffer = buffer.copyOfRange(buffer.size - maxPendingBytes, buffer.size)
        }
        val lines = mutableListOf<String>()
        while (true) {
            val idx = indexOfCrLf(buffer)
            if (idx < 0) break
            lines.add(decode(buffer, 0, idx))
            buffer = buffer.copyOfRange(idx + 2, buffer.size)
        }
        return lines
    }

    /**
     * 清除未完成行缓存。断线时调用，避免与下一连接的数据拼接。
     */
    fun reset() {
        buffer = ByteArray(0)
    }

    private fun indexOfCrLf(bytes: ByteArray): Int {
        val last = bytes.size - 1
        for (i in 0 until last) {
            if (bytes[i] == CR && bytes[i + 1] == LF) return i
        }
        return -1
    }

    private fun decode(bytes: ByteArray, offset: Int, length: Int): String =
        String(bytes, offset, length, Charsets.ISO_8859_1)

    private fun concat(a: ByteArray, b: ByteArray): ByteArray {
        val result = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, result, 0, a.size)
        System.arraycopy(b, 0, result, a.size, b.size)
        return result
    }

    companion object {
        const val CR: Byte = 0x0D
        const val LF: Byte = 0x0A
        const val DEFAULT_MAX_PENDING_BYTES: Int = 64 * 1024
    }
}
