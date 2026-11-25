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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.ActivePlayerBackend
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import com.example.ui.PlayerUiState
import com.example.ui.components.LegacyPlayerView

@Composable
fun PlayerScreen(
    playerState: PlayerUiState,
    relatedVideos: List<VideoItem>,
    bookmarkedIds: Set<String>,
    followedChannelIds: Set<String>,
    isFullscreen: Boolean,
    onSetFullscreen: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSwitchBackend: (ActivePlayerBackend) -> Unit,
    onPositionChanged: (String, Long) -> Unit,
    onBookmarkToggle: (VideoItem) -> Unit,
    onToggleSubscribe: (String, String) -> Unit,
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
                OutlinedButton(onClick = onBack, shape = RoundedCornerShape(4.dp)) {
                    Text("Voltar ao início")
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
                            text = "Preparando reprodutor clássico...",
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
                        Button(
                            onClick = { onRetryVideo(playerState.videoId) },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Tentar novamente")
                        }
                        OutlinedButton(
                            shape = RoundedCornerShape(4.dp),
                            onClick = {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://www.youtube.com/watch?v=${playerState.videoId}")
                                )
                                context.startActivity(intent)
                            }
                        ) {
                            Text(stringResource(R.string.action_open_external))
                        }
                    }
                }
            }
        }

        is PlayerUiState.Ready -> {
            val stream = playerState.stream
            val isBookmarked = stream.videoId in bookmarkedIds
            val isSubscribed = stream.channelId.isNotBlank() && stream.channelId in followedChannelIds
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
                        activeBackend = playerState.activeBackend,
                        isFullscreen = true,
                        onToggleFullscreen = { onSetFullscreen(false) },
                        onSwitchBackend = onSwitchBackend,
                        onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                return
            }

            var useVerticalShortFrame by remember(stream.videoId) { mutableStateOf(stream.isShort) }
            val nextShortOrVideo = remember(relatedVideos, stream.videoId, stream.isShort) {
                val others = relatedVideos.filter { it.id != stream.videoId }
                if (stream.isShort) {
                    others.firstOrNull { it.isShort } ?: others.firstOrNull()
                } else {
                    null
                }
            }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val isWideTablet = maxWidth >= 780.dp

                if (isWideTablet) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                        ) {
                            PlayerTopHeader(title = stream.channelName, onBack = onBack)
                            AdaptivePlayerStage(
                                stream = stream,
                                activeBackend = playerState.activeBackend,
                                useVerticalShortFrame = useVerticalShortFrame,
                                isWideTablet = true,
                                onToggleFullscreen = { onSetFullscreen(true) },
                                onSwitchBackend = onSwitchBackend,
                                onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) }
                            )
                            PlayerMetadataPanel(
                                stream = stream,
                                activeBackend = playerState.activeBackend,
                                useVerticalShortFrame = useVerticalShortFrame,
                                nextShortVideo = nextShortOrVideo,
                                isBookmarked = isBookmarked,
                                isSubscribed = isSubscribed,
                                onSwitchBackend = onSwitchBackend,
                                onToggleAspect = { useVerticalShortFrame = !useVerticalShortFrame },
                                onNextShort = { nextShortOrVideo?.let(onSelectRelated) },
                                onBookmarkToggle = { onBookmarkToggle(currentAsVideo) },
                                onToggleSubscribe = {
                                    onToggleSubscribe(stream.channelId, stream.channelName)
                                },
                                onOpenExternal = {
                                    val targetUrl = if (stream.isShort) {
                                        "https://www.youtube.com/shorts/${stream.videoId}"
                                    } else {
                                        "https://www.youtube.com/watch?v=${stream.videoId}"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    context.startActivity(intent)
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight()
                                .border(1.dp, MaterialTheme.colorScheme.outline)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Próximos vídeos",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("player_lazy_column"),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                        item {
                            PlayerTopHeader(title = stream.channelName, onBack = onBack)
                        }
                        item {
                            AdaptivePlayerStage(
                                stream = stream,
                                activeBackend = playerState.activeBackend,
                                useVerticalShortFrame = useVerticalShortFrame,
                                isWideTablet = false,
                                onToggleFullscreen = { onSetFullscreen(true) },
                                onSwitchBackend = onSwitchBackend,
                                onPositionChanged = { pos -> onPositionChanged(stream.videoId, pos) }
                            )
                        }
                        item {
                            PlayerMetadataPanel(
                                stream = stream,
                                activeBackend = playerState.activeBackend,
                                useVerticalShortFrame = useVerticalShortFrame,
                                nextShortVideo = nextShortOrVideo,
                                isBookmarked = isBookmarked,
                                isSubscribed = isSubscribed,
                                onSwitchBackend = onSwitchBackend,
                                onToggleAspect = { useVerticalShortFrame = !useVerticalShortFrame },
                                onNextShort = { nextShortOrVideo?.let(onSelectRelated) },
                                onBookmarkToggle = { onBookmarkToggle(currentAsVideo) },
                                onToggleSubscribe = {
                                    onToggleSubscribe(stream.channelId, stream.channelName)
                                },
                                onOpenExternal = {
                                    val targetUrl = if (stream.isShort) {
                                        "https://www.youtube.com/shorts/${stream.videoId}"
                                    } else {
                                        "https://www.youtube.com/watch?v=${stream.videoId}"
                                    }
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                                    context.startActivity(intent)
                                }
                            )
                        }
                        if (relatedVideos.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    text = "Próximos vídeos",
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
    Surface(color = MaterialTheme.colorScheme.surface) {
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
}

@Composable
private fun AdaptivePlayerStage(
    stream: ResolvedStream,
    activeBackend: ActivePlayerBackend,
    useVerticalShortFrame: Boolean,
    isWideTablet: Boolean,
    onToggleFullscreen: () -> Unit,
    onSwitchBackend: (ActivePlayerBackend) -> Unit,
    onPositionChanged: (Long) -> Unit
) {
    if (useVerticalShortFrame) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            LegacyPlayerView(
                stream = stream,
                activeBackend = activeBackend,
                isFullscreen = false,
                onToggleFullscreen = onToggleFullscreen,
                onSwitchBackend = onSwitchBackend,
                onPositionChanged = onPositionChanged,
                modifier = Modifier
                    .heightIn(max = if (isWideTablet) 460.dp else 320.dp)
                    .aspectRatio(9f / 16f)
            )
        }
    } else {
        LegacyPlayerView(
            stream = stream,
            activeBackend = activeBackend,
            isFullscreen = false,
            onToggleFullscreen = onToggleFullscreen,
            onSwitchBackend = onSwitchBackend,
            onPositionChanged = onPositionChanged,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerMetadataPanel(
    stream: ResolvedStream,
    activeBackend: ActivePlayerBackend,
    useVerticalShortFrame: Boolean,
    nextShortVideo: VideoItem?,
    isBookmarked: Boolean,
    isSubscribed: Boolean,
    onSwitchBackend: (ActivePlayerBackend) -> Unit,
    onToggleAspect: () -> Unit,
    onNextShort: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onOpenExternal: () -> Unit
) {
    var expandDescription by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(
            text = stream.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Classic 2013 Channel Row + Subscribe Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stream.channelName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stream.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Via ${stream.resolvedVia}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (stream.channelId.isNotBlank()) {
                Button(
                    onClick = onToggleSubscribe,
                    shape = RoundedCornerShape(3.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else Color.White
                    ),
                    modifier = Modifier.testTag("subscribe_channel_button")
                ) {
                    Text(if (isSubscribed) "Inscrito ✓" else "Inscrever-se")
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // 3-Tier Engine Selector
        Text(
            text = "Modo de Reprodução (troque se o vídeo bloquear embed):",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = activeBackend == ActivePlayerBackend.NATIVE_H264,
                onClick = { onSwitchBackend(ActivePlayerBackend.NATIVE_H264) },
                enabled = stream.directH264Url != null,
                shape = RoundedCornerShape(4.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Memory, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text(if (stream.directH264Url != null) "H.264 Nativo" else "H.264 Indisponível") },
                modifier = Modifier.testTag("chip_native_engine")
            )

            FilterChip(
                selected = activeBackend == ActivePlayerBackend.IFRAME_EMBED,
                onClick = { onSwitchBackend(ActivePlayerBackend.IFRAME_EMBED) },
                shape = RoundedCornerShape(4.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Embed Direto") },
                modifier = Modifier.testTag("chip_iframe_engine")
            )

            FilterChip(
                selected = activeBackend == ActivePlayerBackend.MOBILE_WEB,
                onClick = { onSwitchBackend(ActivePlayerBackend.MOBILE_WEB) },
                shape = RoundedCornerShape(4.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                label = { Text("Mobile Direto") },
                modifier = Modifier.testTag("chip_mobile_engine")
            )
        }

        if (activeBackend != ActivePlayerBackend.NATIVE_H264) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Se o YouTube pedir verificação neste IP/emulador, tente H.264 Nativo, Embed Direto ou Abrir no navegador.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onBookmarkToggle,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.testTag("player_bookmark_button")
            ) {
                Icon(
                    imageVector = if (isBookmarked) Icons.Filled.WatchLater else Icons.Outlined.WatchLater,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBookmarked) "Salvo" else "Assistir mais tarde")
            }

            OutlinedButton(
                onClick = onOpenExternal,
                shape = RoundedCornerShape(4.dp),
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

        if (stream.isShort || nextShortVideo != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onToggleAspect,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("toggle_short_aspect_button")
                ) {
                    Text(if (useVerticalShortFrame) "Tela 9:16 (Short)" else "Tela 16:9")
                }
                if (nextShortVideo != null) {
                    Button(
                        onClick = onNextShort,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.testTag("next_short_button")
                    ) {
                        Text("Próximo Short →")
                    }
                }
            }
        }

        if (stream.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .clickable { expandDescription = !expandDescription },
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Sobre este vídeo", style = MaterialTheme.typography.titleMedium)
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
            .clip(RoundedCornerShape(4.dp))
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
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF101010))
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
            Spacer(modifier = Modifier.height(3.dp))
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
