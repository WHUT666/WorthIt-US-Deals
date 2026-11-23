package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DealCategory
import com.example.ui.components.DealCard
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.copyTextToClipboard
import com.example.ui.util.openSafeExternalUrl
import com.example.viewmodel.DealRadarViewModel

@Composable
fun FeedScreen(
    viewModel: DealRadarViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = WorthItTheme.extendedColors

    val rankedDeals by viewModel.rankedDeals.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val filterQuery by viewModel.feedFilterQuery.collectAsStateWithLifecycle()
    val freshOnly by viewModel.freshOnlyFilter.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingFeeds.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val sourceStatuses by viewModel.sourceStatuses.collectAsStateWithLifecycle()
    val latestAlert by viewModel.latestAlert.collectAsStateWithLifecycle()
    val copiedCoupon by viewModel.copiedCouponToast.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.dispatchTopDealNotification()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("feed_screen")
    ) {
        // Filter bar + Live sync + Notification test trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = filterQuery,
                onValueChange = { viewModel.setFeedFilterQuery(it) },
                placeholder = { Text("Filter live deals (e.g. 5090, OLED, Apple)...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Filter deals",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (filterQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setFeedFilterQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear filter")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("feed_search_input")
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = { viewModel.syncLiveDealsNow() },
                enabled = !isSyncing,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .testTag("feed_sync_btn")
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh live US feeds",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.hasNotificationPermission()) {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.dispatchTopDealNotification()
                    }
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .testTag("send_push_alert_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = "Dispatch top deal push alert",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (isSyncing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        // Copied coupon confirmation strip
        if (copiedCoupon != null) {
            Surface(
                color = ext.success,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Coupon code $copiedCoupon copied — paste at retailer checkout",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // Real triggered algorithm alert banner
        latestAlert?.let { alert ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { alert.dealId?.let { viewModel.openDealById(it) } }
                    .testTag("push_alert_banner"),
                colors = CardDefaults.cardColors(containerColor = ext.alertBannerBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ext.warning,
                        modifier = Modifier.size(22.dp)
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

        // Honest live sync telemetry strip
        val healthyCount = sourceStatuses.count { it.isHealthy }
        val totalSources = sourceStatuses.size
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                if (healthyCount > 0) ext.livePulse else ext.warning,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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

        // Fresh Drops (<6h) + Category Filter Chips
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "fresh_only_chip") {
                FilterChip(
                    selected = freshOnly,
                    onClick = { viewModel.toggleFreshOnlyFilter() },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "Fresh (<6h)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ext.success,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("fresh_only_chip")
                )
            }
            items(DealCategory.entries) { cat ->
                val isSelected = cat == selectedCategory
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setCategory(cat) },
                    label = {
                        Text(
                            text = cat.displayName,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("category_chip_${cat.name}")
                )
            }
        }

        // Content List / Loading / Empty states
        when {
            rankedDeals.isEmpty() && isSyncing -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Fetching real-time US deals...",
                            style = MaterialTheme.typography.titleMedium
                        )
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
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (filterQuery.isNotBlank() || selectedCategory != DealCategory.ALL || freshOnly) {
                                "No active deals match your current filters"
                            } else {
                                "No live deals loaded yet"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Sync Live Feeds to pull fresh drops, or reset filters.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(onClick = {
                            viewModel.clearFeedFilters()
                            viewModel.syncLiveDealsNow()
                        }) {
                            Text("Sync Live Feeds")
                        }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("deals_lazy_column"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(rankedDeals, key = { it.deal.id }) { scoredDeal ->
                        val deal = scoredDeal.deal
                        DealCard(
                            scoredDeal = scoredDeal,
                            onClick = { viewModel.openDealDetail(deal) },
                            onVoteWorth = { viewModel.voteDeal(deal, true) },
                            onVoteNotWorth = { viewModel.voteDeal(deal, false) },
                            onToggleSave = { viewModel.toggleSaveDeal(deal) },
                            onDirectCheckout = {
                                if (userProfile.autoCopyCoupons && deal.coupon != null) {
                                    copyTextToClipboard(context, "Promo Code", deal.coupon.code)
                                    viewModel.recordCouponCopy(deal.coupon.code, deal.id, deal.merchant, deal.category)
                                }
                                viewModel.recordOutboundClick(deal.id, deal.merchant, deal.category)
                                openSafeExternalUrl(context, deal.buyUrl)
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }
}
