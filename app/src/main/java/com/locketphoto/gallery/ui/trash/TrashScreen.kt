package com.locketphoto.gallery.ui.trash

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MediaRepository
import com.locketphoto.gallery.thumbs.Thumb
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.ceil

class TrashViewModel(repo: MediaRepository) : ViewModel() {
    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val items: StateFlow<List<MediaItem>?> = repo.observeTrash()
        .onEach { list ->
            val ids = list.mapTo(HashSet()) { it.id }
            _selection.update { sel -> sel.filterTo(LinkedHashSet()) { it in ids } }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggle(id: Long) = _selection.update { if (id in it) it - id else it + id }
    fun clear() { _selection.value = emptySet() }
    fun selectAll() { _selection.value = items.value.orEmpty().mapTo(LinkedHashSet()) { it.id } }
}

@Composable
fun TrashScreen(
    items: List<MediaItem>?,
    selection: Set<Long>,
    thumbPx: Int,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRestore: (List<MediaItem>) -> Unit,
    onDeleteForever: (List<MediaItem>) -> Unit,
) {
    val selecting = selection.isNotEmpty()
    BackHandler { if (selecting) onClearSelection() else onBack() }
    val list = items.orEmpty()
    val chosen = list.filter { it.id in selection }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(if (selecting) "${selection.size} selected" else "Trash", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Deleted for good after 30 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (list.isNotEmpty()) {
                    TextButton(onClick = if (selection.size == list.size) onClearSelection else onSelectAll) {
                        Text(if (selection.size == list.size) "Select none" else "Select all")
                    }
                }
            }

            when {
                items == null -> Unit
                list.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Trash is empty", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { item ->
                        TrashCell(item, thumbPx, selected = item.id in selection, onClick = { onToggle(item.id) })
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Surface(tonalElevation = 6.dp, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(vertical = 10.dp),
                ) {
                    TextButton(onClick = { onRestore(chosen) }) {
                        Icon(Icons.Outlined.RestoreFromTrash, null)
                        Spacer(Modifier.size(8.dp))
                        Text("Restore")
                    }
                    TextButton(onClick = { onDeleteForever(chosen) }) {
                        Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.size(8.dp))
                        Text("Delete forever", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrashCell(item: MediaItem, thumbPx: Int, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(if (selected) 0.84f else 1f, spring(dampingRatio = 0.45f), label = "sel")
    val daysLeft = ceil((item.expiresAt - System.currentTimeMillis()) / 86_400_000.0).toInt().coerceAtLeast(0)
    Box(Modifier.aspectRatio(1f).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = RoundedCornerShape(if (selected) 14.dp else 3.dp)
            }.background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = Thumb(item.uri, thumbPx, item.modifiedAt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.85f },
            )
            Text(
                if (daysLeft == 1) "1 day" else "$daysLeft days",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape),
            )
        } else {
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp)
                    .background(Color.Black.copy(alpha = 0.18f), CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
            )
        }
    }
}
