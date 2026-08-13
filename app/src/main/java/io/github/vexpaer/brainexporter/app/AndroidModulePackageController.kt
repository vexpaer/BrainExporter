package io.github.vexpaer.brainexporter.app

import android.content.Context
import androidx.core.content.edit
import io.github.vexpaer.brainexporter.algorithm.DeclarativeModuleFactory
import io.github.vexpaer.brainexporter.runtime.BrainExporterRuntime
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleDescriptor
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOrigin
import io.github.vexpaer.brainexporter.ui.ModulePackageController

/** Persists validated declarative packages and attaches them to the current runtime. */
class AndroidModulePackageController(
    context: Context,
    private val runtime: BrainExporterRuntime,
    private val factory: DeclarativeModuleFactory = DeclarativeModuleFactory(),
) : ModulePackageController {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    init {
        restoreInstalledPackages()
    }

    override fun importManifest(manifest: String): Result<ProcessingModuleDescriptor> = runCatching {
        val module = factory.create(manifest, ProcessingModuleOrigin.IMPORTED)
        val descriptor = runtime.installModule(module)
        if (!preferences.edit().putString(descriptor.id, manifest).commit()) {
            runCatching { runtime.uninstallModule(descriptor.id) }
            throw IllegalStateException("模块已验证，但无法保存到本机")
        }
        descriptor
    }

    override fun uninstall(moduleId: String): Result<Unit> = runCatching {
        runtime.uninstallModule(moduleId)
        preferences.edit { remove(moduleId) }
    }

    private fun restoreInstalledPackages() {
        preferences.all.values.filterIsInstance<String>().forEach { manifest ->
            runCatching {
                runtime.installModule(factory.create(manifest, ProcessingModuleOrigin.IMPORTED))
            }
        }
    }

    private companion object {
        const val PREFERENCES = "brainexporter_modules_v1"
    }
}
