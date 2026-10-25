package io.github.vexpaer.brainexporter.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.Settings
import androidx.core.content.edit
import androidx.core.net.toUri
import io.github.vexpaer.brainexporter.ui.AppUpdateController
import io.github.vexpaer.brainexporter.ui.AppUpdateListener
import io.github.vexpaer.brainexporter.ui.AppUpdateState
import io.github.vexpaer.brainexporter.ui.UpdatePhase
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Checks GitHub Latest Release, downloads its APK with DownloadManager, then opens Android installer. */
class GitHubReleaseUpdater(
    context: Context,
    currentVersion: String,
) : AppUpdateController {
    private val applicationContext = context.applicationContext
    private val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)
    private val listeners = CopyOnWriteArraySet<AppUpdateListener>()
    private val worker = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "brainexporter-updater").apply { isDaemon = true }
    }
    private val preferences = applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val normalizedCurrentVersion = currentVersion.substringBefore('-')

    @Volatile
    private var currentState = AppUpdateState(currentVersion = normalizedCurrentVersion)
    private var latestRelease: ReleaseAsset? = null
    private var downloadId: Long? = null
    private var polling: ScheduledFuture<*>? = null
    @Volatile
    private var awaitingInstallPermission = false

    init {
        recoverDownload()
    }

    override fun state(): AppUpdateState = currentState

    override fun addListener(listener: AppUpdateListener): AutoCloseable {
        listeners += listener
        listener.onStateChanged(currentState)
        return AutoCloseable { listeners -= listener }
    }

    override fun checkForUpdates() {
        if (currentState.phase in setOf(UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING, UpdatePhase.READY_TO_INSTALL)) {
            return
        }
        publish(currentState.copy(phase = UpdatePhase.CHECKING, progress = null, message = "Checking GitHub releases…"))
        worker.execute {
            runCatching(::fetchLatestRelease)
                .onSuccess { release ->
                    latestRelease = release
                    if (isNewerVersion(release.version, normalizedCurrentVersion)) {
                        publish(
                            currentState.copy(
                                phase = UpdatePhase.AVAILABLE,
                                availableVersion = release.version,
                                message = "BrainExporter v${release.version}",
                                releaseUrl = release.releaseUrl,
                            ),
                        )
                    } else {
                        publish(
                            currentState.copy(
                                phase = UpdatePhase.UP_TO_DATE,
                                availableVersion = release.version,
                                message = "You're up to date.",
                                releaseUrl = release.releaseUrl,
                            ),
                        )
                    }
                }
                .onFailure { failure ->
                    publish(
                        currentState.copy(
                            phase = UpdatePhase.ERROR,
                            progress = null,
                            message = "Update check failed: ${failure.message ?: "Network unavailable"}",
                        ),
                    )
                }
        }
    }

    override fun downloadUpdate() {
        val release = latestRelease
        if (release == null) {
            checkForUpdates()
            return
        }
        if (currentState.phase == UpdatePhase.DOWNLOADING) return
        runCatching {
            require(release.apkUrl.startsWith(RELEASE_DOWNLOAD_PREFIX)) { "Untrusted release APK URL" }
            require(release.assetName.matches(SAFE_ASSET_NAME)) { "Invalid release APK filename" }
            val request = DownloadManager.Request(release.apkUrl.toUri())
                .setTitle("BrainExporter v${release.version}")
                .setDescription("Downloading app update")
                .setMimeType(APK_MIME_TYPE)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(applicationContext, Environment.DIRECTORY_DOWNLOADS, release.assetName)
            val id = downloadManager.enqueue(request)
            downloadId = id
            preferences.edit {
                putLong(KEY_DOWNLOAD_ID, id)
                putString(KEY_DOWNLOAD_VERSION, release.version)
                putString(KEY_RELEASE_URL, release.releaseUrl)
            }
            publish(
                currentState.copy(
                    phase = UpdatePhase.DOWNLOADING,
                    availableVersion = release.version,
                    progress = 0,
                    message = "Downloading v${release.version}",
                    releaseUrl = release.releaseUrl,
                ),
            )
            startPolling()
        }.onFailure { failure ->
            publish(
                currentState.copy(
                    phase = UpdatePhase.ERROR,
                    progress = null,
                    message = "Could not start download: ${failure.message ?: "Download manager unavailable"}",
                ),
            )
        }
    }

    override fun installDownloadedUpdate() {
        val id = downloadId ?: preferences.getLong(KEY_DOWNLOAD_ID, -1L).takeIf { it >= 0L }
        if (id == null) {
            publish(currentState.copy(phase = UpdatePhase.ERROR, message = "No installable update available"))
            return
        }
        if (!applicationContext.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${applicationContext.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { applicationContext.startActivity(settingsIntent) }
                .onSuccess {
                    awaitingInstallPermission = true
                    publish(
                        currentState.copy(
                            phase = UpdatePhase.READY_TO_INSTALL,
                            message = "Allow installation from this source, then return to continue",
                        ),
                    )
                }
                .onFailure { failure ->
                    publish(currentState.copy(phase = UpdatePhase.ERROR, message = "Could not open install settings: ${failure.message}"))
                }
            return
        }
        val uri = downloadManager.getUriForDownloadedFile(id)
        if (uri == null) {
            publish(currentState.copy(phase = UpdatePhase.ERROR, message = "Downloaded APK not found"))
            return
        }
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { applicationContext.startActivity(installIntent) }
            .onSuccess {
                awaitingInstallPermission = false
                publish(currentState.copy(phase = UpdatePhase.READY_TO_INSTALL, message = "Opened Android installer"))
            }
            .onFailure { failure ->
                publish(currentState.copy(phase = UpdatePhase.ERROR, message = "Could not open installer: ${failure.message}"))
            }
    }

    /** Called by the host after returning from the unknown-apps settings page. */
    fun onHostResume() {
        if (awaitingInstallPermission && currentState.phase == UpdatePhase.READY_TO_INSTALL &&
            applicationContext.packageManager.canRequestPackageInstalls()
        ) {
            awaitingInstallPermission = false
            installDownloadedUpdate()
        }
    }

    private fun fetchLatestRelease(): ReleaseAsset {
        val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 8_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "BrainExporter-Android/$normalizedCurrentVersion")
            val responseCode = connection.responseCode
            require(responseCode in 200..299) { "GitHub API returned HTTP $responseCode" }
            val root = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val version = root.getString("tag_name").removePrefix("v")
            val releaseUrl = root.getString("html_url")
            val assets = root.getJSONArray("assets")
            val candidates = buildList {
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        add(name to asset.getString("browser_download_url"))
                    }
                }
            }
            val expectedName = "BrainExporter-v$version.apk"
            val apk = candidates.firstOrNull { it.first == expectedName }
                ?: candidates.firstOrNull { it.first.startsWith("BrainExporter", ignoreCase = true) }
                ?: candidates.firstOrNull()
                ?: throw IllegalStateException("Latest release has no APK asset")
            ReleaseAsset(version, releaseUrl, apk.first, apk.second)
        } finally {
            connection.disconnect()
        }
    }

    private fun recoverDownload() {
        val savedId = preferences.getLong(KEY_DOWNLOAD_ID, -1L)
        if (savedId < 0L) return
        val version = preferences.getString(KEY_DOWNLOAD_VERSION, null)
        if (version != null && !isNewerVersion(version, normalizedCurrentVersion)) {
            downloadManager.remove(savedId)
            preferences.edit {
                remove(KEY_DOWNLOAD_ID)
                remove(KEY_DOWNLOAD_VERSION)
                remove(KEY_RELEASE_URL)
            }
            return
        }
        downloadId = savedId
        publish(
            currentState.copy(
                phase = UpdatePhase.DOWNLOADING,
                availableVersion = version,
                progress = null,
                message = "Restoring download state",
                releaseUrl = preferences.getString(KEY_RELEASE_URL, null),
            ),
        )
        startPolling()
    }

    private fun startPolling() {
        polling?.cancel(false)
        polling = worker.scheduleWithFixedDelay(::pollDownload, 0, 750, TimeUnit.MILLISECONDS)
    }

    private fun pollDownload() {
        val id = downloadId ?: return
        val query = DownloadManager.Query().setFilterById(id)
        downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                failDownload("Download record no longer exists")
                return
            }
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            when (status) {
                DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED, DownloadManager.STATUS_RUNNING -> {
                    val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val progress = if (total > 0L) ((downloaded * 100L) / total).toInt().coerceIn(0, 100) else null
                    publish(
                        currentState.copy(
                            phase = UpdatePhase.DOWNLOADING,
                            progress = progress,
                            message = progress?.let { "Downloading · $it%" } ?: "Waiting for download manager",
                        ),
                    )
                }

                DownloadManager.STATUS_SUCCESSFUL -> {
                    polling?.cancel(false)
                    polling = null
                    publish(
                        currentState.copy(
                            phase = UpdatePhase.READY_TO_INSTALL,
                            progress = 100,
                            message = "Update ready to install",
                        ),
                    )
                    if (applicationContext.packageManager.canRequestPackageInstalls()) {
                        installDownloadedUpdate()
                    }
                }

                DownloadManager.STATUS_FAILED -> {
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    failDownload("System download failed ($reason)")
                }
            }
        }
    }

    private fun failDownload(message: String) {
        polling?.cancel(false)
        polling = null
        downloadId?.let { id -> downloadManager.remove(id) }
        preferences.edit { remove(KEY_DOWNLOAD_ID) }
        downloadId = null
        publish(currentState.copy(phase = UpdatePhase.ERROR, progress = null, message = message))
    }

    private fun publish(next: AppUpdateState) {
        currentState = next
        listeners.forEach { listener -> runCatching { listener.onStateChanged(next) } }
    }

    override fun close() {
        polling?.cancel(false)
        worker.shutdownNow()
        listeners.clear()
    }

    private data class ReleaseAsset(
        val version: String,
        val releaseUrl: String,
        val assetName: String,
        val apkUrl: String,
    )

    private companion object {
        const val LATEST_RELEASE_API = "https://api.github.com/repos/vexpaer/BrainExporter/releases/latest"
        const val RELEASE_DOWNLOAD_PREFIX = "https://github.com/vexpaer/BrainExporter/releases/download/"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val PREFERENCES = "brainexporter_updater_v1"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_VERSION = "download_version"
        const val KEY_RELEASE_URL = "release_url"
        val SAFE_ASSET_NAME = Regex("[a-zA-Z0-9._-]+\\.apk", RegexOption.IGNORE_CASE)
    }
}

internal fun isNewerVersion(candidate: String, current: String): Boolean {
    fun parts(version: String): List<Int> = version.removePrefix("v").substringBefore('-')
        .split('.')
        .map { it.toIntOrNull() ?: 0 }
    val candidateParts = parts(candidate)
    val currentParts = parts(current)
    for (index in 0 until maxOf(candidateParts.size, currentParts.size)) {
        val candidatePart = candidateParts.getOrElse(index) { 0 }
        val currentPart = currentParts.getOrElse(index) { 0 }
        if (candidatePart != currentPart) return candidatePart > currentPart
    }
    return false
}
