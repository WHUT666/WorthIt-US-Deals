package com.example.repository

import com.example.algorithm.DealPushRecommendationEngine
import com.example.data.local.ConversionEventEntity
import com.example.data.local.DealDao
import com.example.data.local.DealEntity
import com.example.data.local.PriceSnapshotEntity
import com.example.data.local.TrackedProductEntity
import com.example.data.local.UserPreferencesDataStore
import com.example.data.network.DealTextParser
import com.example.data.network.LiveDealNetworkClient
import com.example.model.ConversionFunnelStats
import com.example.model.CouponDetail
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.DirectRetailerLink
import com.example.model.LiveComparisonGroup
import com.example.model.LiveSearchResponse
import com.example.model.MerchantPriceQuote
import com.example.model.MonitorSourceStatus
import com.example.model.PricePoint
import com.example.model.TrackedUrlWatch
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import com.example.notification.DealNotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncOutcome(
    val fetchedCount: Int,
    val healthySources: Int,
    val totalSources: Int,
    val triggeredAlerts: List<DealPushRecommendationEngine.ScoredDeal>
)

class WorthItRepository(
    private val dealDao: DealDao,
    private val prefsDataStore: UserPreferencesDataStore,
    private val networkClient: LiveDealNetworkClient,
    private val notificationManager: DealNotificationManager
) {
    private val _sourceStatuses = MutableStateFlow<List<MonitorSourceStatus>>(emptyList())
    val sourceStatuses: StateFlow<List<MonitorSourceStatus>> = _sourceStatuses.asStateFlow()

    val userProfileFlow: Flow<UserPreferenceProfile> = prefsDataStore.userProfileFlow

    val allDealsFlow: Flow<List<DealItem>> = dealDao.observeAllDeals().map { entities ->
        entities.map { it.toDomain() }
    }

    val savedDealsFlow: Flow<List<DealItem>> = dealDao.observeSavedDeals().map { entities ->
        entities.map { it.toDomain() }
    }

    val trackedUrlsFlow: Flow<List<TrackedUrlWatch>> = dealDao.observeTrackedUrls().map { entities ->
        entities.map { entity ->
            TrackedUrlWatch(
                id = entity.id,
                label = entity.label,
                url = entity.url,
                merchant = USMerchant.fromNameSafe(entity.merchantName),
                targetPrice = entity.targetPrice,
                lastObservedPrice = entity.lastObservedPrice,
                lastStatusMessage = entity.lastStatusMessage,
                lastCheckedAtMillis = entity.lastCheckedAtMillis
            )
        }
    }

    val behaviorAffinityFlow: Flow<UserBehaviorAffinity> = combine(
        dealDao.observeCategoryAffinityCounts(),
        dealDao.observeMerchantAffinityCounts()
    ) { catRows, merchRows ->
        val maxCat = catRows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
        val maxMerch = merchRows.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1

        val catBoosts = catRows.associate { row ->
            DealCategory.fromNameSafe(row.categoryName) to ((row.count.toFloat() / maxCat) * 28f).coerceAtMost(28f)
        }
        val merchBoosts = merchRows.associate { row ->
            USMerchant.fromNameSafe(row.merchantName) to ((row.count.toFloat() / maxMerch) * 20f).coerceAtMost(20f)
        }
        UserBehaviorAffinity(
            categoryBoosts = catBoosts,
            merchantBoosts = merchBoosts,
            totalInteractions = catRows.sumOf { it.count }
        )
    }

    val conversionFunnelStatsFlow: Flow<ConversionFunnelStats> = combine(
        dealDao.observeFunnelStageCounts(),
        dealDao.observeMerchantAffinityCounts(),
        dealDao.observeCategoryAffinityCounts()
    ) { stages, merchRows, catRows ->
        val map = stages.associate { it.stage to it.count }
        ConversionFunnelStats(
            impressions = map["IMPRESSION"] ?: 0,
            detailViews = map["DETAIL_VIEW"] ?: 0,
            couponCopies = map["COUPON_COPY"] ?: 0,
            outboundClicks = map["OUTBOUND_CLICK"] ?: 0,
            bookmarks = map["BOOKMARK"] ?: 0,
            worthVotes = map["VOTE_WORTH"] ?: 0,
            topConvertingMerchant = merchRows.firstOrNull()?.let { USMerchant.fromNameSafe(it.merchantName) },
            topInterestCategory = catRows.firstOrNull()?.let { DealCategory.fromNameSafe(it.categoryName) }
        )
    }

    fun observePriceHistoryForDeal(dealId: String): Flow<List<PricePoint>> {
        val formatter = SimpleDateFormat("MMM d HH:mm", Locale.US)
        return dealDao.observePriceSnapshotsForDeal(dealId).map { snapshots ->
            snapshots.map { snap ->
                PricePoint(
                    dateLabel = formatter.format(Date(snap.recordedAtMillis)),
                    price = snap.price,
                    timestampMillis = snap.recordedAtMillis
                )
            }
        }
    }

    /**
     * Fetches real live deals across all monitored US sources, records real price snapshots
     * in Room, computes observed historic lows, and triggers real Android notifications for
     * newly qualified high-value deals.
     */
    suspend fun syncLiveDeals(): SyncOutcome {
        val batch = networkClient.fetchAllLiveFeeds()
        _sourceStatuses.value = batch.sourceStatuses

        val profile = userProfileFlow.first()
        val affinity = behaviorAffinityFlow.first()
        val now = System.currentTimeMillis()
        val triggeredAlerts = mutableListOf<DealPushRecommendationEngine.ScoredDeal>()

        // Deduplicate by deal id before persisting
        val distinctDeals = batch.deals.distinctBy { it.id }
        val entitiesToUpsert = mutableListOf<DealEntity>()

        for (incoming in distinctDeals) {
            val existing = dealDao.getDealById(incoming.id)
            val productKey = DealTextParser.normalizeProductKey(incoming.title)

            // Record real price snapshot if price is newly observed or changed
            if (incoming.currentPrice != null) {
                val latestSnap = dealDao.getLatestSnapshotForDeal(incoming.id)
                if (latestSnap == null || kotlin.math.abs(latestSnap.price - incoming.currentPrice) >= 0.01) {
                    dealDao.insertPriceSnapshot(
                        PriceSnapshotEntity(
                            dealId = incoming.id,
                            productKey = productKey,
                            merchantName = incoming.merchant.name,
                            price = incoming.currentPrice,
                            recordedAtMillis = now
                        )
                    )
                }
            }

            val observedMin = if (incoming.currentPrice != null) {
                val dbMin = dealDao.getLowestObservedPrice(incoming.id, productKey)
                listOfNotNull(dbMin, incoming.currentPrice, existing?.historicLowPrice).minOrNull()
            } else {
                existing?.historicLowPrice
            }

            val mergedWorthVotes = maxOf(incoming.worthVotes, existing?.worthVotes ?: 0)
            val mergedNotWorthVotes = maxOf(incoming.notWorthVotes, existing?.notWorthVotes ?: 0)
            val isAlreadyNotified = existing?.notificationSent ?: false

            val entity = DealEntity(
                id = incoming.id,
                title = incoming.title,
                brand = incoming.brand,
                merchantName = incoming.merchant.name,
                categoryName = incoming.category.name,
                currentPrice = incoming.currentPrice ?: existing?.currentPrice,
                originalPrice = incoming.originalPrice ?: existing?.originalPrice,
                historicLowPrice = observedMin,
                couponCode = incoming.coupon?.code ?: existing?.couponCode,
                couponDescription = incoming.coupon?.discountDescription ?: existing?.couponDescription,
                cashBackRate = incoming.cashBackRate,
                freeShipping = incoming.freeShipping,
                worthVotes = mergedWorthVotes,
                notWorthVotes = mergedNotWorthVotes,
                communityCommentsCount = maxOf(incoming.communityCommentsCount, existing?.communityCommentsCount ?: 0),
                sourceName = incoming.sourceName,
                imageUrl = incoming.imageUrl ?: existing?.imageUrl,
                buyUrl = incoming.buyUrl,
                sourceUrl = incoming.sourceUrl,
                summary = incoming.summary.ifBlank { existing?.summary.orEmpty() },
                publishedAtMillis = incoming.publishedAtMillis,
                lastCheckedAtMillis = now,
                isSaved = existing?.isSaved ?: false,
                userVote = existing?.userVote ?: 0,
                notificationSent = isAlreadyNotified
            )

            entitiesToUpsert.add(entity)

            // Evaluate push notification trigger for new/unnotified deals (cap 2 per sync to avoid spam)
            if (!isAlreadyNotified && triggeredAlerts.size < 2) {
                val domainDeal = entity.toDomain()
                val scored = DealPushRecommendationEngine.calculateScore(domainDeal, profile, affinity, now)
                if (DealPushRecommendationEngine.shouldTriggerPushNotification(scored, profile)) {
                    triggeredAlerts.add(scored)
                }
            }
        }

        if (entitiesToUpsert.isNotEmpty()) {
            dealDao.upsertDeals(entitiesToUpsert)
        }

        // Dispatch real system notifications and mark sent in Room
        for (scoredAlert in triggeredAlerts) {
            notificationManager.postDealAlertNotification(
                deal = scoredAlert.deal,
                reasonTitle = scoredAlert.recommendationReason.title
            )
            dealDao.markNotificationSent(scoredAlert.deal.id)
        }

        // Prune unsaved deals older than 5 days to keep local cache clean
        val fiveDaysAgo = now - (5L * 24L * 3_600_000L)
        dealDao.pruneStaleDeals(fiveDaysAgo)

        return SyncOutcome(
            fetchedCount = distinctDeals.size,
            healthySources = batch.sourceStatuses.count { it.isHealthy },
            totalSources = batch.sourceStatuses.size,
            triggeredAlerts = triggeredAlerts
        )
    }

    /**
     * Real-time multi-platform live search across Slickdeals RSS + Reddit Search JSON +
     * CheapShark API + Room local cache. Groups genuine fetched offers by product & merchant,
     * and includes direct 1-tap live retailer links for all 8 major US stores.
     */
    suspend fun searchMultiPlatformLive(query: String): LiveSearchResponse {
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())

        val directLinks = USMerchant.majorUSRetailers.map { merchant ->
            DirectRetailerLink(
                merchant = merchant,
                searchUrl = String.format(Locale.US, merchant.searchUrlTemplate, encoded)
            )
        }

        if (trimmed.isBlank()) {
            return LiveSearchResponse(
                query = "",
                groups = emptyList(),
                totalLiveOffers = 0,
                sourcesTelemetry = emptyList(),
                directRetailerLinks = emptyList(),
                searchedAtMillis = now
            )
        }

        val liveBatch = networkClient.searchLiveAcrossSources(trimmed)
        val cachedMatches = dealDao.searchCachedDeals(trimmed).map { it.toDomain() }

        // Merge & deduplicate live + cached matches
        val merged = (liveBatch.deals + cachedMatches)
            .distinctBy { "${it.merchant.name}|${it.title.lowercase(Locale.US).take(55)}" }
            .filter { deal ->
                val tokens = trimmed.lowercase(Locale.US).split(Regex("""\s+""")).filter { it.isNotBlank() }
                tokens.any { t ->
                    deal.title.contains(t, ignoreCase = true) ||
                        deal.brand.contains(t, ignoreCase = true) ||
                        deal.summary.contains(t, ignoreCase = true)
                }
            }

        // Persist newly discovered live search deals into Room cache & record real price snapshots
        for (deal in liveBatch.deals.take(20)) {
            val existing = dealDao.getDealById(deal.id)
            if (existing == null) {
                dealDao.upsertDeal(deal.toEntity())
            }
            if (deal.currentPrice != null) {
                val latest = dealDao.getLatestSnapshotForDeal(deal.id)
                if (latest == null || kotlin.math.abs(latest.price - deal.currentPrice) >= 0.01) {
                    dealDao.insertPriceSnapshot(
                        PriceSnapshotEntity(
                            dealId = deal.id,
                            productKey = DealTextParser.normalizeProductKey(deal.title),
                            merchantName = deal.merchant.name,
                            price = deal.currentPrice,
                            recordedAtMillis = now
                        )
                    )
                }
            }
        }

        // Group offers by normalized brand + product cluster to compare across merchants
        val grouped = merged.groupBy { deal ->
            val keyTokens = DealTextParser.normalizeProductKey(deal.title).split("_").take(3).joinToString("_")
            "${deal.brand.lowercase(Locale.US)}_$keyTokens"
        }.map { (groupKey, items) ->
            val sortedItems = items.sortedWith(
                compareBy<DealItem> { it.currentPrice == null }
                    .thenBy { it.currentPrice ?: Double.MAX_VALUE }
                    .thenByDescending { it.publishedAtMillis }
            )
            val priced = sortedItems.mapNotNull { it.currentPrice }
            val lowestPrice = priced.minOrNull()
            val highestPrice = priced.maxOrNull()
            val lead = sortedItems.first()

            val quotes = sortedItems.mapIndexed { idx, item ->
                MerchantPriceQuote(
                    id = item.id,
                    merchant = item.merchant,
                    title = item.title,
                    price = item.currentPrice,
                    originalPrice = item.originalPrice,
                    couponCode = item.coupon?.code,
                    sourceName = item.sourceName,
                    buyUrl = item.buyUrl,
                    imageUrl = item.imageUrl,
                    isLowestAmongAll = idx == 0 && item.currentPrice != null,
                    publishedAtMillis = item.publishedAtMillis,
                    fetchedAtMillis = now
                )
            }

            LiveComparisonGroup(
                id = groupKey,
                groupTitle = lead.title,
                brand = lead.brand,
                category = lead.category,
                bestPrice = lowestPrice,
                highestPrice = highestPrice,
                bestMerchant = lead.merchant,
                quotes = quotes
            )
        }.sortedWith(
            compareByDescending<LiveComparisonGroup> { it.quotes.size > 1 }
                .thenBy { it.bestPrice == null }
                .thenByDescending { it.quotes.firstOrNull()?.publishedAtMillis ?: 0L }
        )

        return LiveSearchResponse(
            query = trimmed,
            groups = grouped,
            totalLiveOffers = merged.size,
            sourcesTelemetry = liveBatch.sourceStatuses,
            directRetailerLinks = directLinks,
            searchedAtMillis = now
        )
    }

    /**
     * Adds a user-specified US retailer product URL, immediately fetches live price metadata
     * over HTTP, persists in Room, and fires an alert if the target price is met.
     */
    suspend fun addAndCheckTrackedUrl(
        rawUrl: String,
        customLabel: String,
        targetPrice: Double?
    ): Result<TrackedUrlWatch> {
        val url = rawUrl.trim()
        if (!DealTextParser.isSafePublicHttpUrl(url)) {
            return Result.failure(IllegalArgumentException("Enter a valid public https:// retailer product URL."))
        }
        val merchant = DealTextParser.detectMerchant(customLabel, url)
        val now = System.currentTimeMillis()
        val fetchResult = networkClient.fetchDirectProductUrl(url)

        val resolvedLabel: String
        val observedPrice: Double?
        val statusMsg: String

        if (fetchResult.isSuccess) {
            val page = fetchResult.getOrThrow()
            resolvedLabel = customLabel.ifBlank { page.title ?: "${merchant.displayName} Product" }
            observedPrice = page.price
            statusMsg = when {
                observedPrice != null && targetPrice != null && observedPrice <= targetPrice ->
                    "Target hit! Live price $${String.format(Locale.US, "%.2f", observedPrice)}"
                observedPrice != null ->
                    "Live verified: $${String.format(Locale.US, "%.2f", observedPrice)}" +
                        (page.availability?.let { " • $it" } ?: "")
                else -> "Page loaded, price hidden behind retailer script"
            }
        } else {
            resolvedLabel = customLabel.ifBlank { "${merchant.displayName} Watch Item" }
            observedPrice = null
            statusMsg = fetchResult.exceptionOrNull()?.message ?: "Unable to fetch price right now"
        }

        val id = dealDao.insertTrackedUrl(
            TrackedProductEntity(
                label = resolvedLabel,
                url = url,
                merchantName = merchant.name,
                targetPrice = targetPrice,
                lastObservedPrice = observedPrice,
                lastStatusMessage = statusMsg,
                lastCheckedAtMillis = now
            )
        )

        if (observedPrice != null) {
            dealDao.insertPriceSnapshot(
                PriceSnapshotEntity(
                    dealId = "tracked_$id",
                    productKey = DealTextParser.normalizeProductKey(resolvedLabel),
                    merchantName = merchant.name,
                    price = observedPrice,
                    recordedAtMillis = now
                )
            )
            if (targetPrice != null && observedPrice <= targetPrice) {
                notificationManager.postTrackedUrlDropNotification(
                    label = resolvedLabel,
                    price = observedPrice,
                    targetPrice = targetPrice,
                    url = url
                )
            }
        }

        return Result.success(
            TrackedUrlWatch(
                id = id,
                label = resolvedLabel,
                url = url,
                merchant = merchant,
                targetPrice = targetPrice,
                lastObservedPrice = observedPrice,
                lastStatusMessage = statusMsg,
                lastCheckedAtMillis = now
            )
        )
    }

    suspend fun refreshAllTrackedUrls(): Int {
        val items = dealDao.getAllTrackedUrlsOnce()
        val now = System.currentTimeMillis()
        var updatedCount = 0
        for (item in items) {
            val res = networkClient.fetchDirectProductUrl(item.url)
            if (res.isSuccess) {
                val page = res.getOrThrow()
                val label = item.label.ifBlank { page.title ?: item.label }
                val price = page.price ?: item.lastObservedPrice
                val status = if (page.price != null) {
                    "Live verified: $${String.format(Locale.US, "%.2f", page.price)}"
                } else {
                    "Checked (no structured price exposed)"
                }
                dealDao.updateTrackedUrlResult(item.id, label, price, status, now)
                if (page.price != null) {
                    updatedCount++
                    dealDao.insertPriceSnapshot(
                        PriceSnapshotEntity(
                            dealId = "tracked_${item.id}",
                            productKey = DealTextParser.normalizeProductKey(label),
                            merchantName = item.merchantName,
                            price = page.price,
                            recordedAtMillis = now
                        )
                    )
                    if (item.targetPrice != null && page.price <= item.targetPrice) {
                        notificationManager.postTrackedUrlDropNotification(
                            label = label,
                            price = page.price,
                            targetPrice = item.targetPrice,
                            url = item.url
                        )
                    }
                }
            } else {
                dealDao.updateTrackedUrlResult(
                    item.id,
                    item.label,
                    item.lastObservedPrice,
                    res.exceptionOrNull()?.message?.take(70) ?: "Check failed",
                    now
                )
            }
        }
        return updatedCount
    }

    suspend fun removeTrackedUrl(id: Long) {
        dealDao.deleteTrackedUrl(id)
    }

    suspend fun toggleSaveDeal(deal: DealItem) {
        val newSaved = !deal.isSaved
        dealDao.updateSavedState(deal.id, newSaved)
        if (newSaved) {
            logConversionEvent(deal.id, deal.merchant, deal.category, "BOOKMARK")
        }
    }

    suspend fun voteDeal(deal: DealItem, isWorth: Boolean) {
        val desiredVote = if (isWorth) 1 else -1
        val currentVote = when (deal.userVote) {
            true -> 1
            false -> -1
            null -> 0
        }
        if (currentVote == desiredVote) return

        var worth = deal.worthVotes
        var notWorth = deal.notWorthVotes
        if (currentVote == 1) worth = (worth - 1).coerceAtLeast(0)
        if (currentVote == -1) notWorth = (notWorth - 1).coerceAtLeast(0)
        if (desiredVote == 1) worth++ else notWorth++

        dealDao.updateVoteState(deal.id, desiredVote, worth, notWorth)
        if (isWorth) {
            logConversionEvent(deal.id, deal.merchant, deal.category, "VOTE_WORTH")
        }
    }

    suspend fun logConversionEvent(
        dealId: String,
        merchant: USMerchant,
        category: DealCategory,
        stage: String
    ) {
        dealDao.insertConversionEvent(
            ConversionEventEntity(
                dealId = dealId,
                merchantName = merchant.name,
                categoryName = category.name,
                stage = stage,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }

    suspend fun resetBehavioralLearning() {
        dealDao.clearConversionEvents()
    }

    suspend fun saveUserProfile(profile: UserPreferenceProfile) {
        prefsDataStore.saveProfile(profile)
    }

    fun sendNotificationForDeal(deal: DealItem, reason: String): Boolean {
        return notificationManager.postDealAlertNotification(deal, reason)
    }

    fun hasNotificationPermission(): Boolean = notificationManager.hasNotificationPermission()

    private fun DealEntity.toDomain(): DealItem {
        val merchant = USMerchant.fromNameSafe(merchantName)
        val category = DealCategory.fromNameSafe(categoryName)
        val isLow = currentPrice != null && historicLowPrice != null && currentPrice <= historicLowPrice + 0.01
        return DealItem(
            id = id,
            title = title,
            brand = brand,
            merchant = merchant,
            category = category,
            currentPrice = currentPrice,
            originalPrice = originalPrice,
            historicLowPrice = historicLowPrice,
            isHistoricLow = isLow,
            coupon = couponCode?.let { CouponDetail(it, couponDescription ?: "Apply code $it at checkout") },
            cashBackRate = cashBackRate,
            freeShipping = freeShipping,
            worthVotes = worthVotes,
            notWorthVotes = notWorthVotes,
            communityCommentsCount = communityCommentsCount,
            sourceName = sourceName,
            imageUrl = imageUrl,
            buyUrl = buyUrl,
            sourceUrl = sourceUrl,
            summary = summary,
            publishedAtMillis = publishedAtMillis,
            lastCheckedAtMillis = lastCheckedAtMillis,
            isSaved = isSaved,
            userVote = when (userVote) {
                1 -> true
                -1 -> false
                else -> null
            }
        )
    }

    private fun DealItem.toEntity(): DealEntity = DealEntity(
        id = id,
        title = title,
        brand = brand,
        merchantName = merchant.name,
        categoryName = category.name,
        currentPrice = currentPrice,
        originalPrice = originalPrice,
        historicLowPrice = historicLowPrice,
        couponCode = coupon?.code,
        couponDescription = coupon?.discountDescription,
        cashBackRate = cashBackRate,
        freeShipping = freeShipping,
        worthVotes = worthVotes,
        notWorthVotes = notWorthVotes,
        communityCommentsCount = communityCommentsCount,
        sourceName = sourceName,
        imageUrl = imageUrl,
        buyUrl = buyUrl,
        sourceUrl = sourceUrl,
        summary = summary,
        publishedAtMillis = publishedAtMillis,
        lastCheckedAtMillis = lastCheckedAtMillis,
        isSaved = isSaved,
        userVote = when (userVote) {
            true -> 1
            false -> -1
            null -> 0
        }
    )
}
