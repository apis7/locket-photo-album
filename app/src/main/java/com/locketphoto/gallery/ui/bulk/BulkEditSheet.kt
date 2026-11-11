package com.locketphoto.gallery.ui.bulk

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.locketphoto.gallery.edit.AdjustKind
import com.locketphoto.gallery.edit.BulkSpec
import com.locketphoto.gallery.edit.Filters

private val BulkAdjusts = listOf(AdjustKind.Brightness, AdjustKind.Contrast, AdjustKind.Saturation, AdjustKind.Warmth)

/**
 * "Edit together": one set of changes for every selected photo.
 * Videos in the selection are left alone (the sheet says so).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BulkEditSheet(photoCount: Int, videoCount: Int, onDismiss: () -> Unit, onApply: (BulkSpec) -> Unit) {
    var spec by remember { mutableStateOf(BulkSpec()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text("Edit $photoCount photo${if (photoCount == 1) "" else "s"}", style = MaterialTheme.typography.headlineMedium)
            if (videoCount > 0) {
                Text(
                    "$videoCount video${if (videoCount == 1) "" else "s"} will be skipped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Section("Turn")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = spec.quarterTurns != 0,
                    onClick = { spec = spec.copy(quarterTurns = (spec.quarterTurns + 1) % 4) },
                    label = { Text(if (spec.quarterTurns == 0) "Rotate" else "${spec.quarterTurns * 90}°") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.RotateRight, null) },
                )
                FilterChip(
                    selected = spec.mirror,
                    onClick = { spec = spec.copy(mirror = !spec.mirror) },
                    label = { Text("Mirror") },
                    leadingIcon = { Icon(Icons.Rounded.Flip, null) },
                )
                FilterChip(
                    selected = spec.auto,
                    onClick = { spec = spec.copy(auto = !spec.auto) },
                    label = { Text("Auto enhance") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                )
            }

            Section("Filter")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Filters.all.forEach { f ->
                    FilterChip(selected = spec.filterId == f.id, onClick = { spec = spec.copy(filterId = f.id) }, label = { Text(f.name) })
                }
            }
            if (spec.filterId != Filters.none.id) {
                LabeledSlider("Intensity", spec.filterIntensity, 0f..1f) { spec = spec.copy(filterIntensity = it) }
            }

            Section("Adjust")
            BulkAdjusts.forEach { kind ->
                LabeledSlider(kind.label, spec.adjust.get(kind), -1f..1f) { spec = spec.copy(adjust = spec.adjust.with(kind, it)) }
            }

            Section("Save")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !spec.overwrite,
                    onClick = { spec = spec.copy(overwrite = false) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("As copies") }
                SegmentedButton(
                    selected = spec.overwrite,
                    onClick = { spec = spec.copy(overwrite = true) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("Replace originals") }
            }
            Text(
                if (spec.overwrite) "Android will ask once to allow changing these files." else "Edited copies go next to the originals.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { spec = BulkSpec(overwrite = spec.overwrite) }, enabled = !spec.isNoop) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(spec) }, enabled = !spec.isNoop && photoCount > 0) {
                    Text("Apply to $photoCount")
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        // Centered sliders (-1..1) shouldn't look half-full at zero: one track color throughout.
        val centered = range.start < 0f
        val track = MaterialTheme.colorScheme.surfaceContainerHigh
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = if (centered) SliderDefaults.colors(activeTrackColor = track, inactiveTrackColor = track) else SliderDefaults.colors(),
            modifier = Modifier.weight(1f),
        )
        Text(
            "${(value * 100).toInt()}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(40.dp).padding(start = 8.dp),
        )
    }
}
