package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ChannelEntity
import com.example.data.model.DeviceTelemetry
import com.example.data.model.OptimizationSettings
import com.example.data.model.VideoItem
import com.example.ui.FeedUiState
import com.example.ui.components.VideoCard

@Composable
fun FeedScreen(
    feedState: FeedUiState,
    channels: List<ChannelEntity>,
    selectedChannelId: String?,
    bookmarkedIds: Set<String>,
    settings: OptimizationSettings,
    telemetry: DeviceTelemetry,
    onSelectChannel: (String?) -> Unit,
    onRemoveChannel: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenAddChannelDialog: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onBookmarkToggle: (VideoItem) -> Unit,
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var contentFilter by remember { mutableStateOf("ALL") }

    Column(modifier = modifier.fillMaxSize()) {
        // Classic 2013 Section Title + Hardware Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val selectedChannelName = channels.firstOrNull { it.channelId == selectedChannelId }?.name
                Text(
                    text = selectedChannelName ?: "O que assistir",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Feed Atom RSS • ${settings.qualityPreset.label} • RAM livre ${telemetry.availableRamMb} MB",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("refresh_feed_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Compact hardware profile strip
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onOpenDiagnostics)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .testTag("hardware_banner"),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${telemetry.deviceModel} • ${if (telemetry.hasHardwareAvc) "H.264 HW" else "Modo Eco"} • ${settings.engineMode.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Channel Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                FilterChip(
                    selected = selectedChannelId == null,
                    onClick = { onSelectChannel(null) },
                    label = { Text("Todos (${channels.size})") },
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("channel_chip_all"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            items(channels, key = { it.channelId }) { channel ->
                FilterChip(
                    selected = selectedChannelId == channel.channelId,
                    onClick = { onSelectChannel(channel.channelId) },
                    label = { Text(channel.name) },
                    shape = RoundedCornerShape(4.dp),
                    trailingIcon = if (selectedChannelId == channel.channelId && channels.size > 1) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remover canal ${channel.name}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { onRemoveChannel(channel.channelId) }
                            )
                        }
                    } else null,
                    modifier = Modifier.testTag("channel_chip_${channel.channelId}"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onOpenAddChannelDialog,
                    shape = RoundedCornerShape(4.dp),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = { Text(stringResource(R.string.action_add_channel)) },
                    modifier = Modifier.testTag("add_channel_chip")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (feedState) {
            is FeedUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Carregando feeds leves (~20 KB/canal)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is FeedUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = feedState.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = onRefresh,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("retry_feed_button")
                        ) {
                            Text("Tentar novamente")
                        }
                    }
                }
            }

            is FeedUiState.Success -> {
                val shortsCount = feedState.videos.count { it.isShort }
                val filteredVideos = when (contentFilter) {
                    "SHORTS" -> feedState.videos.filter { it.isShort }
                    "VIDEOS" -> feedState.videos.filter { !it.isShort }
                    else -> feedState.videos
                }

                if (shortsCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = contentFilter == "ALL",
                            onClick = { contentFilter = "ALL" },
                            label = { Text("Tudo (${feedState.videos.size})") },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("filter_all_media")
                        )
                        FilterChip(
                            selected = contentFilter == "VIDEOS",
                            onClick = { contentFilter = "VIDEOS" },
                            label = { Text("Vídeos (${feedState.videos.size - shortsCount})") },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("filter_videos_only")
                        )
                        FilterChip(
                            selected = contentFilter == "SHORTS",
                            onClick = { contentFilter = "SHORTS" },
                            label = { Text("Shorts ($shortsCount)") },
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.testTag("filter_shorts_only")
                        )
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = when {
                        maxWidth >= 900.dp -> 3
                        maxWidth >= 560.dp -> 2
                        else -> 1
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredVideos, key = { it.id }) { video ->
                            VideoCard(
                                video = video,
                                isBookmarked = video.id in bookmarkedIds,
                                lowRamMode = settings.lowRamThumbnails,
                                onClick = { onVideoClick(video) },
                                onBookmarkToggle = { onBookmarkToggle(video) }
                            )
                        }
                    }
                }
            }
        }
    }
}
