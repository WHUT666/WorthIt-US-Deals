package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PricePoint
import com.example.ui.theme.WorthItTheme
import com.example.ui.util.formatMoney

@Composable
fun PriceHistoryChartCard(
    pricePoints: List<PricePoint>,
    historicLow: Double?,
    currentPrice: Double?,
    modifier: Modifier = Modifier
) {
    val ext = WorthItTheme.extendedColors
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("price_history_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShowChart,
                        contentDescription = "Observed price history",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Observed Price History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (historicLow != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(ext.successContainer, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = ext.success,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Low ${formatMoney(historicLow)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = ext.success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                pricePoints.isEmpty() -> {
                    Text(
                        text = if (currentPrice != null) {
                            "Baseline snapshot recorded at ${formatMoney(currentPrice)}. Additional syncs will chart price movements over time."
                        } else {
                            "No numeric price snapshots recorded yet for this listing."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                pricePoints.size == 1 -> {
                    val onlyPoint = pricePoints.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "First Observed Snapshot",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = onlyPoint.dateLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatMoney(onlyPoint.price),
                            style = MaterialTheme.typography.titleLarge,
                            color = ext.success,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    val minPrice = pricePoints.minOf { it.price } * 0.96
                    val maxPrice = pricePoints.maxOf { it.price } * 1.04
                    val range = (maxPrice - minPrice).coerceAtLeast(1.0)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val stepX = w / (pricePoints.size - 1).coerceAtLeast(1)

                        val points = pricePoints.mapIndexed { idx, pt ->
                            val x = idx * stepX
                            val normY = ((pt.price - minPrice) / range).toFloat()
                            val y = h - (normY * (h - 16.dp.toPx())) - 8.dp.toPx()
                            Offset(x, y)
                        }

                        // Horizontal reference grid lines
                        drawLine(gridColor, Offset(0f, h), Offset(w, h), strokeWidth = 1.dp.toPx())
                        drawLine(gridColor.copy(alpha = 0.5f), Offset(0f, h / 2f), Offset(w, h / 2f), strokeWidth = 1.dp.toPx())

                        val linePath = Path()
                        val fillPath = Path()
                        points.forEachIndexed { idx, pt ->
                            if (idx == 0) {
                                linePath.moveTo(pt.x, pt.y)
                                fillPath.moveTo(pt.x, h)
                                fillPath.lineTo(pt.x, pt.y)
                            } else {
                                linePath.lineTo(pt.x, pt.y)
                                fillPath.lineTo(pt.x, pt.y)
                            }
                        }
                        fillPath.lineTo(points.last().x, h)
                        fillPath.close()

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(primaryColor.copy(alpha = 0.18f), primaryColor.copy(alpha = 0.0f))
                            )
                        )
                        drawPath(
                            path = linePath,
                            color = primaryColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )

                        points.forEachIndexed { idx, pt ->
                            val isLatest = idx == points.lastIndex
                            drawCircle(
                                color = if (isLatest) ext.success else primaryColor,
                                radius = if (isLatest) 6.dp.toPx() else 4.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = surfaceColor,
                                radius = if (isLatest) 2.5.dp.toPx() else 1.8.dp.toPx(),
                                center = pt
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Display first, middle (if >=3), and latest snapshot labels cleanly without crowding
                    val labelIndices = when {
                        pricePoints.size <= 4 -> pricePoints.indices.toList()
                        else -> listOf(0, pricePoints.size / 2, pricePoints.lastIndex)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        labelIndices.forEach { idx ->
                            val pt = pricePoints[idx]
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = pt.dateLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatMoney(pt.price),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
