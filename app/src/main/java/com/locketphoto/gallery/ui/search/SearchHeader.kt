package com.locketphoto.gallery.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Search box, date-range filter and one-tap suggestions; sits at the top of the results grid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHeader(
    query: String,
    onQuery: (String) -> Unit,
    range: LongRange?,
    onRange: (LongRange?) -> Unit,
    topTags: List<String>,
    resultCount: Int,
) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text("Name, folder, tag, album, month, year…") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(50),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
        ) {
            if (range != null) {
                InputChip(
                    selected = true,
                    onClick = { picking = true },
                    label = { Text(formatRange(range)) },
                    leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) },
                    trailingIcon = {
                        Icon(Icons.Rounded.Close, "Clear dates", Modifier.size(18.dp).padding(0.dp))
                    },
                )
                TextButton(onClick = { onRange(null) }) { Text("Any date") }
            } else {
                AssistChip(onClick = { picking = true }, label = { Text("Dates") }, leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)) })
            }
            listOf("Favorites", "Videos", "Screenshots").forEach { word ->
                AssistChip(onClick = { onQuery(word.lowercase()) }, label = { Text(word) })
            }
            topTags.take(10).forEach { tag ->
                AssistChip(onClick = { onQuery(tag) }, label = { Text(tag) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, null, Modifier.size(18.dp)) })
            }
        }
        if (query.isNotBlank() || range != null) {
            Text(
                if (resultCount == 1) "1 result" else "$resultCount results",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 4.dp),
            )
        }
    }

    if (picking) {
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = range?.first?.let(::toUtcDay),
            initialSelectedEndDateMillis = range?.last?.let(::toUtcDay),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis!!
                        val end = state.selectedEndDateMillis ?: start
                        onRange(localStartOf(start)..localEndOf(end))
                        picking = false
                    },
                ) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f, fill = false))
        }
    }
}

// Pickers speak UTC midnights; photos are in local time.
private fun utcDate(utcMillis: Long): LocalDate = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
fun localStartOf(utcMillis: Long): Long = utcDate(utcMillis).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun localEndOf(utcMillis: Long): Long = utcDate(utcMillis).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
fun toUtcDay(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun formatRange(r: LongRange): String {
    val f = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val z = ZoneId.systemDefault()
    val a = Instant.ofEpochMilli(r.first).atZone(z).toLocalDate()
    val b = Instant.ofEpochMilli(r.last).atZone(z).toLocalDate()
    return if (a == b) f.format(a) else "${f.format(a)} – ${f.format(b)}"
}
