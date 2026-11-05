package com.example.peedetector.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.peedetector.ble.AlarmDialogState
import com.example.peedetector.ble.AlarmRule
import com.example.peedetector.ble.BleUiState
import com.example.peedetector.ble.ConnectionState
import com.example.peedetector.ble.ExportDialogState
import com.example.peedetector.ble.ScannedDevice
import com.example.peedetector.export.CsvExporter
import java.text.DateFormat
import java.util.Date

@Composable
fun BleScreen(
    state: BleUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onDismissAlarm: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onChartWindowChange: (Long) -> Unit,
    onDismissExport: () -> Unit,
    onSaveCsv: (String) -> Unit,
    onShareCsv: (String) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_MONITOR) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == TAB_MONITOR,
                    onClick = { selectedTab = TAB_MONITOR },
                    icon = { Text(if (selectedTab == TAB_MONITOR) "●" else "○") },
                    label = { Text("监测") },
                )
                NavigationBarItem(
                    selected = selectedTab == TAB_DEBUG,
                    onClick = { selectedTab = TAB_DEBUG },
                    icon = { Text(if (selectedTab == TAB_DEBUG) "●" else "○") },
                    label = { Text("调试") },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
        ) {
            StatusSection(state)
            Spacer(Modifier.height(12.dp))
            if (selectedTab == TAB_MONITOR) {
                MonitorContent(
                    state = state,
                    onStartRecording = onStartRecording,
                    onStopRecording = onStopRecording,
                    onChartWindowChange = onChartWindowChange,
                    modifier = Modifier.weight(1f),
                )
            } else {
                DebugContent(
                    state = state,
                    onScan = onScan,
                    onStopScan = onStopScan,
                    onDeviceClick = onDeviceClick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    state.alarmDialog?.let { alarm ->
        AlarmDialog(alarm, onDismissAlarm)
    }
    state.exportDialog?.let { export ->
        ExportDialog(export, onSaveCsv, onShareCsv, onDismissExport)
    }
}

@Composable
private fun MonitorContent(
    state: BleUiState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onChartWindowChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        RecordingControl(state.isRecording, onStartRecording, onStopRecording)
        Spacer(Modifier.height(12.dp))
        AlarmSettingsSection(state.selectedAlarmRule)
        Spacer(Modifier.height(12.dp))
        CapacitanceChart(
            samples = state.capacitanceSamples,
            alarmRule = state.selectedAlarmRule,
            windowMillis = state.chartWindowMillis,
            onWindowChange = onChartWindowChange,
        )
    }
}

@Composable
private fun RecordingControl(isRecording: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Button(
        onClick = { if (isRecording) onStop() else onStart() },
        colors = if (isRecording) {
            ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isRecording) "停止录制" else "开始录制")
    }
}

@Composable
private fun ExportDialog(
    export: ExportDialogState,
    onSave: (String) -> Unit,
    onShare: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val csv = remember(export) { CsvExporter.samplesToWideCsv(export.samples) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录制完成") },
        text = {
            Column {
                Text("共录制 ${export.samples.size} 条样本")
                Spacer(Modifier.height(8.dp))
                Text(
                    "起始：${formatDateTime(export.startedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Text(
                    "结束：${formatDateTime(export.endedAtMillis)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onSave(csv) }) { Text("保存到本地") }
                TextButton(onClick = { onShare(csv) }) { Text("转发分享") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun formatDateTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(millis))

@Composable
private fun DebugContent(
    state: BleUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        ScanButton(state, onScan, onStopScan)
        Spacer(Modifier.height(12.dp))
        if (state.connectionState == ConnectionState.SCANNING || state.devices.isNotEmpty()) {
            DeviceList(state.devices, onDeviceClick)
            Spacer(Modifier.height(12.dp))
        }
        ReceiveSection(state.receivedLines, Modifier.weight(1f))
    }
}

@Composable
private fun AlarmSettingsSection(rule: AlarmRule) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("报警设置", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${rule.displayName}：任一通道 > ${formatCapacitance(rule.thresholdPf)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "收起 ▲" else "报警选项 ▼")
                }
            }

            if (expanded) {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = true, onClick = {})
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(rule.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "任一通道超过 ${formatCapacitance(rule.thresholdPf)} 时报警",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmDialog(alarm: AlarmDialogState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("报警信息") },
        text = {
            Column {
                Text(alarm.rule.displayName, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                alarm.channels.forEach { channel ->
                    Text("CH${channel.channel}：${formatCapacitance(channel.valuePf)}")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "接收时间：${
                        DateFormat.getTimeInstance(DateFormat.MEDIUM).format(
                            Date(alarm.triggeredAtMillis),
                        )
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
    )
}

@Composable
private fun StatusSection(state: BleUiState) {
    val text = statusText(state)
    val color = when (state.connectionState) {
        ConnectionState.CONNECTED_WAITING, ConnectionState.RECEIVING -> Color(0xFF2E7D32)
        ConnectionState.DISCONNECTED ->
            if (state.error != null) Color(0xFFC62828) else Color(0xFF616161)
        else -> Color(0xFF1565C0)
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("连接状态", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = color)
        }
    }
}

private fun statusText(state: BleUiState): String = when (state.connectionState) {
    ConnectionState.DISCONNECTED -> state.error ?: "未连接"
    ConnectionState.SCANNING -> "扫描中"
    ConnectionState.CONNECTING -> "连接中"
    ConnectionState.DISCOVERING -> "发现服务中"
    ConnectionState.SUBSCRIBING -> "订阅中"
    ConnectionState.CONNECTED_WAITING -> "已连接，等待数据"
    ConnectionState.RECEIVING -> "接收中"
}

@Composable
private fun ScanButton(state: BleUiState, onScan: () -> Unit, onStopScan: () -> Unit) {
    val isScanning = state.connectionState == ConnectionState.SCANNING
    val isBusy = state.connectionState in setOf(
        ConnectionState.CONNECTING,
        ConnectionState.DISCOVERING,
        ConnectionState.SUBSCRIBING,
        ConnectionState.CONNECTED_WAITING,
        ConnectionState.RECEIVING,
    )
    Button(
        onClick = { if (isScanning) onStopScan() else onScan() },
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(if (isScanning) "停止扫描" else "扫描设备")
    }
}

@Composable
private fun DeviceList(devices: List<ScannedDevice>, onDeviceClick: (String) -> Unit) {
    Text("设备列表", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    if (devices.isEmpty()) {
        Text(
            "未发现设备…",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        return
    }
    // 设备列表限制最大高度，超出部分内部滚动，避免大量设备挤占接收区。
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 220.dp),
    ) {
        items(devices) { device ->
            DeviceRow(device, onDeviceClick)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeviceRow(device: ScannedDevice, onDeviceClick: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDeviceClick(device.address) },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.name ?: "未知设备",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }
            if (device.hasFff0Service) {
                Text(
                    "FFF0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                "${device.rssi} dBm",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun ReceiveSection(lines: List<String>, modifier: Modifier = Modifier) {
    Text("接收数据", style = MaterialTheme.typography.labelMedium)
    Spacer(Modifier.height(4.dp))
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }
    Card(modifier = modifier.fillMaxWidth()) {
        if (lines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
            ) {
                items(lines) { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("需要蓝牙权限", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "本应用需要蓝牙扫描与连接权限，用于发现并连接 E104-BT5010A 硬件设备。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestPermission) {
            Text("授予权限")
        }
    }
}

private const val TAB_MONITOR = 0
private const val TAB_DEBUG = 1
