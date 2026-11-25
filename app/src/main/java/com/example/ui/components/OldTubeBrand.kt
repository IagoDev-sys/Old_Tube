package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.ChannelEntity
import com.example.ui.theme.ClassicGuideDark
import com.example.ui.theme.ClassicGuideItemHover
import com.example.ui.theme.ClassicGuideText
import com.example.ui.theme.ClassicGuideTextMuted
import com.example.ui.theme.ClassicTubeRed
import com.example.ui.theme.ClassicTubeRedBright
import com.example.ui.theme.ClassicTubeRedDark
import com.example.ui.theme.DisplayFontFamily

@Composable
fun OldTubeWordmark(
    onDarkBackground: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "OLD",
            fontFamily = DisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            letterSpacing = (-0.8).sp,
            color = if (onDarkBackground) Color.White else Color(0xFF222222)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(ClassicTubeRedBright, ClassicTubeRed, ClassicTubeRedDark)
                    )
                )
                .border(0.8.dp, Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        ) {
            Text(
                text = "Tube",
                fontFamily = DisplayFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                letterSpacing = (-0.4).sp,
                color = Color.White
            )
        }
    }
}

@Composable
fun OldTubeTopBar(
    isDarkSurface: Boolean,
    onSearchClick: () -> Unit,
    onPasteLinkClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OldTubeWordmark(onDarkBackground = isDarkSurface)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .clickable(onClick = onPasteLinkClick)
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_paste_link"),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Link",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Pesquisar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("topbar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Ajustes e Hardware",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline, thickness = 1.dp)
        }
    }
}

data class GuideNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val testTag: String
)

@Composable
fun OldTubeGuideSidebar(
    navItems: List<GuideNavItem>,
    currentRoute: String,
    channels: List<ChannelEntity>,
    selectedChannelId: String?,
    onNavigate: (String) -> Unit,
    onSelectChannel: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(ClassicGuideDark)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = "GUIA CLÁSSICO",
            style = MaterialTheme.typography.labelSmall,
            color = ClassicGuideTextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
        )

        navItems.forEach { item ->
            val selected = currentRoute == item.route && selectedChannelId == null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) ClassicGuideItemHover else Color.Transparent)
                    .clickable { onNavigate(item.route) }
                    .minimumInteractiveComponentSize()
                    .testTag(item.testTag),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(36.dp)
                        .background(if (selected) ClassicTubeRedBright else Color.Transparent)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = if (selected) ClassicTubeRedBright else ClassicGuideText,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Color.White else ClassicGuideText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (channels.isNotEmpty()) {
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 10.dp)
            )
            Text(
                text = "INSCRIÇÕES LOCAIS (${channels.size})",
                style = MaterialTheme.typography.labelSmall,
                color = ClassicGuideTextMuted,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
            )

            channels.forEach { channel ->
                val isSelected = selectedChannelId == channel.channelId && currentRoute == "feed"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) ClassicGuideItemHover else Color.Transparent)
                        .clickable { onSelectChannel(channel.channelId) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(ClassicTubeRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = channel.name.take(1).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = channel.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) Color.White else ClassicGuideText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
