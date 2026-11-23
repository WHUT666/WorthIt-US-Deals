package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class StageCountRow(
    val stage: String,
    val count: Int
)

data class MerchantEventCountRow(
    val merchantName: String,
    val count: Int
)

data class CategoryEventCountRow(
    val categoryName: String,
    val count: Int
)

@Dao
interface DealDao {

    // --- Deals ---
    @Query("SELECT * FROM deals ORDER BY publishedAtMillis DESC LIMIT 250")
    fun observeAllDeals(): Flow<List<DealEntity>>

    @Query("SELECT * FROM deals WHERE isSaved = 1 ORDER BY lastCheckedAtMillis DESC")
    fun observeSavedDeals(): Flow<List<DealEntity>>

    @Query("SELECT * FROM deals WHERE id = :dealId LIMIT 1")
    suspend fun getDealById(dealId: String): DealEntity?

    @Query("SELECT * FROM deals WHERE LOWER(title) LIKE '%' || LOWER(:token) || '%' OR LOWER(brand) LIKE '%' || LOWER(:token) || '%' ORDER BY publishedAtMillis DESC LIMIT 60")
    suspend fun searchCachedDeals(token: String): List<DealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeals(deals: List<DealEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeal(deal: DealEntity)

    @Query("UPDATE deals SET isSaved = :saved WHERE id = :dealId")
    suspend fun updateSavedState(dealId: String, saved: Boolean)

    @Query("UPDATE deals SET userVote = :userVote, worthVotes = :worthVotes, notWorthVotes = :notWorthVotes WHERE id = :dealId")
    suspend fun updateVoteState(dealId: String, userVote: Int, worthVotes: Int, notWorthVotes: Int)

    @Query("UPDATE deals SET notificationSent = 1 WHERE id = :dealId")
    suspend fun markNotificationSent(dealId: String)

    @Query("DELETE FROM deals WHERE isSaved = 0 AND lastCheckedAtMillis < :cutoffMillis")
    suspend fun pruneStaleDeals(cutoffMillis: Long)

    // --- Real Observed Price History Snapshots ---
    @Query("SELECT * FROM price_snapshots WHERE dealId = :dealId ORDER BY recordedAtMillis ASC")
    fun observePriceSnapshotsForDeal(dealId: String): Flow<List<PriceSnapshotEntity>>

    @Query("SELECT * FROM price_snapshots WHERE dealId = :dealId ORDER BY recordedAtMillis DESC LIMIT 1")
    suspend fun getLatestSnapshotForDeal(dealId: String): PriceSnapshotEntity?

    @Query("SELECT MIN(price) FROM price_snapshots WHERE dealId = :dealId OR productKey = :productKey")
    suspend fun getLowestObservedPrice(dealId: String, productKey: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceSnapshot(snapshot: PriceSnapshotEntity)

    // --- User-Added Custom Retailer URL Watchlist ---
    @Query("SELECT * FROM tracked_urls WHERE isActive = 1 ORDER BY lastCheckedAtMillis DESC")
    fun observeTrackedUrls(): Flow<List<TrackedProductEntity>>

    @Query("SELECT * FROM tracked_urls WHERE isActive = 1")
    suspend fun getAllTrackedUrlsOnce(): List<TrackedProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackedUrl(entity: TrackedProductEntity): Long

    @Query("UPDATE tracked_urls SET label = :label, lastObservedPrice = :price, lastStatusMessage = :status, lastCheckedAtMillis = :checkedAt WHERE id = :id")
    suspend fun updateTrackedUrlResult(id: Long, label: String, price: Double?, status: String, checkedAt: Long)

    @Query("DELETE FROM tracked_urls WHERE id = :id")
    suspend fun deleteTrackedUrl(id: Long)

    // --- Real Conversion Funnel & Behavioral Affinity Logs ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertConversionEvent(event: ConversionEventEntity)

    @Query("SELECT stage, COUNT(*) as count FROM conversion_events GROUP BY stage")
    fun observeFunnelStageCounts(): Flow<List<StageCountRow>>

    @Query("SELECT merchantName, COUNT(*) as count FROM conversion_events WHERE stage IN ('OUTBOUND_CLICK', 'COUPON_COPY', 'BOOKMARK', 'DETAIL_VIEW') GROUP BY merchantName ORDER BY count DESC")
    fun observeMerchantAffinityCounts(): Flow<List<MerchantEventCountRow>>

    @Query("SELECT categoryName, COUNT(*) as count FROM conversion_events WHERE stage IN ('OUTBOUND_CLICK', 'COUPON_COPY', 'BOOKMARK', 'DETAIL_VIEW', 'VOTE_WORTH') GROUP BY categoryName ORDER BY count DESC")
    fun observeCategoryAffinityCounts(): Flow<List<CategoryEventCountRow>>

    @Query("DELETE FROM conversion_events")
    suspend fun clearConversionEvents()
}
