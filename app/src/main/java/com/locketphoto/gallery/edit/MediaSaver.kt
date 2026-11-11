package com.locketphoto.gallery.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.locketphoto.gallery.data.MediaItem
import java.io.File
import java.io.InputStream

/** Writes edited photos/videos back to MediaStore: overwrite in place, or a new copy beside it. */
object MediaSaver {

    /** Formats we can re-encode in place. Anything else (HEIC, DNG, GIF...) is copy-only. */
    fun canOverwritePhoto(mime: String) = mime in setOf("image/jpeg", "image/png", "image/webp")
    fun canOverwriteVideo(mime: String) = mime == "video/mp4"

    private data class Source(val displayName: String, val relativePath: String?)

    private fun source(context: Context, uri: Uri): Source {
        val proj = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH)
        context.contentResolver.query(uri, proj, null, null, null)?.use { c ->
            if (c.moveToFirst()) return Source(c.getString(0) ?: "edited", c.getString(1))
        }
        return Source("edited", null)
    }

    private fun editedName(original: String, ext: String): String {
        val base = original.substringBeforeLast('.').removeSuffix("_edited")
        return "${base}_edited.$ext"
    }

    // ---------------- photos ----------------

    private val exifTags = listOf(
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME, ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MAKE, ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH, ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_IMAGE_DESCRIPTION, ExifInterface.TAG_USER_COMMENT,
    )

    /** Reads the original's EXIF. Asks for the un-redacted file so GPS survives (needs ACCESS_MEDIA_LOCATION). */
    private fun readExif(context: Context, uri: Uri): Map<String, String> {
        fun read(stream: InputStream?): Map<String, String> = stream?.use { s ->
            val exif = ExifInterface(s)
            exifTags.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }.toMap()
        } ?: emptyMap()
        return try {
            read(context.contentResolver.openInputStream(MediaStore.setRequireOriginal(uri)))
        } catch (_: Exception) {
            runCatching { read(context.contentResolver.openInputStream(uri)) }.getOrDefault(emptyMap())
        }
    }

    private fun encodePhoto(context: Context, bmp: Bitmap, mime: String, exif: Map<String, String>): File {
        val (format, ext) = when (mime) {
            "image/png" -> Bitmap.CompressFormat.PNG to "png"
            "image/webp" -> Bitmap.CompressFormat.WEBP_LOSSY to "webp"
            else -> Bitmap.CompressFormat.JPEG to "jpg"
        }
        val file = File.createTempFile("locket_edit_", ".$ext", context.cacheDir)
        file.outputStream().use { bmp.compress(format, 95, it) }
        if (format == Bitmap.CompressFormat.JPEG || format == Bitmap.CompressFormat.WEBP_LOSSY) {
            runCatching {
                val out = ExifInterface(file)
                for ((k, v) in exif) out.setAttribute(k, v)
                // Pixels are already upright; the saved file must not be rotated again.
                out.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                out.setAttribute(ExifInterface.TAG_SOFTWARE, "Locket Album")
                out.saveAttributes()
            }
        }
        return file
    }

    /** Replaces the original's bytes. Caller must already hold write access (createWriteRequest). */
    fun overwritePhoto(context: Context, item: MediaItem, bmp: Bitmap) {
        val exif = readExif(context, item.uri)
        val file = encodePhoto(context, bmp, item.mimeType, exif)
        try {
            copyInto(context, file, item.uri)
        } finally {
            file.delete()
        }
    }

    /** A new JPEG next to the original (same folder, same capture time, so it sorts beside it). */
    fun savePhotoCopy(context: Context, item: MediaItem, bmp: Bitmap): Uri {
        val exif = readExif(context, item.uri)
        val file = encodePhoto(context, bmp, "image/jpeg", exif)
        try {
            return insertCopy(context, item, file, "image/jpeg", "jpg", MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Pictures/Locket/")
        } finally {
            file.delete()
        }
    }

    // ---------------- videos ----------------

    fun overwriteVideo(context: Context, item: MediaItem, exported: File) {
        copyInto(context, exported, item.uri)
        keepDateTaken(context, item.uri, item.takenAt)
    }

    fun saveVideoCopy(context: Context, item: MediaItem, exported: File): Uri =
        insertCopy(context, item, exported, "video/mp4", "mp4", MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), "Movies/Locket/")

    // ---------------- shared ----------------

    /** A re-encoded file may lack the original capture time; the rescan would then move it in the timeline. */
    private fun keepDateTaken(context: Context, uri: Uri, takenAt: Long) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, takenAt) }, null, null)
        }
    }

    private fun copyInto(context: Context, file: File, uri: Uri) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to $uri")
        out.use { o -> file.inputStream().use { it.copyTo(o, 256 * 1024) } }
    }

    private fun insertCopy(context: Context, item: MediaItem, file: File, mime: String, ext: String, collection: Uri, fallbackPath: String): Uri {
        val src = source(context, item.uri)
        val resolver = context.contentResolver
        fun values(path: String?) = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, editedName(src.displayName, ext))
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            if (path != null) put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            put(MediaStore.MediaColumns.DATE_TAKEN, item.takenAt)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        // Same folder when Android allows it there; otherwise our own folder.
        val uri = runCatching { resolver.insert(collection, values(src.relativePath)) }.getOrNull()
            ?: resolver.insert(collection, values(fallbackPath))
            ?: error("Couldn't create a new file")
        try {
            copyInto(context, file, uri)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            keepDateTaken(context, uri, item.takenAt)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }
}
