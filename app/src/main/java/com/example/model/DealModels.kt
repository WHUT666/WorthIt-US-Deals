package com.example.model

enum class USMerchant(
    val displayName: String,
    val domain: String,
    val brandColorHex: Long,
    val searchUrlTemplate: String,
    val estimatedCashBackRate: Double
) {
    AMAZON(
        "Amazon",
        "amazon.com",
        0xFFE47911,
        "https://www.amazon.com/s?k=%s",
        0.03
    ),
    BEST_BUY(
        "Best Buy",
        "bestbuy.com",
        0xFF0046BE,
        "https://www.bestbuy.com/site/searchpage.jsp?st=%s",
        0.05
    ),
    WALMART(
        "Walmart",
        "walmart.com",
        0xFF0071DC,
        "https://www.walmart.com/search?q=%s",
        0.03
    ),
    TARGET(
        "Target",
        "target.com",
        0xFFCC0000,
        "https://www.target.com/s?searchTerm=%s",
        0.05
    ),
    NEWEGG(
        "Newegg",
        "newegg.com",
        0xFFF36F21,
        "https://www.newegg.com/p/pl?d=%s",
        0.02
    ),
    B_AND_H(
        "B&H Photo",
        "bhphotovideo.com",
        0xFF007A78,
        "https://www.bhphotovideo.com/c/search?Ntt=%s",
        0.02
    ),
    COSTCO(
        "Costco",
        "costco.com",
        0xFF005DAA,
        "https://www.costco.com/CatalogSearch?dept=All&keyword=%s",
        0.02
    ),
    HOMEDEPOT(
        "Home Depot",
        "homedepot.com",
        0xFFF96302,
        "https://www.homedepot.com/s/%s",
        0.02
    ),
    MICRO_CENTER(
        "Micro Center",
        "microcenter.com",
        0xFF005696,
        "https://www.microcenter.com/search/search_results.aspx?Ntt=%s",
        0.01
    ),
    EBAY(
        "eBay",
        "ebay.com",
        0xFF0064D2,
        "https://www.ebay.com/sch/i.html?_nkw=%s",
        0.02
    ),
    STEAM_PC(
        "PC & Digital",
        "cheapshark.com",
        0xFF1B2838,
        "https://www.cheapshark.com/search#q:%s",
        0.0
    ),
    OTHER(
        "US Retailer",
        "retail",
        0xFF4B5563,
        "https://www.google.com/search?tbm=shop&q=%s",
        0.01
    );

    companion object {
        val majorUSRetailers: List<USMerchant> = listOf(
            AMAZON, BEST_BUY, WALMART, TARGET, NEWEGG, B_AND_H, COSTCO, HOMEDEPOT
        )

        fun fromNameSafe(name: String): USMerchant =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: OTHER
    }
}

enum class DealCategory(val displayName: String) {
    ALL("All"),
    ELECTRONICS("Tech"),
    APPLE("Apple"),
    GAMING("Gaming & PC"),
    HOME_KITCHEN("Home"),
    APPAREL("Apparel"),
    GROCERY("Grocery"),
    AUTOMOTIVE("Tools & Auto");

    companion object {
        fun fromNameSafe(name: String): DealCategory =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ELECTRONICS
    }
}

enum class VerificationStatus(val label: String) {
    UNVERIFIED("Unverified"),
    VERIFIED_LIVE("Verified Live"),
    PRICE_INCREASED("Price Increased"),
    OUT_OF_STOCK("Out of Stock / Expired"),
    STORE_CHECK_REQUIRED("Check at Store");

    companion object {
        fun fromNameSafe(name: String): VerificationStatus =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: UNVERIFIED
    }
}

enum class FreshnessTier(val badgeLabel: String) {
    FLASH("FLASH"),
    FRESH("FRESH"),
    AGING("AGING"),
    STALE("STALE")
}

enum class AppThemeMode(val displayName: String) {
    LIGHT("Light"),
    SYSTEM("Auto"),
    DARK("Dark");

    companion object {
        fun fromNameSafe(name: String?): AppThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: LIGHT
    }
}

data class PricePoint(
    val dateLabel: String,
    val price: Double,
    val timestampMillis: Long
)

data class CouponDetail(
    val code: String,
    val discountDescription: String
)

data class DealItem(
    val id: String,
    val title: String,
    val brand: String,
    val merchant: USMerchant,
    val category: DealCategory,
    val currentPrice: Double?,
    val originalPrice: Double?,
    val historicLowPrice: Double?,
    val isHistoricLow: Boolean,
    val coupon: CouponDetail? = null,
    val cashBackRate: Double = 0.0,
    val freeShipping: Boolean = false,
    val worthVotes: Int = 0,
    val notWorthVotes: Int = 0,
    val communityCommentsCount: Int = 0,
    val sourceName: String,
    val imageUrl: String? = null,
    val buyUrl: String,
    val sourceUrl: String,
    val summary: String = "",
    val publishedAtMillis: Long = System.currentTimeMillis(),
    val lastCheckedAtMillis: Long = System.currentTimeMillis(),
    val priceHistory: List<PricePoint> = emptyList(),
    val isSaved: Boolean = false,
    val userVote: Boolean? = null,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val verifiedPrice: Double? = null,
    val verifiedAtMillis: Long = 0L,
    val verificationNote: String = "",
    val isExpired: Boolean = false
) {
    val effectiveCurrentPrice: Double?
        get() = verifiedPrice ?: currentPrice

    val discountPercent: Int
        get() {
            val cur = effectiveCurrentPrice ?: return 0
            val orig = originalPrice ?: return 0
            if (orig <= 0.0 || orig <= cur) return 0
            return (((orig - cur) / orig) * 100.0).toInt().coerceIn(0, 99)
        }

    val worthRatio: Float
        get() {
            val total = worthVotes + notWorthVotes
            return if (total > 0) worthVotes.toFloat() / total.toFloat() else 0.75f
        }

    val effectivePriceAfterCashback: Double?
        get() = effectiveCurrentPrice?.let { it * (1.0 - cashBackRate) }

    fun freshnessTier(nowMillis: Long = System.currentTimeMillis()): FreshnessTier {
        val ageMinutes = ((nowMillis - publishedAtMillis) / 60_000L).coerceAtLeast(0L)
        return when {
            ageMinutes <= 30L -> FreshnessTier.FLASH
            ageMinutes <= 180L -> FreshnessTier.FRESH
            ageMinutes <= 1080L -> FreshnessTier.AGING
            else -> FreshnessTier.STALE
        }
    }
}

data class UserPreferenceProfile(
    val preferredCategories: Set<DealCategory> = setOf(
        DealCategory.ELECTRONICS,
        DealCategory.APPLE,
        DealCategory.GAMING
    ),
    val preferredMerchants: Set<USMerchant> = setOf(
        USMerchant.AMAZON,
        USMerchant.BEST_BUY,
        USMerchant.NEWEGG
    ),
    val minimumDiscountThreshold: Int = 15,
    val onlyHistoricLows: Boolean = false,
    val maxBudgetUSD: Double = 3000.0,
    val watchedKeywords: List<String> = listOf("5090", "OLED", "AirPods", "MacBook"),
    val pushNotificationsEnabled: Boolean = true,
    val autoCopyCoupons: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    // Rebalanced weights emphasizing time-sensitivity for real flash deals
    val discountWeight: Float = 0.22f,
    val historicLowWeight: Float = 0.18f,
    val socialWorthWeight: Float = 0.17f,
    val personalAffinityWeight: Float = 0.18f,
    val merchantTrustWeight: Float = 0.10f,
    val freshnessWeight: Float = 0.15f
)

data class UserBehaviorAffinity(
    val categoryBoosts: Map<DealCategory, Float> = emptyMap(),
    val merchantBoosts: Map<USMerchant, Float> = emptyMap(),
    val totalInteractions: Int = 0
)

data class ScoreBreakdown(
    val discountScore: Int,
    val historicLowScore: Int,
    val socialScore: Int,
    val personalAndBehaviorScore: Int,
    val merchantScore: Int,
    val freshnessScore: Int
)

data class RecommendationReason(
    val title: String,
    val score: Int,
    val tags: List<String>,
    val breakdown: ScoreBreakdown
)

data class MonitorSourceStatus(
    val sourceId: String,
    val displayName: String,
    val endpointHost: String,
    val isHealthy: Boolean,
    val httpCode: Int?,
    val latencyMs: Long,
    val dealsFetched: Int,
    val lastSyncedAtMillis: Long,
    val errorMessage: String? = null
)

data class TrackedUrlWatch(
    val id: Long,
    val label: String,
    val url: String,
    val merchant: USMerchant,
    val targetPrice: Double?,
    val lastObservedPrice: Double?,
    val lastStatusMessage: String,
    val lastCheckedAtMillis: Long,
    val priceHistory: List<PricePoint> = emptyList()
) {
    val isTargetHit: Boolean
        get() = targetPrice != null && lastObservedPrice != null && lastObservedPrice <= targetPrice
}

data class ConversionFunnelStats(
    val impressions: Int = 0,
    val detailViews: Int = 0,
    val couponCopies: Int = 0,
    val outboundClicks: Int = 0,
    val bookmarks: Int = 0,
    val worthVotes: Int = 0,
    val topConvertingMerchant: USMerchant? = null,
    val topInterestCategory: DealCategory? = null
) {
    val detailCtrPercent: Int
        get() = if (impressions > 0) ((detailViews.toFloat() / impressions) * 100).toInt().coerceAtMost(100) else 0

    val outboundConversionPercent: Int
        get() = if (detailViews > 0) ((outboundClicks.toFloat() / detailViews) * 100).toInt().coerceAtMost(100) else 0
}
