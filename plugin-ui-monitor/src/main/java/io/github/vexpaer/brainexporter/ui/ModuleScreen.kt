package io.github.vexpaer.brainexporter.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.MonitorController
import io.github.vexpaer.brainexporter.sdk.MonitorSnapshot
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleOrigin
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleState
import io.github.vexpaer.brainexporter.sdk.ProcessingModuleType

/** 模块页:信号处理工作台。名称/类型/启停/输入输出一眼可见,导入明确但不抢眼。 */
@Composable
internal fun ModuleScreen(
    snapshot: MonitorSnapshot,
    controller: MonitorController,
    packages: ModulePackageController,
    onNotice: (String) -> Unit,
    onOpenMonitor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var removalRequest by remember { mutableStateOf<ProcessingModuleState?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val result = runCatching { readModuleManifest(context, uri) }
            .fold(
                onSuccess = packages::importManifest,
                onFailure = { Result.failure(it) },
            )
        result.onSuccess { descriptor -> onNotice("已导入 ${descriptor.displayName}") }
            .onFailure { failure -> onNotice("导入失败：${failure.message ?: "模块包无效"}") }
    }

    removalRequest?.let { module ->
        BrandAlertDialog(
            onDismissRequest = { removalRequest = null },
            title = "移除 ${module.descriptor.displayName}？",
            confirmLabel = "移除",
            onConfirm = {
                packages.uninstall(module.descriptor.id)
                    .onSuccess { onNotice("模块已移除") }
                    .onFailure { onNotice("移除失败：${it.message}") }
                removalRequest = null
            },
            dismissLabel = "取消",
            onDismiss = { removalRequest = null },
            text = { Text("将从应用内移除这个导入模块。已有 EEG CSV 不受影响。") },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    PageHeader(
                        overline = "MODULES",
                        title = "处理模块",
                        subtitle = "导入、启用并在监测页查看实时输出",
                    )
                }
                OutlinedButton(onClick = {
                    importer.launch(
                        arrayOf("application/json", "text/json", "text/plain", "application/octet-stream"),
                    )
                }) { Text("导入模块") }
            }
        }
        item { ModuleContractOverview() }
        items(snapshot.modules, key = { it.descriptor.id }) { module ->
            ModuleCard(
                module = module,
                selected = snapshot.selectedModuleId == module.descriptor.id,
                onEnabledChange = { controller.setModuleEnabled(module.descriptor.id, it) },
                onVisualize = {
                    if (!module.enabled) controller.setModuleEnabled(module.descriptor.id, true)
                    controller.selectModule(module.descriptor.id)
                    onOpenMonitor()
                },
                onRemove = { removalRequest = module },
            )
        }
        if (snapshot.modules.isEmpty()) {
            item {
                InstrumentSurface(color = Obsidian) {
                    EmptyState(
                        icon = Icons.Filled.Extension,
                        title = "尚未安装模块。",
                        detail = "导入 .be-module.json 后，可把处理结果加入监测页查看。",
                    )
                }
            }
        }
        item {
            Text(
                "导入格式为 .be-module.json。应用只运行已实现并经过参数校验的安全引擎，不执行外部 Dex/JAR。",
                color = MoonDust.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(vertical = Spacing.XS),
            )
        }
    }
}

@Composable
private fun ModuleContractOverview() {
    InstrumentSurface {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            SectionLabel("统一接口与自动可视化")
            ContractRow(
                color = OrbitBlue,
                title = "脑电 → 脑电",
                detail = "保持采样索引与通道结构；自动获得时域、PSD、频谱和波段图。",
            )
            ContractRow(
                color = LunarViolet,
                title = "脑电 → 一个或多个特征值",
                detail = "每个特征声明名称、单位和可选通道；自动生成数值卡与历史曲线。",
            )
        }
    }
}

@Composable
private fun ContractRow(color: androidx.compose.ui.graphics.Color, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(9.dp).background(color, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, color = Starlight, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MoonDust, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ModuleCard(
    module: ProcessingModuleState,
    selected: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onVisualize: () -> Unit,
    onRemove: () -> Unit,
) {
    val descriptor = module.descriptor
    val typeColor = if (descriptor.type == ProcessingModuleType.EEG_TO_EEG) OrbitBlue else LunarViolet
    val borderColor = if (selected && module.enabled) OrbitBlue.copy(alpha = 0.45f) else Hairline
    InstrumentSurface(
        color = if (module.enabled) Eclipse else Obsidian,
        borderColor = borderColor,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                Box(Modifier.size(10.dp).background(typeColor, CircleShape))
                Column(Modifier.weight(1f)) {
                    Text(descriptor.displayName, color = Starlight, fontWeight = FontWeight.SemiBold)
                    Text(
                        "v${descriptor.version} · ${if (descriptor.origin == ProcessingModuleOrigin.BUILT_IN) "内置" else "已导入"}",
                        color = MoonDust,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Switch(checked = module.enabled, onCheckedChange = onEnabledChange)
            }
            Text(descriptor.description, color = MoonDust, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XXS), verticalAlignment = Alignment.CenterVertically) {
                ModuleTag(
                    if (descriptor.type == ProcessingModuleType.EEG_TO_EEG) "EEG → EEG" else "EEG → 特征",
                    typeColor,
                )
                ModuleTag(descriptor.engine, MoonDust)
            }
            Text(
                module.error ?: module.message,
                color = if (module.error == null) MoonDust else Danger,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
                Button(onClick = onVisualize) { Text(if (selected) "正在监测" else "在监测中查看") }
                if (descriptor.origin == ProcessingModuleOrigin.IMPORTED) {
                    OutlinedButton(onClick = onRemove) { Text("移除") }
                }
            }
        }
    }
}

@Composable
private fun ModuleTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .background(color.copy(alpha = 0.10f), ShapeCapsule)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

private fun readModuleManifest(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("无法读取所选文件")
    return input.bufferedReader().use { reader ->
        val text = StringBuilder()
        val buffer = CharArray(4_096)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            text.append(buffer, 0, count)
            require(text.length <= 65_536) { "模块清单不能超过 64 KiB" }
        }
        text.toString()
    }
}
