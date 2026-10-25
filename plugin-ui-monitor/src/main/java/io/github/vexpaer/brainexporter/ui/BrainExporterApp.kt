package io.github.vexpaer.brainexporter.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import kotlinx.coroutines.delay

/** Permission gates are supplied by the host app; the UI plug-in stays testable. */
typealias PermissionGate = (onGranted: () -> Unit) -> Unit
typealias RecordingPermissionGate = (onGranted: () -> Unit) -> Unit

const val GITHUB_REPOSITORY = "https://github.com/vexpaer/BrainExporter"
const val GITHUB_RELEASES = "$GITHUB_REPOSITORY/releases/latest"

private val NavItems = listOf(
    OrbitalItem(Icons.Filled.Public, "Home"),
    OrbitalItem(Icons.Filled.GraphicEq, "Monitor"),
    OrbitalItem(Icons.Filled.Extension, "Modules"),
    OrbitalItem(Icons.Filled.AccountCircle, "Account"),
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
    val accountStore = remember(context) { LocalAccountStore(context) }
    val demoController = remember { DemoSignalController() }
    var demoEnabled by rememberSaveable { mutableStateOf(false) }
    var demoSnapshot by remember { mutableStateOf(demoController.snapshot()) }
    DisposableEffect(demoController) {
        val subscription = demoController.addListener { demoSnapshot = it }
        onDispose { subscription.close() }
    }

    BrainExporterTheme {
        var page by rememberSaveable { mutableIntStateOf(0) }
        LaunchedEffect(demoEnabled, page) {
            while (demoEnabled && page == 1) {
                demoController.advance()
                delay(128L)
            }
        }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val notice: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) } }
        val openGithub: () -> Unit = {
            runCatching { uriHandler.openUri(GITHUB_REPOSITORY) }
                .onFailure { notice("Could not open GitHub: ${it.message}") }
        }
        val openReleases: () -> Unit = {
            runCatching { uriHandler.openUri(updateState.releaseUrl ?: GITHUB_RELEASES) }
                .onFailure { notice("Could not open release: ${it.message}") }
        }
        var dismissedUpdateVersion by rememberSaveable { mutableStateOf<String?>(null) }
        var dismissedInstallVersion by rememberSaveable { mutableStateOf<String?>(null) }

        if (updateState.phase == UpdatePhase.AVAILABLE &&
            updateState.availableVersion != dismissedUpdateVersion
        ) {
            BrandAlertDialog(
                onDismissRequest = { dismissedUpdateVersion = updateState.availableVersion },
                title = "Version v${updateState.availableVersion}",
                confirmLabel = "Download update",
                onConfirm = {
                    dismissedUpdateVersion = updateState.availableVersion
                    updateController.downloadUpdate()
                },
                dismissLabel = "Later",
                onDismiss = { dismissedUpdateVersion = updateState.availableVersion },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        Text("A new version is available on GitHub. Download the APK with Android's download manager.")
                        TextButton(onClick = openReleases) { Text("View release") }
                    }
                },
            )
        }
        if (updateState.phase == UpdatePhase.READY_TO_INSTALL &&
            updateState.availableVersion != dismissedInstallVersion
        ) {
            BrandAlertDialog(
                onDismissRequest = { dismissedInstallVersion = updateState.availableVersion },
                title = "Update ready",
                confirmLabel = "Install update",
                onConfirm = {
                    dismissedInstallVersion = updateState.availableVersion
                    updateController.installDownloadedUpdate()
                },
                dismissLabel = "Later",
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
                        val direction = if (targetState > initialState) 1 else -1
                        (fadeIn(tween(230, easing = Motion.Emphasized)) +
                            slideInHorizontally(tween(340, easing = Motion.Emphasized)) {
                                (it * 0.14f).toInt() * direction
                            }).togetherWith(
                                fadeOut(tween(130)) +
                                    slideOutHorizontally(tween(200)) {
                                        (-it * 0.08f).toInt() * direction
                                    },
                            )
                    }
                },
                label = "page-transition",
            ) { targetPage ->
                when (targetPage) {
                    0 -> PlanetHomeScreen(
                        connectionPhase = snapshot.connection.phase,
                        onOpenMonitor = { page = 1 },
                        onOpenDemo = { demoEnabled = true; page = 1 },
                        modifier = Modifier.padding(padding),
                    )
                    1 -> MonitorScreen(
                        snapshot = if (demoEnabled) demoSnapshot else snapshot,
                        controller = if (demoEnabled) demoController else controller,
                        permissionGate = permissionGate,
                        recordingPermissionGate = recordingPermissionGate,
                        demo = demoEnabled,
                        onStartDemo = { demoEnabled = true },
                        onExitDemo = { demoEnabled = false },
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
            title = if (registering) "Create account" else "Sign in",
            confirmLabel = if (registering) "Register" else "Sign in",
            onConfirm = {
                val result = if (registering && password != confirmation) {
                    AuthResult(false, "Passwords do not match.")
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
            dismissLabel = "Cancel",
            onDismiss = { showAuth = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    Text("Credentials stay on this device.")
                    BrandTextField(
                        label = "Username",
                        value = username,
                        onValueChange = { username = it },
                        singleLine = true,
                    )
                    BrandTextField(
                        label = "Password",
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    if (registering) {
                        BrandTextField(
                            label = "Confirm password",
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
                title = "Account",
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
                        contentDescription = "Local account avatar",
                        tint = OrbitBlue,
                        modifier = Modifier.size(52.dp),
                    )
                }
                Spacer(Modifier.height(Spacing.XXS))
                Text(
                    currentUser ?: "Guest",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Starlight,
                )
                Text(
                    if (currentUser == null) "Stored on this device" else "Signed in locally",
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
                    ) { Text("Sign in") }
                    OutlinedButton(
                        onClick = { registering = true; showAuth = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("Register") }
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = { store.logout(); currentUser = null; onNotice("Signed out.") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign out") }
            }
        }
        item { UpdateCard(updateState, updateController::checkForUpdates, updateController::downloadUpdate, updateController::installDownloadedUpdate, onOpenRelease) }
        item {
            InstrumentSurface(color = Obsidian, contentPadding = PaddingValues(0.dp)) {
                ProfileEntry("EEG data & privacy", "Saved to Documents/eegData") {
                    onNotice("EEG files stay in Documents/eegData. No cloud upload.")
                }
                HorizontalHairline()
                ProfileEntry("GitHub & about", GITHUB_REPOSITORY, onOpenGithub)
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
                        "App version · v${state.currentVersion}",
                        color = Starlight,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val (dot, label) = when (state.phase) {
                    UpdatePhase.UP_TO_DATE -> Success to "Up to date"
                    UpdatePhase.AVAILABLE -> SolarMetal to "Update available"
                    UpdatePhase.DOWNLOADING, UpdatePhase.READY_TO_INSTALL -> OrbitBlue to "Ready to install"
                    UpdatePhase.ERROR -> Danger to "Check failed"
                    else -> MoonDust to "Not checked"
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
                    UpdatePhase.AVAILABLE -> Button(onClick = onDownload) { Text("Download v${state.availableVersion}") }
                    UpdatePhase.READY_TO_INSTALL -> Button(onClick = onInstall) { Text("Install update") }
                    UpdatePhase.CHECKING, UpdatePhase.DOWNLOADING -> OutlinedButton(onClick = {}, enabled = false) {
                        Text(if (state.phase == UpdatePhase.CHECKING) "Checking…" else "Downloading…")
                    }
                    else -> OutlinedButton(onClick = onCheck) { Text("Check for updates") }
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
