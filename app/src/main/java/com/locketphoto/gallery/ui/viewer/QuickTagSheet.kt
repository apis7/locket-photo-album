package com.locketphoto.gallery.ui.viewer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Long-press a photo in the viewer: tag it fast. Your existing tags are one tap away
 * (checked = on this photo); type a new one and press Done.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickTagSheet(
    tags: Set<String>,
    allTags: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    // Jump straight to typing when you have no tags yet.
    LaunchedEffect(Unit) { if (allTags.isEmpty()) focus.requestFocus() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().imePadding().padding(bottom = 16.dp)) {
            Text("Tags", style = MaterialTheme.typography.headlineMedium)
            val shown = (allTags + tags).distinct()
            if (shown.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                    shown.forEach { tag ->
                        val on = tag in tags
                        FilterChip(
                            selected = on,
                            onClick = { if (on) onRemove(tag) else onAdd(tag) },
                            label = { Text(tag) },
                            leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(40) },
                singleLine = true,
                placeholder = { Text("New tag") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (text.isNotBlank()) onAdd(text)
                    text = ""
                }),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).focusRequester(focus),
            )
        }
    }
}
