package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.network.LiveDealNetworkClient
import com.example.model.DealCategory
import com.example.model.DirectRetailerLink
import com.example.ui.components.CompactSearchPill
import com.example.ui.components.LiveProductComparisonCard
import com.example.ui.components.PulsingLiveDot
import com.example.ui.components.ScrollToTopPill
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.icons.WorthItIcons
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.copyTextToClipboard
import com.example.ui.util.openSafeExternalUrl
import com.example.viewmodel.DealRadarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiPlatformSearchScreen(
    viewModel: DealRadarViewModel,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val ext = WorthItTheme.extendedColors

    val searchQuery by viewModel.platformSearchQuery.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearchingPlatforms.collectAsStateWithLifecycle()
    val liveResponse by viewModel.liveSearchResponse.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val showScrollToTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= 2 }
    }

    val quickQueries = listOf("RTX 5090", "MacBook", "AirPods", "OLED", "SSD 2TB", "PS5", "Dyson")

    Box(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .testTag("multi_platform_search_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact 42dp Multi-Platform Search Pill
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                CompactSearchPill(
                    value = searchQuery,
                    onValueChange = { viewModel.updatePlatformSearchQuery(it) },
                    placeholder = "Search any SKU (5090, AirPods, SSD)...",
                    inputTestTag = "multi_store_search_input",
                    onSearchSubmit = {
                        keyboard?.hide()
                        viewModel.submitPlatformSearchNow()
                    },
                    trailingAction = {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                keyboard?.hide()
                                viewModel.submitPlatformSearchNow(forceRefresh = true)
                            },
                            enabled = searchQuery.isNotBlank() && !isSearching,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("refresh_search_btn")
                        ) {
                            SpinningSyncIcon(
                                isSyncing = isSearching,
                                tint = if (searchQuery.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                contentDescription = "Run live search",
                                size = 18.dp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(quickQueries) { kw ->
                        SuggestionChip(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                keyboard?.hide()
                                viewModel.submitPlatformSearchNow(kw)
                            },
                            label = { Text(kw, style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }

        if (isSearching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // Main Results Area
        when {
            searchQuery.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(0.5.dp, ext.hairline),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = WorthItIcons.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Real-Time Multi-Store Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Search any product (e.g. 5090, MacBook M4, OLED TV). WorthIt queries live US deal feeds in parallel and opens direct store price checks across 8 major retailers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            isSearching && liveResponse == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Searching live US feeds for \"$searchQuery\"...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Querying Slickdeals, Reddit deals & CheapShark concurrently",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                val resp = liveResponse
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Honest progressive streaming telemetry bar
                    if (resp != null) {
                        item(key = "search_telemetry_bar") {
                            val okCount = resp.sourcesTelemetry.count { it.isHealthy }
                            val completedCount = resp.sourcesTelemetry.size
                            val expectedCount = LiveDealNetworkClient.TOTAL_SEARCH_SOURCES
                            val statusText = if (isSearching) {
                                if (completedCount == 0) {
                                    "Instant local matches • streaming 0/$expectedCount live feeds..."
                                } else {
                                    "Streaming $completedCount/$expectedCount live feeds..."
                                }
                            } else {
                                "Live search: $okCount/$completedCount feeds responded"
                            }
                            Surface(
                                color = ext.successContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        PulsingLiveDot(
                                            color = ext.livePulse,
                                            isActive = true,
                                            size = 7.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = statusText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ext.success,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${resp.totalLiveOffers} active",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ext.success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Direct 8-Retailer Live Store Check Grid (works for ANY of millions of SKUs)
                        item {
                            DirectStoreLauncherCard(
                                query = resp.query,
                                links = resp.directRetailerLinks,
                                onOpenStore = { link ->
                                    viewModel.recordOutboundClick("search_${resp.query}", link.merchant, DealCategory.ALL)
                                    openSafeExternalUrl(context, link.searchUrl)
                                }
                            )
                        }

                        if (resp.groups.isEmpty() && !isSearching) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(0.5.dp, ext.hairline),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "No active community deals found for \"${resp.query}\"",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "We never fabricate fake prices. Use the direct retailer buttons above to check real-time inventory & live MSRP across all 8 US stores, or add the product URL to Live Radar.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(resp.groups, key = { it.id }) { group ->
                                LiveProductComparisonCard(
                                    group = group,
                                    onQuoteClick = { quote ->
                                        if (userProfile.autoCopyCoupons && quote.couponCode != null) {
                                            copyTextToClipboard(context, "Promo Code", quote.couponCode)
                                            viewModel.recordCouponCopy(quote.couponCode, quote.id, quote.merchant, group.category)
                                        }
                                        viewModel.recordOutboundClick(quote.id, quote.merchant, group.category)
                                        openSafeExternalUrl(context, quote.buyUrl)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
        }

        ScrollToTopPill(
            visible = showScrollToTop && searchQuery.isNotBlank(),
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                scope.launch { listState.animateScrollToItem(0) }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 14.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectStoreLauncherCard(
    query: String,
    links: List<DirectRetailerLink>,
    onOpenStore: (DirectRetailerLink) -> Unit
) {
    val ext = WorthItTheme.extendedColors
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(0.5.dp, ext.hairline),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Check Live Store Prices for \"$query\"",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "1-tap direct jump to real-time product search on 8 major US retailers",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                links.forEach { link ->
                    val brandColor = Color(link.merchant.brandColorHex)
                    Surface(
                        color = brandColor.copy(alpha = 0.11f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, brandColor.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clickable { onOpenStore(link) }
                            .testTag("direct_store_${link.merchant.name}")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = link.merchant.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                color = brandColor,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = WorthItIcons.ArrowUpRight,
                                contentDescription = "Search ${link.merchant.displayName}",
                                tint = brandColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
