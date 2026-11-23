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
import com.example.data.network.LiveVerificationResult
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
import com.example.model.VerificationStatus
import com.example.notification.DealNotificationManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class StreamingSyncProgress(
    val completedSources: Int,
    val totalSources: Int,
    val healthySources: Int,
    val totalFetchedDeals: Int,
    val triggeredAlerts: List<DealPushRecommendationEngine.ScoredDeal>,
    val isFinished: Boolean
)

class WorthItRepository(
    private val dealDao: DealDao,
    private val prefsDataStore: UserPreferencesDataStore,
    private val networkClient: LiveDealNetworkClient,
    private val notificationManager: DealNotificationManager
) {
    private val _sourceStatuses = MutableStateFlow<List<MonitorSourceStatus>>(emptyList())
    val sourceStatuses: StateFlow<List<MonitorSourceStatus>> = _sourceStatuses.asStateFlow()

    // 60-second TTL search cache for 0ms repeat / tab-switch queries
    private data class CachedSearchEntry(val response: LiveSearchResponse, val cachedAtMillis: Long)
    private val searchCache = ConcurrentHashMap<String, CachedSearchEntry>()

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
     * Progressive streaming live sync: ingests each source into Room using bulk batched SQLite
     * queries the instant that source returns (typically 250–600ms), never waiting for slow feeds.
     */
    fun syncLiveDealsStreaming(): Flow<StreamingSyncProgress> = flow {
        val profile = userProfileFlow.first()
        val affinity = behaviorAffinityFlow.first()

        val statusMap = linkedMapOf<String, MonitorSourceStatus>()
        var totalFetched = 0
        val triggeredAlerts = mutableListOf<DealPushRecommendationEngine.ScoredDeal>()

        networkClient.streamAllLiveFeeds().collect { singleResult ->
            statusMap[singleResult.status.sourceId] = singleResult.status
            _sourceStatuses.value = statusMap.values.toList()

            if (singleResult.deals.isNotEmpty()) {
                val batchAlerts = ingestDealBatchFast(
                    incomingDeals = singleResult.deals,
                    profile = profile,
                    affinity = affinity,
                    remainingAlertBudget = (2 - triggeredAlerts.size).coerceAtLeast(0)
                )
                totalFetched += singleResult.deals.size
                triggeredAlerts.addAll(batchAlerts)
            }

            emit(
                StreamingSyncProgress(
                    completedSources = statusMap.size,
                    totalSources = LiveDealNetworkClient.TOTAL_FEED_SOURCES,
                    healthySources = statusMap.values.count { it.isHealthy },
                    totalFetchedDeals = totalFetched,
                    triggeredAlerts = triggeredAlerts.toList(),
                    isFinished = false
                )
            )
        }

        // Dispatch real notifications for newly qualified fresh deals
        if (triggeredAlerts.isNotEmpty()) {
            val notifiedIds = mutableListOf<String>()
            for (scoredAlert in triggeredAlerts) {
                notificationManager.postDealAlertNotification(
                    deal = scoredAlert.deal,
                    reasonTitle = scoredAlert.recommendationReason.title
                )
                notifiedIds.add(scoredAlert.deal.id)
            }
            dealDao.markNotificationsSent(notifiedIds)
        }

        // Prune stale (>48h unsaved) and expired (>6h unsaved) listings to keep feed sharp & fast
        val now = System.currentTimeMillis()
        dealDao.pruneStaleDeals(
            cutoffMillis = now - (48L * 3_600_000L),
            expiredCutoffMillis = now - (6L * 3_600_000L)
        )

        emit(
            StreamingSyncProgress(
                completedSources = statusMap.size,
                totalSources = LiveDealNetworkClient.TOTAL_FEED_SOURCES,
                healthySources = statusMap.values.count { it.isHealthy },
                totalFetchedDeals = totalFetched,
                triggeredAlerts = triggeredAlerts.toList(),
                isFinished = true
            )
        )
    }

    /**
     * Batched O(1) SQLite round-trip ingestion eliminating the N+1 query bottleneck.
     */
    private suspend fun ingestDealBatchFast(
        incomingDeals: List<DealItem>,
        profile: UserPreferenceProfile,
        affinity: UserBehaviorAffinity,
        remainingAlertBudget: Int
    ): List<DealPushRecommendationEngine.ScoredDeal> {
        val distinctDeals = incomingDeals.distinctBy { it.id }
        if (distinctDeals.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val ids = distinctDeals.map { it.id }
        val productKeys = distinctDeals.map { DealTextParser.normalizeProductKey(it.title) }.distinct()

        // 3 bulk queries instead of 3 * N queries
        val existingById = dealDao.getDealsByIds(ids).associateBy { it.id }
        val latestSnapByDealId = dealDao.getSnapshotsForDeals(ids)
            .groupBy { it.dealId }
            .mapValues { (_, list) -> list.firstOrNull() }
        val minPriceByKey = dealDao.getMinPricesByProductKeys(productKeys)
            .associate { it.productKey to it.minPrice }

        val entitiesToUpsert = ArrayList<DealEntity>(distinctDeals.size)
        val snapshotsToInsert = ArrayList<PriceSnapshotEntity>()
        val batchAlerts = mutableListOf<DealPushRecommendationEngine.ScoredDeal>()

        for (incoming in distinctDeals) {
            val existing = existingById[incoming.id]
            val productKey = DealTextParser.normalizeProductKey(incoming.title)

            if (incoming.currentPrice != null) {
                val latestSnap = latestSnapByDealId[incoming.id]
                if (latestSnap == null || kotlin.math.abs(latestSnap.price - incoming.currentPrice) >= 0.01) {
                    snapshotsToInsert.add(
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
                listOfNotNull(
                    minPriceByKey[productKey],
                    incoming.currentPrice,
                    existing?.historicLowPrice,
                    existing?.verifiedPrice
                ).minOrNull()
            } else {
                existing?.historicLowPrice
            }

            val preservedVerification = existing?.verificationStatus?.takeIf { it != "UNVERIFIED" }
                ?: incoming.verificationStatus.name
            val preservedExpired = existing?.isExpired ?: incoming.isExpired
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
                worthVotes = maxOf(incoming.worthVotes, existing?.worthVotes ?: 0),
                notWorthVotes = maxOf(incoming.notWorthVotes, existing?.notWorthVotes ?: 0),
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
                notificationSent = isAlreadyNotified,
                verificationStatus = preservedVerification,
                verifiedPrice = existing?.verifiedPrice ?: incoming.verifiedPrice,
                verifiedAtMillis = existing?.verifiedAtMillis ?: incoming.verifiedAtMillis,
                verificationNote = existing?.verificationNote?.ifBlank { incoming.verificationNote } ?: incoming.verificationNote,
                isExpired = preservedExpired
            )
            entitiesToUpsert.add(entity)

            if (!isAlreadyNotified && !preservedExpired && batchAlerts.size < remainingAlertBudget) {
                val domainDeal = entity.toDomain()
                val scored = DealPushRecommendationEngine.calculateScore(domainDeal, profile, affinity, now)
                if (DealPushRecommendationEngine.shouldTriggerPushNotification(scored, profile, now)) {
                    batchAlerts.add(scored)
                }
            }
        }

        if (snapshotsToInsert.isNotEmpty()) {
            dealDao.insertPriceSnapshots(snapshotsToInsert)
        }
        dealDao.upsertDeals(entitiesToUpsert)
        return batchAlerts
    }

    /**
     * Real-time live HTTP verification of a specific deal's merchant page.
     * Updates Room verification status, live price, expiration flag, and price snapshot.
     */
    suspend fun verifyDealLive(deal: DealItem): LiveVerificationResult {
        val result = networkClient.verifyDealLive(deal)
        val now = System.currentTimeMillis()

        dealDao.updateDealVerification(
            dealId = deal.id,
            status = result.status.name,
            verifiedPrice = result.verifiedPrice,
            verifiedAtMillis = now,
            note = result.note,
            isExpired = result.isExpired
        )

        if (result.verifiedPrice != null) {
            val latest = dealDao.getLatestSnapshotForDeal(deal.id)
            if (latest == null || kotlin.math.abs(latest.price - result.verifiedPrice) >= 0.01) {
                dealDao.insertPriceSnapshot(
                    PriceSnapshotEntity(
                        dealId = deal.id,
                        productKey = DealTextParser.normalizeProductKey(deal.title),
                        merchantName = deal.merchant.name,
                        price = result.verifiedPrice,
                        recordedAtMillis = now
                    )
                )
            }
        }
        return result
    }

    /**
     * Stale-While-Revalidate Streaming Multi-Platform Live Search:
     * 1. Emits 0ms cached response if queried within last 60s (unless forceRefresh = true)
     * 2. Immediately emits instant local Room matches (<5ms) + 8 direct store launchers
     * 3. Progressively merges & emits live results as Slickdeals, Reddit, and CheapShark respond
     */
    fun searchMultiPlatformLiveStreaming(
        query: String,
        forceRefresh: Boolean = false
    ): Flow<LiveSearchResponse> = flow {
        val trimmed = query.trim()
        val now = System.currentTimeMillis()
        val cacheKey = trimmed.lowercase(Locale.US)

        if (trimmed.isBlank()) {
            emit(
                LiveSearchResponse(
                    query = "",
                    groups = emptyList(),
                    totalLiveOffers = 0,
                    sourcesTelemetry = emptyList(),
                    directRetailerLinks = emptyList(),
                    searchedAtMillis = now
                )
            )
            return@flow
        }

        if (!forceRefresh) {
            searchCache[cacheKey]?.let { entry ->
                if (now - entry.cachedAtMillis < 60_000L) {
                    emit(entry.response)
                    return@flow
                }
            }
        }

        val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
        val directLinks = USMerchant.majorUSRetailers.map { merchant ->
            DirectRetailerLink(
                merchant = merchant,
                searchUrl = String.format(Locale.US, merchant.searchUrlTemplate, encoded)
            )
        }

        // Phase 1: Instant Room cache matches (<5ms)
        val cachedMatches = dealDao.searchCachedDeals(trimmed).map { it.toDomain() }
        val accumulatedDeals = mutableListOf<DealItem>().apply { addAll(cachedMatches) }
        val telemetry = mutableListOf<MonitorSourceStatus>()

        emit(buildLiveSearchResponse(trimmed, accumulatedDeals, telemetry, directLinks, now))

        // Phase 2: Stream remote multi-platform search sources as each arrives
        val profile = userProfileFlow.first()
        val affinity = behaviorAffinityFlow.first()

        networkClient.streamLiveSearch(trimmed).collect { sourceResult ->
            telemetry.add(sourceResult.status)
            if (sourceResult.deals.isNotEmpty()) {
                accumulatedDeals.addAll(sourceResult.deals)
                // Persist fresh search discoveries in batch without triggering push spam
                ingestDealBatchFast(
                    incomingDeals = sourceResult.deals.take(20),
                    profile = profile,
                    affinity = affinity,
                    remainingAlertBudget = 0
                )
            }
            val snapshotResponse = buildLiveSearchResponse(
                trimmed,
                accumulatedDeals,
                telemetry.toList(),
                directLinks,
                System.currentTimeMillis()
            )
            emit(snapshotResponse)
        }

        val finalResponse = buildLiveSearchResponse(
            trimmed,
            accumulatedDeals,
            telemetry.toList(),
            directLinks,
            System.currentTimeMillis()
        )
        searchCache[cacheKey] = CachedSearchEntry(finalResponse, System.currentTimeMillis())
    }

    private fun buildLiveSearchResponse(
        query: String,
        rawDeals: List<DealItem>,
        telemetry: List<MonitorSourceStatus>,
        directLinks: List<DirectRetailerLink>,
        now: Long
    ): LiveSearchResponse {
        val tokens = query.lowercase(Locale.US).split(Regex("""\s+""")).filter { it.isNotBlank() }

        val merged = rawDeals
            .filter { !it.isExpired && it.verificationStatus != VerificationStatus.OUT_OF_STOCK }
            .filter { deal ->
                tokens.any { t ->
                    deal.title.contains(t, ignoreCase = true) ||
                        deal.brand.contains(t, ignoreCase = true) ||
                        deal.summary.contains(t, ignoreCase = true)
                }
            }
            .distinctBy { "${it.merchant.name}|${it.title.lowercase(Locale.US).take(55)}" }

        val grouped = merged.groupBy { deal ->
            val keyTokens = DealTextParser.normalizeProductKey(deal.title).split("_").take(3).joinToString("_")
            "${deal.brand.lowercase(Locale.US)}_$keyTokens"
        }.map { (groupKey, items) ->
            val sortedItems = items.sortedWith(
                compareBy<DealItem> { it.effectiveCurrentPrice == null }
                    .thenBy { it.effectiveCurrentPrice ?: Double.MAX_VALUE }
                    .thenByDescending { it.publishedAtMillis }
            )
            val priced = sortedItems.mapNotNull { it.effectiveCurrentPrice }
            val lowestPrice = priced.minOrNull()
            val highestPrice = priced.maxOrNull()
            val lead = sortedItems.first()

            val quotes = sortedItems.mapIndexed { idx, item ->
                MerchantPriceQuote(
                    id = item.id,
                    merchant = item.merchant,
                    title = item.title,
                    price = item.effectiveCurrentPrice,
                    originalPrice = item.originalPrice,
                    couponCode = item.coupon?.code,
                    sourceName = item.sourceName,
                    buyUrl = item.buyUrl,
                    imageUrl = item.imageUrl,
                    isLowestAmongAll = idx == 0 && item.effectiveCurrentPrice != null,
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
                .thenByDescending { it.quotes.firstOrNull()?.publishedAtMillis ?: 0L }
        )

        return LiveSearchResponse(
            query = query,
            groups = grouped,
            totalLiveOffers = merged.size,
            sourcesTelemetry = telemetry,
            directRetailerLinks = directLinks,
            searchedAtMillis = now
        )
    }

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
                page.isOutOfStock -> "Out of Stock / Unavailable right now"
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
                val status = when {
                    page.isOutOfStock -> "Out of Stock / Unavailable"
                    page.price != null -> "Live verified: $${String.format(Locale.US, "%.2f", page.price)}"
                    else -> "Checked (no structured price exposed)"
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
        val activePrice = verifiedPrice ?: currentPrice
        val isLow = activePrice != null && historicLowPrice != null && activePrice <= historicLowPrice + 0.01
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
            },
            verificationStatus = VerificationStatus.fromNameSafe(verificationStatus),
            verifiedPrice = verifiedPrice,
            verifiedAtMillis = verifiedAtMillis,
            verificationNote = verificationNote,
            isExpired = isExpired
        )
    }
}
