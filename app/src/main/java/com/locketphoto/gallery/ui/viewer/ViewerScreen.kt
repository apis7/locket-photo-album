package com.locketphoto.gallery.ui.viewer

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import com.locketphoto.gallery.ui.theme.Gold
import com.locketphoto.gallery.ui.components.TwoTapTrashButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.thumbs.Thumb
import com.locketphoto.gallery.ui.components.sharedMedia
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ViewerScreen(
    items: List<MediaItem>,
    startIndex: Int,
    thumbPx: Int,
    onClose: (index: Int) -> Unit,
    onShare: (MediaItem) -> Unit,
    onFavorite: (MediaItem) -> Unit,
    onTrash: (MediaItem) -> Unit,
    tagsOf: (MediaItem) -> Set<String> = { emptySet() },
    allTags: List<String> = emptyList(),
    albumsOf: (MediaItem) -> List<String> = { emptyList() },
    onAddTag: (MediaItem, String) -> Unit = { _, _ -> },
    onRemoveTag: (MediaItem, String) -> Unit = { _, _ -> },
    onAddTo: (MediaItem) -> Unit = {},
    onEdit: ((MediaItem) -> Unit)? = null,
    onSetWallpaper: (MediaItem) -> Unit = {},
) {
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose(0) }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var infoFor by remember { mutableStateOf<MediaItem?>(null) }
    var tagFor by remember { mutableStateOf<MediaItem?>(null) }
    val haptics = LocalHapticFeedback.current
    var activeVideo by remember { mutableStateOf<VideoController?>(null) }
    val context = LocalContext.current
    val dismiss = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val closeThresholdPx = with(density) { 110.dp.toPx() }

    SystemBarsFor(chromeVisible = chrome)
    BackHandler { onClose(pager.currentPage) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val heightPx = with(density) { maxHeight.toPx() }
        val progress = (abs(dismiss.value) / (heightPx * 0.45f)).coerceIn(0f, 1f)

        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 1f - progress)))

        HorizontalPager(
            state = pager,
            key = { items[it].id },
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            val isCurrent = page == pager.currentPage
            MediaPage(
                item = item,
                thumbPx = thumbPx,
                isCurrent = isCurrent,
                settled = page == pager.settledPage,
                dismissOffset = if (isCurrent) dismiss.value else 0f,
                dismissProgress = if (isCurrent) progress else 0f,
                chromeVisible = chrome,
                onTap = { chrome = !chrome },
                onLongPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    tagFor = item
                },
                onDismissDrag = { dy -> scope.launch { dismiss.snapTo(dismiss.value + dy) } },
                onVideo = { v, on -> if (on) activeVideo = v else if (activeVideo === v) activeVideo = null },
                onDismissEnd = {
                    if (abs(dismiss.value) > closeThresholdPx) {
                        onClose(pager.currentPage)
                    } else {
                        scope.launch { dismiss.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)) }
                    }
                },
            )
        }

        val current = items.getOrNull(pager.currentPage)
        AnimatedVisibility(
            visible = chrome && progress < 0.05f,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopChrome(
                current,
                onBack = { onClose(pager.currentPage) },
                onInfo = { infoFor = current },
                onSetWallpaper = { current?.let(onSetWallpaper) },
                onUseAs = { current?.let { useAs(context, it) } },
            )
        }
        AnimatedVisibility(
            visible = chrome && progress < 0.05f,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column {
            activeVideo?.let { VideoControls(it, visible = true) }
            BottomChrome(
                isFavorite = current?.isFavorite == true,
                onShare = { current?.let(onShare) },
                onFavorite = { current?.let(onFavorite) },
                onEdit = onEdit?.let { e -> { current?.let(e) } },
                onAddTo = { current?.let(onAddTo) },
                onTrash = { current?.let(onTrash) },
                trashKey = current?.id,
            )
            }
        }
    }
    tagFor?.let { m ->
        QuickTagSheet(
            tags = tagsOf(m),
            allTags = allTags,
            onAdd = { onAddTag(m, it) },
            onRemove = { onRemoveTag(m, it) },
            onDismiss = { tagFor = null },
        )
    }
    infoFor?.let { shown ->
        // Re-read the live item so tag edits show immediately.
        val live = items.firstOrNull { it.id == shown.id } ?: shown
        InfoSheet(
            item = live,
            tags = tagsOf(live),
            albums = albumsOf(live),
            onAddTag = { onAddTag(live, it) },
            onRemoveTag = { onRemoveTag(live, it) },
            onDismiss = { infoFor = null },
        )
    }
}

@Composable
private fun MediaPage(
    item: MediaItem,
    thumbPx: Int,
    isCurrent: Boolean,
    settled: Boolean,
    dismissOffset: Float,
    dismissProgress: Float,
    chromeVisible: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissEnd: () -> Unit,
    onVideo: (VideoController, Boolean) -> Unit,
) {
    val zoom = remember(item.id) { ZoomState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(isCurrent) { if (!isCurrent) zoom.reset() }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .zoomable(zoom, scope, onTap, onLongPress, onDismissDrag, onDismissEnd),
        contentAlignment = Alignment.Center,
    ) {
        // Fit the photo to the screen ourselves, so the shared box has the photo's true shape.
        val boxRatio = maxWidth / maxHeight
        val (w, h) = if (item.aspect >= boxRatio) maxWidth to maxWidth / item.aspect else maxHeight * item.aspect to maxHeight
        val shrink = 1f - dismissProgress * 0.25f

        Box(
            Modifier
                .offset { IntOffset(0, dismissOffset.roundToInt()) }
                .size(w, h)
                .sharedMedia(item.id, enabled = isCurrent)
                .graphicsLayer {
                    scaleX = zoom.scale * shrink
                    scaleY = zoom.scale * shrink
                    translationX = zoom.offset.x
                    translationY = zoom.offset.y
                },
        ) {
            // Instant: the grid thumbnail is already in memory.
            AsyncImage(
                model = Thumb(item.uri, thumbPx, item.modifiedAt),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (item.isVideo) {
                if (isCurrent && settled) {
                    val video = rememberVideoController(item)
                    // Invisible until the first frame lands, so the thumbnail shows instead of black.
                    VideoSurface(video, Modifier.fillMaxSize().graphicsLayer { alpha = if (video.firstFrame) 1f else 0f })
                    DisposableEffect(video) {
                        onVideo(video, true)
                        onDispose { onVideo(video, false) }
                    }
                } else {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                            .padding(8.dp),
                    )
                }
            } else {
                // Screen-sized decode; a sharper one once the user zooms in.
                val context = LocalContext.current
                val density = LocalDensity.current
                val screenW = with(density) { w.toPx() }.roundToInt().coerceAtLeast(1)
                val screenH = with(density) { h.toPx() }.roundToInt().coerceAtLeast(1)
                var wantHiRes by remember(item.id) { mutableStateOf(false) }
                if (zoom.scale > 1.4f) wantHiRes = true
                val k = if (wantHiRes) 2.5f else 1f
                AsyncImage(
                    model = remember(item.id, k) {
                        ImageRequest.Builder(context)
                            .data(item.uri)
                            .size((screenW * k).roundToInt().coerceAtMost(4096), (screenH * k).roundToInt().coerceAtMost(4096))
                            .memoryCacheKeyExtra("v", item.modifiedAt.toString())
                            .build()
                    },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                MotionPhotoLayer(item, active = isCurrent && settled && !zoom.isZoomed, chromeVisible = chromeVisible)
            }
        }
    }
}

@Composable
private fun TopChrome(
    item: MediaItem?,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onSetWallpaper: () -> Unit,
    onUseAs: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
        if (item != null) {
            val zone = ZoneId.systemDefault()
            val at = Instant.ofEpochMilli(item.takenAt).atZone(zone)
            Column(Modifier.padding(start = 4.dp)) {
                Text(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(at), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at), color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onInfo) { Icon(Icons.Outlined.Info, "Details", tint = Color.White) }
        var menu by remember { mutableStateOf(false) }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More", tint = Color.White) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (item?.isVideo == false) {
                    DropdownMenuItem(
                        text = { Text("Set as wallpaper") },
                        leadingIcon = { Icon(Icons.Outlined.Wallpaper, null) },
                        onClick = { menu = false; onSetWallpaper() },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Use as…") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                    onClick = { menu = false; onUseAs() },
                )
            }
        }
    }
}

@Composable
private fun BottomChrome(
    isFavorite: Boolean,
    onShare: () -> Unit,
    onEdit: (() -> Unit)?,
    onFavorite: () -> Unit,
    onAddTo: () -> Unit,
    onTrash: () -> Unit,
    trashKey: Any?,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 8.dp),
    ) {
        IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share", tint = Color.White) }
        if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Tune, "Edit", tint = Color.White) }
        IconButton(onClick = onFavorite) {
            // Star pops with a bouncy scale when toggled.
            AnimatedContent(
                targetState = isFavorite,
                transitionSpec = {
                    (scaleIn(spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium), initialScale = 0.4f) + fadeIn()) togetherWith fadeOut()
                },
                label = "star",
            ) { fav ->
                Icon(
                    if (fav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    if (fav) "Unstar" else "Star",
                    tint = if (fav) Gold else Color.White,
                )
            }
        }
        IconButton(onClick = onAddTo) { Icon(Icons.Rounded.LibraryAdd, "Add to album or tag", tint = Color.White) }
        TwoTapTrashButton(onConfirm = onTrash, tint = Color.White, resetKey = trashKey)
    }
}

/** Hands the photo to another app as wallpaper / contact photo / etc. */
private fun useAs(context: android.content.Context, item: MediaItem) {
    val intent = android.content.Intent(android.content.Intent.ACTION_ATTACH_DATA)
        .setDataAndType(item.uri, item.mimeType.ifBlank { "image/*" })
        .putExtra("mimeType", item.mimeType)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(android.content.Intent.createChooser(intent, null)) }
}

/** Dark, immersive viewer: light status icons, and system bars hide along with our chrome. */
@Composable
private fun SystemBarsFor(chromeVisible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    DisposableEffect(controller) {
        val wasLight = controller.isAppearanceLightStatusBars
        val wasLightNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.isAppearanceLightStatusBars = wasLight
            controller.isAppearanceLightNavigationBars = wasLightNav
        }
    }
    LaunchedEffect(chromeVisible) {
        if (chromeVisible) controller.show(WindowInsetsCompat.Type.systemBars())
        else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
