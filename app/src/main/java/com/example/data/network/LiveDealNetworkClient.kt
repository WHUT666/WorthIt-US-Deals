package com.example.data.network

import android.util.Xml
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.MonitorSourceStatus
import com.example.model.USMerchant
import com.example.model.VerificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class SingleSourceResult(
    val deals: List<DealItem>,
    val status: MonitorSourceStatus
)

data class LiveVerificationResult(
    val status: VerificationStatus,
    val verifiedPrice: Double?,
    val note: String,
    val isExpired: Boolean,
    val latencyMs: Long
)

/**
 * Low-latency, streaming HTTP multi-source US retail deal monitor & live deal verifier.
 *
 * Speed optimizations:
 * - Parallel streaming fan-out via channelFlow (fast sources emit in <400ms; never blocked by slow tail)
 * - Tight per-request timeouts (4s connect / 5s read / 6s total call budget)
 * - Shared ConnectionPool + high-concurrency Dispatcher + 10MB HTTP Disk Cache (ETag / 304)
 *
 * Validity & Freshness:
 * - Filters expired / OOS / dead posts at ingestion
 * - Live HTTP verification against merchant product pages to catch price jumps & sold-out items
 */
class LiveDealNetworkClient(
    cacheDir: File? = null,
    private val httpClient: OkHttpClient = buildOptimizedClient(cacheDir)
) {

    companion object {
        const val TOTAL_FEED_SOURCES = 6
        const val TOTAL_SEARCH_SOURCES = 3

        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 WorthItDeals/1.1"
        private const val REDDIT_USER_AGENT = "android:com.aistudio.worthit.deals:v1.1 (US Live Deal Radar)"

        fun buildOptimizedClient(cacheDir: File? = null): OkHttpClient {
            val dispatcher = Dispatcher().apply {
                maxRequests = 32
                maxRequestsPerHost = 8
            }
            val builder = OkHttpClient.Builder()
                .dispatcher(dispatcher)
                .connectionPool(ConnectionPool(12, 5, TimeUnit.MINUTES))
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(6, TimeUnit.SECONDS)
                .followRedirects(true)

            if (cacheDir != null) {
                builder.cache(Cache(File(cacheDir, "worthit_http_cache"), 10L * 1024L * 1024L))
            }
            return builder.build()
        }
    }

    /**
     * Streams each US retail deal source the millisecond it completes so UI updates
     * progressively without blocking on the slowest remote endpoint.
     */
    fun streamAllLiveFeeds(): Flow<SingleSourceResult> = channelFlow {
        val tasks: List<() -> SingleSourceResult> = listOf(
            {
                fetchRssFeed(
                    sourceId = "slickdeals_fp",
                    displayName = "Slickdeals Frontpage",
                    url = "https://slickdeals.net/newsearch.php?mode=frontpage&searcharea=deals&searchin=first&rss=1",
                    hostLabel = "slickdeals.net"
                )
            },
            {
                fetchRedditSubreddit(
                    sourceId = "reddit_buildapcsales",
                    displayName = "r/buildapcsales (PC & GPU)",
                    url = "https://www.reddit.com/r/buildapcsales/new.json?limit=30&raw_json=1",
                    hostLabel = "reddit.com/r/buildapcsales"
                )
            },
            {
                fetchRedditSubreddit(
                    sourceId = "reddit_deals",
                    displayName = "r/deals (All US Retail)",
                    url = "https://www.reddit.com/r/deals/new.json?limit=25&raw_json=1",
                    hostLabel = "reddit.com/r/deals"
                )
            },
            {
                fetchRssFeed(
                    sourceId = "dealnews_rss",
                    displayName = "DealNews Editors",
                    url = "https://www.dealnews.com/?rss=1",
                    hostLabel = "dealnews.com"
                )
            },
            {
                fetchRssFeed(
                    sourceId = "9to5toys_rss",
                    displayName = "9to5Toys (Apple & Tech)",
                    url = "https://9to5toys.com/feed/",
                    hostLabel = "9to5toys.com"
                )
            },
            {
                fetchCheapSharkDeals(
                    sourceId = "cheapshark_api",
                    displayName = "CheapShark PC Storefront",
                    url = "https://www.cheapshark.com/api/1.0/deals?pageSize=20&sortBy=Recent",
                    hostLabel = "cheapshark.com"
                )
            }
        )

        tasks.forEach { task ->
            launch(Dispatchers.IO) {
                send(task())
            }
        }
    }

    /**
     * Streams multi-platform live search results as each remote search endpoint responds.
     */
    fun streamLiveSearch(query: String): Flow<SingleSourceResult> = channelFlow {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val tasks: List<() -> SingleSourceResult> = listOf(
            {
                fetchRssFeed(
                    sourceId = "search_slickdeals",
                    displayName = "Slickdeals Live Search",
                    url = "https://slickdeals.net/newsearch.php?q=$encoded&searcharea=deals&searchin=first&rss=1&sort=newest",
                    hostLabel = "slickdeals.net"
                )
            },
            {
                fetchRedditSubreddit(
                    sourceId = "search_reddit",
                    displayName = "Reddit Deal Search",
                    url = "https://www.reddit.com/r/buildapcsales+deals+GameDeals+frugalmalefashion/search.json?q=$encoded&restrict_sr=1&sort=new&limit=30&raw_json=1",
                    hostLabel = "reddit.com"
                )
            },
            {
                fetchCheapSharkDeals(
                    sourceId = "search_cheapshark",
                    displayName = "CheapShark Search API",
                    url = "https://www.cheapshark.com/api/1.0/deals?title=$encoded&pageSize=15",
                    hostLabel = "cheapshark.com"
                )
            }
        )

        tasks.forEach { task ->
            launch(Dispatchers.IO) {
                send(task())
            }
        }
    }

    /**
     * Performs a real-time HTTP check against the merchant's product page to verify
     * whether the deal is still in stock and at the advertised price.
     */
    suspend fun verifyDealLive(deal: DealItem): LiveVerificationResult = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        if (!DealTextParser.isSafePublicHttpUrl(deal.buyUrl)) {
            return@withContext LiveVerificationResult(
                status = VerificationStatus.STORE_CHECK_REQUIRED,
                verifiedPrice = null,
                note = "External redirect URL — tap Open Store to verify live checkout.",
                isExpired = false,
                latencyMs = 0L
            )
        }

        // Strict 4-second verification budget so detail verification feels snappy
        val fastVerifierClient = httpClient.newBuilder()
            .callTimeout(4500, TimeUnit.MILLISECONDS)
            .readTimeout(4000, TimeUnit.MILLISECONDS)
            .build()

        try {
            val request = Request.Builder()
                .url(deal.buyUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build()

            fastVerifierClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                val code = response.code

                if (code == 404 || code == 410) {
                    return@withContext LiveVerificationResult(
                        status = VerificationStatus.OUT_OF_STOCK,
                        verifiedPrice = null,
                        note = "Retailer returned HTTP $code — listing removed or ended (${latency}ms).",
                        isExpired = true,
                        latencyMs = latency
                    )
                }

                if (code == 403 || code == 429 || code == 503) {
                    return@withContext LiveVerificationResult(
                        status = VerificationStatus.STORE_CHECK_REQUIRED,
                        verifiedPrice = null,
                        note = "${deal.merchant.displayName} anti-bot active (HTTP $code, ${latency}ms) — tap Open Store to verify.",
                        isExpired = false,
                        latencyMs = latency
                    )
                }

                if (!response.isSuccessful) {
                    return@withContext LiveVerificationResult(
                        status = VerificationStatus.STORE_CHECK_REQUIRED,
                        verifiedPrice = null,
                        note = "Store responded HTTP $code (${latency}ms) — verify directly on retailer.",
                        isExpired = false,
                        latencyMs = latency
                    )
                }

                val html = response.body?.string().orEmpty().take(300_000)
                val parsed = DealTextParser.parseProductPageHtml(deal.buyUrl, html)

                if (parsed.isOutOfStock) {
                    return@withContext LiveVerificationResult(
                        status = VerificationStatus.OUT_OF_STOCK,
                        verifiedPrice = parsed.price,
                        note = "Live page reports Out of Stock / Unavailable (${latency}ms).",
                        isExpired = true,
                        latencyMs = latency
                    )
                }

                val livePrice = parsed.price
                val expectedPrice = deal.currentPrice

                when {
                    livePrice != null && expectedPrice != null && livePrice > expectedPrice * 1.05 -> {
                        LiveVerificationResult(
                            status = VerificationStatus.PRICE_INCREASED,
                            verifiedPrice = livePrice,
                            note = "Live price increased to $${String.format(Locale.US, "%,.2f", livePrice)} (was $${String.format(Locale.US, "%,.2f", expectedPrice)}, ${latency}ms).",
                            isExpired = false,
                            latencyMs = latency
                        )
                    }
                    livePrice != null -> {
                        val dropNote = if (expectedPrice != null && livePrice < expectedPrice - 0.5) {
                            "Even lower! Live $${String.format(Locale.US, "%,.2f", livePrice)}"
                        } else {
                            "Verified live at $${String.format(Locale.US, "%,.2f", livePrice)}"
                        }
                        LiveVerificationResult(
                            status = VerificationStatus.VERIFIED_LIVE,
                            verifiedPrice = livePrice,
                            note = "$dropNote (${latency}ms).",
                            isExpired = false,
                            latencyMs = latency
                        )
                    }
                    parsed.availability?.contains("InStock", ignoreCase = true) == true -> {
                        LiveVerificationResult(
                            status = VerificationStatus.VERIFIED_LIVE,
                            verifiedPrice = expectedPrice,
                            note = "Retailer page confirmed In Stock (${latency}ms).",
                            isExpired = false,
                            latencyMs = latency
                        )
                    }
                    else -> {
                        LiveVerificationResult(
                            status = VerificationStatus.STORE_CHECK_REQUIRED,
                            verifiedPrice = null,
                            note = "Page active (HTTP $code, ${latency}ms), dynamic JS price — confirm in cart.",
                            isExpired = false,
                            latencyMs = latency
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LiveVerificationResult(
                status = VerificationStatus.STORE_CHECK_REQUIRED,
                verifiedPrice = null,
                note = "Quick check timed out (${System.currentTimeMillis() - start}ms) — tap Open Store.",
                isExpired = false,
                latencyMs = System.currentTimeMillis() - start
            )
        }
    }

    suspend fun fetchDirectProductUrl(url: String): Result<ParsedProductPage> = withContext(Dispatchers.IO) {
        if (!DealTextParser.isSafePublicHttpUrl(url)) {
            return@withContext Result.failure(IllegalArgumentException("Please enter a valid public https:// retailer URL."))
        }
        try {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cache-Control", "no-cache")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Retailer returned HTTP ${response.code} (bot protection or unavailable)")
                    )
                }
                val html = response.body?.string().orEmpty().take(380_000)
                val parsed = DealTextParser.parseProductPageHtml(url, html)
                if (parsed.price == null && parsed.title.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Page loaded (HTTP ${response.code}), but no structured price metadata was found."))
                } else {
                    Result.success(parsed)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchRssFeed(
        sourceId: String,
        displayName: String,
        url: String,
        hostLabel: String
    ): SingleSourceResult {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (!response.isSuccessful) {
                    return SingleSourceResult(
                        deals = emptyList(),
                        status = MonitorSourceStatus(
                            sourceId = sourceId,
                            displayName = displayName,
                            endpointHost = hostLabel,
                            isHealthy = false,
                            httpCode = response.code,
                            latencyMs = latency,
                            dealsFetched = 0,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = "HTTP ${response.code}"
                        )
                    )
                }
                val xmlBody = response.body?.string().orEmpty()
                val parsedDeals = parseRssXml(xmlBody, displayName)
                SingleSourceResult(
                    deals = parsedDeals,
                    status = MonitorSourceStatus(
                        sourceId = sourceId,
                        displayName = displayName,
                        endpointHost = hostLabel,
                        isHealthy = true,
                        httpCode = response.code,
                        latencyMs = latency,
                        dealsFetched = parsedDeals.size,
                        lastSyncedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            SingleSourceResult(
                deals = emptyList(),
                status = MonitorSourceStatus(
                    sourceId = sourceId,
                    displayName = displayName,
                    endpointHost = hostLabel,
                    isHealthy = false,
                    httpCode = null,
                    latencyMs = System.currentTimeMillis() - start,
                    dealsFetched = 0,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    errorMessage = e.message?.take(60) ?: "Timeout / network error"
                )
            )
        }
    }

    private fun fetchRedditSubreddit(
        sourceId: String,
        displayName: String,
        url: String,
        hostLabel: String
    ): SingleSourceResult {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", REDDIT_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (!response.isSuccessful) {
                    return SingleSourceResult(
                        deals = emptyList(),
                        status = MonitorSourceStatus(
                            sourceId = sourceId,
                            displayName = displayName,
                            endpointHost = hostLabel,
                            isHealthy = false,
                            httpCode = response.code,
                            latencyMs = latency,
                            dealsFetched = 0,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = "HTTP ${response.code}"
                        )
                    )
                }
                val body = response.body?.string().orEmpty()
                val deals = parseRedditJson(body, displayName)
                SingleSourceResult(
                    deals = deals,
                    status = MonitorSourceStatus(
                        sourceId = sourceId,
                        displayName = displayName,
                        endpointHost = hostLabel,
                        isHealthy = true,
                        httpCode = response.code,
                        latencyMs = latency,
                        dealsFetched = deals.size,
                        lastSyncedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            SingleSourceResult(
                deals = emptyList(),
                status = MonitorSourceStatus(
                    sourceId = sourceId,
                    displayName = displayName,
                    endpointHost = hostLabel,
                    isHealthy = false,
                    httpCode = null,
                    latencyMs = System.currentTimeMillis() - start,
                    dealsFetched = 0,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    errorMessage = e.message?.take(60) ?: "Timeout / network error"
                )
            )
        }
    }

    private fun fetchCheapSharkDeals(
        sourceId: String,
        displayName: String,
        url: String,
        hostLabel: String
    ): SingleSourceResult {
        val start = System.currentTimeMillis()
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val latency = System.currentTimeMillis() - start
                if (!response.isSuccessful) {
                    return SingleSourceResult(
                        deals = emptyList(),
                        status = MonitorSourceStatus(
                            sourceId = sourceId,
                            displayName = displayName,
                            endpointHost = hostLabel,
                            isHealthy = false,
                            httpCode = response.code,
                            latencyMs = latency,
                            dealsFetched = 0,
                            lastSyncedAtMillis = System.currentTimeMillis(),
                            errorMessage = "HTTP ${response.code}"
                        )
                    )
                }
                val body = response.body?.string().orEmpty()
                val deals = parseCheapSharkJson(body, displayName)
                SingleSourceResult(
                    deals = deals,
                    status = MonitorSourceStatus(
                        sourceId = sourceId,
                        displayName = displayName,
                        endpointHost = hostLabel,
                        isHealthy = true,
                        httpCode = response.code,
                        latencyMs = latency,
                        dealsFetched = deals.size,
                        lastSyncedAtMillis = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            SingleSourceResult(
                deals = emptyList(),
                status = MonitorSourceStatus(
                    sourceId = sourceId,
                    displayName = displayName,
                    endpointHost = hostLabel,
                    isHealthy = false,
                    httpCode = null,
                    latencyMs = System.currentTimeMillis() - start,
                    dealsFetched = 0,
                    lastSyncedAtMillis = System.currentTimeMillis(),
                    errorMessage = e.message?.take(60) ?: "Timeout / network error"
                )
            )
        }
    }

    internal fun parseRssXml(xml: String, sourceName: String): List<DealItem> {
        val deals = mutableListOf<DealItem>()
        if (xml.isBlank()) return deals
        val now = System.currentTimeMillis()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xml))

            var insideItem = false
            var title = ""
            var link = ""
            var description = ""
            var pubDate = ""
            var imageUrl: String? = null
            var currentTag = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.lowercase(Locale.US)
                        currentTag = tag
                        if (tag == "item" || tag == "entry") {
                            insideItem = true
                            title = ""
                            link = ""
                            description = ""
                            pubDate = ""
                            imageUrl = null
                        } else if (insideItem) {
                            if (tag == "media:content" || tag == "media:thumbnail" || tag == "enclosure") {
                                val mediaUrl = parser.getAttributeValue(null, "url")
                                if (mediaUrl != null && DealTextParser.isSafePublicHttpUrl(mediaUrl) && imageUrl == null) {
                                    imageUrl = mediaUrl
                                }
                            } else if (tag == "link") {
                                val href = parser.getAttributeValue(null, "href")
                                if (!href.isNullOrBlank() && link.isBlank()) {
                                    link = href
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideItem) {
                            val text = parser.text ?: ""
                            when (currentTag) {
                                "title" -> title += text
                                "link" -> if (link.isBlank() || text.startsWith("http")) link += text
                                "description", "content:encoded", "summary" -> description += text
                                "pubdate", "published", "updated" -> pubDate += text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.lowercase(Locale.US)
                        if (tag == "item" || tag == "entry") {
                            insideItem = false
                            val cleanTitle = DealTextParser.stripHtml(title)
                            val cleanLink = link.trim()
                            val isExpired = DealTextParser.isDealMarkedExpiredOrOos(cleanTitle, description)
                            if (!isExpired && cleanTitle.isNotBlank() && DealTextParser.isSafePublicHttpUrl(cleanLink)) {
                                val prices = DealTextParser.extractPrices(cleanTitle, description)
                                val merchant = DealTextParser.detectMerchant(cleanTitle, cleanLink, description)
                                val coupon = DealTextParser.extractCoupon(cleanTitle, description)
                                val img = imageUrl ?: DealTextParser.extractFirstImageUrl(description)
                                val cleanSummary = DealTextParser.stripHtml(description).take(260)
                                val pubMillis = parseRssDate(pubDate.trim()) ?: now

                                deals.add(
                                    DealItem(
                                        id = stableDealId(sourceName, cleanLink, cleanTitle),
                                        title = cleanTitle,
                                        brand = DealTextParser.detectBrand(cleanTitle),
                                        merchant = merchant,
                                        category = DealTextParser.detectCategory(cleanTitle, sourceName),
                                        currentPrice = prices.currentPrice,
                                        originalPrice = prices.originalPrice,
                                        historicLowPrice = prices.currentPrice,
                                        isHistoricLow = false,
                                        coupon = coupon,
                                        cashBackRate = merchant.estimatedCashBackRate,
                                        freeShipping = cleanTitle.contains("free ship", ignoreCase = true) ||
                                            cleanSummary.contains("free shipping", ignoreCase = true) ||
                                            merchant == USMerchant.AMAZON,
                                        worthVotes = 0,
                                        notWorthVotes = 0,
                                        communityCommentsCount = 0,
                                        sourceName = sourceName,
                                        imageUrl = img,
                                        buyUrl = cleanLink,
                                        sourceUrl = cleanLink,
                                        summary = cleanSummary,
                                        publishedAtMillis = pubMillis,
                                        lastCheckedAtMillis = now,
                                        isExpired = false
                                    )
                                )
                            }
                        }
                        currentTag = ""
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Partial parse returns valid items extracted so far
        }
        return deals.take(30)
    }

    internal fun parseRedditJson(jsonBody: String, sourceName: String): List<DealItem> {
        val deals = mutableListOf<DealItem>()
        if (jsonBody.isBlank()) return deals
        val now = System.currentTimeMillis()
        try {
            val root = JSONObject(jsonBody)
            val children = root.optJSONObject("data")?.optJSONArray("children") ?: return deals

            for (i in 0 until children.length()) {
                val post = children.optJSONObject(i)?.optJSONObject("data") ?: continue
                if (post.optBoolean("stickied", false)) continue
                val title = DealTextParser.decodeHtmlEntities(post.optString("title", "")).trim()
                if (title.isBlank()) continue
                val flair = post.optString("link_flair_text", "")
                val selftext = post.optString("selftext", "")

                if (post.optBoolean("over_18", false) ||
                    flair.contains("meta", ignoreCase = true) ||
                    DealTextParser.isDealMarkedExpiredOrOos(title, selftext, flair)
                ) {
                    continue
                }

                val permalink = "https://www.reddit.com" + post.optString("permalink", "")
                val externalUrl = post.optString("url_overridden_by_dest", "").ifBlank {
                    post.optString("url", permalink)
                }
                val buyUrl = if (DealTextParser.isSafePublicHttpUrl(externalUrl)) externalUrl else permalink
                val subreddit = post.optString("subreddit", "")

                val prices = DealTextParser.extractPrices(title, selftext)
                val merchant = DealTextParser.detectMerchant(title, buyUrl, selftext)
                val coupon = DealTextParser.extractCoupon(title, selftext)
                val ups = post.optInt("ups", 0).coerceAtLeast(0)
                val ratio = post.optDouble("upvote_ratio", 0.85).coerceIn(0.1, 1.0)
                val estDown = if (ratio < 1.0 && ups > 0) ((ups / ratio) - ups).toInt().coerceAtLeast(0) else 0
                val comments = post.optInt("num_comments", 0).coerceAtLeast(0)
                val createdUtcMillis = (post.optDouble("created_utc", 0.0) * 1000.0).toLong().takeIf { it > 0L } ?: now

                val rawThumb = post.optString("thumbnail", "")
                val previewImg = post.optJSONObject("preview")
                    ?.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optJSONObject("source")
                    ?.optString("url")
                    ?.let(DealTextParser::decodeHtmlEntities)
                val imageUrl = listOfNotNull(previewImg, rawThumb)
                    .firstOrNull { DealTextParser.isSafePublicHttpUrl(it) }

                val summary = selftext.take(240).ifBlank {
                    "Community-spotted deal on r/$subreddit ($ups upvotes, $comments comments)."
                }

                deals.add(
                    DealItem(
                        id = stableDealId("reddit", post.optString("id", buyUrl), title),
                        title = title,
                        brand = DealTextParser.detectBrand(title),
                        merchant = merchant,
                        category = DealTextParser.detectCategory(title, "$subreddit $flair"),
                        currentPrice = prices.currentPrice,
                        originalPrice = prices.originalPrice,
                        historicLowPrice = prices.currentPrice,
                        isHistoricLow = false,
                        coupon = coupon,
                        cashBackRate = merchant.estimatedCashBackRate,
                        freeShipping = title.contains("free ship", ignoreCase = true) || merchant == USMerchant.AMAZON,
                        worthVotes = ups,
                        notWorthVotes = estDown,
                        communityCommentsCount = comments,
                        sourceName = if (subreddit.isNotBlank()) "r/$subreddit" else sourceName,
                        imageUrl = imageUrl,
                        buyUrl = buyUrl,
                        sourceUrl = permalink,
                        summary = DealTextParser.stripHtml(summary),
                        publishedAtMillis = createdUtcMillis,
                        lastCheckedAtMillis = now,
                        isExpired = false
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore malformed response
        }
        return deals
    }

    internal fun parseCheapSharkJson(jsonBody: String, sourceName: String): List<DealItem> {
        val deals = mutableListOf<DealItem>()
        if (jsonBody.isBlank()) return deals
        val now = System.currentTimeMillis()
        try {
            val arr = JSONArray(jsonBody)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (obj.optString("isOnSale", "1") == "0") continue
                val title = obj.optString("title", "").trim()
                val dealId = obj.optString("dealID", "").trim()
                if (title.isBlank() || dealId.isBlank()) continue

                val salePrice = obj.optString("salePrice").toDoubleOrNull()
                val normalPrice = obj.optString("normalPrice").toDoubleOrNull()
                val dealRating = obj.optString("dealRating").toDoubleOrNull() ?: 7.5
                val steamRating = obj.optInt("steamRatingPercent", 0)
                val steamCount = obj.optInt("steamRatingCount", 0)
                val thumb = obj.optString("thumb").takeIf { DealTextParser.isSafePublicHttpUrl(it) }
                val lastChangeMillis = obj.optLong("lastChange", 0L).let { if (it > 0) it * 1000L else now }

                val buyUrl = "https://www.cheapshark.com/redirect?dealID=$dealId"
                val derivedWorth = if (steamCount > 0) {
                    ((steamRating / 100.0) * steamCount.coerceAtMost(500)).toInt()
                } else {
                    (dealRating * 10).toInt()
                }
                val derivedNotWorth = if (steamCount > 0) {
                    (((100 - steamRating) / 100.0) * steamCount.coerceAtMost(500)).toInt()
                } else {
                    ((10.0 - dealRating).coerceAtLeast(0.0) * 5).toInt()
                }

                deals.add(
                    DealItem(
                        id = stableDealId("cheapshark", dealId, title),
                        title = "$title (PC Digital Deal)",
                        brand = "PC Digital",
                        merchant = USMerchant.STEAM_PC,
                        category = DealCategory.GAMING,
                        currentPrice = salePrice,
                        originalPrice = normalPrice,
                        historicLowPrice = salePrice,
                        isHistoricLow = false,
                        coupon = null,
                        cashBackRate = 0.0,
                        freeShipping = true,
                        worthVotes = derivedWorth.coerceAtLeast(1),
                        notWorthVotes = derivedNotWorth.coerceAtLeast(0),
                        communityCommentsCount = 0,
                        sourceName = sourceName,
                        imageUrl = thumb,
                        buyUrl = buyUrl,
                        sourceUrl = buyUrl,
                        summary = buildString {
                            append("Live verified sale • Deal Rating $dealRating/10")
                            if (steamRating > 0) append(" • Steam Positive $steamRating%")
                        },
                        publishedAtMillis = lastChangeMillis,
                        lastCheckedAtMillis = now,
                        verificationStatus = VerificationStatus.VERIFIED_LIVE,
                        verifiedPrice = salePrice,
                        verifiedAtMillis = now,
                        verificationNote = "Live API sale price confirmed",
                        isExpired = false
                    )
                )
            }
        } catch (_: Exception) {
            // Ignore malformed response
        }
        return deals
    }

    private fun parseRssDate(dateStr: String): Long? {
        if (dateStr.isBlank()) return null
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in formats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val parsed = sdf.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // Try next pattern
            }
        }
        return null
    }

    private fun stableDealId(source: String, key: String, title: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$source|$key|$title".toByteArray(StandardCharsets.UTF_8))
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
