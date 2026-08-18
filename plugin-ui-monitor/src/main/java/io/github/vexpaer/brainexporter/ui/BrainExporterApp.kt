package io.github.vexpaer.brainexporter.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import kotlinx.coroutines.launch

/** Permission gates are supplied by the host app; the UI plug-in stays testable. */
typealias PermissionGate = (onGranted: () -> Unit) -> Unit
typealias RecordingPermissionGate = (onGranted: () -> Unit) -> Unit

const val GITHUB_REPOSITORY = "https://github.com/vexpaer/BrainExporter"
const val GITHUB_RELEASES = "$GITHUB_REPOSITORY/releases/latest"

@OptIn(ExperimentalMaterial3Api::class)
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
        val drawerState = rememberDrawerState(DrawerValue.Closed)
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
            AlertDialog(
                onDismissRequest = { dismissedUpdateVersion = updateState.availableVersion },
                title = { Text("发现 v${updateState.availableVersion}") },
                text = { Text("GitHub Release 已发布新版本。可直接交给系统下载器下载 APK，完成后进入 Android 安装流程。") },
                confirmButton = {
                    Button(onClick = {
                        dismissedUpdateVersion = updateState.availableVersion
                        updateController.downloadUpdate()
                    }) { Text("下载更新") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = openReleases) { Text("查看 Release") }
                        TextButton(onClick = { dismissedUpdateVersion = updateState.availableVersion }) { Text("稍后") }
                    }
                },
            )
        }
        if (updateState.phase == UpdatePhase.READY_TO_INSTALL &&
            updateState.availableVersion != dismissedInstallVersion
        ) {
            AlertDialog(
                onDismissRequest = { dismissedInstallVersion = updateState.availableVersion },
                title = { Text("更新已下载") },
                text = { Text(updateState.message) },
                confirmButton = {
                    Button(onClick = {
                        dismissedInstallVersion = updateState.availableVersion
                        updateController.installDownloadedUpdate()
                    }) { Text("安装更新") }
                },
                dismissButton = {
                    TextButton(onClick = { dismissedInstallVersion = updateState.availableVersion }) { Text("稍后") }
                },
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Panel, drawerContentColor = TextPrimary) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Text("BrainExporter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("开放式脑电采集与监测 · v0.3.0", color = Cyan, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(22.dp))
                        DrawerEntry(Icons.Filled.Public, "专注与休息") { page = 0; scope.launch { drawerState.close() } }
                        DrawerEntry(Icons.Filled.GraphicEq, "实时信号监测") { page = 1; scope.launch { drawerState.close() } }
                        DrawerEntry(Icons.Filled.Extension, "处理模块") { page = 2; scope.launch { drawerState.close() } }
                        DrawerEntry(Icons.Filled.MusicNote, "在线音频设置") { page = 3; scope.launch { drawerState.close() } }
                        DrawerEntry(Icons.Filled.AccountCircle, "本地账号") { page = 4; scope.launch { drawerState.close() } }
                        DrawerEntry(Icons.Filled.Help, "帮助与关于") {
                            scope.launch { drawerState.close() }
                            openGithub()
                        }
                    }
                }
            },
        ) {
            Scaffold(
                containerColor = Ink,
                snackbarHost = { SnackbarHost(snackbar) },
                topBar = {
                    if (page != 0) {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("BrainExporter", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = connectionCaption(snapshot),
                                        color = if (snapshot.connection.phase == ConnectionPhase.CONNECTED) Cyan else TextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        imageVector = Icons.Filled.Menu,
                                        contentDescription = "打开导航菜单",
                                        tint = TextPrimary,
                                    )
                                }
                            },
                            actions = { ConnectionDot(snapshot.connection.phase); Spacer(Modifier.size(14.dp)) },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Ink),
                        )
                    }
                },
                bottomBar = {
                    NavigationBar(containerColor = PanelSoft) {
                        listOf(
                            Triple(Icons.Filled.Public, "星球", 0),
                            Triple(Icons.Filled.GraphicEq, "监测", 1),
                            Triple(Icons.Filled.Extension, "模块", 2),
                            Triple(Icons.Filled.MusicNote, "音频", 3),
                            Triple(Icons.Filled.AccountCircle, "我的", 4),
                        ).forEach { (symbol, label, index) ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick = { page = index },
                                icon = { Icon(imageVector = symbol, contentDescription = null) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            ) { padding ->
                androidx.compose.animation.AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(Motion.FastMs)) + scaleIn(
                            initialScale = 0.98f,
                            animationSpec = tween(Motion.FastMs),
                        )).togetherWith(fadeOut(tween(Motion.FastMs)))
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
}

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
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("在线音频", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("音乐在播放时从网络流式加载，不会打包进 APK。", color = TextMuted)
        }
        item {
            OutlinedTextField(
                value = focusUrl,
                onValueChange = { focusUrl = it },
                label = { Text("专注星球音频 URL") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }
        item {
            OutlinedTextField(
                value = restUrl,
                onValueChange = { restUrl = it },
                label = { Text("休息星球音频 URL") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onSave(AudioSources(focusUrl.trim(), restUrl.trim())) }) { Text("保存") }
                OutlinedButton(onClick = onRestore) { Text("恢复默认") }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(CardRadius)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("默认音乐鸣谢", fontWeight = FontWeight.Bold)
                    Text("Study And Relax / Ethereal Relaxation — Kevin MacLeod (incompetech.com)", color = TextMuted)
                    Text("Creative Commons Attribution 3.0", color = Cyan)
                    Text("第三方地址可能失效；你可以替换为任何可直接播放的 HTTPS 音频地址。", color = TextMuted)
                }
            }
        }
    }
}

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
        AlertDialog(
            onDismissRequest = { showAuth = false },
            title = { Text(if (registering) "注册本地账号" else "登录本地账号") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("账号数据只保存在本机，不会上传。", color = TextMuted)
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("账号") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("密码") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (registering) {
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it },
                            label = { Text("确认密码") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
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
                }) { Text(if (registering) "注册" else "登录") }
            },
            dismissButton = { TextButton(onClick = { showAuth = false }) { Text("取消") } },
        )
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Box(Modifier.size(86.dp).background(Panel, CircleShape), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.AccountCircle,
                    contentDescription = "本地账号头像",
                    tint = Cyan,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        item { Text(currentUser ?: "访客", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { Text(if (currentUser == null) "账号仅保存在这台设备" else "本地账号已登录", color = TextMuted) }
        if (currentUser == null) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        item {
            UpdateCard(
                state = updateState,
                onCheck = updateController::checkForUpdates,
                onDownload = updateController::downloadUpdate,
                onInstall = updateController::installDownloadedUpdate,
                onOpenRelease = onOpenRelease,
            )
        }
        item { ProfileEntry("隐私与 EEG 数据", "采集文件保存在 Documents/eegData") { onNotice("EEG CSV 仅写入手机 Documents/eegData，不会上传。") } }
        item { ProfileEntry("帮助与关于", GITHUB_REPOSITORY, onOpenGithub) }
    }
}

@Composable
private fun UpdateCard(
    state: AppUpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(CardRadius),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("应用更新 · v${state.currentVersion}", fontWeight = FontWeight.Bold)
                    Text(state.message, color = if (state.phase == UpdatePhase.ERROR) Danger else TextMuted)
                }
                ConnectionDot(if (state.phase == UpdatePhase.UP_TO_DATE) ConnectionPhase.CONNECTED else ConnectionPhase.DISCONNECTED)
            }
            if (state.phase == UpdatePhase.DOWNLOADING) {
                Box(Modifier.fillMaxWidth().height(5.dp).background(PanelSoft, CircleShape)) {
                    Box(
                        Modifier
                            .fillMaxWidth((state.progress ?: 8).coerceIn(1, 100) / 100f)
                            .height(5.dp)
                            .background(Cyan, CircleShape),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
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
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel)) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(label)
                Text(detail, color = TextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted,
            )
        }
    }
}

@Composable
private fun DrawerEntry(imageVector: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        icon = { Icon(imageVector = imageVector, contentDescription = null, tint = Cyan) },
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
    )
}

@Composable
private fun ConnectionDot(phase: ConnectionPhase) {
    val color = when (phase) {
        ConnectionPhase.CONNECTED -> Cyan
        ConnectionPhase.CONNECTING, ConnectionPhase.SCANNING -> Amber
        ConnectionPhase.ERROR -> Danger
        else -> TextMuted
    }
    Canvas(Modifier.size(11.dp)) {
        drawCircle(color.copy(alpha = 0.18f), radius = size.minDimension / 2)
        drawCircle(color, radius = size.minDimension / 4)
    }
}

private fun connectionCaption(snapshot: MonitorSnapshot): String = when (snapshot.connection.phase) {
    ConnectionPhase.CONNECTED -> snapshot.connection.deviceName ?: "设备已连接"
    ConnectionPhase.CONNECTING -> "正在连接"
    ConnectionPhase.SCANNING -> "正在扫描"
    ConnectionPhase.ERROR -> "需要检查"
    ConnectionPhase.DISCONNECTED -> "设备未连接"
}
