package com.example.data.network

import android.util.Xml
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.MonitorSourceStatus
import com.example.model.USMerchant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class FeedBatchResult(
    val deals: List<DealItem>,
    val sourceStatuses: List<MonitorSourceStatus>
)

data class SingleSourceResult(
    val deals: List<DealItem>,
    val status: MonitorSourceStatus
)

/**
 * Real HTTP multi-source US retail deal monitor & live search engine.
 * Concurrently fetches live RSS/JSON feeds from Slickdeals, Reddit (/r/buildapcsales,
 * /r/deals, /r/GameDeals), DealNews, 9to5Toys, and CheapShark, plus direct retailer product URLs.
 */
class LiveDealNetworkClient(
    private val httpClient: OkHttpClient = defaultOkHttpClient()
) {

    companion object {
        private const val BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36 WorthItDeals/1.0"
        private const val REDDIT_USER_AGENT = "android:com.aistudio.worthit.deals:v1.0 (US Deal Radar)"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Fetches the latest live US retail deals across all monitored sources in parallel.
     */
    suspend fun fetchAllLiveFeeds(): FeedBatchResult = coroutineScope {
        val jobs = listOf(
            async(Dispatchers.IO) {
                fetchRssFeed(
                    sourceId = "slickdeals_fp",
                    displayName = "Slickdeals Frontpage",
                    url = "https://slickdeals.net/newsearch.php?mode=frontpage&searcharea=deals&searchin=first&rss=1",
                    hostLabel = "slickdeals.net"
                )
            },
            async(Dispatchers.IO) {
                fetchRedditSubreddit(
                    sourceId = "reddit_buildapcsales",
                    displayName = "r/buildapcsales (PC & GPU)",
                    url = "https://www.reddit.com/r/buildapcsales/new.json?limit=30&raw_json=1",
                    hostLabel = "reddit.com/r/buildapcsales"
                )
            },
            async(Dispatchers.IO) {
                fetchRedditSubreddit(
                    sourceId = "reddit_deals",
                    displayName = "r/deals (All US Retail)",
                    url = "https://www.reddit.com/r/deals/hot.json?limit=25&raw_json=1",
                    hostLabel = "reddit.com/r/deals"
                )
            },
            async(Dispatchers.IO) {
                fetchRssFeed(
                    sourceId = "dealnews_rss",
                    displayName = "DealNews Editors",
                    url = "https://www.dealnews.com/?rss=1",
                    hostLabel = "dealnews.com"
                )
            },
            async(Dispatchers.IO) {
                fetchRssFeed(
                    sourceId = "9to5toys_rss",
                    displayName = "9to5Toys (Apple & Tech)",
                    url = "https://9to5toys.com/feed/",
                    hostLabel = "9to5toys.com"
                )
            },
            async(Dispatchers.IO) {
                fetchCheapSharkDeals(
                    sourceId = "cheapshark_api",
                    displayName = "CheapShark PC Storefront",
                    url = "https://www.cheapshark.com/api/1.0/deals?pageSize=20&sortBy=Deal%20Rating",
                    hostLabel = "cheapshark.com"
                )
            }
        )

        val results = jobs.awaitAll()
        FeedBatchResult(
            deals = results.flatMap { it.deals },
            sourceStatuses = results.map { it.status }
        )
    }

    /**
     * Real-time multi-platform live search across Slickdeals Search RSS, Reddit multi-subreddit
     * search JSON, and CheapShark Search API in parallel.
     */
    suspend fun searchLiveAcrossSources(query: String): FeedBatchResult = coroutineScope {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.toString())
        val jobs = listOf(
            async(Dispatchers.IO) {
                fetchRssFeed(
                    sourceId = "search_slickdeals",
                    displayName = "Slickdeals Live Search",
                    url = "https://slickdeals.net/newsearch.php?q=$encoded&searcharea=deals&searchin=first&rss=1&sort=newest",
                    hostLabel = "slickdeals.net"
                )
            },
            async(Dispatchers.IO) {
                fetchRedditSubreddit(
                    sourceId = "search_reddit",
                    displayName = "Reddit Deal Search",
                    url = "https://www.reddit.com/r/buildapcsales+deals+GameDeals+frugalmalefashion/search.json?q=$encoded&restrict_sr=1&sort=new&limit=30&raw_json=1",
                    hostLabel = "reddit.com"
                )
            },
            async(Dispatchers.IO) {
                fetchCheapSharkDeals(
                    sourceId = "search_cheapshark",
                    displayName = "CheapShark Search API",
                    url = "https://www.cheapshark.com/api/1.0/deals?title=$encoded&pageSize=15",
                    hostLabel = "cheapshark.com"
                )
            }
        )

        val results = jobs.awaitAll()
        FeedBatchResult(
            deals = results.flatMap { it.deals },
            sourceStatuses = results.map { it.status }
        )
    }

    /**
     * Fetches a direct US retailer product URL and extracts structured price/title/stock metadata.
     */
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
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Retailer returned HTTP ${response.code} (bot protection or unavailable)")
                    )
                }
                val html = response.body?.string().orEmpty().take(450_000)
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
                    errorMessage = e.message?.take(60) ?: "Network error"
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
                    errorMessage = e.message?.take(60) ?: "Network error"
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
                    errorMessage = e.message?.take(60) ?: "Network error"
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
                            if (cleanTitle.isNotBlank() && DealTextParser.isSafePublicHttpUrl(cleanLink)) {
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
                                        isHistoricLow = false, // Determined from real Room snapshot history
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
                                        lastCheckedAtMillis = now
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
            // Partial parse returns whatever items succeeded
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
                // Skip expired/NSFW/meta posts
                val flair = post.optString("link_flair_text", "")
                if (flair.contains("expired", ignoreCase = true) ||
                    flair.contains("meta", ignoreCase = true) ||
                    post.optBoolean("over_18", false)
                ) continue

                val permalink = "https://www.reddit.com" + post.optString("permalink", "")
                val externalUrl = post.optString("url_overridden_by_dest", "").ifBlank {
                    post.optString("url", permalink)
                }
                val buyUrl = if (DealTextParser.isSafePublicHttpUrl(externalUrl)) externalUrl else permalink
                val selftext = post.optString("selftext", "")
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
                        lastCheckedAtMillis = now
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
                            append("CheapShark Deal Rating: $dealRating/10")
                            if (steamRating > 0) append(" • Steam Positive: $steamRating%")
                        },
                        publishedAtMillis = lastChangeMillis,
                        lastCheckedAtMillis = now
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
