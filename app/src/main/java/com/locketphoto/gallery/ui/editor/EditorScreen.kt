package com.locketphoto.gallery.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.ui.theme.Gold

/**
 * The photo / video editor. Always dark. Handles system back itself (asks before discarding).
 *
 * [requestWrite] must run MediaStore.createWriteRequest for the uri and call onGranted once the
 * user allows it. [onDone] gets the saved uri (the original for Save, a new one for Save as copy).
 */
@Composable
fun EditorScreen(
    item: MediaItem,
    requestWrite: (uri: Uri, onGranted: () -> Unit) -> Unit,
    onDone: (savedUri: Uri?) -> Unit,
    onCancel: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(EditorBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (item.isVideo) {
            VideoEditor(item, requestWrite, onDone, onCancel)
        } else {
            PhotoEditor(item, requestWrite, onDone, onCancel)
        }
    }
}

@Composable
internal fun TextEntryDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF232029),
        title = { Text(if (initial.isEmpty()) "Add text" else "Edit text", color = Color.White) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Gold,
                    cursorColor = Gold,
                ),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(if (initial.isNotEmpty() && text.isBlank()) "Remove" else "Done", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White) } },
    )
}
