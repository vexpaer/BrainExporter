package io.github.vexpaer.brainexporter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.vexpaer.brainexporter.sdk.ConnectionPhase

@Composable
internal fun HomeScreen(connectionPhase: ConnectionPhase, onOpenMonitor: () -> Unit, modifier: Modifier = Modifier) {
    val connected = connectionPhase == ConnectionPhase.CONNECTED
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.XL)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Image(painterResource(R.drawable.brain_exporter_logo), contentDescription = "BrainExporter logo", modifier = Modifier.size(64.dp).clip(ShapeL))
                Column(Modifier.weight(1f)) {
                    Text("BrainExporter", style = MaterialTheme.typography.titleLarge)
                    Text("Your portable EEG workspace", style = MaterialTheme.typography.bodyMedium, color = MoonDust)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                Text("Closer to\nthe signal.", style = MaterialTheme.typography.displaySmall)
                Text("Acquire, understand and export EEG from your Android device.", style = MaterialTheme.typography.bodyLarge, color = MoonDust)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(Spacing.XL), verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                    Text(if (connected) "Device connected" else "Start a session", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(if (connected) "Your live workspace is ready." else "Connect your RT-BCI over Bluetooth to view eight EEG channels in real time.", color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Button(onClick = onOpenMonitor, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text(if (connected) "Continue to Monitor" else "Open Monitor")
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item {
            InstrumentSurface(contentPadding = PaddingValues(0.dp)) {
                HomeFeature(Icons.Filled.GraphicEq, "See the whole signal", "8 channels · Waveforms, spectra and band power")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = Spacing.M))
                HomeFeature(Icons.Filled.Extension, "Shape your workflow", "Built-in and importable processing modules")
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = Spacing.M))
                HomeFeature(Icons.Filled.SaveAlt, "Keep your data close", "Timestamped CSV saved on your device")
            }
        }
        item { Text("Built for research. Designed to stay local.", style = MaterialTheme.typography.bodySmall, color = MoonDust) }
    }
}

@Composable
private fun HomeFeature(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) }, leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow))
}
