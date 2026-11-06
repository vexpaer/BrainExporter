package io.github.vexpaer.brainexporter.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
internal fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}

@Composable
internal fun PageHeader(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier.fillMaxWidth(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
}

/** Filled or outlined M3 cards; selection is the only reason to add an outline. */
@Composable
internal fun InstrumentSurface(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    borderColor: Color = Color.Transparent,
    shape: RoundedCornerShape = ShapeM,
    contentPadding: PaddingValues = PaddingValues(Spacing.M),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(), shape = shape,
        colors = CardDefaults.cardColors(containerColor = color),
        border = if (borderColor != Color.Transparent && borderColor != Hairline) androidx.compose.foundation.BorderStroke(1.dp, borderColor) else null,
    ) { Column(Modifier.padding(contentPadding), content = content) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SegmentedModeControl(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = selected == value, onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) { Text(label) }
        }
    }
}

@Composable
internal fun StatusPill(dotColor: Color, label: String, modifier: Modifier = Modifier, textColor: Color = dotColor) {
    Surface(modifier = modifier, shape = ShapeS, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).semantics(mergeDescendants = true) { contentDescription = label },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(6.dp).background(dotColor, CircleShape))
            Text(label, color = textColor, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal data class NavigationDestination(val icon: ImageVector, val label: String)

@Composable
internal fun AppNavigation(items: List<NavigationDestination>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(selected = index == selectedIndex, onClick = { onSelect(index) },
                icon = { Icon(item.icon, contentDescription = null) }, label = { Text(item.label) })
        }
    }
}

@Composable
internal fun BrandAlertDialog(
    onDismissRequest: () -> Unit, title: String, confirmLabel: String, onConfirm: () -> Unit,
    modifier: Modifier = Modifier, dismissLabel: String? = null, onDismiss: (() -> Unit)? = null,
    bodyStyle: TextStyle = MaterialTheme.typography.labelLarge, text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(onDismissRequest = onDismissRequest, modifier = modifier,
        title = { Text(title) }, text = text,
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, style = bodyStyle) } },
        dismissButton = { if (dismissLabel != null && onDismiss != null) TextButton(onClick = onDismiss) { Text(dismissLabel) } })
}

internal data class ChipOption<T>(val value: T, val label: String, val dotColor: Color? = null)

@Composable
internal fun <T> ChipSelector(options: List<ChipOption<T>>, isSelected: (T) -> Boolean, onSelect: (T) -> Unit, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.XS)) {
        items(options.size) { index ->
            val option = options[index]
            FilterChip(selected = isSelected(option.value), onClick = { onSelect(option.value) },
                label = { Text(option.label) },
                leadingIcon = option.dotColor?.let { color -> { Box(Modifier.size(8.dp).background(color, CircleShape)) } },
                modifier = Modifier.heightIn(min = 48.dp))
        }
        if (trailing != null) item { trailing() }
    }
}

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.lowercase().replaceFirstChar { it.titlecase() }, modifier.padding(top = Spacing.XS), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmall)
}

@Composable
internal fun EmptyState(icon: ImageVector, title: String, detail: String?, modifier: Modifier = Modifier, tint: Color = MoonDust) {
    Column(modifier.fillMaxWidth().padding(Spacing.XL), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(48.dp))
        Text(title, color = Starlight, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        detail?.let { Text(it, color = MoonDust, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium) }
    }
}
