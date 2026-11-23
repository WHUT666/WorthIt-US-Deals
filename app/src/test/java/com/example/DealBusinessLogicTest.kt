package com.example

import com.example.algorithm.DealPushRecommendationEngine
import com.example.data.network.DealTextParser
import com.example.model.CouponDetail
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.FreshnessTier
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import com.example.model.VerificationStatus
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
    fun `html scraper extracts json-ld product price, detects out of stock, and blocks unsafe local urls`() {
        assertTrue(DealTextParser.isSafePublicHttpUrl("https://www.bestbuy.com/site/product/12345.p"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://127.0.0.1:8080/admin"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://localhost/secret"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("http://192.168.1.1/router"))
        assertFalse(DealTextParser.isSafePublicHttpUrl("javascript:alert(1)"))

        val inStockHtml = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"Sony WH-1000XM5 Headphones","offers":{"@type":"Offer","price":"278.99","availability":"https://schema.org/InStock"}}
            </script>
            </head><body></body></html>
        """.trimIndent()

        val parsedInStock = DealTextParser.parseProductPageHtml("https://www.bestbuy.com/site/sony", inStockHtml)
        assertEquals("Sony WH-1000XM5 Headphones", parsedInStock.title)
        assertNotNull(parsedInStock.price)
        assertEquals(278.99, parsedInStock.price!!, 0.01)
        assertEquals("InStock", parsedInStock.availability)
        assertFalse(parsedInStock.isOutOfStock)

        val oosHtml = """
            <html><head>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@type":"Product","name":"RTX 5090 FE","offers":{"@type":"Offer","price":"1999.99","availability":"https://schema.org/OutOfStock"}}
            </script>
            </head><body><div>Currently unavailable.</div></body></html>
        """.trimIndent()

        val parsedOos = DealTextParser.parseProductPageHtml("https://www.bestbuy.com/site/rtx5090", oosHtml)
        assertTrue(parsedOos.isOutOfStock)
    }

    @Test
    fun `expired and out-of-stock community posts are filtered at ingestion`() {
        assertTrue(DealTextParser.isDealMarkedExpiredOrOos("[Expired] Apple AirPods Pro 2 $149", "", ""))
        assertTrue(DealTextParser.isDealMarkedExpiredOrOos("[GPU] RTX 5090 FE [OOS]", "", ""))
        assertTrue(DealTextParser.isDealMarkedExpiredOrOos("Sony OLED 65\" TV", "Deal has expired - sold out at Best Buy", ""))
        assertTrue(DealTextParser.isDealMarkedExpiredOrOos("Samsung 2TB NVMe SSD", "", "Expired"))
        assertFalse(DealTextParser.isDealMarkedExpiredOrOos("[GPU] RTX 5090 Founders Edition $1999 In Stock", "Available now with free shipping", "GPU"))
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
        assertEquals(FreshnessTier.FLASH, ranked.first().freshnessTier)
        assertTrue(ranked.first().matchesWatchedKeyword)
        assertTrue(ranked.first().compositeScore > ranked.last().compositeScore + 20f)
        assertTrue(DealPushRecommendationEngine.shouldTriggerPushNotification(ranked.first(), profile, now))
        assertFalse(DealPushRecommendationEngine.shouldTriggerPushNotification(ranked.last(), profile, now))
    }

    @Test
    fun `time-sensitive freshness decay, live verification, and push freshness gate work accurately`() {
        val now = System.currentTimeMillis()
        val profile = UserPreferenceProfile(
            watchedKeywords = listOf("OLED"),
            preferredCategories = setOf(DealCategory.ELECTRONICS),
            preferredMerchants = setOf(USMerchant.BEST_BUY),
            maxBudgetUSD = 2000.0
        )

        val flashVerified = DealItem(
            id = "flash_live",
            title = "LG C4 65-inch OLED 4K TV",
            brand = "LG",
            merchant = USMerchant.BEST_BUY,
            category = DealCategory.ELECTRONICS,
            currentPrice = 1299.99,
            originalPrice = 1899.99,
            historicLowPrice = 1299.99,
            isHistoricLow = true,
            worthVotes = 45,
            notWorthVotes = 2,
            sourceName = "Slickdeals",
            buyUrl = "https://www.bestbuy.com/site/lg-c4",
            sourceUrl = "https://slickdeals.net",
            publishedAtMillis = now - 10 * 60_000L, // 10 minutes ago (FLASH)
            verificationStatus = VerificationStatus.VERIFIED_LIVE,
            verifiedPrice = 1299.99,
            verifiedAtMillis = now - 60_000L
        )
        val staleSameDeal = flashVerified.copy(
            id = "stale_deal",
            publishedAtMillis = now - 36 * 3_600_000L, // 36 hours old (STALE)
            verificationStatus = VerificationStatus.UNVERIFIED,
            verifiedPrice = null,
            verifiedAtMillis = 0L
        )
        val priceIncreasedDeal = flashVerified.copy(
            id = "price_up",
            verificationStatus = VerificationStatus.PRICE_INCREASED,
            verifiedPrice = 1599.99
        )
        val soldOutDeal = flashVerified.copy(
            id = "sold_out",
            verificationStatus = VerificationStatus.OUT_OF_STOCK,
            isExpired = true
        )

        // Default ranking excludes OUT_OF_STOCK / expired listings
        val ranked = DealPushRecommendationEngine.scoreAndRankDeals(
            deals = listOf(soldOutDeal, priceIncreasedDeal, staleSameDeal, flashVerified),
            profile = profile,
            nowMillis = now
        )
        assertEquals(3, ranked.size)
        assertEquals("flash_live", ranked.first().deal.id)
        assertTrue(ranked.first().compositeScore > ranked.first { it.deal.id == "stale_deal" }.compositeScore + 15f)
        assertTrue(ranked.first().compositeScore > ranked.first { it.deal.id == "price_up" }.compositeScore + 20f)

        // Fresh-only (<6h) filter excludes 36h stale deal
        val freshRanked = DealPushRecommendationEngine.scoreAndRankDeals(
            deals = listOf(staleSameDeal, flashVerified),
            profile = profile,
            freshOnly = true,
            nowMillis = now
        )
        assertEquals(1, freshRanked.size)
        assertEquals("flash_live", freshRanked.first().deal.id)

        // Push notification gate blocks stale (>4h) and price-increased deals, allows flash verified deals
        val scoredFlash = DealPushRecommendationEngine.calculateScore(flashVerified, profile, nowMillis = now)
        val scoredStale = DealPushRecommendationEngine.calculateScore(staleSameDeal, profile, nowMillis = now)
        val scoredPriceUp = DealPushRecommendationEngine.calculateScore(priceIncreasedDeal, profile, nowMillis = now)
        assertTrue(DealPushRecommendationEngine.shouldTriggerPushNotification(scoredFlash, profile, now))
        assertFalse(DealPushRecommendationEngine.shouldTriggerPushNotification(scoredStale, profile, now))
        assertFalse(DealPushRecommendationEngine.shouldTriggerPushNotification(scoredPriceUp, profile, now))
    }
}
