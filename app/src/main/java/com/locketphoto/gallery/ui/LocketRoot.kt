package com.locketphoto.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.BurstMode
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MediaWriter
import com.locketphoto.gallery.ui.albums.AddToSheet
import com.locketphoto.gallery.ui.albums.AlbumsScreen
import com.locketphoto.gallery.ui.albums.NameDialog
import com.locketphoto.gallery.ui.components.BottomNav
import com.locketphoto.gallery.ui.components.BottomNavHeight
import com.locketphoto.gallery.ui.components.LocalNavScope
import com.locketphoto.gallery.ui.components.LocalSharedScope
import com.locketphoto.gallery.ui.components.MediaRequester
import com.locketphoto.gallery.ui.components.Tab
import com.locketphoto.gallery.ui.components.rememberMediaRequester
import com.locketphoto.gallery.ui.editor.EditorScreen
import com.locketphoto.gallery.ui.grid.GridActions
import com.locketphoto.gallery.ui.grid.GridScreen
import com.locketphoto.gallery.ui.grid.GridUiState
import com.locketphoto.gallery.ui.grid.buildGridState
import com.locketphoto.gallery.ui.grid.rememberThumbPx
import com.locketphoto.gallery.ui.search.SearchHeader
import com.locketphoto.gallery.ui.search.localEndOf
import com.locketphoto.gallery.ui.trash.TrashScreen
import com.locketphoto.gallery.ui.trash.TrashViewModel
import com.locketphoto.gallery.ui.viewer.ViewerScreen
import com.locketphoto.gallery.ui.wallpaper.WallpaperScreen
import kotlinx.coroutines.launch

/**
 * Routes are plain strings so they survive rotation and process death:
 * tabs "photos" / "albums" / "search", "grid|<source>", "viewer|<source>|<i>",
 * "wallpaper|<source>|<i>", "trash".
 */
private object R {
    const val PHOTOS = "photos"
    const val ALBUMS = "albums"
    const val SEARCH = "search"
    const val TRASH = "trash"
    fun grid(s: Source) = "grid|${s.encode()}"
    fun viewer(s: Source, i: Int) = "viewer|${s.encode()}|$i"
    fun wallpaper(s: Source, i: Int) = "wallpaper|${s.encode()}|$i"
    fun edit(s: Source, i: Int) = "edit|${s.encode()}|$i"
    fun depth(r: String) = when {
        r == PHOTOS || r == ALBUMS || r == SEARCH -> 0
        r.startsWith("grid|") || r == TRASH -> 1
        else -> 2
    }
    fun tab(r: String) = when (r) {
        ALBUMS -> Tab.Albums
        SEARCH -> Tab.Search
        else -> Tab.Photos
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocketRoot() {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel()
    val trashVm: TrashViewModel = viewModel { TrashViewModel(vm.repo) }
    val all by vm.items.collectAsStateWithLifecycle()
    val library by vm.library.collectAsStateWithLifecycle()
    val photosGrid by vm.photosGrid.collectAsStateWithLifecycle()
    val stacks by vm.stacks.collectAsStateWithLifecycle()
    val stackingOn by vm.stackingEnabled.collectAsStateWithLifecycle()
    val stackProgress by vm.stackProgress.collectAsStateWithLifecycle()
    val selection by vm.selection.ids.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val range by vm.dateRange.collectAsStateWithLifecycle()
    val searchResults by vm.searchResults.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()

    val haptics = LocalHapticFeedback.current
    val thumbPx = rememberThumbPx()
    val requester = rememberMediaRequester()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver

    var route by rememberSaveable { mutableStateOf(R.PHOTOS) }
    var lastTab by rememberSaveable { mutableStateOf(R.PHOTOS) }
    var addToIds by remember { mutableStateOf<List<Long>?>(null) }
    var jumping by remember { mutableStateOf(false) }
    val photosGridState = rememberLazyGridState()
    val gridStates = remember { HashMap<String, LazyGridState>() }

    val list = all.orEmpty()
    fun itemsFor(s: Source) = vm.itemsFor(s, list, library, searchResults)
    fun stateFor(s: Source): GridUiState = when (s) {
        Source.All -> photosGrid
        else -> if (all == null) GridUiState() else buildGridState(itemsFor(s))
    }
    fun gridStateFor(s: Source): LazyGridState =
        if (s == Source.All) photosGridState else gridStates.getOrPut(s.encode()) { LazyGridState() }

    fun go(to: String) {
        vm.selection.clear()
        if (R.depth(to) == 0) lastTab = to
        route = to
    }

    fun toast(msg: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(msg)
    }

    val ops = remember(requester) { MediaOps(requester, resolver, haptics::performHapticFeedback, snackbar, scope) }

    fun selectedItems(): List<MediaItem> {
        val ids = vm.selection.ids.value
        return list.filter { it.id in ids }
    }

    fun actionsFor(source: Source, items: List<MediaItem>): GridActions = GridActions(
        onOpen = { i -> go(R.viewer(source, i)) },
        onToggle = { ids ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            vm.selection.toggle(ids)
        },
        onDragStart = { i ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            vm.selection.startDrag(i, items, if (source == Source.All) stacks else emptyMap())
        },
        onDragTo = vm.selection::dragTo,
        onDragEnd = vm.selection::endDrag,
        onClearSelection = vm.selection::clear,
        onSelectAll = { vm.selection.set(items.map { it.id }) },
        onShare = { shareMedia(context, ArrayList(selectedItems().map { it.uri })) },
        onStar = { ops.star(selectedItems()) { vm.selection.clear() } },
        onAddTo = { addToIds = vm.selection.ids.value.toList() },
        onTrash = { ops.trash(selectedItems()) { vm.selection.clear() } },
    )

    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        val from = R.depth(initialState)
                        val to = R.depth(targetState)
                        when {
                            targetState.startsWith("viewer|") || initialState.startsWith("viewer|") ->
                                fadeIn(tween(260)) togetherWith fadeOut(tween(260))
                            from == to -> fadeIn(tween(200)) togetherWith fadeOut(tween(160))
                            else -> {
                                val fwd = to > from
                                (slideInHorizontally(tween(320)) { if (fwd) it else -it / 3 } + fadeIn()) togetherWith
                                    (slideOutHorizontally(tween(320)) { if (fwd) -it / 3 else it } + fadeOut())
                            }
                        }
                    },
                    label = "nav",
                ) { r ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        val parts = r.split('|')
                        when {
                            r == R.PHOTOS -> GridScreen(
                                state = photosGrid,
                                selection = selection,
                                actions = actionsFor(Source.All, list),
                                title = "Photo Album",
                                large = true,
                                gridState = photosGridState,
                                subtitle = stackProgress?.let { "Finding similar photos… ${(it * 100).toInt()}%" }
                                    ?: list.size.takeIf { it > 0 }?.toString(),
                                extraBottom = BottomNavHeight,
                                topActions = {
                                    IconButton(onClick = { jumping = true }) { Icon(Icons.Rounded.CalendarMonth, "Jump to date") }
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            DropdownMenuItem(
                                                text = { Text("Stack similar photos") },
                                                leadingIcon = { Icon(Icons.Rounded.BurstMode, null) },
                                                trailingIcon = { if (stackingOn) Icon(Icons.Rounded.Check, null) },
                                                onClick = { menu = false; vm.setStacking(!stackingOn) },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Trash") },
                                                leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                                onClick = { menu = false; go(R.TRASH) },
                                            )
                                        }
                                    }
                                },
                            )

                            r == R.ALBUMS -> {
                                BackHandler { go(R.PHOTOS) }
                                AlbumsScreen(
                                    albums = library.albums,
                                    folders = folders,
                                    itemsById = remember(list) { list.associateBy { it.id } },
                                    favorites = remember(list) { list.filter { it.isFavorite } },
                                    videos = remember(list) { list.filter { it.isVideo } },
                                    thumbPx = thumbPx,
                                    extraBottom = BottomNavHeight,
                                    onOpenFavorites = { go(R.grid(Source.Favorites)) },
                                    onOpenVideos = { go(R.grid(Source.Videos)) },
                                    onOpenAlbum = { go(R.grid(Source.Album(it))) },
                                    onOpenFolder = { go(R.grid(Source.Folder(it))) },
                                    onCreateAlbum = { name -> vm.createAlbum(name, emptyList()) { go(R.grid(Source.Album(it))) } },
                                )
                            }

                            r == R.SEARCH -> {
                                BackHandler { go(R.PHOTOS) }
                                val results = searchResults
                                GridScreen(
                                    state = remember(results) { buildGridState(results) },
                                    selection = selection,
                                    actions = actionsFor(Source.Search, results),
                                    title = "Search",
                                    large = true,
                                    gridState = gridStateFor(Source.Search),
                                    extraBottom = BottomNavHeight,
                                    emptyText = if (query.isBlank() && range == null) "Find photos by name, folder, tag, album, month or year" else "Nothing found",
                                    header = {
                                        SearchHeader(
                                            query = query,
                                            onQuery = { vm.query.value = it },
                                            range = range,
                                            onRange = { vm.dateRange.value = it },
                                            topTags = library.allTags,
                                            resultCount = results.size,
                                        )
                                    },
                                )
                            }

                            r == R.TRASH -> {
                                val trashItems by trashVm.items.collectAsStateWithLifecycle()
                                val trashSel by trashVm.selection.collectAsStateWithLifecycle()
                                TrashScreen(
                                    items = trashItems,
                                    selection = trashSel,
                                    thumbPx = thumbPx,
                                    onBack = { go(R.PHOTOS) },
                                    onToggle = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); trashVm.toggle(it) },
                                    onSelectAll = trashVm::selectAll,
                                    onClearSelection = trashVm::clear,
                                    onRestore = { l ->
                                        requester.run({ MediaWriter.trash(resolver, l.map { it.uri }, false) }) { ok ->
                                            if (ok) {
                                                trashVm.clear()
                                                toast(if (l.size == 1) "Restored" else "${l.size} items restored")
                                            }
                                        }
                                    },
                                    onDeleteForever = { l ->
                                        requester.run({ MediaWriter.deleteForever(resolver, l.map { it.uri }) }) { ok -> if (ok) trashVm.clear() }
                                    },
                                )
                            }

                            parts[0] == "grid" -> {
                                val source = Source.decode(parts.getOrElse(1) { "all" })
                                val items = itemsFor(source)
                                val album = (source as? Source.Album)?.let { a -> library.albums.firstOrNull { it.id == a.id } }
                                var renaming by remember { mutableStateOf(false) }
                                var confirmDelete by remember { mutableStateOf(false) }
                                GridScreen(
                                    state = remember(items) { buildGridState(items) },
                                    selection = selection,
                                    actions = actionsFor(source, items),
                                    title = when (source) {
                                        Source.Favorites -> "Favorites"
                                        Source.Videos -> "Videos"
                                        is Source.Album -> album?.name ?: "Album"
                                        is Source.Folder -> source.name
                                        is Source.Tag -> "#${source.tag}"
                                        else -> "Photos"
                                    },
                                    subtitle = if (items.size == 1) "1 item" else "${items.size} items",
                                    gridState = gridStateFor(source),
                                    onBack = { go(if (source is Source.Tag) R.SEARCH else R.ALBUMS) },
                                    emptyText = when (source) {
                                        is Source.Album -> "Empty album. Select photos and tap Add to."
                                        Source.Favorites -> "Star photos to see them here"
                                        else -> "Nothing here"
                                    },
                                    topActions = {
                                        if (album != null) {
                                            var menu by remember { mutableStateOf(false) }
                                            Box {
                                                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                                    DropdownMenuItem(
                                                        text = { Text("Rename album") },
                                                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                                                        onClick = { menu = false; renaming = true },
                                                    )
                                                    DropdownMenuItem(
                                                        text = { Text("Delete album") },
                                                        leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                                                        onClick = { menu = false; confirmDelete = true },
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    selectionMenu = if (album == null) null else { close ->
                                        DropdownMenuItem(
                                            text = { Text("Remove from album") },
                                            leadingIcon = { Icon(Icons.Rounded.RemoveCircleOutline, null) },
                                            onClick = {
                                                close()
                                                vm.removeFromAlbum(album.id, vm.selection.ids.value)
                                                vm.selection.clear()
                                            },
                                        )
                                    },
                                )
                                if (renaming && album != null) {
                                    NameDialog("Rename album", "Rename", album.name, onDismiss = { renaming = false }) {
                                        renaming = false
                                        vm.renameAlbum(album.id, it)
                                    }
                                }
                                if (confirmDelete && album != null) {
                                    AlertDialog(
                                        onDismissRequest = { confirmDelete = false },
                                        title = { Text("Delete \"${album.name}\"?") },
                                        text = { Text("The album goes away. The photos stay in your library.") },
                                        confirmButton = {
                                            TextButton(onClick = {
                                                confirmDelete = false
                                                vm.deleteAlbum(album.id)
                                                go(R.ALBUMS)
                                            }) { Text("Delete album") }
                                        },
                                        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
                                    )
                                }
                            }

                            parts[0] == "edit" -> {
                                val source = Source.decode(parts.getOrElse(1) { "all" })
                                val index = parts.getOrNull(2)?.toIntOrNull() ?: 0
                                // Pin the item when the editor opens: saving can reorder the list.
                                val item = remember(r) { itemsFor(source).getOrNull(index) }
                                if (item == null) {
                                    go(lastTab)
                                } else {
                                    EditorScreen(
                                        item = item,
                                        requestWrite = { uri, granted ->
                                            requester.run({ MediaWriter.write(resolver, listOf(uri)) }) { ok -> if (ok) granted() }
                                        },
                                        onDone = { saved ->
                                            // Land on the saved file (a new copy, or the overwritten original).
                                            val latest = itemsFor(source)
                                            val at = saved?.let { u -> latest.indexOfFirst { it.uri == u } }?.takeIf { it >= 0 } ?: index
                                            route = R.viewer(source, at)
                                            if (saved != null) toast("Saved")
                                        },
                                        onCancel = { route = R.viewer(source, index) },
                                    )
                                }
                            }

                            parts[0] == "wallpaper" -> {
                                val source = Source.decode(parts.getOrElse(1) { "all" })
                                val item = itemsFor(source).getOrNull(parts.getOrNull(2)?.toIntOrNull() ?: 0)
                                if (item == null) {
                                    go(lastTab)
                                } else {
                                    WallpaperScreen(item) { msg ->
                                        go(R.viewer(source, parts.getOrNull(2)?.toIntOrNull() ?: 0))
                                        msg?.let { toast(it) }
                                    }
                                }
                            }

                            parts[0] == "viewer" -> {
                                val source = Source.decode(parts.getOrElse(1) { "all" })
                                val items = itemsFor(source)
                                val start = parts.getOrNull(2)?.toIntOrNull() ?: 0
                                val back = if (source == Source.All) R.PHOTOS else if (source == Source.Search) R.SEARCH else R.grid(source)
                                val albumNames = remember(library) {
                                    val m = HashMap<Long, MutableList<String>>()
                                    library.albums.forEach { a -> a.mediaIds.forEach { m.getOrPut(it) { mutableListOf() } += a.name } }
                                    m
                                }
                                ViewerScreen(
                                    items = items,
                                    startIndex = start,
                                    thumbPx = thumbPx,
                                    onClose = { index ->
                                        // Make sure the photo we land on is on screen, so it can fly home.
                                        val gs = gridStateFor(source)
                                        val st = stateFor(source)
                                        val entry = st.entryIndexOfItem.getOrNull(index)?.takeIf { it >= 0 }
                                        if (entry != null) {
                                            val visible = gs.layoutInfo.visibleItemsInfo
                                            val first = visible.firstOrNull()?.index ?: 0
                                            val last = visible.lastOrNull()?.index ?: 0
                                            if (entry <= first || entry >= last) gs.requestScrollToItem((entry - 8).coerceAtLeast(0))
                                        }
                                        route = back
                                    },
                                    onShare = { shareMedia(context, arrayListOf(it.uri)) },
                                    onFavorite = { ops.star(listOf(it)) },
                                    onTrash = { ops.trash(listOf(it)) },
                                    tagsOf = { library.tags[it.id].orEmpty() },
                                    albumsOf = { albumNames[it.id].orEmpty() },
                                    onAddTag = { m, t -> vm.addTag(listOf(m.id), t) },
                                    onRemoveTag = { m, t -> vm.removeTag(listOf(m.id), t) },
                                    onAddTo = { addToIds = listOf(it.id) },
                                    onSetWallpaper = { m -> go(R.wallpaper(source, items.indexOf(m).coerceAtLeast(0))) },
                                    onEdit = { m -> go(R.edit(source, items.indexOf(m).coerceAtLeast(0))) },
                                )
                            }
                        }
                    }
                }
            }
        }

        val showNav = R.depth(route) == 0 && selection.isEmpty()
        AnimatedVisibility(
            visible = showNav,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            BottomNav(current = R.tab(route), onSelect = { tab ->
                val to = when (tab) {
                    Tab.Photos -> R.PHOTOS
                    Tab.Albums -> R.ALBUMS
                    Tab.Search -> R.SEARCH
                }
                if (to == route && to == R.PHOTOS) scope.launch { photosGridState.animateScrollToItem(0) } else go(to)
            })
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (showNav) BottomNavHeight + 16.dp else 72.dp),
        )
    }

    addToIds?.let { ids ->
        val tagsOnAll = remember(ids, library) {
            ids.map { library.tags[it].orEmpty() }.reduceOrNull { a, b -> a intersect b }.orEmpty()
        }
        AddToSheet(
            count = ids.size,
            albums = library.albums,
            allTags = (library.allTags + tagsOnAll).distinct(),
            tagsOnAll = tagsOnAll,
            onDismiss = { addToIds = null },
            onAddToAlbum = { albumId ->
                vm.addToAlbum(albumId, ids)
                addToIds = null
                vm.selection.clear()
                toast("Added to ${library.albums.firstOrNull { it.id == albumId }?.name ?: "album"}")
            },
            onNewAlbum = { name ->
                vm.createAlbum(name, ids)
                addToIds = null
                vm.selection.clear()
                toast("Added to $name")
            },
            onAddTag = { vm.addTag(ids, it) },
            onRemoveTag = { vm.removeTag(ids, it) },
        )
    }

    if (jumping) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { jumping = false },
            confirmButton = {
                TextButton(enabled = state.selectedDateMillis != null, onClick = {
                    jumping = false
                    val end = localEndOf(state.selectedDateMillis!!)
                    // Newest-first list: first photo taken on or before that day.
                    val idx = list.indexOfFirst { it.takenAt <= end }.takeIf { it >= 0 } ?: list.lastIndex
                    val entry = photosGrid.entryIndexOfItem.getOrNull(idx)?.takeIf { it >= 0 } ?: 0
                    scope.launch { photosGridState.animateScrollToItem((entry - 1).coerceAtLeast(0)) } // animated so the date bubble shows where you landed
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { jumping = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

/** Trash / star with Android's confirm flow, haptics and an Undo snackbar. */
private class MediaOps(
    private val requester: MediaRequester,
    private val resolver: android.content.ContentResolver,
    private val haptic: (HapticFeedbackType) -> Unit,
    private val snackbar: SnackbarHostState,
    private val scope: kotlinx.coroutines.CoroutineScope,
) {
    fun trash(items: List<MediaItem>, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        requester.run({ MediaWriter.trash(resolver, uris, true) }) { ok ->
            if (!ok) return@run
            haptic(HapticFeedbackType.Confirm)
            after()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val msg = if (items.size == 1) "Moved to trash" else "${items.size} items moved to trash"
                val res = snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) requester.run({ MediaWriter.trash(resolver, uris, false) })
            }
        }
    }

    fun star(items: List<MediaItem>, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        // Star all, unless every one is already starred, then unstar all.
        val value = !items.all { it.isFavorite }
        requester.run({ MediaWriter.favorite(resolver, items.map { it.uri }, value) }) { ok ->
            if (ok) {
                haptic(HapticFeedbackType.Confirm)
                after()
            }
        }
    }
}

fun shareMedia(context: Context, uris: ArrayList<Uri>) {
    if (uris.isEmpty()) return
    val send = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    send.type = context.contentResolver.getType(uris[0])?.takeIf { uris.size == 1 } ?: "*/*"
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, null))
}
