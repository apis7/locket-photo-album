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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.locketphoto.gallery.ui.theme.LocketTheme
import com.locketphoto.gallery.ui.LocketRoot
import com.locketphoto.gallery.ui.viewer.ExternalViewer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // "Open with Locket" from another app (camera, files, messages…).
        val openUri = intent.takeIf { it.action == Intent.ACTION_VIEW || it.action == "com.android.camera.action.REVIEW" }?.data
        val openType = intent.type
        setContent {
            LocketTheme {
                // Surface also sets the default text/icon color for light and dark mode.
                androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (openUri == null) {
                        PermissionGate { LocketRoot() }
                    } else {
                        // Another app granted us this one file, so show it even without library access.
                        val thumbPx = com.locketphoto.gallery.ui.grid.rememberThumbPx()
                        PermissionGate(
                            denied = {
                                ExternalViewer(openUri, openType, thumbPx, onClose = ::finish, onShare = {
                                    com.locketphoto.gallery.ui.shareMedia(this, arrayListOf(it.uri))
                                })
                            },
                        ) { LocketRoot(openUri, openType, onExit = ::finish) }
                    }
                }
            }
        }
    }
}

private val mediaPermissions: Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        Manifest.permission.ACCESS_MEDIA_LOCATION,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.ACCESS_MEDIA_LOCATION)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
}

private fun has(context: Context, p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

private enum class Access { None, Partial, Full }

/** Location is optional (Details only); access to photos is what gates the app. */
private fun mediaAccess(context: Context): Access = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        has(context, Manifest.permission.READ_MEDIA_IMAGES) && has(context, Manifest.permission.READ_MEDIA_VIDEO) -> Access.Full
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && has(context, Manifest.permission.READ_EXTERNAL_STORAGE) -> Access.Full
    // Android 14+: the user chose "Select photos" â€” show what they picked.
    Build.VERSION.SDK_INT >= 34 && has(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.Partial
    else -> Access.None
}

/** Shown when only some photos were shared with the app (Android 14+). */
@Composable
private fun PartialAccessBanner(onManage: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
        shadowElevation = 6.dp,
        modifier = modifier
            .windowInsetsPadding(androidx.compose.foundation.layout.WindowInsets.statusBars)
            .padding(top = 64.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
            Text("Showing only the photos you picked", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.material3.TextButton(onClick = onManage) { Text("Allow all") }
        }
    }
}

@Composable
private fun PermissionGate(
    /** Shown instead of the "Allow access" screen when access is missing. */
    denied: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var access by remember { mutableStateOf(mediaAccess(context)) }
    var asks by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = mediaAccess(context)
        asks++
    }
    // Re-check when returning from system Settings (or after changing the selection).
    LifecycleResumeEffect(Unit) {
        access = mediaAccess(context)
        onPauseOrDispose { }
    }

    if (access != Access.None) {
        Box(Modifier.fillMaxSize()) {
            content()
            if (access == Access.Partial) {
                PartialAccessBanner(
                    onManage = { launcher.launch(mediaPermissions) },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
        return
    }
    if (denied != null) {
        denied()
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
