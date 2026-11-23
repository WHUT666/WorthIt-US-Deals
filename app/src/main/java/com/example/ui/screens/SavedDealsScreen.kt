package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.algorithm.DealPushRecommendationEngine
import com.example.ui.components.DealCard
import com.example.ui.icons.WorthItIcons
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.copyTextToClipboard
import com.example.ui.util.openSafeExternalUrl
import com.example.viewmodel.DealRadarViewModel

@Composable
fun SavedDealsScreen(
    viewModel: DealRadarViewModel,
    listState: LazyListState = rememberLazyListState(),
    onExploreDeals: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ext = WorthItTheme.extendedColors
    val savedDeals by viewModel.savedDeals.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val behaviorAffinity by viewModel.behaviorAffinity.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("saved_deals_screen")
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Saved Deals (${savedDeals.size})",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Persisted locally in Room. Live sync updates observed prices and snapshots for your watchlist.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (savedDeals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 48.dp),
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
                                imageVector = WorthItIcons.BookmarkOutlined,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No saved deals yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Bookmark any deal from the live feed to keep tracking its price history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExploreDeals,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("saved_explore_deals_btn")
                    ) {
                        Text("Explore Live Deals")
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(savedDeals, key = { it.id }) { deal ->
                    val scored = DealPushRecommendationEngine.calculateScore(deal, userProfile, behaviorAffinity)
                    DealCard(
                        scoredDeal = scored,
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
            }
        }
    }
}
