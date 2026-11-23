package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "deals")
data class DealEntity(
    @PrimaryKey val id: String,
    val title: String,
    val brand: String,
    val merchantName: String,
    val categoryName: String,
    val currentPrice: Double?,
    val originalPrice: Double?,
    val historicLowPrice: Double?,
    val couponCode: String?,
    val couponDescription: String?,
    val cashBackRate: Double,
    val freeShipping: Boolean,
    val worthVotes: Int,
    val notWorthVotes: Int,
    val communityCommentsCount: Int,
    val sourceName: String,
    val imageUrl: String?,
    val buyUrl: String,
    val sourceUrl: String,
    val summary: String,
    val publishedAtMillis: Long,
    val lastCheckedAtMillis: Long,
    val isSaved: Boolean = false,
    val userVote: Int = 0, // 1 = worth, -1 = not worth, 0 = none
    val notificationSent: Boolean = false
)

@Entity(
    tableName = "price_snapshots",
    indices = [Index(value = ["dealId"]), Index(value = ["productKey"])]
)
data class PriceSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dealId: String,
    val productKey: String,
    val merchantName: String,
    val price: Double,
    val recordedAtMillis: Long
)

@Entity(tableName = "tracked_urls")
data class TrackedProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val url: String,
    val merchantName: String,
    val targetPrice: Double?,
    val lastObservedPrice: Double?,
    val lastStatusMessage: String,
    val lastCheckedAtMillis: Long,
    val isActive: Boolean = true
)

@Entity(
    tableName = "conversion_events",
    indices = [Index(value = ["stage"]), Index(value = ["timestampMillis"])]
)
data class ConversionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dealId: String,
    val merchantName: String,
    val categoryName: String,
    val stage: String, // IMPRESSION, DETAIL_VIEW, COUPON_COPY, OUTBOUND_CLICK, BOOKMARK, VOTE_WORTH
    val timestampMillis: Long = System.currentTimeMillis()
)
