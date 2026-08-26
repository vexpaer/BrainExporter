package io.github.vexpaer.brainexporter.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import kotlinx.coroutines.launch

/** Permission gates are supplied by the host app; the UI plug-in stays testable. */
typealias PermissionGate = (onGranted: () -> Unit) -> Unit
typealias RecordingPermissionGate = (onGranted: () -> Unit) -> Unit

const val GITHUB_REPOSITORY = "https://github.com/vexpaer/BrainExporter"
const val GITHUB_RELEASES = "$GITHUB_REPOSITORY/releases/latest"

private val NavItems = listOf(
    OrbitalItem(Icons.Filled.Public, "星球"),
    OrbitalItem(Icons.Filled.GraphicEq, "监测"),
    OrbitalItem(Icons.Filled.Extension, "模块"),
    OrbitalItem(Icons.Filled.MusicNote, "音频"),
    OrbitalItem(Icons.Filled.AccountCircle, "我的"),
)

@Composable
fun BrainExporterApp(
    controller: MonitorController,
    permissionGate: PermissionGate,
    recordingPermissionGate: RecordingPermissionGate,
    modulePackages: ModulePackageController,
    updateController: AppUpdateController,
) {
    var snapshot by remember(controller) { mutableStateOf(controller.snapshot()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(controller) {
        val subscription = controller.addListener { next -> mainHandler.post { snapshot = next } }
        onDispose {
            subscription.close()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }
    var updateState by remember(updateController) { mutableStateOf(updateController.state()) }
    DisposableEffect(updateController) {
        val subscription = updateController.addListener { next -> mainHandler.post { updateState = next } }
        onDispose { subscription.close() }
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val audioPreferences = remember(context) { AudioSourcePreferences(context) }
    var audioSources by remember { mutableStateOf(audioPreferences.load()) }
    val audioPlayer = remember(context) { OnlineAudioPlayer(context) }
    var playback by remember { mutableStateOf(audioPlayer.state) }
    DisposableEffect(audioPlayer) {
        audioPlayer.setListener { playback = it }
        onDispose { audioPlayer.close() }
    }
    val accountStore = remember(context) { LocalAccountStore(context) }

    BrainExporterTheme {
        var page by rememberSaveable { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val notice: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
        val openGithub: () -> Unit = {
            runCatching { uriHandler.openUri(GITHUB_REPOSITORY) }
                .onFailure { notice("无法打开 GitHub：${it.message}") }
        }
        val openReleases: () -> Unit = {
            runCatching { uriHandler.openUri(updateState.releaseUrl ?: GITHUB_RELEASES) }
                .onFailure { notice("无法打开 Release：${it.message}") }
        }
        var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
        var dismissedInstallVersion by rememberSaveable { mutableStateOf<String?>(null) }

        LaunchedEffect(playback) {
            if (playback.phase == PlaybackPhase.ERROR) snackbar.showSnackbar(playback.message)
        }

        if (updateState.phase == UpdatePhase.AVAILABLE &&
            updateState.availableVersion != dismissedUpdateVersion
        ) {
            BrandAlertDialog(
                onDismissRequest = { dismissedUpdateVersion = updateState.availableVersion },
                title = "发现 v${updateState.availableVersion}",
                confirmLabel = "下载更新",
                onConfirm = {
                    dismissedUpdateVersion = updateState.availableVersion
                    updateController.downloadUpdate()
                },
                dismissLabel = "稍后",
                onDismiss = { dismissedUpdateVersion = updateState.availableVersion },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("GitHub Release 已发布新版本。可直接交给系统下载器下载 APK，完成后进入 Android 安装流程。")
                        TextButton(onClick = openReleases) { Text("查看 Release") }
                    }
                },
            )
        }
        if (updateState.phase == UpdatePhase.READY_TO_INSTALL &&
            updateState.availableVersion != dismissedInstallVersion
        ) {
            BrandAlertDialog(
                onDismissRequest = { dismissedInstallVersion = updateState.availableVersion },
                title = "更新已下载",
                confirmLabel = "安装更新",
                onConfirm = {
                    dismissedInstallVersion = updateState.availableVersion
                    updateController.installDownloadedUpdate()
                },
                dismissLabel = "稍后",
                onDismiss = { dismissedInstallVersion = updateState.availableVersion },
                text = { Text(updateState.message) },
            )
        }

        Scaffold(
            containerColor = Void,
            snackbarHost = {
                SnackbarHost(snackbar) { data ->
                    Snackbar(
                        data,
                        containerColor = Eclipse,
                        contentColor = Starlight,
                        actionColor = OrbitBlue,
                        dismissActionContentColor = MoonDust,
                        shape = ShapeM,
                    )
                }
            },
            bottomBar = {
                OrbitalNavigation(
                    items = NavItems,
                    selectedIndex = page,
                    onSelect = { page = it },
                )
            },
        ) { padding ->
            val reduceMotion = rememberReduceMotion()
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    if (reduceMotion) {
                        fadeIn(snap()).togetherWith(fadeOut(snap()))
                    } else {
                        (fadeIn(tween(Motion.StandardMs, easing = Motion.Emphasized)) + scaleIn(
                            initialScale = 0.985f,
                            animationSpec = tween(Motion.StandardMs, easing = Motion.Emphasized),
                        )).togetherWith(fadeOut(tween(Motion.FastMs)))
                    }
                },
                label = "page-transition",
            ) { targetPage ->
                when (targetPage) {
                    0 -> PlanetHomeScreen(
                        playback = playback,
                        sources = audioSources,
                        connectionPhase = snapshot.connection.phase,
                        onToggle = audioPlayer::toggle,
                        onStop = audioPlayer::stop,
                        modifier = Modifier.padding(padding),
                    )
                    1 -> MonitorScreen(
                        snapshot = snapshot,
                        controller = controller,
                        permissionGate = permissionGate,
                        recordingPermissionGate = recordingPermissionGate,
                        modifier = Modifier.padding(padding),
                    )
                    2 -> ModuleScreen(
                        snapshot = snapshot,
                        controller = controller,
                        packages = modulePackages,
                        onNotice = notice,
                        onOpenMonitor = { page = 1 },
                        modifier = Modifier.padding(padding),
                    )
                    3 -> AudioSettingsScreen(
                        sources = audioSources,
                        modifier = Modifier.padding(padding),
                        onSave = { next ->
                            runCatching { audioPreferences.save(next) }
                                .onSuccess {
                                    audioPlayer.stop()
                                    audioSources = audioPreferences.load()
                                    notice("在线音频地址已保存在本机。")
                                }
                                .onFailure { notice(it.message ?: "音频地址无效。") }
                        },
                        onRestore = {
                            audioPlayer.stop()
                            audioSources = audioPreferences.restoreDefaults()
                            notice("已恢复默认在线音频。")
                        },
                    )
                    else -> ProfileScreen(
                        store = accountStore,
                        modifier = Modifier.padding(padding),
                        onNotice = notice,
                        onOpenGithub = openGithub,
                        updateState = updateState,
                        updateController = updateController,
                        onOpenRelease = openReleases,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 品牌输入字段：字段容器 + 说明文本，替代默认 OutlinedTextField 观感。
// 长 URL 保持多行完整可编辑，不为外观牺牲可用性。
// ---------------------------------------------------------------------------

@Composable
private fun BrandTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    supportingText: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderColor = if (focused) OrbitBlue.copy(alpha = 0.7f) else Hairline
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeM)
            .background(Obsidian)
            .border(1.dp, borderColor, ShapeM),
    ) {
        Column(Modifier.padding(horizontal = Spacing.M, vertical = Spacing.S)) {
            Text(
                text = label.uppercase(),
                color = if (focused) OrbitBlue else MoonDust,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            )
            Spacer(Modifier.height(Spacing.XXS))
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                visualTransformation = visualTransformation,
                cursorBrush = SolidColor(OrbitBlue),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Starlight),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = MoonDust.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = Spacing.M, end = Spacing.M, bottom = Spacing.S),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 在线音频设置：清晰字段容器 + 状态反馈 + 版权说明。
// ---------------------------------------------------------------------------

@Composable
private fun AudioSettingsScreen(
    sources: AudioSources,
    onSave: (AudioSources) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focusUrl by rememberSaveable(sources.focusUrl) { mutableStateOf(sources.focusUrl) }
    var restUrl by rememberSaveable(sources.restUrl) { mutableStateOf(sources.restUrl) }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item {
            PageHeader(
                overline = "AUDIO",
                title = "在线音频",
                subtitle = "音乐在播放时从网络流式加载，不会打包进 APK。",
            )
        }
        item {
            BrandTextField(
                label = "专注星球音频 URL",
                value = focusUrl,
                onValueChange = { focusUrl = it },
                minLines = 3,
                supportingText = "必须是可直接播放的 HTTPS 地址。",
            )
        }
        item {
            BrandTextField(
                label = "休息星球音频 URL",
                value = restUrl,
                onValueChange = { restUrl = it },
                minLines = 3,
                supportingText = "必须是可直接播放的 HTTPS 地址。",
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Button(onClick = { onSave(AudioSources(focusUrl.trim(), restUrl.trim())) }) {
                    Text("保存")
                }
                OutlinedButton(onClick = onRestore) { Text("恢复默认") }
            }
        }
        item {
            InstrumentSurface(color = Obsidian) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.XXS)) {
                    SectionLabel("CREDITS · 默认音乐鸣谢")
                    Spacer(Modifier.height(Spacing.XXS))
                    Text("Study And Relax / Ethereal Relaxation — Kevin MacLeod (incompetech.com)", color = MoonDust, style = MaterialTheme.typography.bodySmall)
                    Text("Creative Commons Attribution 3.0", color = OrbitBlue, style = MaterialTheme.typography.labelMedium)
                    Text("第三方地址可能失效；你可以替换为任何可直接播放的 HTTPS 音频地址。", color = MoonDust, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 我的：账号状态、更新、隐私与入口的清晰层级。
// ---------------------------------------------------------------------------

@Composable
private fun ProfileScreen(
    store: LocalAccountStore,
    onNotice: (String) -> Unit,
    onOpenGithub: () -> Unit,
    updateState: AppUpdateState,
    updateController: AppUpdateController,
    onOpenRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentUser by remember { mutableStateOf(store.currentUser()) }
    var showAuth by rememberSaveable { mutableStateOf(false) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }

    if (showAuth) {
        BrandAlertDialog(
            onDismissRequest = { showAuth = false },
            title = if (registering) "注册本地账号" else "登录本地账号",
            confirmLabel = if (registering) "注册" else "登录",
            onConfirm = {
                val result = if (registering && password != confirmation) {
                    AuthResult(false, "两次输入的密码不一致。")
                } else if (registering) {
                    store.register(username, password)
                } else {
                    store.login(username, password)
                }
                onNotice(result.message)
                if (result.success) {
                    currentUser = result.username
                    password = ""
                    confirmation = ""
                    showAuth = false
                }
            },
            dismissLabel = "取消",
            onDismiss = { showAuth = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text("账号数据只保存在本机，不会上传。")
                    BrandTextField(
                        label = "账号",
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                    )
                    BrandTextField(
                        label = "密码",
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (registering) {
                        BrandTextField(
                            label = "确认密码",
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            },
        )
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            PageHeader(
                overline = "ACCOUNT",
                title = "我的",
                subtitle = null,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.S),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.XXS),
            ) {
                Box(
                    Modifier
                        .size(84.dp)
                        .background(Eclipse, CircleShape)
                        .border(1.dp, Hairline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "本地账号头像",
                        tint = OrbitBlue,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.XXS))
                Text(
                    currentUser ?: "访客",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Starlight,
                )
                Text(
                    if (currentUser == null) "账号仅保存在这台设备" else "本地账号已登录",
                    color = MoonDust,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (currentUser == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                    Button(
                        onClick = { registering = false; showAuth = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("登录") }
                    OutlinedButton(
                        onClick = { registering = true; showAuth = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("注册") }
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = { store.logout(); currentUser = null; onNotice("已退出本地账号。") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("退出登录") }
            }
        }
        item { UpdateCard(updateState, updateController::checkForUpdates, updateController::downloadUpdate, updateController::installDownloadedUpdate, onOpenRelease) }
        item {
            InstrumentSurface(color = Obsidian, contentPadding = PaddingValues(0.dp)) {
                ProfileEntry("隐私与 EEG 数据", "采集文件保存在 Documents/eegData") {
                    onNotice("EEG CSV 仅写入手机 Documents/eegData，不会上传。")
                }
                HorizontalHairline()
                ProfileEntry("帮助与关于", GITHUB_REPOSITORY, onOpenGithub)
            }
        }
    }
}

@Composable
private fun HorizontalHairline() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.M)
            .height(1.dp)
            .background(Hairline),
    )
}

@Composable
private fun UpdateCard(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    InstrumentSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("VERSION")
                    Text(
                        "应用更新 · v${state.currentVersion}",
                        color = Starlight,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val (dot, label) = when (state.phase) {
                    UpdatePhase.UP_TO_DATE -> Success to "已是最新"
                    UpdatePhase.AVAILABLE -> SolarMetal to "有新版本"
                    UpdatePhase.DOWNLOADING, UpdatePhase.READY_TO_INSTALL -> OrbitBlue to "待安装"
                    UpdatePhase.ERROR -> Danger to "检查失败"
                    else -> MoonDust to "未检查"
                }
                StatusPill(dotColor = dot, label = label, textColor = if (state.phase == UpdatePhase.UP_TO_DATE) MoonDust else dot)
            }
            Text(
                state.message,
                color = if (state.phase == UpdatePhase.ERROR) Danger else MoonDust,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.phase == UpdatePhase.DOWNLOADING) {
                Box(Modifier.fillMaxWidth().height(4.dp).background(Hairline, ShapeCapsule)) {
                    Box(
                        Modifier
                            .fillMaxWidth((state.progress ?: 8).coerceIn(1, 100) / 100f)
                            .height(4.dp)
                            .background(OrbitBlue, ShapeCapsule),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                when (state.phase) {
                    UpdatePhase.AVAILABLE -> Button(onClick = onDownload) { Text("下载 v${state.availableVersion}") }
                    UpdatePhase.READY_TO_INSTALL -> Button(onClick = onInstall) { Text("安装更新") }
                    UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> OutlinedButton(onClick = {}, enabled = false) {
                        Text(if (state.phase == UpdatePhase.CHECKING) "检查中…" else "下载中…")
                    }
                    else -> OutlinedButton(onClick = onCheck) { Text("检查更新") }
                }
                TextButton(onClick = onOpenRelease) { Text("GitHub Release") }
            }
        }
    }
}

@Composable
private fun ProfileEntry(label: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Starlight, style = MaterialTheme.typography.titleSmall)
            Text(
                detail,
                color = MoonDust,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        androidx.compose.material3.Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MoonDust,
        )
    }
}
