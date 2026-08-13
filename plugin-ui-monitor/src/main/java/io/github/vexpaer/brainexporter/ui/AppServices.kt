package io.github.vexpaer.brainexporter.ui

import io.github.vexpaer.brainexporter.sdk.ProcessingModuleDescriptor

enum class UpdatePhase {
    IDLE,
    CHECKING,
    UP_TO_DATE,
    AVAILABLE,
    DOWNLOADING,
    READY_TO_INSTALL,
    ERROR,
}

data class AppUpdateState(
    val currentVersion: String,
    val phase: UpdatePhase = UpdatePhase.IDLE,
    val availableVersion: String? = null,
    val progress: Int? = null,
    val message: String = "尚未检查更新",
    val releaseUrl: String? = null,
)

fun interface AppUpdateListener {
    fun onStateChanged(state: AppUpdateState)
}

interface AppUpdateController : AutoCloseable {
    fun state(): AppUpdateState
    fun addListener(listener: AppUpdateListener): AutoCloseable
    fun checkForUpdates()
    fun downloadUpdate()
    fun installDownloadedUpdate()
}

/** Host bridge: parsing, persistence and runtime installation stay outside the UI plug-in. */
interface ModulePackageController {
    fun importManifest(manifest: String): Result<ProcessingModuleDescriptor>
    fun uninstall(moduleId: String): Result<Unit>
}
