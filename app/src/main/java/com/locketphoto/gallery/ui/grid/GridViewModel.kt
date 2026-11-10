package com.locketphoto.gallery.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.locketphoto.gallery.data.MediaItem
import com.locketphoto.gallery.data.MediaRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

sealed interface GridEntry {
    val key: String

    /** Short label for the scroll bubble, e.g. "Nov 2026". */
    val scrubLabel: String

    data class Header(override val key: String, val title: String, override val scrubLabel: String) : GridEntry

    data class Cell(val item: MediaItem, val index: Int, override val scrubLabel: String) : GridEntry {
        override val key: String get() = "m:${item.id}"
    }
}

data class GridUiState(
    val loading: Boolean = true,
    val entries: List<GridEntry> = emptyList(),
    val items: List<MediaItem> = emptyList(),
)

class GridViewModel(repo: MediaRepository) : ViewModel() {

    val state: StateFlow<GridUiState> = repo.observeAll()
        .map { items -> GridUiState(loading = false, entries = buildEntries(items), items = items) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState())

    private fun buildEntries(items: List<MediaItem>): List<GridEntry> {
        val zone = ZoneId.systemDefault()
        val thisYear = LocalDate.now(zone).year
        val locale = Locale.getDefault()
        val monthFull = DateTimeFormatter.ofPattern("MMMM", locale)
        val monthYearFull = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
        val monthYearShort = DateTimeFormatter.ofPattern("MMM yyyy", locale)

        val out = ArrayList<GridEntry>(items.size + items.size / 20 + 1)
        var currentKey = -1
        var currentShort = ""
        items.forEachIndexed { index, item ->
            val date = Instant.ofEpochMilli(item.takenAt).atZone(zone).toLocalDate()
            val key = date.year * 100 + date.monthValue
            if (key != currentKey) {
                currentKey = key
                currentShort = monthYearShort.format(date)
                val title = if (date.year == thisYear) monthFull.format(date) else monthYearFull.format(date)
                out += GridEntry.Header("h:$key", title, currentShort)
            }
            out += GridEntry.Cell(item, index, currentShort)
        }
        return out
    }
}
