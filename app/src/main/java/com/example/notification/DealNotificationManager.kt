package com.example.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.DealItem

class DealNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "worthit_deal_alerts"
        private const val CHANNEL_NAME = "WorthIt Price Drop Alerts"
        private const val CHANNEL_DESC = "Instant notifications when monitored US retailers drop prices or hit keyword alerts."
    }

    init {
        ensureChannelCreated()
    }

    private fun ensureChannelCreated() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Dispatches a real Android system notification for a high-scoring deal alert.
     * Returns true if posted to the system tray, false if permission is not granted.
     */
    fun postDealAlertNotification(deal: DealItem, reasonTitle: String): Boolean {
        if (!hasNotificationPermission()) return false

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deal_id", deal.id)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            deal.id.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openMerchantIntent = Intent(Intent.ACTION_VIEW, Uri.parse(deal.buyUrl))
        val merchantPendingIntent = PendingIntent.getActivity(
            context,
            deal.id.hashCode() + 1,
            openMerchantIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priceText = deal.currentPrice?.let { "$${String.format("%.2f", it)}" } ?: "Live Deal"
        val discountBadge = if (deal.discountPercent > 0) " (${deal.discountPercent}% OFF)" else ""
        val title = "${deal.merchant.displayName}: $priceText$discountBadge"
        val body = "${deal.title} — $reasonTitle"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Open ${deal.merchant.displayName}", merchantPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(deal.id.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun postTrackedUrlDropNotification(label: String, price: Double, targetPrice: Double, url: String): Boolean {
        if (!hasNotificationPermission()) return false

        val openUrlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val pendingIntent = PendingIntent.getActivity(
            context,
            url.hashCode(),
            openUrlIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Target Price Hit: $${String.format("%.2f", price)}")
            .setContentText("$label dropped to $${String.format("%.2f", price)} (Target: $${String.format("%.2f", targetPrice)})")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(url.hashCode(), notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
