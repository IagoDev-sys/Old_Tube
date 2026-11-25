package com.example.data.remote

import android.util.Xml
import com.example.data.model.BridgePingResult
import com.example.data.model.OptimizationSettings
import com.example.data.model.PlaybackEngineMode
import com.example.data.model.ResolvedStream
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class YouTubeLegacyNetwork {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .connectionPool(ConnectionPool(4, 2, TimeUnit.MINUTES))
        .followRedirects(true)
        .build()

    private val defaultBridges = listOf(
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://yewtu.be",
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de"
    )

    fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (Regex("^[A-Za-z0-9_-]{11}$").matches(trimmed)) {
            return trimmed
        }
        val patterns = listOf(
            Regex("[?&]v=([A-Za-z0-9_-]{11})"),
            Regex("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/shorts/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/embed/([A-Za-z0-9_-]{11})"),
            Regex("youtube\\.com/live/([A-Za-z0-9_-]{11})")
        )
        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null) return match.groupValues[1]
        }
        return null
    }

    fun buildThumbnailUrl(videoId: String, lowRamThumbnails: Boolean): String {
        val quality = if (lowRamThumbnails) "mqdefault" else "hqdefault"
        return "https://i.ytimg.com/vi/$videoId/$quality.jpg"
    }

    suspend fun fetchChannelFeeds(
        channelIds: List<String>,
        lowRamThumbnails: Boolean
    ): List<VideoItem> = coroutineScope {
        if (channelIds.isEmpty()) return@coroutineScope emptyList()
        val deferred = channelIds.map { channelId ->
            async(Dispatchers.IO) {
                runCatching { fetchSingleChannelFeed(channelId, lowRamThumbnails) }
                    .getOrDefault(emptyList())
            }
        }
        deferred.awaitAll()
            .flatten()
            .sortedByDescending { it.publishedAt }
    }

    private fun fetchSingleChannelFeed(
        channelId: String,
        lowRamThumbnails: Boolean
    ): List<VideoItem> {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        val body = httpGet(url) ?: return emptyList()
        return parseAtomFeed(body, lowRamThumbnails)
    }

    internal fun parseAtomFeed(xml: String, lowRamThumbnails: Boolean): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var eventType = parser.eventType
        var insideEntry = false
        var insideAuthor = false
        var currentTag = ""

        var videoId = ""
        var title = ""
        var authorName = ""
        var channelId = ""
        var published = ""
        var description = ""
        var views = ""
        var linkHref = ""

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "entry" -> {
                            insideEntry = true
                            videoId = ""
                            title = ""
                            authorName = ""
                            channelId = ""
                            published = ""
                            description = ""
                            views = ""
                            linkHref = ""
                        }
                        "author" -> if (insideEntry) insideAuthor = true
                        "link" -> if (insideEntry) {
                            val rel = parser.getAttributeValue(null, "rel") ?: ""
                            if (rel == "alternate" || linkHref.isEmpty()) {
                                linkHref = parser.getAttributeValue(null, "href") ?: linkHref
                            }
                        }
                        "media:statistics" -> if (insideEntry) {
                            views = parser.getAttributeValue(null, "views") ?: ""
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text ?: ""
                    if (insideEntry && text.isNotBlank()) {
                        when (currentTag) {
                            "yt:videoId" -> videoId = text.trim()
                            "yt:channelId" -> channelId = text.trim()
                            "title" -> if (title.isEmpty()) title = text.trim()
                            "name" -> if (insideAuthor) authorName = text.trim()
                            "published" -> published = text.trim()
                            "media:description" -> description = text.trim()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "author" -> insideAuthor = false
                        "entry" -> {
                            if (videoId.isNotBlank() && title.isNotBlank()) {
                                val isShort = linkHref.contains("/shorts/", ignoreCase = true)
                                videos.add(
                                    VideoItem(
                                        id = videoId,
                                        title = title,
                                        channelName = authorName.ifBlank { "Canal" },
                                        channelId = channelId,
                                        thumbnailUrl = buildThumbnailUrl(videoId, lowRamThumbnails),
                                        publishedAt = formatIsoDate(published),
                                        durationText = "",
                                        viewCountText = formatViews(views),
                                        description = description,
                                        sourceTag = if (isShort) "Short RSS" else "Atom RSS",
                                        isShort = isShort
                                    )
                                )
                            }
                            insideEntry = false
                        }
                    }
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }
        return videos
    }

    suspend fun resolveChannelFromInput(rawInput: String): Pair<String, String>? =
        withContext(Dispatchers.IO) {
            val trimmed = rawInput.trim()
            if (Regex("^UC[A-Za-z0-9_-]{22}$").matches(trimmed)) {
                val feedVideos = runCatching { fetchSingleChannelFeed(trimmed, true) }.getOrNull()
                val name = feedVideos?.firstOrNull()?.channelName ?: "Canal $trimmed"
                return@withContext trimmed to name
            }

            val targetUrl = normalizeChannelTargetUrl(trimmed) ?: return@withContext null

            val html = httpGet(targetUrl, mobileUa = false) ?: return@withContext null
            val channelId = Regex("""(?:itemprop="channelId"\s+content="|externalId":")(UC[A-Za-z0-9_-]{22})"""")
                .find(html)?.groupValues?.getOrNull(1)
                ?: return@withContext null

            val titleMatch = Regex("""<meta\s+property="og:title"\s+content="([^"]+)"""")
                .find(html)?.groupValues?.getOrNull(1)
                ?: "Canal"

            channelId to decodeHtmlEntities(titleMatch)
        }

    suspend fun searchVideos(
        query: String,
        lowRamThumbnails: Boolean,
        customBridgeUrl: String
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val directResults = runCatching {
            searchViaYouTubeInitialData(cleanQuery, lowRamThumbnails)
        }.getOrDefault(emptyList())

        if (directResults.isNotEmpty()) {
            return@withContext directResults
        }

        val bridges = (listOf(customBridgeUrl) + defaultBridges)
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("https://") }
            .distinct()
        for (bridge in bridges.take(3)) {
            val invidious = runCatching {
                searchViaInvidious(bridge, cleanQuery, lowRamThumbnails)
            }.getOrDefault(emptyList())
            if (invidious.isNotEmpty()) return@withContext invidious

            val piped = runCatching {
                searchViaPiped(bridge, cleanQuery, lowRamThumbnails)
            }.getOrDefault(emptyList())
            if (piped.isNotEmpty()) return@withContext piped
        }

        emptyList()
    }

    private fun searchViaYouTubeInitialData(
        query: String,
        lowRamThumbnails: Boolean
    ): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encoded&sp=EgIQAQ%253D%253D"
        val html = httpGet(url, mobileUa = false) ?: return emptyList()
        val jsonText = extractBalancedJsonObject(html, "ytInitialData") ?: return emptyList()
        val root = JSONObject(jsonText)

        val results = mutableListOf<VideoItem>()
        val contents = root
            .optJSONObject("contents")
            ?.optJSONObject("twoColumnSearchResultsRenderer")
            ?.optJSONObject("primaryContents")
            ?.optJSONObject("sectionListRenderer")
            ?.optJSONArray("contents") ?: return emptyList()

        for (i in 0 until contents.length()) {
            val section = contents.optJSONObject(i)
            val items = section
                ?.optJSONObject("itemSectionRenderer")
                ?.optJSONArray("contents") ?: continue

            for (j in 0 until items.length()) {
                val entry = items.optJSONObject(j) ?: continue

                val videoRenderer = entry.optJSONObject("videoRenderer")
                if (videoRenderer != null) {
                    val videoId = videoRenderer.optString("videoId")
                    if (videoId.length != 11) continue

                    val title = readRunsText(videoRenderer.optJSONObject("title"))
                    if (title.isBlank()) continue

                    val ownerObj = videoRenderer.optJSONObject("ownerText")
                        ?: videoRenderer.optJSONObject("longBylineText")
                    val channelName = readRunsText(ownerObj).ifBlank { "YouTube" }
                    val channelId = ownerObj
                        ?.optJSONArray("runs")
                        ?.optJSONObject(0)
                        ?.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")
                        ?.optString("browseId") ?: ""

                    val navUrl = videoRenderer.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("commandMetadata")
                        ?.optJSONObject("webCommandMetadata")
                        ?.optString("url") ?: ""
                    val isShort = navUrl.contains("/shorts/", ignoreCase = true)

                    val duration = videoRenderer.optJSONObject("lengthText")?.optString("simpleText") ?: ""
                    val views = videoRenderer.optJSONObject("shortViewCountText")?.optString("simpleText")
                        ?: videoRenderer.optJSONObject("viewCountText")?.optString("simpleText")
                        ?: ""
                    val published = videoRenderer.optJSONObject("publishedTimeText")?.optString("simpleText") ?: ""

                    results.add(
                        VideoItem(
                            id = videoId,
                            title = title,
                            channelName = channelName,
                            channelId = channelId,
                            thumbnailUrl = buildThumbnailUrl(videoId, lowRamThumbnails),
                            publishedAt = published,
                            durationText = duration,
                            viewCountText = views,
                            sourceTag = if (isShort) "Short" else "Busca Direta",
                            isShort = isShort
                        )
                    )
                    if (results.size >= 24) break
                    continue
                }

                // Extract Shorts shelf items when present in search
                val reelItems = entry.optJSONObject("reelShelfRenderer")?.optJSONArray("items")
                if (reelItems != null) {
                    for (k in 0 until reelItems.length()) {
                        val reel = reelItems.optJSONObject(k)?.optJSONObject("reelItemRenderer") ?: continue
                        val videoId = reel.optString("videoId")
                        if (videoId.length != 11) continue
                        val title = reel.optJSONObject("headline")?.optString("simpleText")
                            ?.ifBlank { readRunsText(reel.optJSONObject("headline")) }
                            ?: "Short $videoId"
                        val views = reel.optJSONObject("viewCountText")?.optString("simpleText") ?: ""
                        results.add(
                            VideoItem(
                                id = videoId,
                                title = title,
                                channelName = "YouTube Shorts",
                                thumbnailUrl = buildThumbnailUrl(videoId, lowRamThumbnails),
                                viewCountText = views,
                                sourceTag = "Short",
                                isShort = true
                            )
                        )
                        if (results.size >= 24) break
                    }
                }
            }
        }
        return results
    }

    private fun searchViaInvidious(
        bridgeUrl: String,
        query: String,
        lowRamThumbnails: Boolean
    ): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = httpGet("$bridgeUrl/api/v1/search?q=$encoded&type=video") ?: return emptyList()
        if (!body.trimStart().startsWith("[")) return emptyList()
        val arr = JSONArray(body)
        val list = mutableListOf<VideoItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("videoId")
            if (id.length != 11) continue
            val title = item.optString("title")
            if (title.isBlank()) continue
            list.add(
                VideoItem(
                    id = id,
                    title = title,
                    channelName = item.optString("author", "Canal"),
                    channelId = item.optString("authorId"),
                    thumbnailUrl = buildThumbnailUrl(id, lowRamThumbnails),
                    publishedAt = item.optString("publishedText"),
                    durationText = formatDuration(item.optLong("lengthSeconds", 0L)),
                    viewCountText = formatViews(item.optLong("viewCount", -1L).takeIf { it >= 0 }?.toString() ?: ""),
                    sourceTag = "Invidious API"
                )
            )
            if (list.size >= 24) break
        }
        return list
    }

    private fun searchViaPiped(
        bridgeUrl: String,
        query: String,
        lowRamThumbnails: Boolean
    ): List<VideoItem> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = httpGet("$bridgeUrl/search?q=$encoded&filter=videos") ?: return emptyList()
        if (!body.trimStart().startsWith("{")) return emptyList()
        val json = JSONObject(body)
        val items = json.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<VideoItem>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val itemUrl = item.optString("url")
            val id = extractVideoId("https://youtube.com$itemUrl") ?: continue
            val seconds = item.optLong("duration", 0L)
            list.add(
                VideoItem(
                    id = id,
                    title = item.optString("title"),
                    channelName = item.optString("uploaderName", "Canal"),
                    channelId = item.optString("uploaderUrl").substringAfterLast("/"),
                    thumbnailUrl = buildThumbnailUrl(id, lowRamThumbnails),
                    publishedAt = item.optString("uploadedDate"),
                    durationText = formatDuration(seconds),
                    viewCountText = formatViews(item.optLong("views", -1L).takeIf { it >= 0 }?.toString() ?: ""),
                    sourceTag = "Piped API"
                )
            )
        }
        return list
    }

    suspend fun fetchOEmbedMetadata(videoId: String, lowRamThumbnails: Boolean): VideoItem? =
        withContext(Dispatchers.IO) {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val body = httpGet(url) ?: return@withContext null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
            VideoItem(
                id = videoId,
                title = json.optString("title", "Vídeo $videoId"),
                channelName = json.optString("author_name", "YouTube"),
                thumbnailUrl = buildThumbnailUrl(videoId, lowRamThumbnails),
                sourceTag = "oEmbed"
            )
        }

    suspend fun resolvePlayableStream(
        videoId: String,
        knownVideo: VideoItem?,
        settings: OptimizationSettings,
        forceShort: Boolean = false
    ): ResolvedStream = withContext(Dispatchers.IO) {
        val baseline = knownVideo ?: fetchOEmbedMetadata(videoId, settings.lowRamThumbnails)
        val isShort = forceShort || (knownVideo?.isShort == true)
        val title = baseline?.title ?: "Vídeo $videoId"
        val channelName = baseline?.channelName ?: "YouTube"
        val channelId = baseline?.channelId ?: ""
        val thumb = baseline?.thumbnailUrl ?: buildThumbnailUrl(videoId, settings.lowRamThumbnails)
        val desc = baseline?.description ?: ""

        val loopQuery = if (isShort) "&loop=1&playlist=$videoId" else ""
        val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId" +
            "?autoplay=1&playsinline=1&rel=0&modestbranding=1&vq=${settings.qualityPreset.youtubeVqParam}$loopQuery"
        val mobileWatchUrl = if (isShort) {
            "https://m.youtube.com/shorts/$videoId"
        } else {
            "https://m.youtube.com/watch?v=$videoId"
        }

        if (settings.engineMode == PlaybackEngineMode.LITE_EMBED_ONLY) {
            return@withContext ResolvedStream(
                videoId = videoId,
                title = title,
                channelName = channelName,
                channelId = channelId,
                thumbnailUrl = thumb,
                description = desc,
                directH264Url = null,
                qualityLabel = settings.qualityPreset.label,
                codecLabel = "Embed Direto",
                embedUrl = embedUrl,
                mobileWatchUrl = mobileWatchUrl,
                resolvedVia = if (isShort) "Shorts Embed" else "Embed YouTube",
                isShort = isShort
            )
        }

        // 1. Try direct InnerTube progressive H.264 stream (fastest, avoids community bridge downtime)
        val directInnerTube = runCatching {
            resolveViaInnerTube(
                videoId = videoId,
                maxHeight = settings.qualityPreset.maxHeight,
                fallbackTitle = title,
                fallbackChannel = channelName,
                channelId = channelId,
                thumb = thumb,
                fallbackDesc = desc,
                embedUrl = embedUrl,
                mobileWatchUrl = mobileWatchUrl,
                isShort = isShort
            )
        }.getOrNull()
        if (directInnerTube != null && directInnerTube.directH264Url != null) {
            return@withContext directInnerTube
        }

        // 2. Fallback to Invidious / Piped community bridges
        val bridges = (listOf(settings.customBridgeUrl) + defaultBridges)
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("https://") }
            .distinct()
        for (bridge in bridges.take(3)) {
            val bridgeResult = runCatching {
                resolveFromBridge(
                    bridgeUrl = bridge,
                    videoId = videoId,
                    maxHeight = settings.qualityPreset.maxHeight,
                    fallbackTitle = title,
                    fallbackChannel = channelName,
                    channelId = channelId,
                    thumb = thumb,
                    fallbackDesc = desc,
                    embedUrl = embedUrl,
                    mobileWatchUrl = mobileWatchUrl,
                    isShort = isShort
                )
            }.getOrNull()
            if (bridgeResult != null && bridgeResult.directH264Url != null) {
                return@withContext bridgeResult
            }
        }

        ResolvedStream(
            videoId = videoId,
            title = title,
            channelName = channelName,
            channelId = channelId,
            thumbnailUrl = thumb,
            description = desc,
            directH264Url = null,
            qualityLabel = settings.qualityPreset.label,
            codecLabel = "Embed + Mobile",
            embedUrl = embedUrl,
            mobileWatchUrl = mobileWatchUrl,
            resolvedVia = if (isShort) "Shorts Direto" else "YouTube Direto",
            isShort = isShort
        )
    }

    private fun resolveViaInnerTube(
        videoId: String,
        maxHeight: Int,
        fallbackTitle: String,
        fallbackChannel: String,
        channelId: String,
        thumb: String,
        fallbackDesc: String,
        embedUrl: String,
        mobileWatchUrl: String,
        isShort: Boolean
    ): ResolvedStream? {
        val clients = listOf(
            Triple("ANDROID_VR", "1.60.19", "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; Quest 3 Build/SQ3A.220605.009.A1) gzip"),
            Triple("IOS", "19.45.4", "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"),
            Triple("ANDROID_TESTSUITE", "1.9", "com.google.android.youtube/1.9 (Linux; U; Android 11) gzip")
        )

        for ((clientName, clientVersion, userAgent) in clients) {
            val payload = JSONObject().apply {
                put("videoId", videoId)
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", clientName)
                        put("clientVersion", clientVersion)
                        put("hl", "pt")
                        put("gl", "BR")
                    })
                })
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }.toString()

            val req = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", userAgent)
                .header("Content-Type", "application/json")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .build()

            val body = runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
            }.getOrNull() ?: continue

            val json = runCatching { JSONObject(body) }.getOrNull() ?: continue
            val status = json.optJSONObject("playabilityStatus")?.optString("status") ?: ""
            if (status != "OK") continue

            val formats = json.optJSONObject("streamingData")?.optJSONArray("formats") ?: continue
            val picked = pickBestProgressiveH264FromInnerTube(formats, maxHeight) ?: continue
            val details = json.optJSONObject("videoDetails")

            return ResolvedStream(
                videoId = videoId,
                title = details?.optString("title")?.ifBlank { fallbackTitle } ?: fallbackTitle,
                channelName = details?.optString("author")?.ifBlank { fallbackChannel } ?: fallbackChannel,
                channelId = details?.optString("channelId")?.ifBlank { channelId } ?: channelId,
                thumbnailUrl = thumb,
                description = details?.optString("shortDescription")?.ifBlank { fallbackDesc } ?: fallbackDesc,
                directH264Url = picked.first,
                qualityLabel = picked.second,
                codecLabel = "H.264 / MP4 (Hardware)",
                embedUrl = embedUrl,
                mobileWatchUrl = mobileWatchUrl,
                resolvedVia = "Stream Direto ($clientName)",
                isShort = isShort
            )
        }
        return null
    }

    private fun pickBestProgressiveH264FromInnerTube(
        formats: JSONArray,
        maxHeight: Int
    ): Pair<String, String>? {
        data class Candidate(val url: String, val height: Int, val quality: String)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until formats.length()) {
            val f = formats.optJSONObject(i) ?: continue
            val url = f.optString("url")
            if (url.isBlank()) continue
            val mime = f.optString("mimeType").lowercase()
            if (!mime.contains("video/mp4")) continue
            val height = f.optInt("height", 360)
            val label = f.optString("qualityLabel").ifBlank { "${height}p" }
            candidates.add(Candidate(url, height, label))
        }
        if (candidates.isEmpty()) return null
        val withinCap = candidates.filter { it.height <= maxHeight }.maxByOrNull { it.height }
        val chosen = withinCap ?: candidates.minByOrNull { it.height } ?: return null
        return chosen.url to chosen.quality
    }

    private fun resolveFromBridge(
        bridgeUrl: String,
        videoId: String,
        maxHeight: Int,
        fallbackTitle: String,
        fallbackChannel: String,
        channelId: String,
        thumb: String,
        fallbackDesc: String,
        embedUrl: String,
        mobileWatchUrl: String,
        isShort: Boolean
    ): ResolvedStream? {
        val invBody = httpGet("$bridgeUrl/api/v1/videos/$videoId")
        if (invBody != null && invBody.trimStart().startsWith("{")) {
            val json = JSONObject(invBody)
            val formatStreams = json.optJSONArray("formatStreams")
            if (formatStreams != null) {
                val picked = pickBestProgressiveH264FromInvidious(formatStreams, maxHeight)
                if (picked != null) {
                    return ResolvedStream(
                        videoId = videoId,
                        title = json.optString("title", fallbackTitle).ifBlank { fallbackTitle },
                        channelName = json.optString("author", fallbackChannel).ifBlank { fallbackChannel },
                        channelId = channelId,
                        thumbnailUrl = thumb,
                        description = json.optString("description", fallbackDesc).ifBlank { fallbackDesc },
                        directH264Url = picked.first,
                        qualityLabel = picked.second,
                        codecLabel = "H.264 / MP4 (Hardware)",
                        embedUrl = embedUrl,
                        mobileWatchUrl = mobileWatchUrl,
                        resolvedVia = bridgeUrl.removePrefix("https://"),
                        isShort = isShort
                    )
                }
            }
        }

        val pipedBody = httpGet("$bridgeUrl/streams/$videoId")
        if (pipedBody != null && pipedBody.trimStart().startsWith("{")) {
            val json = JSONObject(pipedBody)
            val streams = json.optJSONArray("videoStreams")
            if (streams != null) {
                val picked = pickBestProgressiveH264FromPiped(streams, maxHeight)
                if (picked != null) {
                    return ResolvedStream(
                        videoId = videoId,
                        title = json.optString("title", fallbackTitle).ifBlank { fallbackTitle },
                        channelName = json.optString("uploader", fallbackChannel).ifBlank { fallbackChannel },
                        channelId = channelId,
                        thumbnailUrl = thumb,
                        description = json.optString("description", fallbackDesc).ifBlank { fallbackDesc },
                        directH264Url = picked.first,
                        qualityLabel = picked.second,
                        codecLabel = "H.264 / AVC (Hardware)",
                        embedUrl = embedUrl,
                        mobileWatchUrl = mobileWatchUrl,
                        resolvedVia = bridgeUrl.removePrefix("https://"),
                        isShort = isShort
                    )
                }
            }
        }
        return null
    }

    private fun pickBestProgressiveH264FromPiped(
        streams: JSONArray,
        maxHeight: Int
    ): Pair<String, String>? {
        data class Candidate(val url: String, val height: Int, val quality: String)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            val videoOnly = s.optBoolean("videoOnly", true)
            val mimeType = s.optString("mimeType").lowercase()
            val codec = s.optString("codec").lowercase()
            val format = s.optString("format").lowercase()
            val url = s.optString("url")
            if (videoOnly || url.isBlank()) continue
            val isH264Mp4 = (mimeType.contains("mp4") || format.contains("mpeg_4")) &&
                (codec.isBlank() || codec.contains("avc"))
            if (!isH264Mp4) continue
            val height = s.optInt("height", 360)
            val quality = s.optString("quality", "${height}p")
            candidates.add(Candidate(url, height, quality))
        }
        if (candidates.isEmpty()) return null
        val withinCap = candidates.filter { it.height <= maxHeight }.maxByOrNull { it.height }
        val chosen = withinCap ?: candidates.minByOrNull { it.height } ?: return null
        return chosen.url to chosen.quality
    }

    private fun pickBestProgressiveH264FromInvidious(
        streams: JSONArray,
        maxHeight: Int
    ): Pair<String, String>? {
        data class Candidate(val url: String, val height: Int, val quality: String)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            val container = s.optString("container").lowercase()
            val url = s.optString("url")
            if (container != "mp4" || url.isBlank()) continue
            val res = s.optString("resolution", "360p")
            val height = res.filter { it.isDigit() }.toIntOrNull() ?: 360
            candidates.add(Candidate(url, height, res))
        }
        if (candidates.isEmpty()) return null
        val withinCap = candidates.filter { it.height <= maxHeight }.maxByOrNull { it.height }
        val chosen = withinCap ?: candidates.minByOrNull { it.height } ?: return null
        return chosen.url to chosen.quality
    }

    suspend fun pingBridgeInstances(customBridgeUrl: String): List<BridgePingResult> =
        withContext(Dispatchers.IO) {
            val targets = (listOf(customBridgeUrl) + defaultBridges)
                .map { it.trim().trimEnd('/') }
                .filter { it.startsWith("https://") }
                .distinct()
            coroutineScope {
                targets.map { base ->
                    async {
                        val start = System.currentTimeMillis()
                        val ok = runCatching {
                            val req = Request.Builder()
                                .url("$base/api/v1/stats")
                                .header("User-Agent", DESKTOP_UA)
                                .build()
                            client.newCall(req).execute().use { resp ->
                                resp.isSuccessful || resp.code in 200..499
                            }
                        }.getOrDefault(false)
                        val elapsed = System.currentTimeMillis() - start
                        BridgePingResult(
                            baseUrl = base,
                            reachable = ok,
                            latencyMs = elapsed,
                            note = if (ok) "Online (${elapsed} ms)" else "Indisponível / Timeout"
                        )
                    }
                }.awaitAll()
            }
        }

    private fun httpGet(url: String, mobileUa: Boolean = true): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", if (mobileUa) MOBILE_UA else DESKTOP_UA)
            .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
            .header("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+999; SOCS=CAI")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()
        }
    }

    private fun extractBalancedJsonObject(html: String, symbol: String): String? {
        val idx = html.indexOf(symbol)
        if (idx == -1) return null
        val braceStart = html.indexOf('{', idx)
        if (braceStart == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in braceStart until html.length) {
            val c = html[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return html.substring(braceStart, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun readRunsText(obj: JSONObject?): String {
        if (obj == null) return ""
        val simple = obj.optString("simpleText")
        if (simple.isNotBlank()) return simple
        val runs = obj.optJSONArray("runs") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.optString("text") ?: "")
        }
        return sb.toString()
    }

    private fun formatIsoDate(iso: String): String {
        if (iso.length < 10) return iso
        val datePart = iso.substring(0, 10)
        val parts = datePart.split("-")
        return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else datePart
    }

    private fun formatViews(raw: String): String {
        val count = raw.toLongOrNull() ?: return ""
        return when {
            count >= 1_000_000 -> String.format("%.1fM visualizações", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.0fK visualizações", count / 1_000.0)
            else -> "$count visualizações"
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    companion object {
        private const val DESKTOP_UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 7.0; SM-T560) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        fun isSafeBridgeUrl(rawUrl: String): Boolean {
            val trimmed = rawUrl.trim().trimEnd('/')
            if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return false
            val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return false
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            if (!uri.userInfo.isNullOrEmpty()) return false
            val host = uri.host?.lowercase() ?: return false
            if (host.isBlank() || host == "localhost" || host.startsWith("127.") || host.startsWith("10.") || host.startsWith("192.168.")) {
                return false
            }
            return host.contains(".")
        }

        fun normalizeChannelTargetUrl(rawInput: String): String? {
            val trimmed = rawInput.trim()
            if (trimmed.isBlank() || trimmed.any { it.isWhitespace() }) return null
            if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
                if (!uri.scheme.equals("https", ignoreCase = true)) return null
                if (!uri.userInfo.isNullOrEmpty()) return null
                val host = uri.host?.lowercase()?.trimEnd('.') ?: return null
                val allowed = host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
                return if (allowed) trimmed else null
            }
            val cleanHandle = trimmed.removePrefix("@")
            if (!Regex("^[A-Za-z0-9_.-]{2,64}$").matches(cleanHandle)) return null
            return "https://www.youtube.com/@$cleanHandle"
        }
    }
}
