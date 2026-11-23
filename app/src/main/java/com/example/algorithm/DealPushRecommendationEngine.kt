package com.example.algorithm

import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.RecommendationReason
import com.example.model.ScoreBreakdown
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * WorthIt DealRank & Push Qualification Engine
 *
 * Six-factor normalized weighted scoring model with closed-loop behavioral learning:
 *   S_total = w_d * S_discount
 *           + w_h * S_historic_low
 *           + w_s * S_social_trust
 *           + w_p * S_personal_and_behavior
 *           + w_m * S_merchant_fulfillment
 *           + w_v * S_time_freshness
 *
 * Weights are user-tunable via DataStore and normalized to sum to 1.0.
 * Behavioral boosts are learned dynamically from Room ConversionEventEntity logs.
 */
object DealPushRecommendationEngine {

    data class ScoredDeal(
        val deal: DealItem,
        val compositeScore: Float,
        val recommendationReason: RecommendationReason,
        val matchesWatchedKeyword: Boolean
    )

    fun scoreAndRankDeals(
        deals: List<DealItem>,
        profile: UserPreferenceProfile,
        behaviorAffinity: UserBehaviorAffinity = UserBehaviorAffinity(),
        filterCategory: DealCategory? = null,
        searchQuery: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): List<ScoredDeal> {
        val tokens = searchQuery.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }

        val filtered = deals.filter { deal ->
            val matchCategory = filterCategory == null || filterCategory == DealCategory.ALL || deal.category == filterCategory
            val matchQuery = tokens.isEmpty() || tokens.all { token ->
                deal.title.contains(token, ignoreCase = true) ||
                    deal.brand.contains(token, ignoreCase = true) ||
                    deal.merchant.displayName.contains(token, ignoreCase = true) ||
                    deal.sourceName.contains(token, ignoreCase = true) ||
                    deal.summary.contains(token, ignoreCase = true)
            }
            val matchBudget = deal.currentPrice == null || deal.currentPrice <= profile.maxBudgetUSD
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

        // Normalize weights so user adjustments always sum to 1.0
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
        val discountScore = when {
            deal.discountPercent >= 50 -> {
                tags.add("${deal.discountPercent}% OFF")
                100f
            }
            deal.discountPercent >= 30 -> {
                tags.add("${deal.discountPercent}% OFF")
                75f + ((deal.discountPercent - 30) / 20f) * 25f
            }
            deal.discountPercent > 0 -> {
                25f + (deal.discountPercent / 30f) * 50f
            }
            deal.currentPrice != null -> 45f // Verified live price without stated MSRP
            else -> 30f
        }.coerceIn(0f, 100f)

        // 2. Observed Historic Low Score (0..100)
        val historicScore = when {
            deal.isHistoricLow && deal.currentPrice != null -> {
                tags.add("Observed Low")
                100f
            }
            deal.currentPrice != null && deal.historicLowPrice != null && deal.historicLowPrice > 0.0 -> {
                val delta = ((deal.currentPrice - deal.historicLowPrice) / deal.historicLowPrice).toFloat()
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

        // 3. Community Social Validation Score (Wilson-style dampened Worth ratio, 0..100)
        val totalVotes = deal.worthVotes + deal.notWorthVotes
        val socialScore = if (totalVotes > 0) {
            val confidence = min(1.0f, totalVotes / 50f)
            val baseRatio = deal.worthRatio * 100f
            // Blend toward neutral 68 when vote volume is small
            val blended = (baseRatio * confidence) + (68f * (1f - confidence))
            if (deal.worthRatio >= 0.85f && totalVotes >= 15) {
                tags.add("${(deal.worthRatio * 100).toInt()}% Worth")
            }
            blended.coerceIn(0f, 100f)
        } else {
            62f
        }

        // 4. Personal Preferences + Real Learned Behavioral Affinity (0..100)
        var personalScore = 30f
        if (profile.preferredCategories.contains(deal.category)) {
            personalScore += 25f
        }
        if (profile.preferredMerchants.contains(deal.merchant)) {
            personalScore += 15f
        }
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
        // Closed-loop behavioral learning boost from Room conversion history
        val catBoost = behaviorAffinity.categoryBoosts[deal.category] ?: 0f
        val merchBoost = behaviorAffinity.merchantBoosts[deal.merchant] ?: 0f
        if (catBoost >= 10f || merchBoost >= 10f) {
            tags.add("For You")
        }
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
            tags.add("Coupon ${deal.coupon.code}")
        }
        if (deal.cashBackRate >= 0.04) merchantScore += 4f
        merchantScore = merchantScore.coerceIn(0f, 100f)

        // 6. Time-Decay Freshness Score (exponential half-life ~18 hours)
        val ageHours = max(0.0, (nowMillis - deal.publishedAtMillis) / 3_600_000.0)
        val freshnessScore = (100.0 * exp(-ageHours / 24.0)).toFloat().coerceIn(10f, 100f)
        if (ageHours < 1.5) {
            tags.add("Just Spotted")
        }

        val composite = (discountScore * wDiscount) +
            (historicScore * wHistoric) +
            (socialScore * wSocial) +
            (personalScore * wPersonal) +
            (merchantScore * wMerchant) +
            (freshnessScore * wFreshness)

        val reasonTitle = when {
            matchesKeyword -> "Matches your watched keyword radar"
            deal.isHistoricLow && deal.discountPercent >= 25 -> "Lowest observed price with ${deal.discountPercent}% savings"
            deal.coupon != null -> "Verified promo code ${deal.coupon.code} ready to clip"
            catBoost >= 12f -> "Tailored to your recent ${deal.category.displayName} activity"
            deal.discountPercent >= 35 -> "Deep ${deal.discountPercent}% discount at ${deal.merchant.displayName}"
            else -> "Live verified deal from ${deal.sourceName}"
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
            matchesWatchedKeyword = matchesKeyword
        )
    }

    /**
     * Qualification gate for dispatching real Android system push notifications.
     * Prevents notification fatigue by gating on either a watched keyword match
     * or a verified deep discount / observed low in the user's preferred categories.
     */
    fun shouldTriggerPushNotification(
        scoredDeal: ScoredDeal,
        profile: UserPreferenceProfile
    ): Boolean {
        if (!profile.pushNotificationsEnabled) return false
        val deal = scoredDeal.deal
        if (deal.currentPrice != null && deal.currentPrice > profile.maxBudgetUSD) return false

        if (scoredDeal.matchesWatchedKeyword) return true

        val meetsDiscount = deal.discountPercent >= profile.minimumDiscountThreshold
        val matchesPreferredCategory = profile.preferredCategories.contains(deal.category)
        val highConfidence = scoredDeal.compositeScore >= 72f

        return meetsDiscount && matchesPreferredCategory && (deal.isHistoricLow || highConfidence)
    }
}
