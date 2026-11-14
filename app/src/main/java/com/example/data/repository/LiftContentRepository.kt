package com.example.data.repository

import com.example.data.model.LiftFetchResult
import com.example.data.model.LiftMediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LiftContentRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build(),
) {

    suspend fun fetchFromLift(rawUrl: String): Result<LiftFetchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = normalizeUrl(rawUrl)
            if (isDirectMediaStream(normalizedUrl)) {
                val uri = URI(normalizedUrl)
                val title = uri.path.substringAfterLast('/').ifBlank { "Lift Stream" }
                val domain = uri.host ?: "lift"
                val item = LiftMediaItem(
                    id = hashId(normalizedUrl),
                    title = title,
                    description = "Direct playable media stream from $domain",
                    sourcePageUrl = normalizedUrl,
                    streamUrl = normalizedUrl,
                    thumbnailUrl = "",
                    sourceDomain = domain,
                    mediaType = if (normalizedUrl.contains(".m3u8", ignoreCase = true)) "hls" else "video",
                )
                return@runCatching LiftFetchResult(
                    requestedUrl = normalizedUrl,
                    finalUrl = normalizedUrl,
                    pageTitle = title,
                    siteDescription = "Direct media stream",
                    primaryStreamUrl = normalizedUrl,
                    items = listOf(item),
                    detectedStreamUrls = listOf(normalizedUrl),
                )
            }

            val request = Request.Builder()
                .url(normalizedUrl)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,application/json;q=0.8,*/*;q=0.7")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Lift website returned HTTP ${response.code}")
                }
                val finalUrl = response.request.url.toString()
                val contentType = response.header("Content-Type").orEmpty()
                val bodyString = response.body?.string().orEmpty()

                if (contentType.contains("json", ignoreCase = true) || bodyString.trimStart().startsWith("{") || bodyString.trimStart().startsWith("[")) {
                    return@runCatching parseJsonFeed(normalizedUrl, finalUrl, bodyString)
                }

                parseHtmlPage(normalizedUrl, finalUrl, bodyString)
            }
        }
    }

    private fun parseHtmlPage(
        requestedUrl: String,
        finalUrl: String,
        html: String,
    ): LiftFetchResult {
        val doc: Document = Jsoup.parse(html, finalUrl)
        val domain = runCatching { URI(finalUrl).host?.removePrefix("www.") ?: "lift" }.getOrDefault("lift")

        val ogTitle = doc.select("meta[property=og:title], meta[name=twitter:title]").attr("content").trim()
        val pageTitle = ogTitle.ifBlank { doc.title().trim() }.ifBlank { domain }
        val ogDesc = doc.select("meta[property=og:description], meta[name=description]").attr("content").trim()
        val ogImage = resolveUrl(finalUrl, doc.select("meta[property=og:image], meta[name=twitter:image]").attr("content").trim())

        val detectedStreams = linkedSetOf<String>()

        // 1. OpenGraph & Twitter player stream tags
        listOf(
            "meta[property=og:video]",
            "meta[property=og:video:url]",
            "meta[property=og:video:secure_url]",
            "meta[name=twitter:player:stream]",
        ).forEach { selector ->
            doc.select(selector).forEach { el ->
                val url = resolveUrl(finalUrl, el.attr("content").trim())
                if (url.isNotBlank() && isLikelyMediaOrEmbed(url)) {
                    detectedStreams.add(url)
                }
            }
        }

        // 2. HTML5 <video> and <source> tags
        doc.select("video, video source").forEach { el ->
            val src = resolveUrl(finalUrl, el.attr("abs:src").ifBlank { el.attr("data-src") })
            if (src.isNotBlank()) {
                detectedStreams.add(src)
            }
        }

        // 3. JSON-LD VideoObject / Movie metadata
        val jsonLdItems = mutableListOf<LiftMediaItem>()
        doc.select("script[type=application/ld+json]").forEach { script ->
            val raw = script.data().trim()
            extractFromJsonLd(raw, finalUrl, domain, detectedStreams, jsonLdItems)
        }

        // 4. Regex scan for direct HLS/DASH/MP4 URLs inside HTML / script payloads
        STREAM_URL_REGEX.findAll(html).forEach { match ->
            val rawFound = match.value.replace("\\/", "/")
            val cleaned = resolveUrl(finalUrl, rawFound)
            if (isDirectMediaStream(cleaned)) {
                detectedStreams.add(cleaned)
            }
        }

        val items = mutableListOf<LiftMediaItem>()
        val primaryStream = detectedStreams.firstOrNull { isDirectMediaStream(it) }
            ?: detectedStreams.firstOrNull().orEmpty()

        if (primaryStream.isNotBlank()) {
            items += LiftMediaItem(
                id = hashId("$finalUrl#$primaryStream"),
                title = pageTitle,
                description = ogDesc.ifBlank { "Extracted directly from $domain" },
                sourcePageUrl = finalUrl,
                streamUrl = primaryStream,
                thumbnailUrl = ogImage,
                sourceDomain = domain,
                mediaType = if (primaryStream.contains(".m3u8", ignoreCase = true)) "hls" else "video",
            )
        }

        items += jsonLdItems

        // 5. Extract linked Lift media/feature cards from page markup
        val seenUrls = items.map { it.sourcePageUrl }.toMutableSet()
        val cardCandidates = doc.select("article a[href], .card a[href], a[href*=/watch], a[href*=/video], a[href*=/videos], a[href*=/movie], a[href*=/show], a[href]")
        for (anchor in cardCandidates) {
            if (items.size >= 24) break
            val href = anchor.absUrl("href").trim()
            if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("#")) continue
            if (href in seenUrls) continue

            val imgEl = anchor.selectFirst("img") ?: anchor.parent()?.selectFirst("img")
            val thumb = imgEl?.let {
                resolveUrl(finalUrl, it.attr("abs:src").ifBlank { it.attr("data-src") })
            }.orEmpty()

            val heading = anchor.selectFirst("h1, h2, h3, h4, .title")?.text()?.trim()
                ?: anchor.attr("title").trim()
                ?: anchor.text().trim()
            val cleanTitle = heading.ifBlank { anchor.text().trim() }

            if (cleanTitle.length < 3 || cleanTitle.length > 140) continue
            val isMediaHref = href.contains("/watch", true) ||
                href.contains("/video", true) ||
                href.contains("/film", true) ||
                href.contains("/movie", true) ||
                href.contains("/episode", true) ||
                isDirectMediaStream(href)
            if (!isMediaHref && thumb.isBlank()) continue

            seenUrls.add(href)
            items += LiftMediaItem(
                id = hashId(href),
                title = cleanTitle,
                description = anchor.parent()?.selectFirst("p")?.text()?.trim()
                    ?.take(220)
                    ?.ifBlank { "Page on $domain" }
                    ?: "Page on $domain",
                sourcePageUrl = href,
                streamUrl = if (isDirectMediaStream(href)) href else "",
                thumbnailUrl = thumb,
                sourceDomain = domain,
                mediaType = if (isDirectMediaStream(href)) "video" else "page",
            )
        }

        return LiftFetchResult(
            requestedUrl = requestedUrl,
            finalUrl = finalUrl,
            pageTitle = pageTitle,
            siteDescription = ogDesc,
            primaryStreamUrl = primaryStream,
            items = items.distinctBy { it.sourcePageUrl + "|" + it.streamUrl },
            detectedStreamUrls = detectedStreams.toList(),
        )
    }

    private fun parseJsonFeed(
        requestedUrl: String,
        finalUrl: String,
        jsonText: String,
    ): LiftFetchResult {
        val domain = runCatching { URI(finalUrl).host?.removePrefix("www.") ?: "lift" }.getOrDefault("lift")
        val items = mutableListOf<LiftMediaItem>()
        val streams = linkedSetOf<String>()

        fun inspectJsonObject(obj: JSONObject) {
            val title = obj.optString("title").ifBlank { obj.optString("name") }
            val desc = obj.optString("description").ifBlank { obj.optString("summary") }
            val stream = obj.optString("streamUrl")
                .ifBlank { obj.optString("contentUrl") }
                .ifBlank { obj.optString("videoUrl") }
                .ifBlank { obj.optString("url").takeIf { isDirectMediaStream(it) }.orEmpty() }
            val page = obj.optString("permalink")
                .ifBlank { obj.optString("pageUrl") }
                .ifBlank { obj.optString("url") }
                .ifBlank { finalUrl }
            val thumb = obj.optString("thumbnailUrl")
                .ifBlank { obj.optString("poster") }
                .ifBlank { obj.optString("image") }

            if (title.isNotBlank() && (stream.isNotBlank() || page.isNotBlank())) {
                val resolvedStream = resolveUrl(finalUrl, stream)
                if (resolvedStream.isNotBlank()) streams.add(resolvedStream)
                items += LiftMediaItem(
                    id = hashId("$page|$resolvedStream|$title"),
                    title = title.take(140),
                    description = desc.take(300).ifBlank { "Fetched from $domain" },
                    sourcePageUrl = resolveUrl(finalUrl, page),
                    streamUrl = resolvedStream,
                    thumbnailUrl = resolveUrl(finalUrl, thumb),
                    sourceDomain = domain,
                    mediaType = if (resolvedStream.contains(".m3u8", true)) "hls" else "video",
                )
            }

            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val child = obj.opt(key)) {
                    is JSONObject -> inspectJsonObject(child)
                    is JSONArray -> {
                        for (i in 0 until child.length()) {
                            (child.opt(i) as? JSONObject)?.let { inspectJsonObject(it) }
                        }
                    }
                }
            }
        }

        val trimmed = jsonText.trim()
        if (trimmed.startsWith("{")) {
            inspectJsonObject(JSONObject(trimmed))
        } else if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                (arr.opt(i) as? JSONObject)?.let { inspectJsonObject(it) }
            }
        }

        return LiftFetchResult(
            requestedUrl = requestedUrl,
            finalUrl = finalUrl,
            pageTitle = domain,
            siteDescription = "JSON media feed from $domain",
            primaryStreamUrl = streams.firstOrNull().orEmpty(),
            items = items.distinctBy { it.id }.take(30),
            detectedStreamUrls = streams.toList(),
        )
    }

    private fun extractFromJsonLd(
        raw: String,
        baseUrl: String,
        domain: String,
        detectedStreams: MutableSet<String>,
        outItems: MutableList<LiftMediaItem>,
    ) {
        if (raw.isBlank()) return
        runCatching {
            val objects = mutableListOf<JSONObject>()
            if (raw.startsWith("[")) {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { objects.add(it) }
                }
            } else if (raw.startsWith("{")) {
                objects.add(JSONObject(raw))
            }

            for (obj in objects) {
                val contentUrl = resolveUrl(baseUrl, obj.optString("contentUrl"))
                val embedUrl = resolveUrl(baseUrl, obj.optString("embedUrl"))
                val stream = contentUrl.ifBlank { embedUrl.takeIf { isDirectMediaStream(it) }.orEmpty() }
                if (stream.isNotBlank()) {
                    detectedStreams.add(stream)
                    val title = obj.optString("name").ifBlank { "Lift Video" }
                    val desc = obj.optString("description").ifBlank { "Video on $domain" }
                    val thumb = resolveUrl(baseUrl, obj.optString("thumbnailUrl"))
                    val page = resolveUrl(baseUrl, obj.optString("url")).ifBlank { baseUrl }
                    outItems += LiftMediaItem(
                        id = hashId("$page#$stream"),
                        title = title.take(140),
                        description = desc.take(300),
                        sourcePageUrl = page,
                        streamUrl = stream,
                        thumbnailUrl = thumb,
                        sourceDomain = domain,
                        mediaType = if (stream.contains(".m3u8", true)) "hls" else "video",
                    )
                }
            }
        }
    }

    fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Please enter a Lift website or media URL." }
        return if (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        val clean = candidate.trim()
        if (clean.isBlank()) return ""
        return runCatching {
            URI(baseUrl).resolve(clean).toString()
        }.getOrDefault(clean)
    }

    fun isDirectMediaStream(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
            lower.contains(".mpd") ||
            lower.contains(".mp4") ||
            lower.contains(".webm") ||
            lower.contains(".mkv")
    }

    private fun isLikelyMediaOrEmbed(url: String): Boolean {
        return isDirectMediaStream(url) || url.startsWith("http", ignoreCase = true)
    }

    private fun hashId(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val STREAM_URL_REGEX = Regex(
            """https?:\\?/\\?/[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|webm)(?:\?[^\s"'<>\\]*)?""",
            RegexOption.IGNORE_CASE,
        )
    }
}
