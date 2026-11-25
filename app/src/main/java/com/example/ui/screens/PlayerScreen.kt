package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.ui.PlayerUiState
import com.example.ui.components.LegacyPlayerView

@Composable
fun PlayerScreen(
    playerState: PlayerUiState,
    relatedVideos: List<VideoItem>,
    bookmarkedIds: Set<String>,
    isFullscreen: Boolean,
    onSetFullscreen: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSwitchEngine: (Boolean) -> Unit,
    onPositionChanged: (String, Long) -> Unit,
    onBookmarkToggle: (VideoItem) -> Unit,
    onSelectRelated: (VideoItem) -> Unit,
    onRetryVideo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        if (isFullscreen) {
            onSetFullscreen(false)
        } else {
            onBack()
        }
    }

    val context = LocalContext.current

    when (playerState) {
        is PlayerUiState.Idle -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                OutlinedButton(onClick = onBack) {
                    Text("Voltar ao feed")
                }
            }
        }

        is PlayerUiState.Loading -> {
            Column(modifier = modifier.fillMaxSize()) {
                PlayerTopHeader(title = playerState.title, onBack = onBack)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Resolvendo stream H.264 / Lite...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                }
            }
        }

        is PlayerUiState.Error -> {
            Column(modifier = modifier.fillMaxSize()) {
                PlayerTopHeader(title = "Erro de reprodução", onBack = onBack)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = playerState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onRetryVideo(playerState.videoId) }) {
                            Text("Tentar novamente")
                        }
                        OutlinedButton(onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://www.youtube.com/watch?v=${playerState.videoId}")
                            )
                            context.startActivity(intent)
                        }) {
                            Text(stringResource(R.string.action_open_external))
                        }
                    }
                }
            }
        }

        is PlayerUiState.Ready -> {
            val stream = playerState.stream
            val isBookmarked = stream.videoId in bookmarkedIds
            val currentAsVideo = VideoItem(
                id = stream.videoId,
                title = stream.title,
                channelName = stream.channelName,
                channelId = stream.channelId,
                thumbnailUrl = stream.thumbnailUrl,
                description = stream.description,
                sourceTag = stream.resolvedVia
            )

            if (isFullscreen) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    LegacyPlayerView(
                        stream = stream,
                        useLiteEmbed = playerState.useLiteEmbed,
                        isFullscreen = true,
                        onToggleFullscreen = { onSetFullscreen(false) },
                        onFallbackToLiteEmbed = { onSwitchEngine(true) },
                        onSwitchEngine = onSwitchEngine,
                        onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                return
            }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val isWideTablet = maxWidth >= 780.dp

                if (isWideTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(0.64f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            PlayerTopHeader(title = stream.channelName, onBack = onBack)
                            LegacyPlayerView(
                                stream = stream,
                                useLiteEmbed = playerState.useLiteEmbed,
                                isFullscreen = false,
                                onToggleFullscreen = { onSetFullscreen(true) },
                                onFallbackToLiteEmbed = { onSwitchEngine(true) },
                                onSwitchEngine = onSwitchEngine,
                                onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                            PlayerMetadataPanel(
                                stream = stream,
                                useLiteEmbed = playerState.useLiteEmbed,
                                isBookmarked = isBookmarked,
                                onSwitchEngine = onSwitchEngine,
                                onBookmarkToggle = { onBookmarkToggle(currentAsVideo) },
                                onOpenExternal = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/watch?v=${stream.videoId}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.36f)
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "A seguir",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    relatedVideos.filter { it.id != stream.videoId }.take(15),
                                    key = { it.id }
                                ) { video ->
                                    CompactUpNextRow(
                                        video = video,
                                        onClick = { onSelectRelated(video) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            PlayerTopHeader(title = stream.channelName, onBack = onBack)
                        }
                        item {
                            LegacyPlayerView(
                                stream = stream,
                                useLiteEmbed = playerState.useLiteEmbed,
                                isFullscreen = false,
                                onToggleFullscreen = { onSetFullscreen(true) },
                                onFallbackToLiteEmbed = { onSwitchEngine(true) },
                                onSwitchEngine = onSwitchEngine,
                                onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }
                        item {
                            PlayerMetadataPanel(
                                stream = stream,
                                useLiteEmbed = playerState.useLiteEmbed,
                                isBookmarked = isBookmarked,
                                onSwitchEngine = onSwitchEngine,
                                onBookmarkToggle = { onBookmarkToggle(currentAsVideo) },
                                onOpenExternal = {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://www.youtube.com/watch?v=${stream.videoId}")
                                    )
                                    context.startActivity(intent)
                                }
                            )
                        }
                        if (relatedVideos.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "A seguir",
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            items(
                                relatedVideos.filter { it.id != stream.videoId }.take(12),
                                key = { it.id }
                            ) { video ->
                                CompactUpNextRow(
                                    video = video,
                                    onClick = { onSelectRelated(video) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerTopHeader(
    title: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("player_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.action_back)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PlayerMetadataPanel(
    stream: ResolvedStream,
    useLiteEmbed: Boolean,
    isBookmarked: Boolean,
    onSwitchEngine: (Boolean) -> Unit,
    onBookmarkToggle: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var expandDescription by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = stream.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "${stream.channelName} • Origem: ${stream.resolvedVia}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Engine / Codec control row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !useLiteEmbed,
                onClick = { onSwitchEngine(false) },
                enabled = stream.directH264Url != null,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = {
                    Text(
                        if (stream.directH264Url != null) "H.264 Nativo" else "H.264 Indisponível"
                    )
                },
                modifier = Modifier.testTag("chip_native_engine")
            )

            FilterChip(
                selected = useLiteEmbed,
                onClick = { onSwitchEngine(true) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                label = { Text("IFrame Lite") },
                modifier = Modifier.testTag("chip_lite_engine")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onBookmarkToggle,
                modifier = Modifier.testTag("player_bookmark_button")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBookmarked) "Salvo" else "Salvar")
            }

            OutlinedButton(
                onClick = onOpenExternal,
                modifier = Modifier.testTag("player_external_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_open_external))
            }
        }

        if (stream.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expandDescription = !expandDescription },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Descrição",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = if (expandDescription) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = null
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stream.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expandDescription) 40 else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactUpNextRow(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
            .testTag("up_next_${video.id}"),
        verticalAlignment = Alignment.Top
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
