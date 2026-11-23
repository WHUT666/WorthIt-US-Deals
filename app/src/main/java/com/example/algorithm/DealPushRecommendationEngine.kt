package com.example.algorithm

import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.FreshnessTier
import com.example.model.RecommendationReason
import com.example.model.ScoreBreakdown
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import com.example.model.VerificationStatus
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * WorthIt Time-Sensitive DealRank & Push Qualification Engine
 *
 * Designed specifically for flash-sale US retail dynamics where top deals sell out quickly:
 * - Excludes expired / out-of-stock listings from active feed
 * - Applies a steep time-decay freshness curve (<30m FLASH boost, heavy penalty after 18h)
 * - Boosts live-verified in-stock deals (+12 pts) and penalizes price-increased listings (-25 pts)
 * - Incorporates closed-loop behavioral affinity from Room conversion events
 */
object DealPushRecommendationEngine {

    private const val FRESH_ONLY_WINDOW_MILLIS = 6L * 3_600_000L // 6 hours

    data class ScoredDeal(
        val deal: DealItem,
        val compositeScore: Float,
        val recommendationReason: RecommendationReason,
        val matchesWatchedKeyword: Boolean,
        val freshnessTier: FreshnessTier
    )

    fun scoreAndRankDeals(
        deals: List<DealItem>,
        profile: UserPreferenceProfile,
        behaviorAffinity: UserBehaviorAffinity = UserBehaviorAffinity(),
        filterCategory: DealCategory? = null,
        searchQuery: String = "",
        freshOnly: Boolean = false,
        includeExpired: Boolean = false,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ScoredDeal> {
        val tokens = searchQuery.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }

        val filtered = deals.filter { deal ->
            if (!includeExpired && (deal.isExpired || deal.verificationStatus == VerificationStatus.OUT_OF_STOCK)) {
                return@filter false
            }
            if (freshOnly) {
                val ageMillis = (nowMillis - deal.publishedAtMillis).coerceAtLeast(0L)
                val recentlyVerified = deal.verificationStatus == VerificationStatus.VERIFIED_LIVE &&
                    (nowMillis - deal.verifiedAtMillis) <= 3_600_000L
                if (ageMillis > FRESH_ONLY_WINDOW_MILLIS && !recentlyVerified) {
                    return@filter false
                }
            }
            val matchCategory = filterCategory == null || filterCategory == DealCategory.ALL || deal.category == filterCategory
            val matchQuery = tokens.isEmpty() || tokens.all { token ->
                deal.title.contains(token, ignoreCase = true) ||
                    deal.brand.contains(token, ignoreCase = true) ||
                    deal.merchant.displayName.contains(token, ignoreCase = true) ||
                    deal.sourceName.contains(token, ignoreCase = true) ||
                    deal.summary.contains(token, ignoreCase = true)
            }
            val effectivePrice = deal.effectiveCurrentPrice
            val matchBudget = effectivePrice == null || effectivePrice <= profile.maxBudgetUSD
            val matchHistoric = !profile.onlyHistoricLows || deal.isHistoricLow
            matchCategory && matchQuery && matchBudget && matchHistoric
        }

        return filtered
            .map { deal -> calculateScore(deal, profile, behaviorAffinity, nowMillis) }
            .sortedByDescending { it.compositeScore }
    }

    fun calculateScore(
        deal: DealItem,
        profile: UserPreferenceProfile,
        behaviorAffinity: UserBehaviorAffinity = UserBehaviorAffinity(),
        nowMillis: Long = System.currentTimeMillis()
    ): ScoredDeal {
        val tags = mutableListOf<String>()
        val freshnessTier = deal.freshnessTier(nowMillis)

        val rawSum = (profile.discountWeight +
            profile.historicLowWeight +
            profile.socialWorthWeight +
            profile.personalAffinityWeight +
            profile.merchantTrustWeight +
            profile.freshnessWeight).coerceAtLeast(0.01f)

        val wDiscount = profile.discountWeight / rawSum
        val wHistoric = profile.historicLowWeight / rawSum
        val wSocial = profile.socialWorthWeight / rawSum
        val wPersonal = profile.personalAffinityWeight / rawSum
        val wMerchant = profile.merchantTrustWeight / rawSum
        val wFreshness = profile.freshnessWeight / rawSum

        // 1. Discount Magnitude Score (0..100)
        val effectivePrice = deal.effectiveCurrentPrice
        val discountScore = when {
            deal.discountPercent >= 50 -> {
                tags.add("${deal.discountPercent}% OFF")
                100f
            }
            deal.discountPercent >= 30 -> {
                tags.add("${deal.discountPercent}% OFF")
                75f + ((deal.discountPercent - 30) / 20f) * 25f
            }
            deal.discountPercent > 0 -> 25f + (deal.discountPercent / 30f) * 50f
            effectivePrice != null -> 45f
            else -> 30f
        }.coerceIn(0f, 100f)

        // 2. Observed Historic Low Score (0..100)
        val historicScore = when {
            deal.isHistoricLow && effectivePrice != null -> {
                tags.add("Observed Low")
                100f
            }
            effectivePrice != null && deal.historicLowPrice != null && deal.historicLowPrice > 0.0 -> {
                val delta = ((effectivePrice - deal.historicLowPrice) / deal.historicLowPrice).toFloat()
                when {
                    delta <= 0.02f -> {
                        tags.add("Matches Low")
                        92f
                    }
                    delta <= 0.08f -> 72f
                    delta <= 0.20f -> 50f
                    else -> 30f
                }
            }
            else -> 40f
        }

        // 3. Community Social Validation Score (0..100)
        val totalVotes = deal.worthVotes + deal.notWorthVotes
        val socialScore = if (totalVotes > 0) {
            val confidence = min(1.0f, totalVotes / 50f)
            val blended = (deal.worthRatio * 100f * confidence) + (68f * (1f - confidence))
            if (deal.worthRatio >= 0.85f && totalVotes >= 15) {
                tags.add("${(deal.worthRatio * 100).toInt()}% Worth")
            }
            blended.coerceIn(0f, 100f)
        } else {
            62f
        }

        // 4. Personal Preferences + Real Learned Behavioral Affinity (0..100)
        var personalScore = 30f
        if (profile.preferredCategories.contains(deal.category)) personalScore += 25f
        if (profile.preferredMerchants.contains(deal.merchant)) personalScore += 15f
        val matchesKeyword = profile.watchedKeywords.any { kw ->
            kw.isNotBlank() && (
                deal.title.contains(kw, ignoreCase = true) ||
                    deal.brand.contains(kw, ignoreCase = true) ||
                    deal.summary.contains(kw, ignoreCase = true)
                )
        }
        if (matchesKeyword) {
            personalScore += 35f
            tags.add("Keyword Hit")
        }
        val catBoost = behaviorAffinity.categoryBoosts[deal.category] ?: 0f
        val merchBoost = behaviorAffinity.merchantBoosts[deal.merchant] ?: 0f
        if (catBoost >= 10f || merchBoost >= 10f) tags.add("For You")
        personalScore = (personalScore + catBoost + merchBoost).coerceIn(0f, 100f)

        // 5. Merchant Trust, Shipping & Coupon Stackability Score (0..100)
        var merchantScore = when (deal.merchant) {
            USMerchant.AMAZON, USMerchant.BEST_BUY, USMerchant.COSTCO -> 78f
            USMerchant.WALMART, USMerchant.TARGET, USMerchant.B_AND_H, USMerchant.NEWEGG -> 74f
            USMerchant.HOMEDEPOT, USMerchant.MICRO_CENTER -> 72f
            USMerchant.STEAM_PC, USMerchant.EBAY -> 65f
            USMerchant.OTHER -> 55f
        }
        if (deal.freeShipping) merchantScore += 8f
        if (deal.coupon != null) {
            merchantScore += 10f
            tags.add("Code ${deal.coupon.code}")
        }
        if (deal.cashBackRate >= 0.04) merchantScore += 4f
        merchantScore = merchantScore.coerceIn(0f, 100f)

        // 6. Steep Time-Sensitive Freshness Score (half-life ~5.5 hours)
        val ageMinutes = max(0.0, (nowMillis - deal.publishedAtMillis) / 60_000.0)
        val freshnessScore = when {
            ageMinutes <= 30.0 -> {
                tags.add(0, "FLASH <30m")
                100f
            }
            ageMinutes <= 120.0 -> {
                tags.add("Fresh Drop")
                (95.0 - ((ageMinutes - 30.0) / 90.0) * 12.0).toFloat()
            }
            else -> {
                val ageHours = ageMinutes / 60.0
                (83.0 * exp(-(ageHours - 2.0) / 7.5)).toFloat().coerceIn(5f, 83f)
            }
        }

        // Verification Adjustment: reward live-verified deals, penalize price-increased/OOS
        val verificationModifier = when (deal.verificationStatus) {
            VerificationStatus.VERIFIED_LIVE -> {
                tags.add(0, "Verified Live")
                12f
            }
            VerificationStatus.PRICE_INCREASED -> -25f
            VerificationStatus.OUT_OF_STOCK -> -60f
            VerificationStatus.UNVERIFIED, VerificationStatus.STORE_CHECK_REQUIRED -> 0f
        }

        val composite = (
            (discountScore * wDiscount) +
                (historicScore * wHistoric) +
                (socialScore * wSocial) +
                (personalScore * wPersonal) +
                (merchantScore * wMerchant) +
                (freshnessScore * wFreshness) +
                verificationModifier
            ).coerceIn(0f, 100f)

        val reasonTitle = when {
            deal.verificationStatus == VerificationStatus.OUT_OF_STOCK -> "Out of stock / ended at retailer"
            deal.verificationStatus == VerificationStatus.PRICE_INCREASED -> "Live retailer price rose above advertised deal"
            deal.verificationStatus == VerificationStatus.VERIFIED_LIVE && matchesKeyword ->
                "Verified live in stock & matches your keyword radar"
            matchesKeyword -> "Flash match for your watched keyword radar"
            freshnessTier == FreshnessTier.FLASH -> "Just dropped (<30m ago) — act fast before stock runs out"
            deal.verificationStatus == VerificationStatus.VERIFIED_LIVE ->
                "Verified live on ${deal.merchant.displayName}"
            deal.isHistoricLow && deal.discountPercent >= 25 -> "Lowest observed price with ${deal.discountPercent}% savings"
            deal.coupon != null -> "Promo code ${deal.coupon.code} ready for 1-tap clip"
            else -> "Live deal from ${deal.sourceName}"
        }

        val breakdown = ScoreBreakdown(
            discountScore = discountScore.toInt(),
            historicLowScore = historicScore.toInt(),
            socialScore = socialScore.toInt(),
            personalAndBehaviorScore = personalScore.toInt(),
            merchantScore = merchantScore.toInt(),
            freshnessScore = freshnessScore.toInt()
        )

        return ScoredDeal(
            deal = deal,
            compositeScore = composite,
            recommendationReason = RecommendationReason(
                title = reasonTitle,
                score = composite.toInt(),
                tags = tags.distinct().take(3),
                breakdown = breakdown
            ),
            matchesWatchedKeyword = matchesKeyword,
            freshnessTier = freshnessTier
        )
    }

    /**
     * Only dispatches real push notifications for fresh (< 4 hours), active, non-expired deals
     * so users never receive stale alerts for already-dead flash sales.
     */
    fun shouldTriggerPushNotification(
        scoredDeal: ScoredDeal,
        profile: UserPreferenceProfile,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (!profile.pushNotificationsEnabled) return false
        val deal = scoredDeal.deal
        if (deal.isExpired || deal.verificationStatus == VerificationStatus.OUT_OF_STOCK || deal.verificationStatus == VerificationStatus.PRICE_INCREASED) {
            return false
        }
        // Never push stale deals older than 4 hours
        val ageMillis = (nowMillis - deal.publishedAtMillis).coerceAtLeast(0L)
        if (ageMillis > 4L * 3_600_000L) return false

        val effectivePrice = deal.effectiveCurrentPrice
        if (effectivePrice != null && effectivePrice > profile.maxBudgetUSD) return false

        if (scoredDeal.matchesWatchedKeyword) return true

        val meetsDiscount = deal.discountPercent >= profile.minimumDiscountThreshold
        val matchesPreferredCategory = profile.preferredCategories.contains(deal.category)
        val highConfidence = scoredDeal.compositeScore >= 72f

        return meetsDiscount && matchesPreferredCategory && (deal.isHistoricLow || highConfidence)
    }
}
