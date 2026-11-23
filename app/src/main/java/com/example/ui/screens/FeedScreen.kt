package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DealCategory
import com.example.ui.components.CompactSearchPill
import com.example.ui.components.DealCard
import com.example.ui.components.PulsingLiveDot
import com.example.ui.components.ScrollToTopPill
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.icons.WorthItIcons
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.copyTextToClipboard
import com.example.ui.util.openSafeExternalUrl
import com.example.viewmodel.DealRadarViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    viewModel: DealRadarViewModel,
    listState: LazyListState = rememberLazyListState(),
    onSearchAllStores: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val ext = WorthItTheme.extendedColors

    val rankedDeals by viewModel.rankedDeals.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filterQuery by viewModel.feedFilterQuery.collectAsStateWithLifecycle()
    val freshOnly by viewModel.freshOnlyFilter.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingFeeds.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val sourceStatuses by viewModel.sourceStatuses.collectAsStateWithLifecycle()
    val latestAlert by viewModel.latestAlert.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val showScrollToTop by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= 3 }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.dispatchTopDealNotification()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("feed_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Compact 42dp Search Pill + Spinning Sync + Push Alert Bell (saves ~24dp vertical viewport)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CompactSearchPill(
                    value = filterQuery,
                    onValueChange = { viewModel.setFeedFilterQuery(it) },
                    placeholder = "Filter deals (5090, OLED, Apple)...",
                    inputTestTag = "feed_search_input",
                    onSearchSubmit = {
                        keyboard?.hide()
                        if (filterQuery.isNotBlank() && rankedDeals.isEmpty()) {
                            onSearchAllStores(filterQuery.trim())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, ext.hairline)
                ) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.syncLiveDealsNow()
                        },
                        enabled = !isSyncing,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("feed_sync_btn")
                    ) {
                        SpinningSyncIcon(
                            isSyncing = isSyncing,
                            tint = MaterialTheme.colorScheme.primary,
                            contentDescription = "Refresh live US feeds",
                            size = 19.dp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.hasNotificationPermission()) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.dispatchTopDealNotification()
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("send_push_alert_btn")
                    ) {
                        Icon(
                            imageVector = WorthItIcons.BellAlert,
                            contentDescription = "Dispatch top deal push alert",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            // Slim Fresh (<6h) + Category Filter Chips (compact 34dp height)
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                item(key = "fresh_only_chip") {
                    FilterChip(
                        selected = freshOnly,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.toggleFreshOnlyFilter()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = WorthItIcons.FlashBolt,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = "Fresh (<6h)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = ext.success,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = freshOnly,
                            borderColor = ext.hairline
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("fresh_only_chip")
                    )
                }
                items(DealCategory.entries) { cat ->
                    val isSelected = cat == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.setCategory(cat)
                        },
                        label = {
                            Text(
                                text = cat.displayName,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = ext.hairline
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("category_chip_${cat.name}")
                    )
                }
            }

            // Native Material 3 Pull-to-Refresh wrapping scrollable deals & collapsible headers
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.syncLiveDealsNow()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    rankedDeals.isEmpty() && isSyncing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(strokeWidth = 2.5.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fetching real-time US deals...",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Querying Slickdeals, Reddit, DealNews, 9to5Toys & CheapShark",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    rankedDeals.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(0.5.dp, ext.hairline),
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (filterQuery.isNotBlank()) WorthItIcons.SearchOutlined else Icons.Default.WifiOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (filterQuery.isNotBlank() || selectedCategory != DealCategory.ALL || freshOnly) {
                                        "No feed deals match your filters"
                                    } else {
                                        "No live deals loaded yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (filterQuery.isNotBlank()) {
                                        "Search \"$filterQuery\" live across all 8 US retailers, or reset your feed filters."
                                    } else {
                                        "Pull down or tap Sync Live Feeds to pull fresh drops."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                if (filterQuery.isNotBlank()) {
                                    Button(
                                        onClick = {
                                            keyboard?.hide()
                                            onSearchAllStores(filterQuery.trim())
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.testTag("feed_empty_search_all_stores_btn")
                                    ) {
                                        Icon(WorthItIcons.SearchOutlined, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Search \"$filterQuery\" Across 8 Stores")
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.clearFeedFilters() },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Reset Filters")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.clearFeedFilters()
                                            viewModel.syncLiveDealsNow()
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text("Sync Live Feeds")
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val healthyCount = sourceStatuses.count { it.isHealthy }
                        val totalSources = sourceStatuses.size

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("deals_lazy_column"),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Scrollable Live Telemetry Strip (scrolls cleanly out of the way to maximize deal viewport!)
                            item(key = "live_telemetry_strip") {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(0.5.dp, ext.hairline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 11.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            PulsingLiveDot(
                                                color = if (healthyCount > 0) ext.livePulse else ext.warning,
                                                isActive = healthyCount > 0 || isSyncing,
                                                size = 7.dp
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = syncMessage ?: if (isSyncing) {
                                                    "Crawling Slickdeals, Reddit, DealNews, 9to5Toys & CheapShark..."
                                                } else {
                                                    "Live US Feed Ready ($healthyCount/$totalSources sources online)"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "${rankedDeals.size} deals",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Smooth Animated Push Alert Banner inside scroll area
                            item(key = "push_alert_banner_slot") {
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = latestAlert != null,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    latestAlert?.let { alert ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { alert.dealId?.let { viewModel.openDealById(it) } }
                                                .testTag("push_alert_banner"),
                                            colors = CardDefaults.cardColors(containerColor = ext.alertBannerBg),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = WorthItIcons.FlashBolt,
                                                    contentDescription = null,
                                                    tint = ext.warning,
                                                    modifier = Modifier.size(19.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = alert.title,
                                                        color = ext.alertBannerOnBg,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = alert.subtitle,
                                                        color = ext.alertBannerOnBg.copy(alpha = 0.8f),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { viewModel.dismissAlert() },
                                                    modifier = Modifier.minimumInteractiveComponentSize()
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Dismiss alert",
                                                        tint = ext.alertBannerOnBg,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Ranked Deal Cards with smooth item placement animations
                            items(rankedDeals, key = { it.deal.id }) { scoredDeal ->
                                val deal = scoredDeal.deal
                                DealCard(
                                    scoredDeal = scoredDeal,
                                    onClick = { viewModel.openDealDetail(deal) },
                                    onVoteWorth = { viewModel.voteDeal(deal, true) },
                                    onVoteNotWorth = { viewModel.voteDeal(deal, false) },
                                    onToggleSave = { viewModel.toggleSaveDeal(deal) },
                                    onCopyCoupon = { code ->
                                        copyTextToClipboard(context, "Promo Code", code)
                                        viewModel.recordCouponCopy(code, deal.id, deal.merchant, deal.category)
                                    },
                                    onDirectCheckout = {
                                        if (userProfile.autoCopyCoupons && deal.coupon != null) {
                                            copyTextToClipboard(context, "Promo Code", deal.coupon.code)
                                            viewModel.recordCouponCopy(deal.coupon.code, deal.id, deal.merchant, deal.category)
                                        }
                                        viewModel.recordOutboundClick(deal.id, deal.merchant, deal.category)
                                        openSafeExternalUrl(context, deal.buyUrl)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }

                            // Seamless bridge to 8-Store Real-Time Search whenever filtering
                            if (filterQuery.isNotBlank()) {
                                item(key = "search_all_stores_bridge") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                keyboard?.hide()
                                                onSearchAllStores(filterQuery.trim())
                                            }
                                            .testTag("feed_bridge_search_btn")
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(
                                                    imageVector = WorthItIcons.Storefront,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Compare \"$filterQuery\" live across 8 US stores",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Icon(
                                                imageVector = WorthItIcons.ArrowUpRight,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            item(key = "feed_bottom_spacer") { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }

        // Floating Scroll-to-Top Pill Button
        ScrollToTopPill(
            visible = showScrollToTop,
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
