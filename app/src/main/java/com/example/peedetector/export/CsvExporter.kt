package com.example.peedetector.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.peedetector.ble.CapacitanceSample
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 录制数据的 CSV 序列化与导出（本地保存 / 分享）辅助工具。
 *
 * 序列化为纯函数，便于 JVM 单元测试；文件读写与 Intent 构造独立于此，
 * 不依赖 BLE 传输与 UI。
 */
object CsvExporter {

    private const val HEADER = "timestamp_ms,CH1_pf,CH2_pf,CH3_pf,CH4_pf"
    private const val MIME_TYPE = "text/csv"

    /**
     * 将样本按接收时间戳分组，输出宽表 CSV：
     * 每行一个时间点，四通道各占一列，缺失的通道留空。
     * 时间戳为 epoch 毫秒，行按时间升序排列。
     */
    fun samplesToWideCsv(samples: List<CapacitanceSample>): String {
        val grouped = samples.groupBy { it.receivedAtMillis }
        val sb = StringBuilder(HEADER)
        grouped.keys.sorted().forEach { timestamp ->
            val row = grouped.getValue(timestamp)
            sb.append('\n').append(timestamp)
            for (channel in 1..4) {
                val value = row.firstOrNull { it.channel == channel }?.valuePf
                sb.append(',').append(value?.let(::formatValue) ?: "")
            }
        }
        return sb.toString()
    }

    private fun formatValue(value: Double): String = value.toString()

    /** 以当前时间生成导出文件名，例如 peedetector_20261106_143025.csv。 */
    fun exportFileName(): String =
        "peedetector_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"

    /** 将 CSV 文本写入系统文件选择器返回的 [uri]。 */
    fun writeToUri(context: Context, uri: Uri, csv: String) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        }
    }

    /**
     * 把 CSV 文本写入缓存目录临时文件，返回「分享」选择器 Intent。
     * 调用方需使用 Activity 启动该 Intent。
     */
    fun buildShareChooser(context: Context, csv: String): Intent {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, exportFileName())
        file.writeText(csv, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(sendIntent, "分享 CSV")
    }
}
