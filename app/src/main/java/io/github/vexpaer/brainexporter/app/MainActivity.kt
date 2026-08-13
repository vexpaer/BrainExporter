package io.github.vexpaer.brainexporter.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.vexpaer.brainexporter.algorithm.BandPassEegModule
import io.github.vexpaer.brainexporter.algorithm.BasicEegAlgorithm
import io.github.vexpaer.brainexporter.algorithm.WindowStatisticsFeatureModule
import io.github.vexpaer.brainexporter.device.rtbci.RtBciDevicePlugin
import io.github.vexpaer.brainexporter.runtime.BrainExporterRuntime
import io.github.vexpaer.brainexporter.ui.BrainExporterApp

class MainActivity : ComponentActivity() {
    private lateinit var runtime: BrainExporterRuntime
    private lateinit var updater: GitHubReleaseUpdater
    private lateinit var modulePackages: AndroidModulePackageController
    private var pendingPermissionAction: (() -> Unit)? = null
    private var pendingRecordingAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = requiredBluetoothPermissions().all { permission ->
            result[permission] == true || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        }
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                this,
                "需要附近设备/蓝牙权限才能扫描和连接脑电设备。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private val recordingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingRecordingAction
        pendingRecordingAction = null
        if (granted) {
            action?.invoke()
        } else {
            Toast.makeText(
                this,
                "Android 8/9 需要存储权限才能写入 Documents/eegData。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runtime = BrainExporterRuntime(
            device = RtBciDevicePlugin(applicationContext),
            algorithm = BasicEegAlgorithm(),
            recordingSink = AndroidEegRecordingSink(applicationContext),
            modules = listOf(
                BandPassEegModule.builtIn1To40Hz(),
                WindowStatisticsFeatureModule.builtIn(),
            ),
        )
        modulePackages = AndroidModulePackageController(applicationContext, runtime)
        val currentVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.2.0"
        updater = GitHubReleaseUpdater(applicationContext, currentVersion)
        setContent {
            BrainExporterApp(
                controller = runtime,
                permissionGate = { action -> runWithBluetoothPermissions(action) },
                recordingPermissionGate = { action -> runWithRecordingPermission(action) },
                modulePackages = modulePackages,
                updateController = updater,
            )
        }
        updater.checkForUpdates()
    }

    private fun runWithBluetoothPermissions(action: () -> Unit) {
        val missing = requiredBluetoothPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requiredBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun runWithRecordingPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P ||
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingRecordingAction = action
            recordingPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onDestroy() {
        if (::updater.isInitialized) updater.close()
        if (::runtime.isInitialized) runtime.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::updater.isInitialized) updater.onHostResume()
    }
}
