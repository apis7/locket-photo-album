package com.locketphoto.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.locketphoto.gallery.data.MediaRepository
import com.locketphoto.gallery.data.MediaWriter
import com.locketphoto.gallery.ui.components.LocalNavScope
import com.locketphoto.gallery.ui.components.LocalSharedScope
import com.locketphoto.gallery.ui.components.rememberMediaRequester
import com.locketphoto.gallery.ui.grid.GridActions
import com.locketphoto.gallery.ui.grid.GridScreen
import com.locketphoto.gallery.ui.grid.GridViewModel
import com.locketphoto.gallery.ui.grid.rememberThumbPx
import com.locketphoto.gallery.ui.trash.TrashScreen
import com.locketphoto.gallery.ui.trash.TrashViewModel
import com.locketphoto.gallery.ui.viewer.ViewerScreen
import kotlinx.coroutines.launch

/** "grid", "trash", or "viewer:<index>" — a plain string so it survives rotation/process death. */
private object Route {
    const val GRID = "grid"
    const val TRASH = "trash"
    fun viewer(index: Int) = "viewer:$index"
    fun viewerIndex(route: String) = route.removePrefix("viewer:").toIntOrNull()?.takeIf { route.startsWith("viewer:") }
}

@Composable
fun LocketRoot() {
    val app = LocalContext.current.applicationContext
    val context = LocalContext.current
    val repo = remember { MediaRepository(app.contentResolver) }
    val vm: GridViewModel = viewModel { GridViewModel(repo) }
    val trashVm: TrashViewModel = viewModel { TrashViewModel(repo) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val gridState = rememberLazyGridState()
    val thumbPx = rememberThumbPx()
    val requester = rememberMediaRequester()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var route by rememberSaveable { mutableStateOf(Route.GRID) }

    val resolver = app.contentResolver

    fun trash(items: List<MediaItem>, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        requester.run({ MediaWriter.trash(resolver, uris, true) }) { ok ->
            if (!ok) return@run
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            after()
            scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                val msg = if (items.size == 1) "Moved to trash" else "${items.size} items moved to trash"
                val res = snackbar.showSnackbar(msg, actionLabel = "Undo", duration = SnackbarDuration.Long)
                if (res == SnackbarResult.ActionPerformed) {
                    requester.run({ MediaWriter.trash(resolver, uris, false) })
                }
            }
        }
    }

    fun star(items: List<MediaItem>, after: () -> Unit = {}) {
        if (items.isEmpty()) return
        // Star all, unless every one is already starred, then unstar all.
        val value = !items.all { it.isFavorite }
        requester.run({ MediaWriter.favorite(resolver, items.map { it.uri }, value) }) { ok ->
            if (ok) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                after()
            }
        }
    }

    fun selectedItems(): List<MediaItem> {
        val chosen = vm.selection.value
        return vm.state.value.items.filter { it.id in chosen }
    }

    val actions = remember(vm) {
        GridActions(
            onOpen = { index -> route = Route.viewer(index) },
            onToggle = { id ->
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                vm.toggle(id)
            },
            onDragStart = { index ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                vm.startDragSelect(index)
            },
            onDragTo = { index -> vm.dragSelectTo(index) },
            onDragEnd = vm::endDragSelect,
            onClearSelection = vm::clearSelection,
            onSelectAll = vm::selectAll,
            onShare = { shareMedia(context, ArrayList(selectedItems().map { it.uri })) },
            onStarSelected = { star(selectedItems()) { vm.clearSelection() } },
            onTrashSelected = { trash(selectedItems()) { vm.clearSelection() } },
            onOpenTrash = { route = Route.TRASH },
        )
    }

    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this) {
                AnimatedContent(
                    targetState = route,
                    transitionSpec = {
                        if (targetState == Route.TRASH || initialState == Route.TRASH) {
                            val forward = targetState == Route.TRASH
                            (slideInHorizontally(tween(320)) { if (forward) it else -it / 3 } + fadeIn()) togetherWith
                                (slideOutHorizontally(tween(320)) { if (forward) -it / 3 else it } + fadeOut())
                        } else {
                            fadeIn(tween(260)) togetherWith fadeOut(tween(260))
                        }
                    },
                    label = "nav",
                ) { r ->
                    CompositionLocalProvider(LocalNavScope provides this) {
                        val viewerIndex = Route.viewerIndex(r)
                        when {
                            r == Route.TRASH -> {
                                val trashItems by trashVm.items.collectAsStateWithLifecycle()
                                val trashSel by trashVm.selection.collectAsStateWithLifecycle()
                                TrashScreen(
                                    items = trashItems,
                                    selection = trashSel,
                                    thumbPx = thumbPx,
                                    onBack = { route = Route.GRID },
                                    onToggle = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); trashVm.toggle(it) },
                                    onSelectAll = trashVm::selectAll,
                                    onClearSelection = trashVm::clear,
                                    onRestore = { list ->
                                        requester.run({ MediaWriter.trash(resolver, list.map { it.uri }, false) }) { ok ->
                                            if (ok) {
                                                trashVm.clear()
                                                scope.launch { snackbar.showSnackbar(if (list.size == 1) "Restored" else "${list.size} items restored") }
                                            }
                                        }
                                    },
                                    onDeleteForever = { list ->
                                        // Always confirmed by Android: this one can't be undone.
                                        requester.run({ MediaWriter.deleteForever(resolver, list.map { it.uri }) }) { ok ->
                                            if (ok) trashVm.clear()
                                        }
                                    },
                                )
                            }
                            viewerIndex != null -> ViewerScreen(
                                items = state.items,
                                startIndex = viewerIndex,
                                thumbPx = thumbPx,
                                onClose = { index ->
                                    // Make sure the photo we land on is on screen, so it can fly home.
                                    val entry = state.entryIndexOfItem.getOrNull(index)
                                    if (entry != null) {
                                        val visible = gridState.layoutInfo.visibleItemsInfo
                                        val first = visible.firstOrNull()?.index ?: 0
                                        val last = visible.lastOrNull()?.index ?: 0
                                        if (entry <= first || entry >= last) gridState.requestScrollToItem((entry - 8).coerceAtLeast(0))
                                    }
                                    route = Route.GRID
                                },
                                onShare = { item -> shareMedia(context, arrayListOf(item.uri)) },
                                onFavorite = { item -> star(listOf(item)) },
                                onTrash = { item -> trash(listOf(item)) },
                            )
                            else -> GridScreen(state = state, selection = selection, actions = actions, gridState = gridState)
                        }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 72.dp),
        )
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
