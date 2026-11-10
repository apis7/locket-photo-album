package com.locketphoto.gallery.ui.grid

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.thumbs.Thumb
import com.locketphoto.gallery.ui.components.rememberShimmerClock
import com.locketphoto.gallery.ui.components.shimmer

private const val COLUMNS = 4
private val CellGap = 2.dp
private val CellShape = RoundedCornerShape(3.dp)

@Composable
fun GridScreen(
    state: GridUiState,
    onOpen: (index: Int) -> Unit,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val clock = rememberShimmerClock()
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant
    val shimmerHi = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val status = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val topBarHeight = 64.dp

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val density = LocalDensity.current
        // Request thumbnails in a few fixed size buckets so the cache is reused across rotations.
        val cellPx = with(density) { (maxWidth / COLUMNS).toPx() }.toInt()
        val thumbPx = when {
            cellPx <= 256 -> 256
            cellPx <= 384 -> 384
            else -> 512
        }
        val padding = PaddingValues(
            top = status.calculateTopPadding() + topBarHeight,
            bottom = nav.calculateBottomPadding() + 24.dp,
        )

        if (state.loading) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(CellGap),
                verticalArrangement = Arrangement.spacedBy(CellGap),
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(COLUMNS * 12) {
                    Box(Modifier.aspectRatio(1f).graphicsLayer { clip = true; shape = CellShape }.shimmer(clock, shimmerBase, shimmerHi))
                }
            }
        } else if (state.items.isEmpty()) {
            Text(
                "No photos yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(COLUMNS),
                state = gridState,
                contentPadding = padding,
                horizontalArrangement = Arrangement.spacedBy(CellGap),
                verticalArrangement = Arrangement.spacedBy(CellGap),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(
                    count = state.entries.size,
                    key = { state.entries[it].key },
                    contentType = { if (state.entries[it] is GridEntry.Header) 0 else 1 },
                    span = { if (state.entries[it] is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                ) { i ->
                    when (val e = state.entries[i]) {
                        is GridEntry.Header -> MonthHeader(e.title)
                        is GridEntry.Cell -> MediaCell(
                            item = e.item,
                            thumbPx = thumbPx,
                            clock = clock,
                            shimmerBase = shimmerBase,
                            shimmerHi = shimmerHi,
                            onClick = { onOpen(e.index) },
                        )
                    }
                }
            }

            if (state.items.size > 60) {
                DateScrubber(
                    state = gridState,
                    labelAt = { idx -> state.entries.getOrNull(idx)?.scrubLabel.orEmpty() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding(), end = 2.dp),
                )
            }
        }

        TopBar(height = topBarHeight, count = state.items.size)
    }
}

@Composable
private fun TopBar(height: androidx.compose.ui.unit.Dp, count: Int) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to bg, 0.7f to bg.copy(alpha = 0.92f), 1f to bg.copy(alpha = 0f)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp)
            .height(height),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.Bottom) {
            Text("Locket", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
            if (count > 0) {
                Text(
                    "  $count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun MonthHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 14.dp, top = 18.dp, bottom = 8.dp),
    )
}

@Composable
private fun MediaCell(
    item: MediaItem,
    thumbPx: Int,
    clock: State<Float>,
    shimmerBase: Color,
    shimmerHi: Color,
    onClick: () -> Unit,
) {
    var loaded by remember(item.id) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    Box(
        Modifier
            .aspectRatio(1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = true
                shape = CellShape
            }
            .then(if (loaded) Modifier else Modifier.shimmer(clock, shimmerBase, shimmerHi))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        AsyncImage(
            model = Thumb(item.uri, thumbPx, item.modifiedAt),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onSuccess = { loaded = true },
            modifier = Modifier.fillMaxSize(),
        )
        if (item.isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    formatDuration(item.durationMs),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        if (item.isFavorite) {
            Icon(
                Icons.Rounded.Star,
                null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp),
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
