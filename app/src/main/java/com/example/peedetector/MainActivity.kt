package com.example.peedetector

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.peedetector.ble.BleManager
import com.example.peedetector.export.CsvExporter
import com.example.peedetector.ui.BleScreen
import com.example.peedetector.ui.PermissionScreen
import com.example.peedetector.ui.theme.PeeDetectorTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bleManager = BleManager(this)
        setContent {
            PeeDetectorTheme {
                BleApp(bleManager)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bleManager.isInitialized) {
            bleManager.close()
        }
    }
}

@Composable
private fun BleApp(bleManager: BleManager) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(hasBlePermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasPermission = result.values.all { it }
    }

    var pendingCsv by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val csv = pendingCsv
        pendingCsv = null
        if (uri != null && csv != null) {
            CsvExporter.writeToUri(context, uri, csv)
        }
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }

    if (!hasPermission) {
        PermissionScreen(
            onRequestPermission = {
                permissionLauncher.launch(REQUIRED_PERMISSIONS)
            },
        )
    } else {
        val state by bleManager.state.collectAsState()
        BleScreen(
            state = state,
            onScan = { bleManager.startScan() },
            onStopScan = { bleManager.stopScan() },
            onDeviceClick = { bleManager.connect(it) },
            onDismissAlarm = { bleManager.dismissAlarm() },
            onStartRecording = { bleManager.startRecording() },
            onStopRecording = { bleManager.stopRecording() },
            onChartWindowChange = { bleManager.setChartWindowMillis(it) },
            onDismissExport = { bleManager.dismissExportDialog() },
            onSaveCsv = { csv ->
                pendingCsv = csv
                saveLauncher.launch(CsvExporter.exportFileName())
            },
            onShareCsv = { csv ->
                shareLauncher.launch(CsvExporter.buildShareChooser(context, csv))
            },
        )
    }
}

private fun hasBlePermissions(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) ==
        PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.BLUETOOTH_SCAN,
    Manifest.permission.BLUETOOTH_CONNECT,
)
