package com.example.model

data class MerchantPriceQuote(
    val id: String,
    val merchant: USMerchant,
    val title: String,
    val price: Double?,
    val originalPrice: Double?,
    val couponCode: String? = null,
    val sourceName: String,
    val buyUrl: String,
    val imageUrl: String? = null,
    val isLowestAmongAll: Boolean = false,
    val publishedAtMillis: Long,
    val fetchedAtMillis: Long
)

data class LiveComparisonGroup(
    val id: String,
    val groupTitle: String,
    val brand: String,
    val category: DealCategory,
    val bestPrice: Double?,
    val highestPrice: Double?,
    val bestMerchant: USMerchant,
    val quotes: List<MerchantPriceQuote>
) {
    val savingsSpread: Double
        get() {
            val low = bestPrice ?: return 0.0
            val high = highestPrice ?: return 0.0
            return (high - low).coerceAtLeast(0.0)
        }
}

data class DirectRetailerLink(
    val merchant: USMerchant,
    val searchUrl: String
)

data class LiveSearchResponse(
    val query: String,
    val groups: List<LiveComparisonGroup>,
    val totalLiveOffers: Int,
    val sourcesTelemetry: List<MonitorSourceStatus>,
    val directRetailerLinks: List<DirectRetailerLink>,
    val searchedAtMillis: Long
)
