package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.algorithm.DealPushRecommendationEngine
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.network.LiveDealNetworkClient
import com.example.model.AppThemeMode
import com.example.model.ConversionFunnelStats
import com.example.model.DealCategory
import com.example.model.DealItem
import com.example.model.LiveSearchResponse
import com.example.model.MonitorSourceStatus
import com.example.model.PricePoint
import com.example.model.TrackedUrlWatch
import com.example.model.USMerchant
import com.example.model.UserBehaviorAffinity
import com.example.model.UserPreferenceProfile
import com.example.notification.DealNotificationManager
import com.example.repository.WorthItRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class AlertBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val dealId: String? = null
)

data class UiFeedbackToast(
    val id: Long = System.currentTimeMillis(),
    val message: String,
    val actionLabel: String? = null,
    val undoDealId: String? = null
)

private data class FeedFilterState(
    val category: DealCategory,
    val query: String,
    val freshOnly: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class DealRadarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WorthItRepository = WorthItRepository(
        dealDao = AppDatabase.getInstance(application).dealDao(),
        prefsDataStore = UserPreferencesDataStore(application),
        networkClient = LiveDealNetworkClient(cacheDir = application.cacheDir),
        notificationManager = DealNotificationManager(application)
    )

    private val _selectedCategory = MutableStateFlow(DealCategory.ALL)
    val selectedCategory: StateFlow<DealCategory> = _selectedCategory.asStateFlow()

    private val _feedFilterQuery = MutableStateFlow("")
    val feedFilterQuery: StateFlow<String> = _feedFilterQuery.asStateFlow()

    private val _freshOnlyFilter = MutableStateFlow(false)
    val freshOnlyFilter: StateFlow<Boolean> = _freshOnlyFilter.asStateFlow()

    private val _selectedDealId = MutableStateFlow<String?>(null)

    private val _isSyncingFeeds = MutableStateFlow(false)
    val isSyncingFeeds: StateFlow<Boolean> = _isSyncingFeeds.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _verifyingDealIds = MutableStateFlow<Set<String>>(emptySet())
    val verifyingDealIds: StateFlow<Set<String>> = _verifyingDealIds.asStateFlow()

    private val _latestAlert = MutableStateFlow<AlertBanner?>(null)
    val latestAlert: StateFlow<AlertBanner?> = _latestAlert.asStateFlow()

    private val _copiedCouponToast = MutableStateFlow<String?>(null)
    val copiedCouponToast: StateFlow<String?> = _copiedCouponToast.asStateFlow()

    private val _uiFeedbackToast = MutableStateFlow<UiFeedbackToast?>(null)
    val uiFeedbackToast: StateFlow<UiFeedbackToast?> = _uiFeedbackToast.asStateFlow()

    // Live Multi-Platform Search state (Stale-While-Revalidate streaming)
    private val _platformSearchQuery = MutableStateFlow("")
    val platformSearchQuery: StateFlow<String> = _platformSearchQuery.asStateFlow()

    private val _isSearchingPlatforms = MutableStateFlow(false)
    val isSearchingPlatforms: StateFlow<Boolean> = _isSearchingPlatforms.asStateFlow()

    private val _liveSearchResponse = MutableStateFlow<LiveSearchResponse?>(null)
    val liveSearchResponse: StateFlow<LiveSearchResponse?> = _liveSearchResponse.asStateFlow()

    private val _isCheckingTrackedUrl = MutableStateFlow(false)
    val isCheckingTrackedUrl: StateFlow<Boolean> = _isCheckingTrackedUrl.asStateFlow()

    private val _trackedUrlFeedback = MutableStateFlow<String?>(null)
    val trackedUrlFeedback: StateFlow<String?> = _trackedUrlFeedback.asStateFlow()

    val sourceStatuses: StateFlow<List<MonitorSourceStatus>> = repository.sourceStatuses

    val userProfile: StateFlow<UserPreferenceProfile> = repository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserPreferenceProfile())

    val behaviorAffinity: StateFlow<UserBehaviorAffinity> = repository.behaviorAffinityFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserBehaviorAffinity())

    val conversionFunnelStats: StateFlow<ConversionFunnelStats> = repository.conversionFunnelStatsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConversionFunnelStats())

    val trackedUrls: StateFlow<List<TrackedUrlWatch>> = repository.trackedUrlsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allDeals: StateFlow<List<DealItem>> = repository.allDealsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedDeals: StateFlow<List<DealItem>> = repository.savedDealsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val feedFilterState = combine(
        selectedCategory,
        feedFilterQuery,
        freshOnlyFilter
    ) { category, query, freshOnly ->
        FeedFilterState(category = category, query = query, freshOnly = freshOnly)
    }

    val rankedDeals: StateFlow<List<DealPushRecommendationEngine.ScoredDeal>> = combine(
        allDeals,
        userProfile,
        behaviorAffinity,
        feedFilterState
    ) { deals, profile, affinity, filter ->
        DealPushRecommendationEngine.scoreAndRankDeals(
            deals = deals,
            profile = profile,
            behaviorAffinity = affinity,
            filterCategory = filter.category,
            searchQuery = filter.query,
            freshOnly = filter.freshOnly
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedScoredDeal: StateFlow<DealPushRecommendationEngine.ScoredDeal?> = combine(
        _selectedDealId,
        allDeals,
        userProfile,
        behaviorAffinity
    ) { dealId, deals, profile, affinity ->
        if (dealId == null) return@combine null
        val deal = deals.firstOrNull { it.id == dealId } ?: return@combine null
        DealPushRecommendationEngine.calculateScore(deal, profile, affinity)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val selectedDealPriceHistory: StateFlow<List<PricePoint>> = _selectedDealId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else repository.observePriceHistoryForDeal(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null
    private var syncJob: Job? = null
    private var toastJob: Job? = null

    init {
        syncLiveDealsNow()
        startAutoRefreshPulse()
    }

    /**
     * Keeps time-sensitive flash drops fresh automatically while the app is open
     * without duplicating an in-flight sync.
     */
    private fun startAutoRefreshPulse() {
        viewModelScope.launch {
            while (isActive) {
                delay(180_000L) // Refresh every 3 minutes
                if (!_isSyncingFeeds.value) {
                    syncLiveDealsNow()
                }
            }
        }
    }

    fun syncLiveDealsNow() {
        if (_isSyncingFeeds.value) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _isSyncingFeeds.value = true
            _syncMessage.value = "Streaming 0/${LiveDealNetworkClient.TOTAL_FEED_SOURCES} US sources..."
            try {
                repository.syncLiveDealsStreaming().collect { progress ->
                    if (!progress.isFinished) {
                        _syncMessage.value =
                            "Streaming ${progress.completedSources}/${progress.totalSources} sources (${progress.totalFetchedDeals} live deals)..."
                    } else {
                        if (progress.healthySources == 0) {
                            _syncMessage.value = "Offline or blocked: showing cached deals from Room."
                        } else {
                            _syncMessage.value =
                                "Synced ${progress.totalFetchedDeals} live deals (${progress.healthySources}/${progress.totalSources} sources online)"
                        }
                        progress.triggeredAlerts.firstOrNull()?.let { alert ->
                            val priceLabel = alert.deal.effectiveCurrentPrice?.let {
                                "$${String.format(Locale.US, "%.2f", it)}"
                            } ?: "Flash Alert"
                            _latestAlert.value = AlertBanner(
                                id = alert.deal.id,
                                title = "Push Alert • ${alert.deal.merchant.displayName} ($priceLabel)",
                                subtitle = "${alert.recommendationReason.title}: ${alert.deal.title}",
                                dealId = alert.deal.id
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _syncMessage.value = "Sync error: ${e.message?.take(60) ?: "Check connection"}"
            } finally {
                _isSyncingFeeds.value = false
            }
        }
    }

    fun setCategory(category: DealCategory) {
        _selectedCategory.value = category
    }

    fun setFeedFilterQuery(query: String) {
        _feedFilterQuery.value = query
    }

    fun toggleFreshOnlyFilter() {
        _freshOnlyFilter.value = !_freshOnlyFilter.value
    }

    fun clearFeedFilters() {
        _selectedCategory.value = DealCategory.ALL
        _feedFilterQuery.value = ""
        _freshOnlyFilter.value = false
    }

    fun openDealDetail(deal: DealItem) {
        _selectedDealId.value = deal.id
        viewModelScope.launch {
            repository.logConversionEvent(deal.id, deal.merchant, deal.category, "DETAIL_VIEW")
        }
        // Automatically verify real-time stock & price against the merchant page on entry
        verifyDealLiveNow(deal, force = false)
    }

    fun closeDealDetail() {
        _selectedDealId.value = null
    }

    fun openDealById(dealId: String) {
        _selectedDealId.value = dealId
        allDeals.value.firstOrNull { it.id == dealId }?.let { deal ->
            verifyDealLiveNow(deal, force = false)
        }
    }

    /**
     * Real-time HTTP stock & price check against the deal's retailer URL.
     * Skips redundant network checks if verified within the past 2 minutes unless force = true.
     */
    fun verifyDealLiveNow(deal: DealItem, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && deal.verifiedAtMillis > 0L && (now - deal.verifiedAtMillis) < 120_000L) {
            return
        }
        if (_verifyingDealIds.value.contains(deal.id)) return

        viewModelScope.launch {
            _verifyingDealIds.update { it + deal.id }
            try {
                repository.verifyDealLive(deal)
            } finally {
                _verifyingDealIds.update { it - deal.id }
            }
        }
    }

    fun voteDeal(deal: DealItem, isWorth: Boolean) {
        viewModelScope.launch {
            repository.voteDeal(deal, isWorth)
        }
    }

    fun toggleSaveDeal(deal: DealItem, showFeedback: Boolean = true) {
        val wasSaved = deal.isSaved
        viewModelScope.launch {
            repository.toggleSaveDeal(deal)
            if (showFeedback) {
                showUiToast(
                    message = if (wasSaved) "Removed from Saved Deals" else "Saved to your watchlist",
                    actionLabel = "Undo",
                    undoDealId = deal.id
                )
            }
        }
    }

    fun undoLastBookmarkToggle(dealId: String) {
        val currentDeal = allDeals.value.firstOrNull { it.id == dealId } ?: return
        dismissUiToast()
        toggleSaveDeal(currentDeal, showFeedback = false)
    }

    fun showUiToast(message: String, actionLabel: String? = null, undoDealId: String? = null) {
        toastJob?.cancel()
        val toast = UiFeedbackToast(
            message = message,
            actionLabel = actionLabel,
            undoDealId = undoDealId
        )
        _uiFeedbackToast.value = toast
        toastJob = viewModelScope.launch {
            delay(3200L)
            if (_uiFeedbackToast.value?.id == toast.id) {
                _uiFeedbackToast.value = null
            }
        }
    }

    fun dismissUiToast() {
        toastJob?.cancel()
        _uiFeedbackToast.value = null
    }

    fun recordOutboundClick(dealId: String, merchant: USMerchant, category: DealCategory) {
        viewModelScope.launch {
            repository.logConversionEvent(dealId, merchant, category, "OUTBOUND_CLICK")
        }
    }

    fun recordCouponCopy(code: String, dealId: String, merchant: USMerchant, category: DealCategory) {
        _copiedCouponToast.value = code
        showUiToast("Promo code $code copied — ready to paste at checkout")
        viewModelScope.launch {
            repository.logConversionEvent(dealId, merchant, category, "COUPON_COPY")
            delay(3000)
            if (_copiedCouponToast.value == code) {
                _copiedCouponToast.value = null
            }
        }
    }

    fun updatePlatformSearchQuery(query: String) {
        _platformSearchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _liveSearchResponse.value = null
            _isSearchingPlatforms.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // Crisp 250ms debounce before streaming local Room + remote feeds
            executeLiveSearch(query, forceRefresh = false)
        }
    }

    fun submitPlatformSearchNow(
        query: String = _platformSearchQuery.value,
        forceRefresh: Boolean = false
    ) {
        _platformSearchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            executeLiveSearch(query, forceRefresh = forceRefresh)
        }
    }

    private suspend fun executeLiveSearch(query: String, forceRefresh: Boolean) {
        _isSearchingPlatforms.value = true
        try {
            repository.searchMultiPlatformLiveStreaming(query, forceRefresh = forceRefresh)
                .collect { partialOrFinalResponse ->
                    _liveSearchResponse.value = partialOrFinalResponse
                }
        } finally {
            _isSearchingPlatforms.value = false
        }
    }

    fun addTrackedUrl(url: String, label: String, targetPriceText: String) {
        viewModelScope.launch {
            _isCheckingTrackedUrl.value = true
            _trackedUrlFeedback.value = null
            val targetPrice = targetPriceText.trim().replace("$", "").toDoubleOrNull()
            val result = repository.addAndCheckTrackedUrl(url, label, targetPrice)
            _trackedUrlFeedback.value = result.fold(
                onSuccess = { watch -> "Added ${watch.merchant.displayName}: ${watch.lastStatusMessage}" },
                onFailure = { err -> err.message ?: "Failed to add URL monitor" }
            )
            _isCheckingTrackedUrl.value = false
        }
    }

    fun refreshTrackedUrls() {
        viewModelScope.launch {
            _isCheckingTrackedUrl.value = true
            val count = repository.refreshAllTrackedUrls()
            _trackedUrlFeedback.value = "Re-checked watched retailer URLs ($count live prices verified)."
            _isCheckingTrackedUrl.value = false
        }
    }

    fun deleteTrackedUrl(id: Long) {
        viewModelScope.launch {
            repository.removeTrackedUrl(id)
        }
    }

    fun savePreferences(updated: UserPreferenceProfile) {
        viewModelScope.launch {
            repository.saveUserProfile(updated)
            showUiToast("Algorithm & alert preferences updated")
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch {
            repository.saveThemeMode(mode)
        }
    }

    fun toggleLightDarkTheme(isCurrentlyDark: Boolean) {
        setThemeMode(if (isCurrentlyDark) AppThemeMode.LIGHT else AppThemeMode.DARK)
    }

    fun resetBehavioralLearning() {
        viewModelScope.launch {
            repository.resetBehavioralLearning()
            showUiToast("Behavioral learning weights reset")
        }
    }

    fun dispatchTopDealNotification(): Boolean {
        val top = rankedDeals.value.firstOrNull() ?: return false
        val posted = repository.sendNotificationForDeal(top.deal, top.recommendationReason.title)
        val priceLabel = top.deal.effectiveCurrentPrice?.let {
            "$${String.format(Locale.US, "%.2f", it)}"
        } ?: "Live Deal"
        _latestAlert.value = AlertBanner(
            id = System.currentTimeMillis().toString(),
            title = if (posted) "System Push Sent • ${top.deal.merchant.displayName}" else "In-App Alert • ${top.deal.merchant.displayName}",
            subtitle = "$priceLabel — ${top.deal.title}",
            dealId = top.deal.id
        )
        return posted
    }

    fun hasNotificationPermission(): Boolean = repository.hasNotificationPermission()

    fun dismissAlert() {
        _latestAlert.value = null
    }

    fun dismissTrackedFeedback() {
        _trackedUrlFeedback.value = null
    }
}
