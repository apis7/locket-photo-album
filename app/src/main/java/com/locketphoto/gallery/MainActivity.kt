package com.locketphoto.gallery

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveable
import com.locketphoto.gallery.ui.components.LocalNavScope
import com.locketphoto.gallery.ui.components.LocalSharedScope
import com.locketphoto.gallery.ui.grid.GridActions
import com.locketphoto.gallery.ui.grid.rememberThumbPx
import com.locketphoto.gallery.ui.viewer.ViewerScreen
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locketphoto.gallery.data.MediaRepository
import com.locketphoto.gallery.ui.grid.GridScreen
import com.locketphoto.gallery.ui.grid.GridViewModel
import com.locketphoto.gallery.ui.theme.LocketTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            LocketTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    PermissionGate { LocketRoot() }
                }
            }
        }
    }
}

@Composable
private fun LocketRoot() {
    val context = LocalContext.current.applicationContext
    val vm: GridViewModel = viewModel { GridViewModel(MediaRepository(context.contentResolver)) }
    val state by vm.state.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val activity = LocalContext.current
    val gridState = rememberLazyGridState()
    var viewerStart by rememberSaveable { mutableStateOf<Int?>(null) }
    val thumbPx = rememberThumbPx()

    val actions = remember(vm) {
        GridActions(
            onOpen = { index -> viewerStart = index },
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
            onShare = {
                val chosen = vm.selection.value
                val uris = ArrayList(vm.state.value.items.filter { it.id in chosen }.map { it.uri })
                shareMedia(activity, uris)
            },
        )
    }

    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            AnimatedContent(
                targetState = viewerStart,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(260)) },
                label = "nav",
            ) { start ->
                CompositionLocalProvider(LocalNavScope provides this) {
                    if (start == null) {
                        GridScreen(state = state, selection = selection, actions = actions, gridState = gridState)
                    } else {
                        ViewerScreen(
                            items = state.items,
                            startIndex = start,
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
                                viewerStart = null
                            },
                            onShare = { item -> shareMedia(activity, arrayListOf(item.uri)) },
                        )
                    }
                }
            }
        }
    }
}

private fun shareMedia(context: Context, uris: ArrayList<Uri>) {
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

private val mediaPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

private fun hasMediaAccess(context: Context) = mediaPermissions.all {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasMediaAccess(context)) }
    var asks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasMediaAccess(context)
        asks++
    }
    // Re-check when returning from system Settings.
    LifecycleResumeEffect(Unit) {
        granted = hasMediaAccess(context)
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(36.dp),
    ) {
        Icon(Icons.Rounded.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(20.dp))
        Text("Your photos, nothing else", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Locket needs access to your photos and videos to show them. " +
                "It has no internet access, so nothing ever leaves your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        // After two refusals Android stops showing the dialog; send the user to Settings instead.
        if (asks < 2) {
            Button(onClick = { launcher.launch(mediaPermissions) }) { Text("Allow access") }
        } else {
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Open settings") }
        }
    }
}
