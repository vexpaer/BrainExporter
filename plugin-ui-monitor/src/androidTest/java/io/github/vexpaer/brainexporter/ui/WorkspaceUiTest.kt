package io.github.vexpaer.brainexporter.ui

import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vexpaer.brainexporter.sdk.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Hardware-free coverage of the redesigned workspace's important interactions. */
@RunWith(AndroidJUnit4::class)
class WorkspaceUiTest {
    @get:Rule val compose = createComposeRule()
    private val controller = FakeMonitor()

    private fun launch() {
        val packages = object : ModulePackageController {
            override fun importManifest(manifest: String): Result<ProcessingModuleDescriptor> = Result.failure(IllegalArgumentException("Test only"))
            override fun uninstall(moduleId: String) = Result.success(Unit)
        }
        val updates = object : AppUpdateController {
            override fun state() = AppUpdateState("0.5.0", UpdatePhase.UP_TO_DATE, message = "You have the latest version")
            override fun addListener(listener: AppUpdateListener) = AutoCloseable { }
            override fun checkForUpdates() = Unit
            override fun downloadUpdate() = Unit
            override fun installDownloadedUpdate() = Unit
            override fun close() = Unit
        }
        compose.setContent {
            BrainExporterApp(controller, { it() }, { it() }, packages, updates)
        }
    }

    @Test fun navigationAndSettings() {
        launch()
        capture("home")
        compose.onNodeWithText("Open Monitor").performScrollTo().assertIsDisplayed()
        tab("Modules")
        compose.onNodeWithText("Import module").assertIsDisplayed()
        capture("modules")
        tab("Settings")
        compose.onNodeWithText("Guest profile").assertIsDisplayed()
        compose.onAllNodesWithText("Account").assertCountEquals(0)
        capture("settings")
        compose.onNodeWithText("Sign in").performClick()
        compose.onNodeWithText("Username").performTextInput("researcher")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onNodeWithText("Cancel").performClick()
        tab("Home")
        compose.onNodeWithText("Open Monitor").performScrollTo().performClick()
        compose.onNodeWithText("Scan for devices").assertIsDisplayed()
        capture("monitor")
    }

    @Test fun scanSelectRecordAndProtectImpedance() {
        launch()
        tab("Monitor")
        compose.onNodeWithText("Scan for devices").performClick()
        compose.onNodeWithText("RT-BCI test").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText("Start recording").performClick()
        compose.runOnIdle { assertEquals(1, controller.recordings) }
        compose.onNodeWithText("Stop recording").assertIsDisplayed()
        capture("recording")
        compose.onNodeWithText("Impedance").performClick()
        compose.onNodeWithContentDescription("Open monitor controls").performClick()
        compose.onNode(hasScrollToIndexAction() and SemanticsMatcher("vertical list") { it.config.contains(SemanticsProperties.VerticalScrollAxisRange) }).performScrollToNode(hasText("Measure all"))
        compose.onNodeWithText("Measure all").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, controller.impedanceRequests) }
    }

    private fun tab(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "screenshots")
        directory.mkdirs()
        val file = File(directory, "$name.png")
        file.outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        // UTP removes the test APK and its scoped data after a run. Preserve
        // captures in shared storage through the instrumentation shell first.
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/brainexporter-ui", "cp ${file.absolutePath} /sdcard/Download/brainexporter-ui/$name.png").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }
}

private class FakeMonitor : MonitorController {
    private var state = MonitorSnapshot(
        connection = ConnectionState(message = "Not connected"),
        acquisition = AcquisitionState(message = "Ready to record"),
    )
    private var listener: SnapshotListener? = null
    var recordings = 0
    var impedanceRequests = 0
    private fun publish(next: MonitorSnapshot) { state = next; listener?.onSnapshot(next) }
    override fun snapshot() = state
    override fun addListener(listener: SnapshotListener): AutoCloseable { this.listener = listener; return AutoCloseable { this.listener = null } }
    override fun scan() = publish(state.copy(devices = listOf(DeviceDescriptor("test", "RT-BCI test", -42))))
    override fun connect(deviceId: String) = publish(state.copy(connection = ConnectionState(ConnectionPhase.CONNECTED, "RT-BCI test")))
    override fun disconnect() = publish(state.copy(connection = ConnectionState(message = "Not connected")))
    override fun setView(view: MonitorView) = publish(state.copy(activeView = view))
    override fun startAcquisition() { recordings++; publish(state.copy(acquisition = AcquisitionState(active = true, message = "Recording locally"))) }
    override fun stopAcquisition() = publish(state.copy(acquisition = AcquisitionState(message = "Saved locally")))
    override fun setModuleEnabled(moduleId: String, enabled: Boolean) = Unit
    override fun selectModule(moduleId: String?) = Unit
    override fun startImpedance(channel: Int?) { impedanceRequests++ }
    override fun stopImpedance() = Unit
    override fun close() = Unit
}
