package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.algorithm.DealPushRecommendationEngine
import com.example.model.FreshnessTier
import com.example.model.VerificationStatus
import com.example.ui.components.MerchantBadge
import com.example.ui.components.PriceHistoryChartCard
import com.example.ui.components.SpinningSyncIcon
import com.example.ui.components.springPressScale
import com.example.ui.icons.WorthItIcons
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.copyTextToClipboard
import com.example.ui.util.formatMoney
import com.example.ui.util.formatRelativeTime
import com.example.ui.util.openSafeExternalUrl
import com.example.viewmodel.DealRadarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    scoredDeal: DealPushRecommendationEngine.ScoredDeal,
    viewModel: DealRadarViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val ext = WorthItTheme.extendedColors
    val deal = scoredDeal.deal
    val activePrice = deal.effectiveCurrentPrice

    val priceHistory by viewModel.selectedDealPriceHistory.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val verifyingDealIds by viewModel.verifyingDealIds.collectAsStateWithLifecycle()
    val isVerifying = verifyingDealIds.contains(deal.id)

    val buyCtaInteraction = remember { MutableInteractionSource() }
    val saveScale by animateFloatAsState(
        targetValue = if (deal.isSaved) 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "detailSaveScale"
    )

    BackHandler { onBack() }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ext.chromeBarBg,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.primary,
                        actionIconContentColor = MaterialTheme.colorScheme.primary
                    ),
                    title = { Text(deal.brand, maxLines = 1, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            modifier = Modifier.testTag("detail_back_btn")
                        ) {
                            Icon(WorthItIcons.BackChevron, contentDescription = "Back", modifier = Modifier.size(22.dp))
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.toggleSaveDeal(deal)
                            },
                            modifier = Modifier.testTag("detail_save_btn")
                        ) {
                            Icon(
                                imageVector = if (deal.isSaved) WorthItIcons.BookmarkFilled else WorthItIcons.BookmarkOutlined,
                                contentDescription = "Save deal",
                                tint = if (deal.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(21.dp)
                                    .graphicsLayer {
                                        scaleX = saveScale
                                        scaleY = saveScale
                                    }
                            )
                        }
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val pricePart = activePrice?.let { " for ${formatMoney(it)}" } ?: ""
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, "${deal.title}$pricePart at ${deal.merchant.displayName}: ${deal.buyUrl}")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Deal"))
                            },
                            modifier = Modifier.testTag("detail_share_btn")
                        ) {
                            Icon(WorthItIcons.ShareArrow, contentDescription = "Share deal", modifier = Modifier.size(20.dp))
                        }
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = ext.hairline)
            }
        },
        bottomBar = {
            Surface(
                color = ext.chromeBarBg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    HorizontalDivider(thickness = 0.5.dp, color = ext.hairline)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (deal.cashBackRate > 0 && deal.effectivePriceAfterCashback != null) "Est. Net After Cashback" else "Live Price",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val displayPrice = deal.effectivePriceAfterCashback ?: activePrice
                            Text(
                                text = displayPrice?.let { formatMoney(it) } ?: "Check Store",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = ext.priceAccent
                            )
                        }

                        Button(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (userProfile.autoCopyCoupons && deal.coupon != null) {
                                    copyTextToClipboard(context, "Promo Code", deal.coupon.code)
                                    viewModel.recordCouponCopy(deal.coupon.code, deal.id, deal.merchant, deal.category)
                                }
                                viewModel.recordOutboundClick(deal.id, deal.merchant, deal.category)
                                openSafeExternalUrl(context, deal.buyUrl)
                            },
                            interactionSource = buyCtaInteraction,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier
                                .springPressScale(buyCtaInteraction, pressedScale = 0.96f)
                                .minimumInteractiveComponentSize()
                                .testTag("detail_buy_now_btn")
                        ) {
                            Text(
                                text = "Open ${deal.merchant.displayName}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(WorthItIcons.ArrowUpRight, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag("deal_detail_content")
        ) {
            if (!deal.imageUrl.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(0.5.dp, ext.hairline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = deal.imageUrl,
                        contentDescription = deal.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp)
                            .padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MerchantBadge(deal.merchant)
                Spacer(modifier = Modifier.width(6.dp))
                if (scoredDeal.freshnessTier == FreshnessTier.FLASH) {
                    Surface(
                        color = ext.warningContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FLASH",
                            color = ext.warning,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (deal.isHistoricLow && activePrice != null) {
                    Surface(
                        color = ext.successContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Observed Low",
                            color = ext.success,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${deal.sourceName} • ${formatRelativeTime(deal.publishedAtMillis)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Real-Time Stock & Price Verification Card (with animated state colors & spinning refresh)
            LiveVerificationBannerCard(
                status = deal.verificationStatus,
                note = deal.verificationNote,
                verifiedAtMillis = deal.verifiedAtMillis,
                isVerifying = isVerifying,
                onReverify = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    viewModel.verifyDealLiveNow(deal, force = true)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Price & Coupon Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, ext.hairline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = activePrice?.let { formatMoney(it) } ?: "See Merchant",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = ext.priceAccent,
                                    fontWeight = FontWeight.Bold
                                )
                                if (deal.originalPrice != null && activePrice != null && deal.originalPrice > activePrice) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatMoney(deal.originalPrice),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (deal.discountPercent > 0 && deal.originalPrice != null && activePrice != null) {
                                Text(
                                    text = "Save ${formatMoney(deal.originalPrice - activePrice)} (${deal.discountPercent}% OFF)",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = ext.priceAccent
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "DealRank ${scoredDeal.compositeScore.toInt()}/100",
                                style = MaterialTheme.typography.titleSmall,
                                color = ext.success,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${(deal.worthRatio * 100).toInt()}% Worth Ratio",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (deal.coupon != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ext.couponContainer,
                            border = BorderStroke(1.dp, ext.coupon.copy(alpha = 0.35f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        WorthItIcons.CouponTicket,
                                        contentDescription = null,
                                        tint = ext.coupon,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Code: ${deal.coupon.code}",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ext.coupon,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = deal.coupon.discountDescription,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ext.coupon
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        copyTextToClipboard(context, "Promo Code", deal.coupon.code)
                                        viewModel.recordCouponCopy(deal.coupon.code, deal.id, deal.merchant, deal.category)
                                    }
                                ) {
                                    Icon(WorthItIcons.CopyLayers, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Worth vs Pass Community Voting with smooth animated states
            val worthContainer by animateColorAsState(
                targetValue = if (deal.userVote == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                animationSpec = tween(180),
                label = "detailWorthBg"
            )
            val passContainer by animateColorAsState(
                targetValue = if (deal.userVote == false) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                animationSpec = tween(180),
                label = "detailPassBg"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.voteDeal(deal, true)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = worthContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_vote_worth")
                ) {
                    Icon(
                        imageVector = if (deal.userVote == true) WorthItIcons.ThumbUpFilled else WorthItIcons.ThumbUpOutlined,
                        contentDescription = null,
                        tint = if (deal.userVote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = deal.worthVotes,
                        transitionSpec = {
                            (slideInVertically { -it / 2 } + fadeIn(tween(140))) togetherWith
                                (slideOutVertically { it / 2 } + fadeOut(tween(120)))
                        },
                        label = "detailWorthAnim"
                    ) { count ->
                        Text("Worth ($count)")
                    }
                }

                FilledTonalButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.voteDeal(deal, false)
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = passContainer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_vote_not_worth")
                ) {
                    Icon(
                        imageVector = WorthItIcons.ThumbDownOutlined,
                        contentDescription = null,
                        tint = if (deal.userVote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    AnimatedContent(
                        targetState = deal.notWorthVotes,
                        transitionSpec = {
                            (slideInVertically { it / 2 } + fadeIn(tween(140))) togetherWith
                                (slideOutVertically { -it / 2 } + fadeOut(tween(120)))
                        },
                        label = "detailPassAnim"
                    ) { count ->
                        Text("Pass ($count)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Real Observed Price History Chart from Room Snapshots
            PriceHistoryChartCard(
                pricePoints = priceHistory,
                historicLow = deal.historicLowPrice,
                currentPrice = activePrice
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Transparent DealRank 6-Factor Breakdown Card with smooth animated progress bars
            val bd = scoredDeal.recommendationReason.breakdown
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, ext.hairline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(WorthItIcons.Sparkles, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Why WorthIt Ranked This ${scoredDeal.compositeScore.toInt()}/100",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scoredDeal.recommendationReason.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ScoreFactorBar("Discount Depth", bd.discountScore, deal.id)
                    ScoreFactorBar("Observed Historic Low", bd.historicLowScore, deal.id)
                    ScoreFactorBar("Community Social Trust", bd.socialScore, deal.id)
                    ScoreFactorBar("Personal & Learned Affinity", bd.personalAndBehaviorScore, deal.id)
                    ScoreFactorBar("Merchant & Coupon Value", bd.merchantScore, deal.id)
                    ScoreFactorBar("Time Freshness", bd.freshnessScore, deal.id)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Live Source Summary & Community Thread Link
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(0.5.dp, ext.hairline),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Listing Summary & Source",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = deal.summary.ifBlank { "Live listing captured from ${deal.sourceName}. Verify final cart total and shipping eligibility on ${deal.merchant.displayName}." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (deal.sourceUrl.isNotBlank() && deal.sourceUrl != deal.buyUrl) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { openSafeExternalUrl(context, deal.sourceUrl) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Community Discussion (${deal.communityCommentsCount} comments)")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LiveVerificationBannerCard(
    status: VerificationStatus,
    note: String,
    verifiedAtMillis: Long,
    isVerifying: Boolean,
    onReverify: () -> Unit
) {
    val ext = WorthItTheme.extendedColors
    val targetContainer = when {
        isVerifying -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        status == VerificationStatus.VERIFIED_LIVE -> ext.successContainer
        status == VerificationStatus.PRICE_INCREASED -> ext.warningContainer
        status == VerificationStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    val targetTint = when {
        isVerifying -> MaterialTheme.colorScheme.primary
        status == VerificationStatus.VERIFIED_LIVE -> ext.success
        status == VerificationStatus.PRICE_INCREASED -> ext.warning
        status == VerificationStatus.OUT_OF_STOCK -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val containerColor by animateColorAsState(targetContainer, tween(260), label = "verifyBg")
    val contentTint by animateColorAsState(targetTint, tween(260), label = "verifyTint")
    val icon = when (status) {
        VerificationStatus.VERIFIED_LIVE -> WorthItIcons.VerifiedShield
        VerificationStatus.PRICE_INCREASED -> Icons.Default.Warning
        VerificationStatus.OUT_OF_STOCK -> Icons.Default.ErrorOutline
        else -> Icons.Default.CheckCircle
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("live_verification_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (isVerifying) {
                    SpinningSyncIcon(
                        isSyncing = true,
                        tint = contentTint,
                        contentDescription = "Checking live stock",
                        size = 19.dp
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = status.label,
                        tint = contentTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    val headline = if (isVerifying) {
                        "Checking live retailer stock & price..."
                    } else {
                        val timeSuffix = if (verifiedAtMillis > 0L) " • ${formatRelativeTime(verifiedAtMillis)}" else ""
                        "${status.label}$timeSuffix"
                    }
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentTint,
                        fontWeight = FontWeight.Bold
                    )
                    val subtitle = if (isVerifying) {
                        "Connecting directly to merchant page to confirm active deal"
                    } else {
                        note.ifBlank { "Tap Re-check to verify current price & stock directly at retailer." }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }

            TextButton(
                onClick = onReverify,
                enabled = !isVerifying,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("reverify_deal_btn")
            ) {
                SpinningSyncIcon(
                    isSyncing = isVerifying,
                    tint = MaterialTheme.colorScheme.primary,
                    contentDescription = "Re-verify deal",
                    size = 15.dp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Re-check", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ScoreFactorBar(label: String, score: Int, animKey: String) {
    var revealed by remember(animKey) { mutableStateOf(false) }
    LaunchedEffect(animKey) { revealed = true }
    val animatedProgress by animateFloatAsState(
        targetValue = if (revealed) (score / 100f).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "scoreBar_$label"
    )
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}
