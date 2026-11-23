package com.example.data.network

import com.example.model.CouponDetail
import com.example.model.DealCategory
import com.example.model.USMerchant
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale

data class ParsedPriceInfo(
    val currentPrice: Double?,
    val originalPrice: Double?
)

data class ParsedProductPage(
    val title: String?,
    val price: Double?,
    val originalPrice: Double?,
    val imageUrl: String?,
    val availability: String?,
    val isOutOfStock: Boolean = false
)

/**
 * Pure, deterministic parser for US retail deal feeds (Slickdeals RSS, Reddit JSON,
 * DealNews RSS, 9to5Toys RSS) and direct retailer product HTML (JSON-LD / Open Graph).
 */
object DealTextParser {

    private val CURRENCY_REGEX = Regex("""\$\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)""")
    private val EQUATION_PRICE_REGEX = Regex(
        """\$\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)\s*[-–]\s*\$\s*(\d+(?:\.\d{1,2})?)\s*=\s*\$\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""
    )
    private val REG_PRICE_REGEX = Regex(
        """(?i)(?:reg\.?|msrp|was|list|orig\.?|retail)\s*:?\s*\$\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?)"""
    )
    private val DOLLAR_OFF_REGEX = Regex(
        """(?i)\$\s*(\d{1,4}(?:\.\d{1,2})?)\s*off\b"""
    )
    private val PERCENT_OFF_REGEX = Regex(
        """(?i)(\d{1,2})%\s*off\b"""
    )
    private val COUPON_CODE_REGEX = Regex(
        """(?i)(?:promo\s+code|coupon\s+code|use\s+code|w/\s*code|with\s+code|code)\s*[:#-]?\s*["']?([A-Z0-9][A-Z0-9-]{3,17})["']?"""
    )
    private val IMG_SRC_REGEX = Regex("""(?i)<img[^>]+src=["'](https?://[^"']+)["']""")
    private val JSON_LD_REGEX = Regex(
        """(?is)<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>"""
    )
    private val META_TAG_REGEX = Regex("""(?i)<meta\s+([^>]+)>""")
    private val TITLE_TAG_REGEX = Regex("""(?is)<title[^>]*>(.*?)</title>""")
    private val HTML_TAG_REGEX = Regex("""<[^>]+>""")

    private val NON_COUPON_WORDS = setOf(
        "CODE", "PROMO", "COUPON", "FREE", "DEAL", "SAVE", "SHIPPING", "PRIME",
        "ONLY", "ONLINE", "STORE", "OFFER", "PRICE", "VALID", "APPLY", "CLIP", "WITH"
    )

    fun isSafePublicHttpUrl(rawUrl: String): Boolean {
        return try {
            val uri = URI(rawUrl.trim())
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return false
            if (scheme != "http" && scheme != "https") return false
            val host = uri.host?.lowercase(Locale.US) ?: return false
            if (host == "localhost" ||
                host.endsWith(".local") ||
                host.startsWith("127.") ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host.startsWith("169.254.") ||
                host == "::1" ||
                host == "0.0.0.0"
            ) {
                return false
            }
            host.contains(".")
        } catch (_: Exception) {
            false
        }
    }

    fun detectMerchant(title: String, url: String = "", description: String = ""): USMerchant {
        val urlLower = url.lowercase(Locale.US)
        val combined = "$title $description".lowercase(Locale.US)

        // Prioritize explicit domain in outbound URL
        when {
            urlLower.contains("amazon.com") || urlLower.contains("amzn.to") -> return USMerchant.AMAZON
            urlLower.contains("bestbuy.com") -> return USMerchant.BEST_BUY
            urlLower.contains("walmart.com") -> return USMerchant.WALMART
            urlLower.contains("target.com") -> return USMerchant.TARGET
            urlLower.contains("newegg.com") -> return USMerchant.NEWEGG
            urlLower.contains("bhphotovideo.com") -> return USMerchant.B_AND_H
            urlLower.contains("costco.com") -> return USMerchant.COSTCO
            urlLower.contains("homedepot.com") -> return USMerchant.HOMEDEPOT
            urlLower.contains("microcenter.com") -> return USMerchant.MICRO_CENTER
            urlLower.contains("ebay.com") -> return USMerchant.EBAY
            urlLower.contains("cheapshark.com") || urlLower.contains("steampowered.com") -> return USMerchant.STEAM_PC
        }

        // Fall back to merchant name mentioned in title/description
        return when {
            combined.contains("amazon") || combined.contains("prime day") -> USMerchant.AMAZON
            combined.contains("best buy") || combined.contains("bestbuy") -> USMerchant.BEST_BUY
            combined.contains("walmart") -> USMerchant.WALMART
            combined.contains("target") -> USMerchant.TARGET
            combined.contains("newegg") -> USMerchant.NEWEGG
            combined.contains("b&h") || combined.contains("bhphoto") -> USMerchant.B_AND_H
            combined.contains("costco") -> USMerchant.COSTCO
            combined.contains("home depot") || combined.contains("homedepot") -> USMerchant.HOMEDEPOT
            combined.contains("micro center") || combined.contains("microcenter") -> USMerchant.MICRO_CENTER
            combined.contains("ebay") -> USMerchant.EBAY
            combined.contains("steam") || combined.contains("humble") || combined.contains("gog.com") -> USMerchant.STEAM_PC
            else -> USMerchant.OTHER
        }
    }

    fun extractPrices(title: String, description: String = ""): ParsedPriceInfo {
        val cleanTitle = decodeHtmlEntities(title)
        val cleanDesc = decodeHtmlEntities(stripHtml(description)).take(350)

        // Pattern 1: Explicit Reddit/Slickdeals formula e.g. ($2,199 - $200 = $1,999)
        EQUATION_PRICE_REGEX.find(cleanTitle)?.let { m ->
            val orig = parseDollar(m.groupValues[1])
            val finalPrice = parseDollar(m.groupValues[3])
            if (finalPrice != null && orig != null && orig > finalPrice) {
                return ParsedPriceInfo(currentPrice = finalPrice, originalPrice = orig)
            }
        }

        // Pattern 2: Explicit "Reg. $xxx / MSRP $xxx / Was $xxx"
        val regPrice = REG_PRICE_REGEX.find(cleanTitle)?.groupValues?.get(1)?.let(::parseDollar)
            ?: REG_PRICE_REGEX.find(cleanDesc)?.groupValues?.get(1)?.let(::parseDollar)

        // Find all candidate dollar prices in title (ignoring gift card / rebate / shipping threshold amounts)
        val titleMatches = CURRENCY_REGEX.findAll(cleanTitle).mapNotNull { match ->
            val value = parseDollar(match.groupValues[1]) ?: return@mapNotNull null
            val after = cleanTitle.substring(match.range.last + 1).trimStart().lowercase(Locale.US)
            val before = cleanTitle.substring(0, match.range.first).trimEnd().lowercase(Locale.US)
            val isAuxAmount = after.startsWith("off") ||
                after.startsWith("gift card") ||
                after.startsWith("shop card") ||
                after.startsWith("credit") ||
                after.startsWith("rebate") ||
                after.startsWith("cash") ||
                after.startsWith("coupon") ||
                before.endsWith("over") ||
                before.endsWith("orders") ||
                before.endsWith("save") ||
                before.endsWith("saves")
            if (isAuxAmount || value <= 0.5) null else value
        }.toList()

        val currentPrice = when {
            titleMatches.isNotEmpty() -> {
                if (regPrice != null) {
                    titleMatches.firstOrNull { it != regPrice } ?: titleMatches.min()
                } else {
                    titleMatches.first()
                }
            }
            else -> {
                // Try description if title had no price
                CURRENCY_REGEX.find(cleanDesc)?.groupValues?.get(1)?.let(::parseDollar)
                    ?.takeIf { it > 0.5 }
            }
        }

        var originalPrice = regPrice
        if (originalPrice == null && titleMatches.size >= 2 && currentPrice != null) {
            val candidateHigh = titleMatches.max()
            if (candidateHigh > currentPrice * 1.03) {
                originalPrice = candidateHigh
            }
        }
        if (originalPrice == null && currentPrice != null) {
            val dollarOff = DOLLAR_OFF_REGEX.find(cleanTitle)?.groupValues?.get(1)?.let(::parseDollar)
            if (dollarOff != null && dollarOff > 0.0 && dollarOff < currentPrice * 5) {
                originalPrice = roundMoney(currentPrice + dollarOff)
            } else {
                val pctOff = PERCENT_OFF_REGEX.find(cleanTitle)?.groupValues?.get(1)?.toIntOrNull()
                if (pctOff != null && pctOff in 5..85) {
                    originalPrice = roundMoney(currentPrice / (1.0 - pctOff / 100.0))
                }
            }
        }

        if (originalPrice != null && currentPrice != null && originalPrice <= currentPrice) {
            originalPrice = null
        }

        return ParsedPriceInfo(currentPrice = currentPrice, originalPrice = originalPrice)
    }

    fun extractCoupon(title: String, description: String = ""): CouponDetail? {
        val text = "$title ${stripHtml(description).take(300)}"
        val match = COUPON_CODE_REGEX.find(text) ?: return null
        val code = match.groupValues[1].uppercase(Locale.US)
        if (code in NON_COUPON_WORDS || code.length < 4) return null
        val desc = when {
            text.contains("% off", ignoreCase = true) -> {
                val pct = PERCENT_OFF_REGEX.find(text)?.groupValues?.get(1)
                if (pct != null) "Apply code $code for $pct% off at checkout" else "Apply promo code $code at checkout"
            }
            text.contains("off", ignoreCase = true) -> {
                val amt = DOLLAR_OFF_REGEX.find(text)?.groupValues?.get(1)
                if (amt != null) "Apply code $code for $$amt off at checkout" else "Apply promo code $code at checkout"
            }
            else -> "Apply promo code $code at checkout"
        }
        return CouponDetail(code = code, discountDescription = desc)
    }

    fun detectBrand(title: String): String {
        val lower = title.lowercase(Locale.US)
        val knownBrands = listOf(
            "Apple" to listOf("apple", "macbook", "airpods", "iphone", "ipad", "homepod", "apple watch"),
            "NVIDIA" to listOf("nvidia", "geforce", "rtx 50", "rtx 40", "rtx 30", "5090", "5080", "5070", "4090", "4080"),
            "AMD" to listOf("amd", "ryzen", "radeon", "rx 90", "rx 79", "rx 78"),
            "Intel" to listOf("intel", "core ultra", "i9-", "i7-", "i5-"),
            "Sony" to listOf("sony", "playstation", "ps5", "wh-1000", "wf-1000", "bravia"),
            "Samsung" to listOf("samsung", "galaxy", "odyssey", "990 pro", "980 pro"),
            "LG" to listOf("lg ", "oled c4", "oled c3", "oled g4", "ultragear"),
            "ASUS" to listOf("asus", "rog ", "tuf ", "zenbook"),
            "MSI" to listOf("msi ", "suprim", "ventus"),
            "Gigabyte" to listOf("gigabyte", "aorus"),
            "Dyson" to listOf("dyson"),
            "Ninja" to listOf("ninja", "creami"),
            "Bose" to listOf("bose", "quietcomfort"),
            "Dell" to listOf("dell", "alienware", "xps "),
            "Lenovo" to listOf("lenovo", "thinkpad", "legion"),
            "Nintendo" to listOf("nintendo", "switch"),
            "Microsoft" to listOf("xbox", "surface"),
            "DeWalt" to listOf("dewalt"),
            "Milwaukee" to listOf("milwaukee", "m18", "m12"),
            "Anker" to listOf("anker", "soundcore"),
            "Logitech" to listOf("logitech"),
            "Corsair" to listOf("corsair"),
            "Patagonia" to listOf("patagonia"),
            "Nike" to listOf("nike"),
            "Adidas" to listOf("adidas"),
            "Lego" to listOf("lego")
        )
        for ((brand, patterns) in knownBrands) {
            if (patterns.any { lower.contains(it) }) return brand
        }
        return title
            .replace(Regex("""^\[[^\]]+\]\s*"""), "")
            .split(" ")
            .firstOrNull { it.length >= 2 && it.first().isLetter() }
            ?.replace(Regex("""[^A-Za-z0-9&'-]"""), "")
            ?.takeIf { it.isNotBlank() }
            ?: "US Deal"
    }

    fun detectCategory(title: String, sourceTag: String = ""): DealCategory {
        val lower = "$title $sourceTag".lowercase(Locale.US)
        return when {
            lower.contains("apple") || lower.contains("macbook") || lower.contains("iphone") ||
                lower.contains("ipad") || lower.contains("airpods") -> DealCategory.APPLE

            lower.contains("rtx") || lower.contains("radeon") || lower.contains("gpu") ||
                lower.contains("5090") || lower.contains("5080") || lower.contains("5070") ||
                lower.contains("ps5") || lower.contains("xbox") || lower.contains("nintendo") ||
                lower.contains("steam") || lower.contains("gaming") || lower.contains("ryzen") ||
                lower.contains("motherboard") || lower.contains("buildapcsales") ||
                lower.contains("gamedeals") || lower.contains("cheapshark") -> DealCategory.GAMING

            lower.contains("vacuum") || lower.contains("dyson") || lower.contains("kitchen") ||
                lower.contains("coffee") || lower.contains("espresso") || lower.contains("air fryer") ||
                lower.contains("mattress") || lower.contains("chair") || lower.contains("desk") ||
                lower.contains("blender") || lower.contains("ninja") || lower.contains("purifier") -> DealCategory.HOME_KITCHEN

            lower.contains("jacket") || lower.contains("hoodie") || lower.contains("shoes") ||
                lower.contains("sneaker") || lower.contains("fleece") || lower.contains("shirt") ||
                lower.contains("jeans") || lower.contains("nike") || lower.contains("adidas") ||
                lower.contains("patagonia") || lower.contains("apparel") -> DealCategory.APPAREL

            lower.contains("snack") || lower.contains("grocery") || lower.contains("protein") ||
                lower.contains("cereal") || lower.contains("pack of") || lower.contains("chocolate") ||
                lower.contains("beverage") || lower.contains("detergent") -> DealCategory.GROCERY

            lower.contains("dewalt") || lower.contains("milwaukee") || lower.contains("drill") ||
                lower.contains("wrench") || lower.contains("socket") || lower.contains("tire") ||
                lower.contains("dash cam") || lower.contains("tool") || lower.contains("homedepot") -> DealCategory.AUTOMOTIVE

            else -> DealCategory.ELECTRONICS
        }
    }

    fun extractFirstImageUrl(html: String): String? {
        val raw = IMG_SRC_REGEX.find(html)?.groupValues?.get(1) ?: return null
        return if (raw.contains("spacer") || raw.contains("pixel") || raw.contains("1x1")) null else raw
    }

    fun stripHtml(input: String): String {
        return decodeHtmlEntities(HTML_TAG_REGEX.replace(input, " "))
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&#8211;", "-")
            .replace("&#8212;", "-")
            .replace("&#8217;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
    }

    /**
     * Parses a real retailer product page HTML via JSON-LD (schema.org Product/Offer)
     * and Open Graph / microdata price tags.
     */
    fun parseProductPageHtml(url: String, html: String): ParsedProductPage {
        var title: String? = null
        var price: Double? = null
        var originalPrice: Double? = null
        var imageUrl: String? = null
        var availability: String? = null

        // 1. Parse JSON-LD structured product blocks
        for (match in JSON_LD_REGEX.findAll(html)) {
            val jsonText = match.groupValues[1].trim()
            if (jsonText.isBlank()) continue
            val parsed = try {
                extractFromJsonLdBlock(jsonText)
            } catch (_: Throwable) {
                extractFromJsonLdFallback(jsonText)
            }
            val effective = if (parsed.title == null && parsed.price == null) {
                extractFromJsonLdFallback(jsonText)
            } else {
                parsed
            }
            if (effective.title != null && title == null) title = effective.title
            if (effective.price != null && price == null) price = effective.price
            if (effective.imageUrl != null && imageUrl == null) imageUrl = effective.imageUrl
            if (effective.availability != null && availability == null) availability = effective.availability
            if (price != null && title != null) break
        }

        // 2. Fallback to Open Graph / Product meta tags
        if (title == null || price == null || imageUrl == null) {
            for (metaMatch in META_TAG_REGEX.findAll(html)) {
                val attrs = metaMatch.groupValues[1]
                val key = extractHtmlAttr(attrs, "property")
                    ?: extractHtmlAttr(attrs, "name")
                    ?: extractHtmlAttr(attrs, "itemprop")
                    ?: continue
                val content = extractHtmlAttr(attrs, "content") ?: continue
                when (key.lowercase(Locale.US)) {
                    "og:title", "twitter:title" -> if (title == null) title = decodeHtmlEntities(content)
                    "og:image", "twitter:image" -> if (imageUrl == null && isSafePublicHttpUrl(content)) imageUrl = content
                    "product:price:amount", "og:price:amount", "price" -> {
                        if (price == null) price = parseDollar(content)
                    }
                    "product:original_price:amount" -> {
                        if (originalPrice == null) originalPrice = parseDollar(content)
                    }
                    "og:availability", "product:availability" -> {
                        if (availability == null) availability = content
                    }
                }
            }
        }

        // 3. Fallback to <title>
        if (title.isNullOrBlank()) {
            title = TITLE_TAG_REGEX.find(html)?.groupValues?.get(1)?.let { stripHtml(it) }
        }

        // 4. Fallback regex for price embedded in retailer JSON state (e.g., "price":1999.99 or "currentPrice":999)
        if (price == null) {
            val jsonPriceRegex = Regex("""(?i)"(?:price|salePrice|currentPrice|customerPrice|buyingPrice)"\s*:\s*"?(\d{1,5}(?:\.\d{1,2})?)"?""")
            price = jsonPriceRegex.find(html)?.groupValues?.get(1)?.toDoubleOrNull()?.takeIf { it > 0.5 }
        }

        val cleanAvailability = availability?.substringAfterLast("/")
        val htmlLowerSnippet = html.take(120_000).lowercase(Locale.US)
        val availLower = cleanAvailability?.lowercase(Locale.US).orEmpty()
        val isOos = availLower.contains("outofstock") ||
            availLower.contains("soldout") ||
            availLower.contains("discontinued") ||
            htmlLowerSnippet.contains("\"availability\":\"https://schema.org/outofstock\"") ||
            htmlLowerSnippet.contains("\"availability\":\"http://schema.org/outofstock\"") ||
            htmlLowerSnippet.contains("currently unavailable.") ||
            htmlLowerSnippet.contains("we don't know when or if this item will be back in stock") ||
            (price == null && (htmlLowerSnippet.contains(">sold out<") || htmlLowerSnippet.contains(">out of stock<")))

        return ParsedProductPage(
            title = title?.take(180),
            price = price,
            originalPrice = originalPrice,
            imageUrl = imageUrl,
            availability = cleanAvailability,
            isOutOfStock = isOos
        )
    }

    /**
     * Detects explicit expiration or out-of-stock markers in US deal posts so dead
     * flash deals are excluded before wasting user attention.
     */
    fun isDealMarkedExpiredOrOos(title: String, description: String = "", flair: String = ""): Boolean {
        val f = flair.trim().lowercase(Locale.US)
        if (f == "expired" || f == "oos" || f == "dead" || f == "sold out" || f == "out of stock") {
            return true
        }
        val t = "$title $f".lowercase(Locale.US)
        val d = stripHtml(description).take(220).lowercase(Locale.US)
        val titleMarkers = listOf(
            "[expired]", "(expired)", "expired:", "expired -", "deal expired",
            "[oos]", "(oos)", " oos", "out of stock", "sold out",
            "[dead]", "(dead)", "dead deal", "deal ended", "price back up",
            "back to regular price", "no longer available"
        )
        if (titleMarkers.any { t.contains(it) }) return true
        val descMarkers = listOf(
            "deal has expired", "deal is now expired", "deal expired",
            "currently out of stock", "now out of stock", "sold out at"
        )
        if (descMarkers.any { d.contains(it) }) return true
        return false
    }

    private fun extractFromJsonLdBlock(rawJson: String): ParsedProductPage {
        val objects = mutableListOf<JSONObject>()
        if (rawJson.startsWith("[")) {
            val arr = JSONArray(rawJson)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { objects.add(it) }
            }
        } else if (rawJson.startsWith("{")) {
            val root = JSONObject(rawJson)
            objects.add(root)
            root.optJSONArray("@graph")?.let { graph ->
                for (i in 0 until graph.length()) {
                    graph.optJSONObject(i)?.let { objects.add(it) }
                }
            }
        }

        for (obj in objects) {
            val typeStr = obj.opt("@type")?.toString()?.lowercase(Locale.US) ?: ""
            if (typeStr.contains("product") || obj.has("offers")) {
                val name = obj.optString("name").takeIf { it.isNotBlank() }?.let(::decodeHtmlEntities)
                val img = when (val imgNode = obj.opt("image")) {
                    is String -> imgNode
                    is JSONArray -> imgNode.optString(0)
                    is JSONObject -> imgNode.optString("url")
                    else -> null
                }?.takeIf { isSafePublicHttpUrl(it) }

                var price: Double? = null
                var availability: String? = null
                val offersNode = obj.opt("offers")
                val offerObj = when (offersNode) {
                    is JSONObject -> offersNode
                    is JSONArray -> offersNode.optJSONObject(0)
                    else -> null
                }
                if (offerObj != null) {
                    price = parseDollar(offerObj.optString("price"))
                        ?: parseDollar(offerObj.optString("lowPrice"))
                    availability = offerObj.optString("availability").takeIf { it.isNotBlank() }
                }
                if (name != null || price != null) {
                    return ParsedProductPage(
                        title = name,
                        price = price,
                        originalPrice = null,
                        imageUrl = img,
                        availability = availability
                    )
                }
            }
        }
        return ParsedProductPage(null, null, null, null, null)
    }

    private fun extractFromJsonLdFallback(rawJson: String): ParsedProductPage {
        val nameMatch = Regex("""(?i)"name"\s*:\s*"([^"]{2,200})"""").find(rawJson)?.groupValues?.get(1)?.let(::decodeHtmlEntities)
        val priceMatch = Regex("""(?i)"(?:price|lowPrice)"\s*:\s*"?(\d{1,5}(?:\.\d{1,2})?)"?""").find(rawJson)?.groupValues?.get(1)?.let(::parseDollar)
        val imgMatch = Regex("""(?i)"image"\s*:\s*"(https?://[^"]+)"""").find(rawJson)?.groupValues?.get(1)?.takeIf { isSafePublicHttpUrl(it) }
        val availMatch = Regex("""(?i)"availability"\s*:\s*"([^"]+)"""").find(rawJson)?.groupValues?.get(1)
        return ParsedProductPage(
            title = nameMatch,
            price = priceMatch,
            originalPrice = null,
            imageUrl = imgMatch,
            availability = availMatch
        )
    }

    private fun extractHtmlAttr(tagAttributes: String, attrName: String): String? {
        val regex = Regex("""(?i)\b$attrName\s*=\s*["']([^"']+)["']""")
        return regex.find(tagAttributes)?.groupValues?.get(1)
    }

    fun normalizeProductKey(title: String): String {
        return title.lowercase(Locale.US)
            .replace(Regex("""\$[\d,.]+"""), "")
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .split(Regex("""\s+"""))
            .filter { it.length >= 2 && it !in setOf("the", "and", "for", "with", "from", "off", "free", "shipping", "deal", "sale") }
            .take(5)
            .joinToString("_")
            .ifBlank { "deal_${title.hashCode()}" }
    }

    private fun parseDollar(raw: String): Double? {
        val cleaned = raw.replace(",", "").replace("$", "").trim()
        return cleaned.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun roundMoney(value: Double): Double = Math.round(value * 100.0) / 100.0
}
