package com.example.peedetector.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** 扫描到的设备展示信息。 */
data class ScannedDevice(
    val address: String,
    val name: String?,
    val rssi: Int,
    val hasFff0Service: Boolean,
)

/** UI 状态。 */
data class BleUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val error: String? = null,
    val devices: List<ScannedDevice> = emptyList(),
    val receivedLines: List<String> = emptyList(),
    val capacitanceSamples: List<CapacitanceSample> = emptyList(),
    val selectedAlarmRule: AlarmRule = AlarmRule.DEFAULT_CAPACITANCE,
    val alarmDialog: AlarmDialogState? = null,
)

/**
 * BLE 连接管理：扫描、连接、服务发现、FFF1 通知订阅与数据接收。
 *
 * 只负责一个连接；断开后由用户重新扫描并点击连接，不做自动重连。
 */
class BleManager(context: Context) {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _state = MutableStateFlow(BleUiState())
    val state: StateFlow<BleUiState> = _state

    private val lineBuffer = LineBuffer()
    private val alarmTracker = CapacitanceAlarmTracker()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scannedDevices = LinkedHashMap<String, ScannedDevice>()

    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var hasReachedConnected = false

    val isBleSupported: Boolean get() = bluetoothAdapter != null
    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    private val scanTimeoutRunnable = Runnable { stopScan() }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val record = result.scanRecord
            val hasFff0 = record?.serviceUuids?.any { it.uuid == BleUuids.SERVICE } ?: false
            val name = record?.deviceName ?: device.name
            scannedDevices[device.address] = ScannedDevice(
                address = device.address,
                name = name,
                rssi = result.rssi,
                hasFff0Service = hasFff0,
            )
            _state.update { it.copy(devices = sortedDevices()) }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScan()
            setDisconnected("扫描失败（错误码 $errorCode）")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hasReachedConnected = true
                    _state.update {
                        it.copy(connectionState = ConnectionState.DISCOVERING, error = null)
                    }
                    gatt.discoverServices()
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    if (this@BleManager.gatt === gatt) {
                        this@BleManager.gatt = null
                    }
                    lineBuffer.reset()
                    val current = _state.value
                    val reason = current.error
                        ?: if (hasReachedConnected) "连接已断开"
                        else "连接失败（错误码 $status）"
                    _state.update {
                        it.copy(connectionState = ConnectionState.DISCONNECTED, error = reason)
                    }
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                disconnectWithError("服务发现失败（错误码 $status）")
                return
            }
            val service = gatt.getService(BleUuids.SERVICE)
            if (service == null) {
                disconnectWithError("未找到服务 FFF0")
                return
            }
            val characteristic = service.getCharacteristic(BleUuids.CHARACTERISTIC_RX)
            if (characteristic == null) {
                disconnectWithError("未找到特征 FFF1")
                return
            }
            val descriptor = characteristic.getDescriptor(BleUuids.CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor == null) {
                disconnectWithError("未找到通知配置描述符（CCCD）")
                return
            }
            if (!gatt.setCharacteristicNotification(characteristic, true)) {
                disconnectWithError("无法开启特征通知")
                return
            }
            _state.update { it.copy(connectionState = ConnectionState.SUBSCRIBING) }
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!gatt.writeDescriptor(descriptor)) {
                disconnectWithError("写入通知订阅失败")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != BleUuids.CLIENT_CHARACTERISTIC_CONFIG) return
            if (status == BluetoothGatt.GATT_SUCCESS) {
                lineBuffer.reset()
                alarmTracker.reset()
                _state.update {
                    it.copy(
                        connectionState = ConnectionState.CONNECTED_WAITING,
                        error = null,
                        receivedLines = emptyList(),
                        capacitanceSamples = emptyList(),
                        alarmDialog = null,
                    )
                }
            } else {
                disconnectWithError("订阅通知失败（错误码 $status）")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != BleUuids.CHARACTERISTIC_RX) return
            val value = characteristic.value ?: return
            val lines = lineBuffer.append(value)
            if (lines.isEmpty()) return
            val receivedAtMillis = System.currentTimeMillis()
            val readings = lines.mapNotNull(CapacitanceLineParser::parse)
            var shouldShowAlarm = false
            var activeAlarmChannels = emptyList<ChannelAlarm>()
            readings.forEach { reading ->
                val transition = alarmTracker.record(reading)
                shouldShowAlarm = shouldShowAlarm || transition.shouldShowDialog
                activeAlarmChannels = transition.activeChannels
            }
            _state.update { current ->
                val samples = trimSamplesToWindow(
                    current.capacitanceSamples + readings.map { reading ->
                        CapacitanceSample(
                            channel = reading.channel,
                            valuePf = reading.valuePf,
                            receivedAtMillis = receivedAtMillis,
                        )
                    },
                    latestTimestampMillis = receivedAtMillis,
                )
                val alarmDialog = when {
                    shouldShowAlarm -> AlarmDialogState(
                        rule = current.selectedAlarmRule,
                        channels = activeAlarmChannels,
                        triggeredAtMillis = receivedAtMillis,
                    )

                    current.alarmDialog != null && activeAlarmChannels.isNotEmpty() ->
                        current.alarmDialog.copy(channels = activeAlarmChannels)

                    else -> current.alarmDialog
                }
                current.copy(
                    connectionState = ConnectionState.RECEIVING,
                    receivedLines = (current.receivedLines + lines).takeLast(MAX_DISPLAY_LINES),
                    capacitanceSamples = samples,
                    alarmDialog = alarmDialog,
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setDisconnected("设备不支持 BLE")
            return
        }
        if (!adapter.isEnabled) {
            setDisconnected("蓝牙未开启，请先开启蓝牙")
            return
        }
        val leScanner = scanner ?: adapter.bluetoothLeScanner?.also { scanner = it }
        if (leScanner == null) {
            setDisconnected("无法获取蓝牙扫描器")
            return
        }
        scannedDevices.clear()
        _state.update {
            it.copy(connectionState = ConnectionState.SCANNING, error = null, devices = emptyList())
        }
        try {
            leScanner.startScan(scanCallback)
        } catch (e: SecurityException) {
            setDisconnected("缺少蓝牙扫描权限")
            return
        }
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        mainHandler.postDelayed(scanTimeoutRunnable, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        mainHandler.removeCallbacks(scanTimeoutRunnable)
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        val current = _state.value
        if (current.connectionState == ConnectionState.SCANNING) {
            _state.update { it.copy(connectionState = ConnectionState.DISCONNECTED) }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        stopScan()
        val adapter = bluetoothAdapter
        if (adapter == null) {
            setDisconnected("设备不支持 BLE")
            return
        }
        hasReachedConnected = false
        _state.update { it.copy(connectionState = ConnectionState.CONNECTING, error = null) }
        try {
            val device = adapter.getRemoteDevice(address)
            val g = device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (g == null) {
                setDisconnected("无法建立连接")
            } else {
                gatt = g
            }
        } catch (e: SecurityException) {
            setDisconnected("缺少蓝牙连接权限")
        }
    }

    fun close() {
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    fun dismissAlarm() {
        _state.update { it.copy(alarmDialog = null) }
    }

    private fun disconnectWithError(message: String) {
        _state.update {
            it.copy(connectionState = ConnectionState.DISCONNECTED, error = message)
        }
        gatt?.disconnect()
    }

    private fun setDisconnected(message: String) {
        _state.update {
            it.copy(connectionState = ConnectionState.DISCONNECTED, error = message)
        }
    }

    private fun sortedDevices(): List<ScannedDevice> =
        scannedDevices.values.sortedWith(
            compareByDescending<ScannedDevice> { it.hasFff0Service }
                .thenByDescending { it.matchesTargetName() }
                .thenByDescending { it.rssi },
        )

    private fun ScannedDevice.matchesTargetName(): Boolean {
        val n = name ?: return false
        return n.contains("E104", ignoreCase = true) ||
            n.contains("BT5010", ignoreCase = true) ||
            n.contains("BT-5010", ignoreCase = true)
    }

    companion object {
        const val SCAN_TIMEOUT_MS = 10_000L
        const val MAX_DISPLAY_LINES = 2000
    }
}
