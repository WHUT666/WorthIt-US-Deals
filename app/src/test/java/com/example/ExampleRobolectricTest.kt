package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.ConversionEventEntity
import com.example.data.local.DealEntity
import com.example.data.local.PriceSnapshotEntity
import com.example.data.network.LiveDealNetworkClient
import com.example.model.DealCategory
import com.example.model.USMerchant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `app_name matches WorthIt US Deals`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("WorthIt US Deals", context.getString(R.string.app_name))
    }

    @Test
    fun `room persists deals price snapshots and conversion funnel stages`() = runTest {
        val dao = db.dealDao()
        val now = System.currentTimeMillis()

        dao.upsertDeal(
            DealEntity(
                id = "rtx_5090_bb",
                title = "NVIDIA GeForce RTX 5090 Founders Edition 32GB",
                brand = "NVIDIA",
                merchantName = USMerchant.BEST_BUY.name,
                categoryName = DealCategory.GAMING.name,
                currentPrice = 1999.99,
                originalPrice = 2199.99,
                historicLowPrice = 1999.99,
                couponCode = null,
                couponDescription = null,
                cashBackRate = 0.05,
                freeShipping = true,
                worthVotes = 140,
                notWorthVotes = 8,
                communityCommentsCount = 45,
                sourceName = "r/buildapcsales",
                imageUrl = null,
                buyUrl = "https://www.bestbuy.com/site/searchpage.jsp?st=rtx+5090",
                sourceUrl = "https://www.reddit.com/r/buildapcsales",
                summary = "Founders Edition drop in stock",
                publishedAtMillis = now,
                lastCheckedAtMillis = now,
                isSaved = true
            )
        )

        dao.insertPriceSnapshot(
            PriceSnapshotEntity(
                dealId = "rtx_5090_bb",
                productKey = "nvidia_geforce_rtx_5090",
                merchantName = USMerchant.BEST_BUY.name,
                price = 2199.99,
                recordedAtMillis = now - 86_400_000L
            )
        )
        dao.insertPriceSnapshot(
            PriceSnapshotEntity(
                dealId = "rtx_5090_bb",
                productKey = "nvidia_geforce_rtx_5090",
                merchantName = USMerchant.BEST_BUY.name,
                price = 1999.99,
                recordedAtMillis = now
            )
        )

        dao.insertConversionEvent(
            ConversionEventEntity(
                dealId = "rtx_5090_bb",
                merchantName = USMerchant.BEST_BUY.name,
                categoryName = DealCategory.GAMING.name,
                stage = "DETAIL_VIEW",
                timestampMillis = now
            )
        )
        dao.insertConversionEvent(
            ConversionEventEntity(
                dealId = "rtx_5090_bb",
                merchantName = USMerchant.BEST_BUY.name,
                categoryName = DealCategory.GAMING.name,
                stage = "OUTBOUND_CLICK",
                timestampMillis = now
            )
        )

        val saved = dao.observeSavedDeals().first()
        assertEquals(1, saved.size)
        assertEquals("rtx_5090_bb", saved.first().id)

        val lowestObserved = dao.getLowestObservedPrice("rtx_5090_bb", "nvidia_geforce_rtx_5090")
        assertNotNull(lowestObserved)
        assertEquals(1999.99, lowestObserved!!, 0.01)

        val snapshots = dao.observePriceSnapshotsForDeal("rtx_5090_bb").first()
        assertEquals(2, snapshots.size)

        val stages = dao.observeFunnelStageCounts().first().associate { it.stage to it.count }
        assertEquals(1, stages["DETAIL_VIEW"])
        assertEquals(1, stages["OUTBOUND_CLICK"])
    }

    @Test
    fun `rss and reddit parsers extract real structured deals`() {
        val client = LiveDealNetworkClient()

        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel>
                <item>
                    <title>Apple AirPods Pro 2 (USB-C) $169.99 (Reg. $249) + Free Shipping at Amazon w/ code SAVE10</title>
                    <link>https://www.amazon.com/dp/B0CHWRXH8B</link>
                    <description><![CDATA[<img src="https://m.media-amazon.com/images/I/test.jpg" /> Lowest price this season.]]></description>
                </item>
            </channel></rss>
        """.trimIndent()

        val rssDeals = client.parseRssXml(sampleRss, "Slickdeals Frontpage")
        assertEquals(1, rssDeals.size)
        val airpods = rssDeals.first()
        assertEquals(USMerchant.AMAZON, airpods.merchant)
        assertEquals("Apple", airpods.brand)
        assertEquals(169.99, airpods.currentPrice!!, 0.01)
        assertEquals(249.00, airpods.originalPrice!!, 0.01)
        assertEquals("SAVE10", airpods.coupon?.code)
        assertTrue(airpods.discountPercent >= 31)
    }
}
