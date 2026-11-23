package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.algorithm.DealPushRecommendationEngine
import com.example.model.FreshnessTier
import com.example.model.USMerchant
import com.example.model.VerificationStatus
import com.example.ui.icons.WorthItIcons
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.formatMoney
import com.example.ui.util.formatRelativeTime

@Composable
fun MerchantBadge(merchant: USMerchant, modifier: Modifier = Modifier) {
    val merchantTint = Color(merchant.brandColorHex)
    Surface(
        color = merchantTint.copy(alpha = 0.13f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier
    ) {
        Text(
            text = merchant.displayName,
            color = merchantTint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DealCard(
    scoredDeal: DealPushRecommendationEngine.ScoredDeal,
    onClick: () -> Unit,
    onVoteWorth: () -> Unit,
    onVoteNotWorth: () -> Unit,
    onToggleSave: () -> Unit,
    onDirectCheckout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deal = scoredDeal.deal
    val ext = WorthItTheme.extendedColors
    val displayPrice = deal.effectiveCurrentPrice

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("deal_card_${deal.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (scoredDeal.freshnessTier == FreshnessTier.FLASH) 1.dp else 0.5.dp,
            color = if (scoredDeal.freshnessTier == FreshnessTier.FLASH) {
                ext.warning.copy(alpha = 0.5f)
            } else {
                ext.hairline
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Top bar: Algorithmic Match Score, Freshness/Verification cue & Save Bookmark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                if (scoredDeal.matchesWatchedKeyword) ext.warningContainer else MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (scoredDeal.matchesWatchedKeyword) WorthItIcons.FlashBolt else WorthItIcons.Sparkles,
                            contentDescription = null,
                            tint = if (scoredDeal.matchesWatchedKeyword) ext.warning else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val primaryTag = scoredDeal.recommendationReason.tags.firstOrNull() ?: deal.sourceName
                        Text(
                            text = "Score ${scoredDeal.compositeScore.toInt()} • $primaryTag",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (scoredDeal.matchesWatchedKeyword) ext.warning else MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Live Verification status chip (when verified)
                    when (deal.verificationStatus) {
                        VerificationStatus.VERIFIED_LIVE -> {
                            Surface(
                                color = ext.successContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = WorthItIcons.VerifiedShield,
                                        contentDescription = "Verified live",
                                        tint = ext.success,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.success,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        VerificationStatus.PRICE_INCREASED -> {
                            Surface(
                                color = ext.warningContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = "Price increased",
                                        tint = ext.warning,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Price Up",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.warning,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        VerificationStatus.OUT_OF_STOCK -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Sold Out",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                        else -> Unit
                    }
                }

                IconButton(
                    onClick = onToggleSave,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("bookmark_btn_${deal.id}")
                ) {
                    Icon(
                        imageVector = if (deal.isSaved) WorthItIcons.BookmarkFilled else WorthItIcons.BookmarkOutlined,
                        contentDescription = if (deal.isSaved) "Remove saved deal" else "Save deal",
                        tint = if (deal.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Main content row with Coil product image + metadata + title
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (!deal.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = deal.imageUrl,
                        contentDescription = deal.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = WorthItIcons.DealsOutlined,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MerchantBadge(deal.merchant)
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (deal.isHistoricLow && displayPrice != null) {
                            Surface(
                                color = ext.successContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "Low",
                                    color = ext.success,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = formatRelativeTime(deal.publishedAtMillis),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (scoredDeal.freshnessTier == FreshnessTier.FLASH) {
                                ext.warning
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (scoredDeal.freshnessTier == FreshnessTier.FLASH) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = deal.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "via ${deal.sourceName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (displayPrice != null) {
                    Text(
                        text = formatMoney(displayPrice),
                        style = MaterialTheme.typography.headlineSmall,
                        color = ext.priceAccent,
                        fontWeight = FontWeight.Bold
                    )
                    if (deal.originalPrice != null && deal.originalPrice > displayPrice) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatMoney(deal.originalPrice),
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = TextDecoration.LineThrough,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (deal.discountPercent > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = ext.priceAccentContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "-${deal.discountPercent}%",
                                color = ext.priceAccent,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "Live Merchant Offer",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (deal.coupon != null) {
                    Surface(
                        color = ext.couponContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = WorthItIcons.CouponTicket,
                                contentDescription = null,
                                tint = ext.coupon,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = deal.coupon.code,
                                color = ext.coupon,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                thickness = 0.5.dp,
                color = ext.hairline
            )

            // Action Footer: Worth / Not Worth + Direct Conversion CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onVoteWorth,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (deal.userVote == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vote_worth_${deal.id}")
                    ) {
                        Icon(
                            imageVector = if (deal.userVote == true) WorthItIcons.ThumbUpFilled else WorthItIcons.ThumbUpOutlined,
                            contentDescription = "Vote worth",
                            tint = if (deal.userVote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${deal.worthVotes}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (deal.userVote == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledTonalButton(
                        onClick = onVoteNotWorth,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (deal.userVote == false) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("vote_not_worth_${deal.id}")
                    ) {
                        Icon(
                            imageVector = WorthItIcons.ThumbDownOutlined,
                            contentDescription = "Vote not worth",
                            tint = if (deal.userVote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${deal.notWorthVotes}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (deal.userVote == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Button(
                    onClick = onDirectCheckout,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("buy_direct_btn_${deal.id}")
                ) {
                    Text(
                        text = if (deal.coupon != null) "Clip & Go" else "Go to Deal",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Icon(
                        imageVector = WorthItIcons.ArrowUpRight,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}
