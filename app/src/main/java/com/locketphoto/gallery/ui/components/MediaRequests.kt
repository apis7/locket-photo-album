package com.locketphoto.gallery.ui.components

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Runs a MediaStore change (trash, star, delete...) through Android's confirm flow.
 *
 * The first time, on Android 12+, it offers "Media management" access instead, which makes
 * every later change silent. If the user goes to Settings, the change runs when they return.
 */
@Stable
class MediaRequester internal constructor(private val context: Context) {
    internal var launch: (IntentSenderRequest) -> Unit = {}
    internal var resultCallback: ((Boolean) -> Unit)? = null
    internal var promptFor by mutableStateOf<(() -> Unit)?>(null)
    internal var runOnResume: (() -> Unit)? = null

    private val prefs get() = context.getSharedPreferences("locket", Context.MODE_PRIVATE)

    val canSkipConfirm: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && MediaStore.canManageMedia(context)

    fun run(build: () -> PendingIntent, onDone: (Boolean) -> Unit = {}) {
        val go = {
            resultCallback = onDone
            launch(IntentSenderRequest.Builder(build().intentSender).build())
        }
        val shouldOffer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !canSkipConfirm && !prefs.getBoolean(KEY_ASKED, false)
        if (shouldOffer) promptFor = go else go()
    }

    internal fun markAsked() = prefs.edit { putBoolean(KEY_ASKED, true) }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    internal fun openSettings() {
        val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, Uri.fromParts("package", context.packageName, null))
        context.startActivity(intent)
    }

    private companion object {
        const val KEY_ASKED = "asked_manage_media"
    }
}

val LocalMediaRequester = compositionLocalOf<MediaRequester> { error("No MediaRequester") }

@Composable
fun rememberMediaRequester(): MediaRequester {
    val context = LocalContext.current
    val requester = remember { MediaRequester(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        requester.resultCallback?.invoke(result.resultCode == Activity.RESULT_OK)
        requester.resultCallback = null
    }
    requester.launch = { launcher.launch(it) }

    LifecycleResumeEffect(requester) {
        requester.runOnResume?.let {
            requester.runOnResume = null
            it()
        }
        onPauseOrDispose { }
    }

    val pending = requester.promptFor
    if (pending != null) {
        AlertDialog(
            onDismissRequest = {
                requester.promptFor = null
                requester.markAsked()
                pending()
            },
            icon = { Icon(Icons.Rounded.AutoAwesome, null) },
            title = { Text("Skip the confirmations?") },
            text = {
                Text(
                    "Android normally asks before every trash or star. Turn on " +
                        "\"Media management\" for Locket Album and it won't ask again. " +
                        "You can change this any time in Settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    requester.promptFor = null
                    requester.markAsked()
                    requester.runOnResume = pending
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) requester.openSettings() else pending()
                }) { Text("Turn it on") }
            },
            dismissButton = {
                TextButton(onClick = {
                    requester.promptFor = null
                    requester.markAsked()
                    pending()
                }) { Text("Not now") }
            },
        )
    }
    return requester
}
