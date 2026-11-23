package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.algorithm.DealPushRecommendationEngine
import com.example.data.local.AppDatabase
import com.example.data.local.UserPreferencesDataStore
import com.example.data.network.LiveDealNetworkClient
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
import kotlinx.coroutines.launch

data class AlertBanner(
    val id: String,
    val title: String,
    val subtitle: String,
    val dealId: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class DealRadarViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: WorthItRepository = WorthItRepository(
        dealDao = AppDatabase.getInstance(application).dealDao(),
        prefsDataStore = UserPreferencesDataStore(application),
        networkClient = LiveDealNetworkClient(),
        notificationManager = DealNotificationManager(application)
    )

    private val _selectedCategory = MutableStateFlow(DealCategory.ALL)
    val selectedCategory: StateFlow<DealCategory> = _selectedCategory.asStateFlow()

    private val _feedFilterQuery = MutableStateFlow("")
    val feedFilterQuery: StateFlow<String> = _feedFilterQuery.asStateFlow()

    private val _selectedDealId = MutableStateFlow<String?>(null)

    private val _isSyncingFeeds = MutableStateFlow(false)
    val isSyncingFeeds: StateFlow<Boolean> = _isSyncingFeeds.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _latestAlert = MutableStateFlow<AlertBanner?>(null)
    val latestAlert: StateFlow<AlertBanner?> = _latestAlert.asStateFlow()

    private val _copiedCouponToast = MutableStateFlow<String?>(null)
    val copiedCouponToast: StateFlow<String?> = _copiedCouponToast.asStateFlow()

    // Live Multi-Platform Search state
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

    val rankedDeals: StateFlow<List<DealPushRecommendationEngine.ScoredDeal>> = combine(
        allDeals,
        userProfile,
        behaviorAffinity,
        selectedCategory,
        feedFilterQuery
    ) { deals, profile, affinity, category, query ->
        DealPushRecommendationEngine.scoreAndRankDeals(
            deals = deals,
            profile = profile,
            behaviorAffinity = affinity,
            filterCategory = category,
            searchQuery = query
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

    init {
        syncLiveDealsNow()
    }

    fun syncLiveDealsNow() {
        if (_isSyncingFeeds.value) return
        viewModelScope.launch {
            _isSyncingFeeds.value = true
            _syncMessage.value = null
            try {
                val outcome = repository.syncLiveDeals()
                if (outcome.healthySources == 0) {
                    _syncMessage.value = "Offline or blocked: showing cached deals from Room."
                } else {
                    _syncMessage.value = "Synced ${outcome.fetchedCount} live deals from ${outcome.healthySources}/${outcome.totalSources} US sources."
                }
                outcome.triggeredAlerts.firstOrNull()?.let { alert ->
                    val priceLabel = alert.deal.currentPrice?.let { "$${String.format("%.2f", it)}" } ?: "Deal Alert"
                    _latestAlert.value = AlertBanner(
                        id = alert.deal.id,
                        title = "Push Alert • ${alert.deal.merchant.displayName} ($priceLabel)",
                        subtitle = "${alert.recommendationReason.title}: ${alert.deal.title}",
                        dealId = alert.deal.id
                    )
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

    fun openDealDetail(deal: DealItem) {
        _selectedDealId.value = deal.id
        viewModelScope.launch {
            repository.logConversionEvent(deal.id, deal.merchant, deal.category, "DETAIL_VIEW")
        }
    }

    fun closeDealDetail() {
        _selectedDealId.value = null
    }

    fun openDealById(dealId: String) {
        _selectedDealId.value = dealId
    }

    fun voteDeal(deal: DealItem, isWorth: Boolean) {
        viewModelScope.launch {
            repository.voteDeal(deal, isWorth)
        }
    }

    fun toggleSaveDeal(deal: DealItem) {
        viewModelScope.launch {
            repository.toggleSaveDeal(deal)
        }
    }

    fun recordOutboundClick(dealId: String, merchant: USMerchant, category: DealCategory) {
        viewModelScope.launch {
            repository.logConversionEvent(dealId, merchant, category, "OUTBOUND_CLICK")
        }
    }

    fun recordCouponCopy(code: String, dealId: String, merchant: USMerchant, category: DealCategory) {
        _copiedCouponToast.value = code
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
            delay(450) // Debounce typing before firing live multi-source HTTP search
            executeLiveSearch(query)
        }
    }

    fun submitPlatformSearchNow(query: String = _platformSearchQuery.value) {
        _platformSearchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch {
            executeLiveSearch(query)
        }
    }

    private suspend fun executeLiveSearch(query: String) {
        _isSearchingPlatforms.value = true
        try {
            val response = repository.searchMultiPlatformLive(query)
            _liveSearchResponse.value = response
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
        }
    }

    fun resetBehavioralLearning() {
        viewModelScope.launch {
            repository.resetBehavioralLearning()
        }
    }

    fun dispatchTopDealNotification(): Boolean {
        val top = rankedDeals.value.firstOrNull() ?: return false
        val posted = repository.sendNotificationForDeal(top.deal, top.recommendationReason.title)
        val priceLabel = top.deal.currentPrice?.let { "$${String.format("%.2f", it)}" } ?: "Live Deal"
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
