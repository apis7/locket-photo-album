package com.locketphoto.gallery.ui.grid

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.thumbs.Thumb
import com.locketphoto.gallery.ui.components.rememberShimmerClock
import com.locketphoto.gallery.ui.components.shimmer
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val COLUMNS = 4
private val CellGap = 2.dp
private val CellShape = RoundedCornerShape(3.dp)

/** Everything the grid can ask of its owner. */
class GridActions(
    val onOpen: (index: Int) -> Unit,
    val onToggle: (id: Long) -> Unit,
    val onDragStart: (index: Int) -> Unit,
    val onDragTo: (index: Int) -> Unit,
    val onDragEnd: () -> Unit,
    val onClearSelection: () -> Unit,
    val onSelectAll: () -> Unit,
    val onShare: () -> Unit,
)

@Composable
fun GridScreen(
    state: GridUiState,
    selection: Set<Long>,
    actions: GridActions,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val clock = rememberShimmerClock()
    val shimmerBase = MaterialTheme.colorScheme.surfaceVariant
    val shimmerHi = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f)
    val status = WindowInsets.statusBars.asPaddingValues()
    val nav = WindowInsets.navigationBars.asPaddingValues()
    val topBarHeight = 64.dp
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting) { actions.onClearSelection() }

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
                modifier = Modifier
                    .fillMaxSize()
                    .dragSelect(gridState, state.entries, actions),
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
                            selecting = selecting,
                            selected = e.item.id in selection,
                            onClick = { if (selecting) actions.onToggle(e.item.id) else actions.onOpen(e.index) },
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

        TopBar(
            height = topBarHeight,
            count = state.items.size,
            selectedCount = selection.size,
            actions = actions,
        )
    }
}

/**
 * Long-press a photo to select it; keep the finger down and drag to select a range.
 * Near the top/bottom edge the grid scrolls by itself so ranges can span screens.
 *
 * Runs in the Initial pass, i.e. before the grid's own scrolling and the cells' clicks see
 * the touch. Until the long-press fires it only watches; once it fires it consumes every
 * event, so the grid can't steal the drag and the cell under the finger doesn't get a click.
 */
private fun Modifier.dragSelect(
    gridState: LazyGridState,
    entries: List<GridEntry>,
    actions: GridActions,
): Modifier = this.then(
    Modifier.pointerInput(entries) {
        val edge = 72.dp.toPx()

        fun itemIndexAt(pos: Offset): Int? {
            val info = gridState.layoutInfo
            // Item offsets are measured from the start of the content (after top padding),
            // so shift the touch point into that space.
            val p = Offset(pos.x, pos.y + info.viewportStartOffset).round()
            val hit = info.visibleItemsInfo.firstOrNull { IntRect(it.offset, it.size).contains(p) } ?: return null
            return (entries.getOrNull(hit.index) as? GridEntry.Cell)?.index
        }

        coroutineScope {
            val scope = this
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val slop = viewConfiguration.touchSlop
                // Phase 1: wait for a long-press without interfering. Moving or lifting cancels.
                val longPressed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                        if (!c.pressed || c.isConsumed) return@withTimeoutOrNull false
                        if ((c.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
                    }
                    @Suppress("UNREACHABLE_CODE") false
                } ?: true
                if (!longPressed) return@awaitEachGesture

                val anchor = itemIndexAt(down.position) ?: return@awaitEachGesture
                actions.onDragStart(anchor)

                // Phase 2: we own this gesture now.
                var lastPos = down.position
                var autoScroll = 0f
                val pump: Job = scope.launch {
                    while (isActive) {
                        if (autoScroll != 0f) {
                            gridState.scrollBy(autoScroll)
                            itemIndexAt(lastPos)?.let(actions.onDragTo)
                        }
                        delay(16)
                    }
                }
                try {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ev.changes.forEach { it.consume() }
                        if (!c.pressed) break
                        lastPos = c.position
                        val y = c.position.y
                        val h = size.height
                        val topEdge = edge * 1.6f // top bar sits over the grid
                        autoScroll = when {
                            y > h - edge -> ((y - (h - edge)) / edge).coerceAtMost(1.5f) * 28f
                            y < topEdge -> -((topEdge - y) / topEdge).coerceAtMost(1.5f) * 28f
                            else -> 0f
                        }
                        itemIndexAt(c.position)?.let(actions.onDragTo)
                    }
                } finally {
                    pump.cancel()
                    actions.onDragEnd()
                }
            }
        }
    },
)

@Composable
private fun TopBar(height: Dp, count: Int, selectedCount: Int, actions: GridActions) {
    val bg = MaterialTheme.colorScheme.background
    val selecting = selectedCount > 0
    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to bg, 0.7f to bg.copy(alpha = 0.94f), 1f to bg.copy(alpha = 0f)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(height),
    ) {
        AnimatedContent(
            targetState = selecting,
            transitionSpec = {
                (fadeIn() + slideInVertically { -it / 3 }) togetherWith (fadeOut() + slideOutVertically { it / 3 })
            },
            label = "topbar",
            modifier = Modifier.fillMaxSize(),
        ) { sel ->
            if (!sel) {
                Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Photo Album", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                    if (count > 0) {
                        Text(
                            "  $count",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            } else {
                Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onClearSelection) { Icon(Icons.Rounded.Close, "Cancel selection") }
                    AnimatedContent(
                        targetState = selectedCount,
                        transitionSpec = {
                            val up = targetState > initialState
                            (slideInVertically { if (up) it else -it } + fadeIn()) togetherWith
                                (slideOutVertically { if (up) -it else it } + fadeOut())
                        },
                        label = "count",
                    ) { n ->
                        Text("$n selected", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = actions.onSelectAll) { Icon(Icons.Rounded.SelectAll, "Select all") }
                    IconButton(onClick = actions.onShare) { Icon(Icons.Rounded.Share, "Share") }
                }
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
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    var loaded by remember(item.id) { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val pressScale by animateFloatAsState(if (pressed) 0.9f else 1f, bouncy, label = "press")
    // Selected photos shrink into a rounded "card" inside their cell.
    val selScale by animateFloatAsState(if (selected) 0.84f else 1f, bouncy, label = "sel")
    val corner by animateDpAsState(if (selected) 14.dp else 3.dp, spring(), label = "corner")

    Box(
        Modifier
            .aspectRatio(1f)
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = pressScale * selScale
                    scaleX = s
                    scaleY = s
                    clip = true
                    shape = RoundedCornerShape(corner)
                }
                .then(if (loaded) Modifier else Modifier.shimmer(clock, shimmerBase, shimmerHi)),
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
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                ) {
                    Text(formatDuration(item.durationMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            if (item.isFavorite) {
                Icon(Icons.Rounded.Star, null, tint = Color.White, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(14.dp))
            }
        }

        // Selection marker: empty ring while selecting, gold check when chosen.
        AnimatedVisibility(
            visible = selecting,
            enter = fadeIn() + scaleIn(initialScale = 0.4f),
            exit = fadeOut() + scaleOut(targetScale = 0.4f),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { scaleIn(spring(dampingRatio = 0.4f), initialScale = 0.3f) togetherWith fadeOut() },
                label = "check",
            ) { on ->
                if (on) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                    )
                } else {
                    Box(
                        Modifier
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.18f), CircleShape)
                            .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    )
                }
            }
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
