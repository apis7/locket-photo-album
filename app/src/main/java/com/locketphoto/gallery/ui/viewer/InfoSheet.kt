package com.locketphoto.gallery.ui.viewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.locketphoto.gallery.data.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private data class Details(
    val name: String? = null,
    val sizeBytes: Long = 0,
    val folder: String? = null,
    val camera: String? = null,
    val exposure: String? = null,
    val lat: Double? = null,
    val lon: Double? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var details by remember(item.id) { mutableStateOf<Details?>(null) }
    LaunchedEffect(item.id) { details = withContext(Dispatchers.IO) { loadDetails(context, item) } }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp)) {
            val at = Instant.ofEpochMilli(item.takenAt).atZone(ZoneId.systemDefault())
            Text("Details", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            InfoRow(
                Icons.Outlined.Schedule,
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(at),
                DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(at),
            )
            val d = details
            InfoRow(
                Icons.Outlined.Image,
                d?.name ?: "…",
                listOfNotNull(
                    if (item.displayWidth > 0) "${item.displayWidth} × ${item.displayHeight}" else null,
                    if (item.displayWidth > 0 && !item.isVideo) "%.1f MP".format(Locale.US, item.displayWidth * item.displayHeight / 1_000_000f) else null,
                    d?.sizeBytes?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                    item.mimeType.substringAfter('/').uppercase().takeIf { it.isNotBlank() },
                ).joinToString("  ·  "),
            )
            d?.camera?.let { InfoRow(Icons.Outlined.CameraAlt, it, d.exposure) }
            d?.folder?.let { InfoRow(Icons.Outlined.Folder, it.trimEnd('/'), null) }
            if (d?.lat != null && d.lon != null) {
                InfoRow(
                    Icons.Outlined.Place,
                    "%.5f, %.5f".format(Locale.US, d.lat, d.lon),
                    "Open in maps",
                    onClick = {
                        val geo = Uri.parse("geo:${d.lat},${d.lon}?q=${d.lat},${d.lon}")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, geo)) }
                    },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun loadDetails(context: Context, item: MediaItem): Details {
    var d = Details()
    val cols = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.RELATIVE_PATH)
    runCatching {
        context.contentResolver.query(item.uri, cols, null, null, null)?.use { c ->
            if (c.moveToFirst()) d = d.copy(name = c.getString(0), sizeBytes = c.getLong(1), folder = c.getString(2))
        }
    }
    if (item.isVideo) return d

    // Location is stripped from the stream unless we hold ACCESS_MEDIA_LOCATION and ask for the original.
    val canLocate = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
    val uri = if (canLocate) MediaStore.setRequireOriginal(item.uri) else item.uri
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim()
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()
            val camera = when {
                model == null -> make
                make == null || model.startsWith(make, ignoreCase = true) -> model
                else -> "$make $model"
            }
            val parts = buildList {
                exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }?.let { add("ƒ/%.1f".format(Locale.US, it)) }
                exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }?.let {
                    add(if (it >= 1) "%.1fs".format(Locale.US, it) else "1/${(1 / it).toInt()}")
                }
                exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.let { add("%.1fmm".format(Locale.US, it)) }
                exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }?.let { add("ISO $it") }
            }
            val ll = exif.latLong
            d = d.copy(
                camera = camera,
                exposure = parts.joinToString("  ·  ").ifBlank { null },
                lat = ll?.get(0),
                lon = ll?.get(1),
            )
        }
    }
    return d
}
