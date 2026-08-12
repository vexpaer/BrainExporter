package io.github.vexpaer.brainexporter.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.edit
import androidx.core.net.toUri

enum class AudioMode(val title: String) {
    FOCUS("专注"),
    REST("休息"),
}

enum class PlaybackPhase {
    STOPPED,
    LOADING,
    PLAYING,
    ERROR,
}

data class AudioPlaybackState(
    val mode: AudioMode? = null,
    val phase: PlaybackPhase = PlaybackPhase.STOPPED,
    val message: String = "点击星球开始",
)

data class AudioSources(
    val focusUrl: String,
    val restUrl: String,
) {
    fun forMode(mode: AudioMode): String = if (mode == AudioMode.FOCUS) focusUrl else restUrl
}

class AudioSourcePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(): AudioSources = AudioSources(
        focusUrl = preferences.getString(KEY_FOCUS, null)?.takeIf(String::isNotBlank) ?: DEFAULT_FOCUS_URL,
        restUrl = preferences.getString(KEY_REST, null)?.takeIf(String::isNotBlank) ?: DEFAULT_REST_URL,
    )

    fun save(sources: AudioSources) {
        requireValidOnlineUrl(sources.focusUrl)
        requireValidOnlineUrl(sources.restUrl)
        preferences.edit {
            putString(KEY_FOCUS, sources.focusUrl.trim())
            putString(KEY_REST, sources.restUrl.trim())
        }
    }

    fun restoreDefaults(): AudioSources {
        preferences.edit {
            remove(KEY_FOCUS)
            remove(KEY_REST)
        }
        return load()
    }

    companion object {
        const val DEFAULT_FOCUS_URL =
            "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Study%20And%20Relax.mp3"
        const val DEFAULT_REST_URL =
            "https://incompetech.com/music/royalty-free/mp3-royaltyfree/Ethereal%20Relaxation.mp3"

        fun requireValidOnlineUrl(value: String) {
            val uri = value.trim().toUri()
            require(uri.scheme.equals("https", true)) { "音频地址必须以 https:// 开头。" }
            require(!uri.host.isNullOrBlank()) { "音频地址缺少有效域名。" }
        }

        private const val PREFERENCES = "brainexporter_audio"
        private const val KEY_FOCUS = "focus_url"
        private const val KEY_REST = "rest_url"
    }
}

/** A small streaming-only audio layer backed by Android MediaPlayer. */
class OnlineAudioPlayer(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stop("音频已暂停")
        }
    }

    private var focusRequest: AudioFocusRequest? = null
    private var player: MediaPlayer? = null
    private var generation = 0L
    private var listener: ((AudioPlaybackState) -> Unit)? = null

    var state: AudioPlaybackState = AudioPlaybackState()
        private set

    fun setListener(value: ((AudioPlaybackState) -> Unit)?) {
        listener = value
        value?.invoke(state)
    }

    fun toggle(mode: AudioMode, url: String) {
        if (state.mode == mode && state.phase in setOf(PlaybackPhase.LOADING, PlaybackPhase.PLAYING)) {
            stop("点击星球重新开始")
        } else {
            play(mode, url)
        }
    }

    fun play(mode: AudioMode, url: String) {
        try {
            AudioSourcePreferences.requireValidOnlineUrl(url)
        } catch (failure: IllegalArgumentException) {
            update(AudioPlaybackState(mode, PlaybackPhase.ERROR, failure.message ?: "音频地址无效"))
            return
        }
        releasePlayer()
        if (!requestAudioFocus()) {
            update(AudioPlaybackState(mode, PlaybackPhase.ERROR, "无法获取系统音频焦点"))
            return
        }
        val token = ++generation
        update(AudioPlaybackState(mode, PlaybackPhase.LOADING, "正在加载在线音乐…"))
        try {
            val next = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(appContext, url.toUri())
                setOnPreparedListener { prepared ->
                    if (token != generation) return@setOnPreparedListener
                    prepared.isLooping = true
                    prepared.start()
                    update(AudioPlaybackState(mode, PlaybackPhase.PLAYING, "正在播放 · 点击停止"))
                }
                setOnErrorListener { _, what, extra ->
                    if (token == generation) {
                        releasePlayer()
                        abandonAudioFocus()
                        update(AudioPlaybackState(mode, PlaybackPhase.ERROR, "在线音频加载失败（$what/$extra）"))
                    }
                    true
                }
                prepareAsync()
            }
            player = next
        } catch (failure: Exception) {
            releasePlayer()
            abandonAudioFocus()
            update(
                AudioPlaybackState(
                    mode,
                    PlaybackPhase.ERROR,
                    "无法播放在线音频：${failure.message ?: "未知错误"}",
                ),
            )
        }
    }

    fun stop(message: String = "点击星球开始") {
        generation++
        releasePlayer()
        abandonAudioFocus()
        update(AudioPlaybackState(message = message))
    }

    private fun releasePlayer() {
        val old = player
        player = null
        runCatching { old?.setOnPreparedListener(null) }
        runCatching { old?.setOnErrorListener(null) }
        runCatching { old?.stop() }
        runCatching { old?.release() }
    }

    private fun requestAudioFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        focusRequest?.let(audioManager::abandonAudioFocusRequest)
        focusRequest = null
    }

    private fun update(next: AudioPlaybackState) {
        state = next
        listener?.invoke(next)
    }

    override fun close() {
        stop()
        listener = null
    }
}
