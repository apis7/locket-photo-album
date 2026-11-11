package com.locketphoto.gallery.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class Album(val id: Long, val name: String, val createdAt: Long, val mediaIds: List<Long>)

/** What the app itself remembers: albums and tags (MediaStore knows the photos). */
data class Library(
    val albums: List<Album> = emptyList(),
    /** media id -> its tags */
    val tags: Map<Long, Set<String>> = emptyMap(),
) {
    val allTags: List<String> by lazy { tags.values.flatten().groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
}

/**
 * Tiny SQLite store. Everything is local to the phone; the app has no network access.
 * Reads are small, so the whole library is reloaded after each change.
 */
class LibraryStore private constructor(context: Context) {

    private val db = Helper(context.applicationContext).writableDatabase
    private val version = MutableStateFlow(0)

    val library: Flow<Library> = version.map { load() }.flowOn(Dispatchers.IO)

    private fun changed() { version.value++ }

    private fun load(): Library {
        val members = HashMap<Long, MutableList<Long>>()
        db.rawQuery("SELECT album_id, media_id FROM album_items ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) members.getOrPut(c.getLong(0)) { mutableListOf() } += c.getLong(1)
        }
        val albums = ArrayList<Album>()
        db.rawQuery("SELECT id, name, created_at FROM albums ORDER BY created_at DESC", null).use { c ->
            while (c.moveToNext()) albums += Album(c.getLong(0), c.getString(1), c.getLong(2), members[c.getLong(0)].orEmpty())
        }
        val tags = HashMap<Long, MutableSet<String>>()
        db.rawQuery("SELECT media_id, tag FROM tags", null).use { c ->
            while (c.moveToNext()) tags.getOrPut(c.getLong(0)) { linkedSetOf() } += c.getString(1)
        }
        return Library(albums, tags)
    }

    suspend fun createAlbum(name: String, mediaIds: Collection<Long> = emptyList()): Long = io {
        val now = System.currentTimeMillis()
        val id = db.insert("albums", null, ContentValues().apply { put("name", name.trim()); put("created_at", now) })
        addToAlbumNow(id, mediaIds)
        id
    }

    suspend fun renameAlbum(id: Long, name: String) = io {
        db.update("albums", ContentValues().apply { put("name", name.trim()) }, "id=?", arrayOf(id.toString()))
    }

    /** Deletes the album only; the photos stay in the library. */
    suspend fun deleteAlbum(id: Long) = io {
        db.delete("album_items", "album_id=?", arrayOf(id.toString()))
        db.delete("albums", "id=?", arrayOf(id.toString()))
    }

    suspend fun addToAlbum(id: Long, mediaIds: Collection<Long>) = io { addToAlbumNow(id, mediaIds) }

    private fun addToAlbumNow(id: Long, mediaIds: Collection<Long>) {
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            mediaIds.forEach { m ->
                db.insertWithOnConflict(
                    "album_items", null,
                    ContentValues().apply { put("album_id", id); put("media_id", m); put("added_at", now) },
                    SQLiteDatabase.CONFLICT_IGNORE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun removeFromAlbum(id: Long, mediaIds: Collection<Long>) = io {
        mediaIds.forEach { db.delete("album_items", "album_id=? AND media_id=?", arrayOf(id.toString(), it.toString())) }
    }

    suspend fun addTag(mediaIds: Collection<Long>, tag: String) = io {
        val t = tag.trim().lowercase()
        if (t.isNotEmpty()) mediaIds.forEach {
            db.insertWithOnConflict("tags", null, ContentValues().apply { put("media_id", it); put("tag", t) }, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    suspend fun removeTag(mediaIds: Collection<Long>, tag: String) = io {
        mediaIds.forEach { db.delete("tags", "media_id=? AND tag=?", arrayOf(it.toString(), tag)) }
    }

    // ---- Similar-photo fingerprints (cached so stacking is computed once per photo) ----

    fun cachedHashes(): Map<Long, Pair<Long, Long>> {
        val out = HashMap<Long, Pair<Long, Long>>()
        db.rawQuery("SELECT media_id, modified, hash FROM hashes", null).use { c ->
            while (c.moveToNext()) out[c.getLong(0)] = c.getLong(1) to c.getLong(2)
        }
        return out
    }

    fun saveHashes(rows: List<Triple<Long, Long, Long>>) {
        db.beginTransaction()
        try {
            rows.forEach { (id, modified, hash) ->
                db.insertWithOnConflict(
                    "hashes", null,
                    ContentValues().apply { put("media_id", id); put("modified", modified); put("hash", hash) },
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block().also { changed() } }

    private class Helper(context: Context) : SQLiteOpenHelper(context, "locket.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE albums (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, created_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE album_items (album_id INTEGER NOT NULL, media_id INTEGER NOT NULL, added_at INTEGER NOT NULL, PRIMARY KEY (album_id, media_id))")
            db.execSQL("CREATE TABLE tags (media_id INTEGER NOT NULL, tag TEXT NOT NULL, PRIMARY KEY (media_id, tag))")
            db.execSQL("CREATE INDEX tags_by_tag ON tags(tag)")
            db.execSQL("CREATE TABLE hashes (media_id INTEGER PRIMARY KEY, modified INTEGER NOT NULL, hash INTEGER NOT NULL)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    companion object {
        @Volatile private var instance: LibraryStore? = null
        fun get(context: Context): LibraryStore =
            instance ?: synchronized(this) { instance ?: LibraryStore(context).also { instance = it } }
    }
}
