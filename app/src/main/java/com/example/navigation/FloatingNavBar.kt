package com.example.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Floating bottom navigation bar recreating the Echo Music open-source UI/UX.
 * Consists of a floating pill container for primary tabs (Home, Library)
 * and a standalone circular tab button for Search.
 */
@Composable
fun FloatingNavBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("floating_nav_bar"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Main pill for Home & Library
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .shadow(16.dp, shape = RoundedCornerShape(percent = 50), spotColor = Color.Black.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FloatingNavTab(
                    title = "Home",
                    selectedIcon = Icons.Filled.Home,
                    unselectedIcon = Icons.Outlined.Home,
                    isSelected = currentRoute == Screen.Home.route,
                    onClick = { onNavigate(Screen.Home.route) },
                    testTag = "nav_item_home",
                    modifier = Modifier.weight(1f)
                )

                FloatingNavTab(
                    title = "Library",
                    selectedIcon = Icons.Filled.LibraryMusic,
                    unselectedIcon = Icons.Outlined.LibraryMusic,
                    isSelected = currentRoute == Screen.Library.route,
                    onClick = { onNavigate(Screen.Library.route) },
                    testTag = "nav_item_library",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Standalone circular button for Search
        val isSearchSelected = currentRoute == Screen.Search.route
        val searchBgColor by animateColorAsState(
            targetValue = if (isSearchSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
            },
            animationSpec = tween(durationMillis = 200),
            label = "search_bg"
        )
        val searchIconColor by animateColorAsState(
            targetValue = if (isSearchSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            animationSpec = tween(durationMillis = 200),
            label = "search_icon"
        )

        Surface(
            modifier = Modifier
                .size(60.dp)
                .shadow(16.dp, shape = CircleShape, spotColor = Color.Black.copy(alpha = 0.5f))
                .clip(CircleShape)
                .clickable { onNavigate(Screen.Search.route) }
                .testTag("nav_item_search"),
            shape = CircleShape,
            color = searchBgColor,
            tonalElevation = 6.dp,
            border = BorderStroke(
                width = 1.dp,
                color = if (isSearchSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                }
            )
        ) {
            Box(
                modifier = Modifier.size(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = searchIconColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingNavTab(
    title: String,
    selectedIcon: ImageVector,
    unselectedIcon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val pillBgColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(durationMillis = 200),
        label = "pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 200),
        label = "content_color"
    )

    Box(
        modifier = modifier
            .padding(vertical = 6.dp, horizontal = 4.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(pillBgColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) selectedIcon else unselectedIcon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
