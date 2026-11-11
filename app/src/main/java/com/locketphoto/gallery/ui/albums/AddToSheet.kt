package com.locketphoto.gallery.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.locketphoto.gallery.data.Album

/**
 * "Add to…" for one or many photos: pick an album (or make one), and add/remove tags.
 * A tag chip is checked when every chosen photo has it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddToSheet(
    count: Int,
    albums: List<Album>,
    allTags: List<String>,
    tagsOnAll: Set<String>,
    onDismiss: () -> Unit,
    onAddToAlbum: (Long) -> Unit,
    onNewAlbum: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 16.dp),
        ) {
            Text(if (count == 1) "Add to…" else "Add $count items to…", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            Text("Album", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { naming = true }.padding(vertical = 12.dp),
            ) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Text("New album", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            }
            albums.forEach { a ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onAddToAlbum(a.id) }.padding(vertical = 12.dp),
                ) {
                    Icon(Icons.Outlined.PhotoAlbum, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(16.dp))
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${a.mediaIds.size}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Tags", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                allTags.forEach { tag ->
                    val on = tag in tagsOnAll
                    FilterChip(
                        selected = on,
                        onClick = { if (on) onRemoveTag(tag) else onAddTag(tag) },
                        label = { Text(tag) },
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it.take(40) },
                singleLine = true,
                placeholder = { Text("New tag") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (newTag.isNotBlank()) onAddTag(newTag)
                    newTag = ""
                }),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (naming) {
        NameDialog(title = "New album", confirm = "Create", initial = "", onDismiss = { naming = false }) {
            naming = false
            onNewAlbum(it)
        }
    }
}
