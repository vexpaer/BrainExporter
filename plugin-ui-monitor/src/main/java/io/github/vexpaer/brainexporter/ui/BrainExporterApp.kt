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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.MonitorController
import kotlinx.coroutines.launch

/** Permission gates are supplied by the host app; the UI plug-in stays testable. */
typealias PermissionGate = (onGranted: () -> Unit) -> Unit
typealias RecordingPermissionGate = (onGranted: () -> Unit) -> Unit

const val GITHUB_REPOSITORY = "https://github.com/vexpaer/BrainExporter"
const val GITHUB_RELEASES = "$GITHUB_REPOSITORY/releases/latest"

private val NavItems = listOf(
    NavigationDestination(Icons.Filled.Home, "Home"),
    NavigationDestination(Icons.Filled.GraphicEq, "Monitor"),
    NavigationDestination(Icons.Filled.Extension, "Modules"),
    NavigationDestination(Icons.Filled.Settings, "Settings"),
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

    BrainExporterTheme {
        var page by rememberSaveable { mutableIntStateOf(0) }
        BackHandler(enabled = page != 0) { page = 0 }
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
                text = { TextButton(onClick = openReleases) { Text("View release") } },
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
                AppNavigation(
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
                    0 -> HomeScreen(
                        connectionPhase = snapshot.connection.phase,
                        onOpenMonitor = { page = 1 },
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
                    else -> SettingsScreen(
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
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = singleLine, minLines = minLines,
        visualTransformation = visualTransformation,
        supportingText = supportingText?.let { message -> { Text(message) } },
    )
}

// ---------------------------------------------------------------------------
// Settings keeps local profile, updates and privacy in one place.
// ---------------------------------------------------------------------------

@Composable
private fun SettingsScreen(
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
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
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
        modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            PageHeader(
                title = "Settings",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            InstrumentSurface(contentPadding = PaddingValues(0.dp)) {
                ListItem(
                    headlineContent = { Text(currentUser ?: "Guest profile") },
                    leadingContent = { Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
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
                ProfileEntry("EEG data & privacy") {
                    onNotice("Documents/eegData · local only")
                }
                HorizontalHairline()
                ProfileEntry("GitHub & about", onOpenGithub)
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
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel("VERSION")
                    Text(
                        "v${state.currentVersion}",
                        color = Starlight,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                val (dot, label) = when (state.phase) {
                    UpdatePhase.UP_TO_DATE -> Success to "Up to date"
                    UpdatePhase.AVAILABLE -> SolarMetal to "Update available"
                    UpdatePhase.DOWNLOADING -> OrbitBlue to "Downloading"
                    UpdatePhase.READY_TO_INSTALL -> OrbitBlue to "Ready to install"
                    UpdatePhase.CHECKING -> OrbitBlue to "Checking"
                    UpdatePhase.ERROR -> Danger to "Check failed"
                    else -> MoonDust to "Not checked"
                }
                StatusPill(dotColor = dot, label = label, textColor = if (state.phase == UpdatePhase.UP_TO_DATE) MoonDust else dot)
            }
            if (state.phase == UpdatePhase.ERROR) Text(state.message, color = Danger, style = MaterialTheme.typography.bodySmall)
            if (state.phase == UpdatePhase.DOWNLOADING) {
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0, 100) / 100f }, modifier = Modifier.fillMaxWidth())
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.XS)) {
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
private fun ProfileEntry(label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClickLabel = label, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    )
}
