package com.example.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun FloatingNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .testTag("floating_nav_bar"),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AuraNavItem(
                title = "Home",
                selectedIcon = Icons.Filled.Home,
                unselectedIcon = Icons.Outlined.Home,
                selected = currentRoute == Screen.Home.route,
                onClick = { onNavigate(Screen.Home.route) },
                testTag = "nav_item_home"
            )
            AuraNavItem(
                title = "Search",
                selectedIcon = Icons.Filled.Search,
                unselectedIcon = Icons.Outlined.Search,
                selected = currentRoute == Screen.Search.route,
                onClick = { onNavigate(Screen.Search.route) },
                testTag = "nav_item_search"
            )
            AuraNavItem(
                title = "Library",
                selectedIcon = Icons.Filled.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                selected = currentRoute == Screen.Library.route,
                onClick = { onNavigate(Screen.Library.route) },
                testTag = "nav_item_library"
            )
        }
    }
}

@Composable
private fun AuraNavItem(
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(180),
        label = "nav_color"
    )

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .testTag(testTag),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (selected) selectedIcon else unselectedIcon,
            contentDescription = title,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = color
        )
    }
}
