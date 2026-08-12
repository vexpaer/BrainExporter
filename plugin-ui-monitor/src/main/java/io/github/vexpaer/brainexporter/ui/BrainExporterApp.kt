package io.github.vexpaer.brainexporter.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainExporterApp(
    controller: MonitorController,
    permissionGate: PermissionGate,
    recordingPermissionGate: RecordingPermissionGate,
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

        LaunchedEffect(playback) {
            if (playback.phase == PlaybackPhase.ERROR) snackbar.showSnackbar(playback.message)
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Panel, drawerContentColor = TextPrimary) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Text("BrainExporter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("开放式脑电采集与监测 · v0.1.0", color = Cyan, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(22.dp))
                        DrawerEntry("◉", "专注与休息") { page = 0; scope.launch { drawerState.close() } }
                        DrawerEntry("∿", "实时信号监测") { page = 1; scope.launch { drawerState.close() } }
                        DrawerEntry("♫", "在线音频设置") { page = 2; scope.launch { drawerState.close() } }
                        DrawerEntry("♙", "本地账号") { page = 3; scope.launch { drawerState.close() } }
                        DrawerEntry("?", "帮助与关于") {
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
                                TextButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Text("☰", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
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
                            Triple("◉", "星球", 0),
                            Triple("∿", "监测", 1),
                            Triple("♫", "音频", 2),
                            Triple("♙", "我的", 3),
                        ).forEach { (symbol, label, index) ->
                            NavigationBarItem(
                                selected = page == index,
                                onClick = { page = index },
                                icon = { Text(symbol) },
                                label = { Text(label) },
                            )
                        }
                    }
                },
            ) { padding ->
                when (page) {
                    0 -> PlanetHomeScreen(
                        playback = playback,
                        sources = audioSources,
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
                    2 -> AudioSettingsScreen(
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
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanetHomeScreen(
    playback: AudioPlaybackState,
    sources: AudioSources,
    onToggle: (AudioMode, String) -> Unit,
    onStop: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val modes = remember { AudioMode.entries }
    val pagerState = rememberPagerState(pageCount = { modes.size })
    LaunchedEffect(pagerState.currentPage) {
        val visibleMode = modes[pagerState.currentPage]
        if (playback.mode != null && playback.mode != visibleMode &&
            playback.phase in setOf(PlaybackPhase.LOADING, PlaybackPhase.PLAYING)
        ) {
            onStop("点击星球开始")
        }
    }
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize().background(
            Brush.radialGradient(
                listOf(Color(0xFF101B35), Color(0xFF050A13), Ink),
                radius = 1_100f,
            ),
        ),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 28.dp),
        pageSpacing = 28.dp,
    ) { index ->
        val mode = modes[index]
        val phase = if (playback.mode == mode) playback.phase else PlaybackPhase.STOPPED
        Planet(
            mode = mode,
            phase = phase,
            onClick = { onToggle(mode, sources.forMode(mode)) },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun Planet(
    mode: AudioMode,
    phase: PlaybackPhase,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "${mode.name}-rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (mode == AudioMode.FOCUS) 14_000 else 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "planet-angle",
    )
    val rotating = phase == PlaybackPhase.PLAYING
    val description = "${mode.title}星球，${when (phase) {
        PlaybackPhase.PLAYING -> "正在播放，点击停止"
        PlaybackPhase.LOADING -> "正在加载在线音乐"
        PlaybackPhase.ERROR -> "播放失败，点击重试"
        PlaybackPhase.STOPPED -> "已停止，点击播放"
    }}；左右滑动切换星球"

    Box(
        modifier = modifier
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(310.dp)) {
            val glow = when {
                phase == PlaybackPhase.ERROR -> Danger
                mode == AudioMode.FOCUS -> Cyan
                else -> Color(0xFFA991FF)
            }
            drawCircle(
                brush = Brush.radialGradient(
                    0f to glow.copy(alpha = if (phase == PlaybackPhase.LOADING) 0.58f else 0.42f),
                    0.42f to glow.copy(alpha = 0.12f),
                    1f to Color.Transparent,
                ),
            )
            drawArc(
                color = glow.copy(alpha = 0.48f),
                startAngle = 195f,
                sweepAngle = 292f,
                useCenter = false,
                topLeft = Offset(size.width * 0.04f, size.height * 0.34f),
                size = Size(size.width * 0.92f, size.height * 0.32f),
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
        Canvas(
            Modifier.size(205.dp).graphicsLayer { rotationZ = if (rotating) rotation else 0f },
        ) {
            val colors = if (mode == AudioMode.FOCUS) {
                listOf(Color(0xFF45E6CF), Color(0xFF28479D), Color(0xFF111B50), Color(0xFF050916))
            } else {
                listOf(Color(0xFFFFD3A2), Color(0xFFA991FF), Color(0xFF47538D), Color(0xFF12152C))
            }
            drawCircle(brush = Brush.radialGradient(colors, center = Offset(size.width * 0.34f, size.height * 0.28f)))
            val detail = if (mode == AudioMode.FOCUS) Color(0xFF8CFFF0) else Color(0xFFFFE7CC)
            drawOval(
                color = detail.copy(alpha = 0.22f),
                topLeft = Offset(size.width * 0.16f, size.height * 0.27f),
                size = Size(size.width * 0.48f, size.height * 0.15f),
            )
            drawArc(
                color = detail.copy(alpha = 0.34f),
                startAngle = 208f,
                sweepAngle = 228f,
                useCenter = false,
                topLeft = Offset(size.width * 0.11f, size.height * 0.47f),
                size = Size(size.width * 0.78f, size.height * 0.28f),
                style = Stroke(width = 5.dp.toPx()),
            )
            drawCircle(detail.copy(alpha = 0.28f), radius = size.minDimension * 0.07f, center = Offset(size.width * 0.72f, size.height * 0.31f))
            drawCircle(detail.copy(alpha = 0.18f), radius = size.minDimension * 0.045f, center = Offset(size.width * 0.33f, size.height * 0.70f))
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
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
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
                Text("♙", style = MaterialTheme.typography.displaySmall, color = Cyan)
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
        item { ProfileEntry("隐私与 EEG 数据", "采集文件保存在 Documents/eegData") { onNotice("EEG CSV 仅写入手机 Documents/eegData，不会上传。") } }
        item { ProfileEntry("帮助与关于", GITHUB_REPOSITORY, onOpenGithub) }
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
            Text("›", color = TextMuted)
        }
    }
}

@Composable
private fun DrawerEntry(symbol: String, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = false,
        icon = { Text(symbol, color = Cyan) },
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
