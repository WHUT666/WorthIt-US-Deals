package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DealCategory
import com.example.model.USMerchant
import com.example.ui.theme.WorthItTheme
import com.example.viewmodel.DealRadarViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AlgorithmProfileScreen(
    viewModel: DealRadarViewModel,
    modifier: Modifier = Modifier
) {
    val ext = WorthItTheme.extendedColors
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val funnelStats by viewModel.conversionFunnelStats.collectAsStateWithLifecycle()
    val behaviorAffinity by viewModel.behaviorAffinity.collectAsStateWithLifecycle()

    var selectedCats by remember(userProfile) { mutableStateOf(userProfile.preferredCategories) }
    var selectedMerchants by remember(userProfile) { mutableStateOf(userProfile.preferredMerchants) }
    var discountThreshold by remember(userProfile) { mutableFloatStateOf(userProfile.minimumDiscountThreshold.toFloat()) }
    var maxBudget by remember(userProfile) { mutableFloatStateOf(userProfile.maxBudgetUSD.toFloat()) }
    var onlyHistoric by remember(userProfile) { mutableStateOf(userProfile.onlyHistoricLows) }
    var pushEnabled by remember(userProfile) { mutableStateOf(userProfile.pushNotificationsEnabled) }
    var autoCopyCoupons by remember(userProfile) { mutableStateOf(userProfile.autoCopyCoupons) }
    var wDiscount by remember(userProfile) { mutableFloatStateOf(userProfile.discountWeight) }
    var wHistoric by remember(userProfile) { mutableFloatStateOf(userProfile.historicLowWeight) }
    var wSocial by remember(userProfile) { mutableFloatStateOf(userProfile.socialWorthWeight) }
    var wPersonal by remember(userProfile) { mutableFloatStateOf(userProfile.personalAffinityWeight) }

    var newKeywordInput by remember { mutableStateOf("") }
    var keywordsList by remember(userProfile) { mutableStateOf(userProfile.watchedKeywords) }
    var showSavedBanner by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        pushEnabled = granted
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("algorithm_profile_screen")
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Real Measured Conversion Funnel & Behavioral Affinity Dashboard
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Analytics,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Conversion Funnel & Learning",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (behaviorAffinity.totalInteractions > 0) {
                        TextButton(onClick = { viewModel.resetBehavioralLearning() }) {
                            Text("Reset")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FunnelMetricTile("Details", "${funnelStats.detailViews}", Modifier.weight(1f))
                    FunnelMetricTile("Coupons", "${funnelStats.couponCopies}", Modifier.weight(1f))
                    FunnelMetricTile("Checkouts", "${funnelStats.outboundClicks}", Modifier.weight(1f))
                    FunnelMetricTile("Conv.", "${funnelStats.outboundConversionPercent}%", Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(10.dp))

                val learnedSummary = buildString {
                    append("Learned signals (${behaviorAffinity.totalInteractions} actions): ")
                    funnelStats.topInterestCategory?.let { append("Top category ${it.displayName}") }
                        ?: append("Browse or bookmark deals to train personal weights")
                    funnelStats.topConvertingMerchant?.let { append(" • Top store ${it.displayName}") }
                }
                Text(
                    text = learnedSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Algorithm Header
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WorthIt Personalized Push Algorithm",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Adjust scoring weights, keyword triggers, and notification gates. Saved persistently in Preferences DataStore and applied live to your feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Tunable Algorithm Factor Weights
        Text(
            text = "Algorithm Factor Weights",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        WeightSliderRow("Discount Depth Weight", wDiscount) { wDiscount = it }
        WeightSliderRow("Observed Low Price Weight", wHistoric) { wHistoric = it }
        WeightSliderRow("Community Trust Weight", wSocial) { wSocial = it }
        WeightSliderRow("Personal & Learned Affinity Weight", wPersonal) { wPersonal = it }

        Spacer(modifier = Modifier.height(14.dp))

        // Minimum Discount & Budget Gates
        Text(
            text = "Push Gate: Min ${discountThreshold.toInt()}% OFF • Max Budget $${maxBudget.toInt()}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Slider(
            value = discountThreshold,
            onValueChange = { discountThreshold = it },
            valueRange = 5f..65f,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("discount_slider")
        )
        Slider(
            value = maxBudget,
            onValueChange = { maxBudget = it },
            valueRange = 100f..5000f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Toggles: Push Alerts, Auto-Copy Coupons, Observed Low Only
        ToggleCard(
            title = "Real System Push Notifications",
            subtitle = "Deliver instant Android notifications when live sync detects keyword hits or qualified drops.",
            checked = pushEnabled,
            testTag = "push_notifications_switch",
            onCheckedChange = { enable ->
                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.hasNotificationPermission()) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    pushEnabled = enable
                }
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToggleCard(
            title = "Auto-Copy Promo Codes on Checkout",
            subtitle = "Zero-friction conversion: copies verified coupon codes to clipboard automatically when opening a store.",
            checked = autoCopyCoupons,
            testTag = "auto_copy_coupon_switch",
            onCheckedChange = { autoCopyCoupons = it }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ToggleCard(
            title = "Observed Low Prices Only",
            subtitle = "Only display deals matching the lowest price observed in local snapshot history.",
            checked = onlyHistoric,
            testTag = "historic_only_switch",
            onCheckedChange = { onlyHistoric = it }
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Watched Keywords Radar
        Text(
            text = "Watched Keywords Radar",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "High-priority instant alerts for specific SKUs or models (e.g. 5090, OLED, AirPods).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newKeywordInput,
                onValueChange = { newKeywordInput = it },
                placeholder = { Text("Add keyword (e.g. 5090, iPad)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("keyword_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val trimmed = newKeywordInput.trim()
                    if (trimmed.isNotBlank()) {
                        keywordsList = (keywordsList + trimmed).distinct()
                        newKeywordInput = ""
                    }
                },
                modifier = Modifier.testTag("add_keyword_btn")
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            keywordsList.forEach { kw ->
                InputChip(
                    selected = true,
                    onClick = { keywordsList = keywordsList.filter { it != kw } },
                    label = { Text(kw) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, contentDescription = "Remove $kw", modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Preferred Categories",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            DealCategory.entries.filter { it != DealCategory.ALL }.forEach { cat ->
                val selected = selectedCats.contains(cat)
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedCats = if (selected) selectedCats - cat else selectedCats + cat
                    },
                    label = { Text(cat.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Preferred US Retailers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            USMerchant.majorUSRetailers.forEach { merchant ->
                val selected = selectedMerchants.contains(merchant)
                FilterChip(
                    selected = selected,
                    onClick = {
                        selectedMerchants = if (selected) selectedMerchants - merchant else selectedMerchants + merchant
                    },
                    label = { Text(merchant.displayName) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = {
                viewModel.savePreferences(
                    userProfile.copy(
                        preferredCategories = selectedCats.ifEmpty { setOf(DealCategory.ELECTRONICS) },
                        preferredMerchants = selectedMerchants.ifEmpty { setOf(USMerchant.AMAZON) },
                        minimumDiscountThreshold = discountThreshold.toInt(),
                        onlyHistoricLows = onlyHistoric,
                        maxBudgetUSD = maxBudget.toDouble(),
                        watchedKeywords = keywordsList,
                        pushNotificationsEnabled = pushEnabled,
                        autoCopyCoupons = autoCopyCoupons,
                        discountWeight = wDiscount,
                        historicLowWeight = wHistoric,
                        socialWorthWeight = wSocial,
                        personalAffinityWeight = wPersonal
                    )
                )
                showSavedBanner = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("save_profile_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Recalibrate Algorithm")
        }

        if (showSavedBanner) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = ext.successContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Saved to DataStore! Feed ranking and push alert rules updated.",
                    color = ext.success,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun FunnelMetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightSliderRow(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.05f..0.50f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
