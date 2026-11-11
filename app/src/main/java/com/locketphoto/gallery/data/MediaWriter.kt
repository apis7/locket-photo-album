package com.locketphoto.gallery.data

import android.app.PendingIntent
import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore

/**
 * All changes to the user's library go through Android's own request API.
 * Android asks the user to confirm each one, unless they've granted this app
 * "Media management", in which case it goes through silently.
 */
object MediaWriter {
    fun favorite(resolver: ContentResolver, uris: List<Uri>, value: Boolean): PendingIntent =
        MediaStore.createFavoriteRequest(resolver, uris, value)

    /** Trashed items are kept by the system for 30 days, then deleted automatically. */
    fun trash(resolver: ContentResolver, uris: List<Uri>, value: Boolean): PendingIntent =
        MediaStore.createTrashRequest(resolver, uris, value)

    /** Permission to overwrite files (used by the editor's "Save"). */
    fun write(resolver: ContentResolver, uris: List<Uri>): PendingIntent =
        MediaStore.createWriteRequest(resolver, uris)

    fun deleteForever(resolver: ContentResolver, uris: List<Uri>): PendingIntent =
        MediaStore.createDeleteRequest(resolver, uris)
}
