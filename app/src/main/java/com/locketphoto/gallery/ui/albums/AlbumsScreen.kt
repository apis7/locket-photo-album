package com.locketphoto.gallery.ui.albums

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.locketphoto.gallery.data.Album
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.thumbs.Thumb
import com.locketphoto.gallery.ui.FolderInfo

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun AlbumsScreen(
    albums: List<Album>,
    folders: List<FolderInfo>,
    itemsById: Map<Long, MediaItem>,
    favorites: List<MediaItem>,
    videos: List<MediaItem>,
    thumbPx: Int,
    extraBottom: androidx.compose.ui.unit.Dp,
    onOpenFavorites: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenFolder: (String) -> Unit,
    onCreateAlbum: (String) -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    val status = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = status.calculateTopPadding() + 8.dp,
            bottom = nav.calculateBottomPadding() + 24.dp + extraBottom,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Albums", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 6.dp, top = 12.dp, bottom = 4.dp))
        }
        item { AlbumCard("Favorites", favorites.size, favorites.firstOrNull(), thumbPx, Icons.Rounded.Star, onOpenFavorites) }
        item { AlbumCard("Videos", videos.size, videos.firstOrNull(), thumbPx, Icons.Rounded.PlayCircle, onOpenVideos) }
        items(albums, key = { "a${it.id}" }) { a ->
            AlbumCard(a.name, a.mediaIds.count { it in itemsById }, a.mediaIds.firstNotNullOfOrNull { itemsById[it] }, thumbPx, null) { onOpenAlbum(a.id) }
        }
        item { NewAlbumCard { creating = true } }

        if (folders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "On this phone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 6.dp, top = 18.dp),
                )
            }
            items(folders, key = { "f${it.name}" }) { f ->
                AlbumCard(f.name, f.count, f.cover, thumbPx, null) { onOpenFolder(f.name) }
            }
        }
    }

    if (creating) {
        NameDialog(title = "New album", confirm = "Create", initial = "", onDismiss = { creating = false }) {
            creating = false
            onCreateAlbum(it)
        }
    }
}

@Composable
private fun AlbumCard(name: String, count: Int, cover: MediaItem?, thumbPx: Int, badge: ImageVector?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "press")
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { clip = true; shape = CardShape }
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (cover != null) {
                AsyncImage(
                    model = Thumb(cover.uri, thumbPx, cover.modifiedAt),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (badge != null) {
                Icon(
                    badge, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
                )
            }
        }
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
        Text(
            if (count == 1) "1 item" else "$count items",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun NewAlbumCard(onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .graphicsLayer { clip = true; shape = CardShape }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        }
        Text("New album", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    }
}

/** Small text-entry dialog used for creating and renaming albums. */
@Composable
fun NameDialog(title: String, confirm: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Name") },
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
