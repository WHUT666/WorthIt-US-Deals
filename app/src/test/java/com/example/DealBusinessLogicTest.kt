package com.example

import com.example.algorithm.DealPushRecommendationEngine
import com.example.data.network.DealTextParser
import com.example.model.CouponDetail
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DealBusinessLogicTest {

    @Test
    fun `parser extracts equation pricing merchant brand and coupon from reddit pc deal`() {
        val title = "[GPU] ASUS ROG RTX 5090 OC 32GB - (\$2,299.99 - \$110 = \$2,189.99) w/ promo code ROG110 [Newegg]"
        val prices = DealTextParser.extractPrices(title)
        val merchant = DealTextParser.detectMerchant(title, "https://www.newegg.com/p/N82E16814126750")
        val coupon = DealTextParser.extractCoupon(title)
        val brand = DealTextParser.detectBrand(title)
        val category = DealTextParser.detectCategory(title, "buildapcsales")

        assertEquals(2189.99, prices.currentPrice!!, 0.01)
        assertEquals(2299.99, prices.originalPrice!!, 0.01)
        assertEquals(USMerchant.NEWEGG, merchant)
        assertEquals("ROG110", coupon?.code)
        assertEquals("NVIDIA", brand)
        assertEquals(DealCategory.GAMING, category)
    }

    @Test
    fun `html scraper extracts json-ld product price and blocks unsafe local urls`() {
        assertTrue(DealTextParser.isSafePublicHttpUrl("https://www.bestbuy.com/site/product/12345.p"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://127.0.0.1:8080/admin"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://localhost/secret"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://192.168.1.1/router"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("javascript:alert(1)"))

        val sampleHtml = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Sony WH-1000XM5 Headphones","offers":{"@type":"Offer","price":"278.99","availability":"https://schema.org/InStock"}}
            </script>
            </head><body></body></html>
        """.trimIndent()

        val parsed = DealTextParser.parseProductPageHtml("https://www.bestbuy.com/site/sony", sampleHtml)
        assertEquals("Sony WH-1000XM5 Headphones", parsed.title)
        assertNotNull(parsed.price)
        assertEquals(278.99, parsed.price!!, 0.01)
        assertEquals("InStock", parsed.availability)
    }

    @Test
    fun `recommendation engine boosts deals matching watched keywords and behavioral affinity`() {
        val now = System.currentTimeMillis()
        val rtxDeal = DealItem(
            id = "gpu1",
            title = "NVIDIA GeForce RTX 5090 Founders Edition",
            brand = "NVIDIA",
            merchant = USMerchant.BEST_BUY,
            category = DealCategory.GAMING,
            currentPrice = 1999.99,
            originalPrice = 2299.99,
            historicLowPrice = 1999.99,
            isHistoricLow = true,
            coupon = CouponDetail("GPU50", "Save $50"),
            worthVotes = 120,
            notWorthVotes = 5,
            sourceName = "r/buildapcsales",
            buyUrl = "https://www.bestbuy.com/site/rtx-5090",
            sourceUrl = "https://www.reddit.com/r/buildapcsales",
            publishedAtMillis = now - 600_000L
        )
        val genericDeal = DealItem(
            id = "snack1",
            title = "Bulk Snack Box 24-Pack",
            brand = "SnackCo",
            merchant = USMerchant.OTHER,
            category = DealCategory.GROCERY,
            currentPrice = 24.99,
            originalPrice = 29.99,
            historicLowPrice = 19.99,
            isHistoricLow = false,
            worthVotes = 4,
            notWorthVotes = 3,
            sourceName = "DealNews",
            buyUrl = "https://example.com/snack",
            sourceUrl = "https://example.com/snack",
            publishedAtMillis = now - 48 * 3_600_000L
        )

        val profile = UserPreferenceProfile(
            watchedKeywords = listOf("5090"),
            preferredCategories = setOf(DealCategory.GAMING),
            preferredMerchants = setOf(USMerchant.BEST_BUY),
            maxBudgetUSD = 2500.0
        )
        val affinity = UserBehaviorAffinity(
            categoryBoosts = mapOf(DealCategory.GAMING to 25f),
            merchantBoosts = mapOf(USMerchant.BEST_BUY to 18f),
            totalInteractions = 12
        )

        val ranked = DealPushRecommendationEngine.scoreAndRankDeals(
            deals = listOf(genericDeal, rtxDeal),
            profile = profile,
            behaviorAffinity = affinity,
            nowMillis = now
        )

        assertEquals("gpu1", ranked.first().deal.id)
        assertTrue(ranked.first().matchesWatchedKeyword)
        assertTrue(ranked.first().compositeScore > ranked.last().compositeScore + 20f)
        assertTrue(DealPushRecommendationEngine.shouldTriggerPushNotification(ranked.first(), profile))
        assertFalse(DealPushRecommendationEngine.shouldTriggerPushNotification(ranked.last(), profile))
    }
}
