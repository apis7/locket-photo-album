package com.locketphoto.gallery.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.locketphoto.gallery.data.Library
import com.locketphoto.gallery.data.LibraryStore
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MediaRepository
import com.locketphoto.gallery.data.Stacks
import com.locketphoto.gallery.ui.grid.GridSelection
import com.locketphoto.gallery.ui.grid.GridUiState
import com.locketphoto.gallery.ui.grid.buildGridState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A list the viewer can page through, and the grid that shows it. */
sealed interface Source {
    data object All : Source
    data object Favorites : Source
    data object Videos : Source
    data object Search : Source
    data class Album(val id: Long) : Source
    data class Folder(val name: String) : Source
    data class Tag(val tag: String) : Source

    fun encode(): String = when (this) {
        All -> "all"
        Favorites -> "fav"
        Videos -> "videos"
        Search -> "search"
        is Album -> "album:$id"
        is Folder -> "folder:$name"
        is Tag -> "tag:$tag"
    }

    companion object {
        fun decode(s: String): Source = when {
            s == "fav" -> Favorites
            s == "videos" -> Videos
            s == "search" -> Search
            s.startsWith("album:") -> Album(s.removePrefix("album:").toLongOrNull() ?: -1)
            s.startsWith("folder:") -> Folder(s.removePrefix("folder:"))
            s.startsWith("tag:") -> Tag(s.removePrefix("tag:"))
            else -> All
        }
    }
}

data class FolderInfo(val name: String, val count: Int, val cover: MediaItem)

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val resolver = app.contentResolver
    val repo = MediaRepository(resolver)
    val store = LibraryStore.get(app)
    private val prefs = app.getSharedPreferences("locket", Context.MODE_PRIVATE)

    val selection = GridSelection()

    /** null until the first load finishes. */
    val items: StateFlow<List<MediaItem>?> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val library: StateFlow<Library> = store.library.stateIn(viewModelScope, SharingStarted.Eagerly, Library())

    // ---- Similar-photo stacks ----
    val stackingEnabled = MutableStateFlow(prefs.getBoolean(KEY_STACKS, false))
    val stackProgress = MutableStateFlow<Float?>(null)

    val stacks: StateFlow<Map<Long, List<Long>>> = combine(stackingEnabled, items.filterNotNull()) { on, list -> on to list }
        .transformLatest { (on, list) ->
            if (!on) {
                emit(emptyMap())
                return@transformLatest
            }
            stackProgress.value = 0f
            val result = Stacks.compute(resolver, store, list) { done, total ->
                stackProgress.value = if (total == 0) 1f else done.toFloat() / total
            }
            stackProgress.value = null
            emit(result)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setStacking(on: Boolean) {
        prefs.edit { putBoolean(KEY_STACKS, on) }
        stackingEnabled.value = on
    }

    /** The main Photos grid. */
    val photosGrid: StateFlow<GridUiState> = combine(items, stacks) { list, st ->
        if (list == null) GridUiState() else buildGridState(list, st)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, GridUiState())

    // ---- Search ----
    val query = MutableStateFlow("")
    /** Inclusive epoch-millis range, or null for any date. */
    val dateRange = MutableStateFlow<LongRange?>(null)

    private val searchIndex: StateFlow<Map<Long, String>> = combine(items.filterNotNull(), library) { list, lib -> buildIndex(list, lib) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val searchResults: StateFlow<List<MediaItem>> = combine(query, dateRange, items.filterNotNull(), searchIndex) { q, range, list, index ->
        val tokens = q.lowercase().split(' ', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty() && range == null) {
            emptyList()
        } else {
            list.filter { item ->
                (range == null || item.takenAt in range) &&
                    (tokens.isEmpty() || index[item.id]?.let { text -> tokens.all { matches(text, it) } } == true)
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun matches(text: String, token: String): Boolean {
        val t = when (token) {
            "videos", "video", "movie", "movies" -> "\u0001video"
            "photos", "photo", "pictures", "picture", "pics" -> "\u0001photo"
            "starred", "star", "stars", "favorites", "favorite", "favourites", "fav" -> "\u0001starred"
            "screenshots", "screenshot" -> "screenshot"
            else -> token
        }
        return text.contains(t)
    }

    private fun buildIndex(list: List<MediaItem>, lib: Library): Map<Long, String> {
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        val albumNames = HashMap<Long, MutableList<String>>()
        lib.albums.forEach { a -> a.mediaIds.forEach { albumNames.getOrPut(it) { mutableListOf() } += a.name } }
        val out = HashMap<Long, String>(list.size)
        for (item in list) {
            val d = Instant.ofEpochMilli(item.takenAt).atZone(zone)
            out[item.id] = buildString {
                append(item.name).append(' ')
                append(item.bucket.orEmpty()).append(' ')
                lib.tags[item.id]?.forEach { append(it).append(' ') }
                albumNames[item.id]?.forEach { append(it).append(' ') }
                append(d.month.getDisplayName(TextStyle.FULL, locale)).append(' ')
                append(d.dayOfWeek.getDisplayName(TextStyle.FULL, locale)).append(' ')
                append(d.year).append(' ')
                append(DateTimeFormatter.ISO_LOCAL_DATE.format(d)).append(' ')
                append(if (item.isVideo) "\u0001video " else "\u0001photo ")
                if (item.isFavorite) append("\u0001starred ")
            }.lowercase()
        }
        return out
    }

    // ---- Lists for each source ----
    fun itemsFor(source: Source, all: List<MediaItem>, lib: Library, search: List<MediaItem>): List<MediaItem> = when (source) {
        Source.All -> all
        Source.Favorites -> all.filter { it.isFavorite }
        Source.Videos -> all.filter { it.isVideo }
        Source.Search -> search
        is Source.Album -> {
            val ids = lib.albums.firstOrNull { it.id == source.id }?.mediaIds?.toHashSet().orEmpty()
            all.filter { it.id in ids }
        }
        is Source.Folder -> all.filter { it.bucket == source.name }
        is Source.Tag -> all.filter { lib.tags[it.id]?.contains(source.tag) == true }
    }

    val folders: StateFlow<List<FolderInfo>> = items.map { list ->
        list.orEmpty().groupBy { it.bucket ?: "Other" }
            .map { (name, l) -> FolderInfo(name, l.size, l.first()) }
            .sortedByDescending { it.count }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Album & tag edits ----
    fun createAlbum(name: String, ids: Collection<Long>, then: (Long) -> Unit = {}) =
        viewModelScope.launch { then(store.createAlbum(name, ids)) }
    fun addToAlbum(id: Long, ids: Collection<Long>) = viewModelScope.launch { store.addToAlbum(id, ids) }
    fun removeFromAlbum(id: Long, ids: Collection<Long>) = viewModelScope.launch { store.removeFromAlbum(id, ids) }
    fun renameAlbum(id: Long, name: String) = viewModelScope.launch { store.renameAlbum(id, name) }
    fun deleteAlbum(id: Long) = viewModelScope.launch { store.deleteAlbum(id) }
    fun addTag(ids: Collection<Long>, tag: String) = viewModelScope.launch { store.addTag(ids, tag) }
    fun removeTag(ids: Collection<Long>, tag: String) = viewModelScope.launch { store.removeTag(ids, tag) }

    // ---- Bulk edit ----
    data class BulkProgress(val index: Int, val total: Int, val result: com.locketphoto.gallery.edit.BulkResult? = null)
    val bulk = MutableStateFlow<BulkProgress?>(null)
    private var bulkJob: kotlinx.coroutines.Job? = null

    fun runBulk(items: List<MediaItem>, spec: com.locketphoto.gallery.edit.BulkSpec) {
        bulkJob?.cancel()
        bulk.value = BulkProgress(0, items.size)
        bulkJob = viewModelScope.launch {
            val result = com.locketphoto.gallery.edit.BulkEdit.run(getApplication(), items, spec) { i, n ->
                bulk.value = BulkProgress(i, n)
            }
            bulk.value = BulkProgress(items.size, items.size, result)
        }
    }

    fun cancelBulk() {
        bulkJob?.cancel()
        bulk.value = null
    }

    fun dismissBulk() { bulk.value = null }

    init {
        // Forget selections whose photos vanished (trashed elsewhere, etc).
        viewModelScope.launch {
            items.filterNotNull().collect { list -> selection.retainOnly(list.mapTo(HashSet()) { it.id }) }
        }
    }

    private companion object {
        const val KEY_STACKS = "stack_similar"
    }
}
