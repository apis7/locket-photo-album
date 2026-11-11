package com.locketphoto.gallery.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Tab(val label: String, val icon: ImageVector) {
    Photos("Photos", Icons.Outlined.PhotoLibrary),
    Albums("Albums", Icons.Outlined.PhotoAlbum),
    Search("Search", Icons.Rounded.Search),
}

val BottomNavHeight = 64.dp

/** Floating pill with a gold indicator that springs between tabs. */
@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier
            .padding(horizontal = 40.dp, vertical = 8.dp)
            .height(56.dp)
            .shadow(14.dp, shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .padding(5.dp),
    ) {
        val tabWidth = maxWidth / Tab.entries.size
        val x by animateDpAsState(
            tabWidth * current.ordinal,
            spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
            label = "indicator",
        )
        Box(
            Modifier
                .offset(x = x)
                .width(tabWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f), shape),
        )
        Row {
            Tab.entries.forEach { tab ->
                val selected = tab == current
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "tabColor",
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier
                        .width(tabWidth)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
                ) {
                    Icon(tab.icon, tab.label, tint = color)
                    if (selected) {
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
        }
    }
}
