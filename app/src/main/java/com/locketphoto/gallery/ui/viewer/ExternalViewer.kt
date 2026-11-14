package com.locketphoto.gallery.ui.viewer

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.locketphoto.gallery.data.ExternalMedia
import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shows one photo or video another app handed us, when it isn't part of the library. */
@Composable
fun ExternalViewer(
    uri: Uri,
    type: String?,
    thumbPx: Int,
    onClose: () -> Unit,
    onShare: (MediaItem) -> Unit,
) {
    val context = LocalContext.current
    var item by remember(uri) { mutableStateOf<MediaItem?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        item = withContext(Dispatchers.IO) { ExternalMedia.describe(context, uri, type) }
        failed = item == null
    }
    val shown = item
    if (shown != null) {
        ViewerScreen(
            items = listOf(shown),
            startIndex = 0,
            thumbPx = thumbPx,
            onClose = { onClose() },
            onShare = onShare,
            onFavorite = {},
            onTrash = {},
            libraryActions = false,
        )
    } else {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (failed) Text("Can't open this file", color = Color.White)
        }
    }
}
