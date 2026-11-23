package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AppThemeMode
import com.example.ui.icons.WorthItBrandIconBadge
import com.example.ui.icons.WorthItIcons
import com.example.ui.screens.AlgorithmProfileScreen
import com.example.ui.screens.DealDetailScreen
import com.example.ui.screens.FeedScreen
import com.example.ui.screens.MultiPlatformSearchScreen
import com.example.ui.screens.RadarLiveScreen
import com.example.ui.screens.SavedDealsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.WorthItTheme
import com.example.viewmodel.DealRadarViewModel

enum class MainAppTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FEED("Deals", WorthItIcons.DealsFilled, WorthItIcons.DealsOutlined),
    COMPARE_SEARCH("Search", WorthItIcons.SearchFilled, WorthItIcons.SearchOutlined),
    RADAR("Radar", WorthItIcons.RadarFilled, WorthItIcons.RadarOutlined),
    SAVED("Saved", WorthItIcons.BookmarkFilled, WorthItIcons.BookmarkOutlined),
    ALGORITHM("Engine", WorthItIcons.SlidersFilled, WorthItIcons.SlidersOutlined)
}

class MainActivity : ComponentActivity() {

    private val viewModel: DealRadarViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleDeepLinkIntent(intent)
        setContent {
            val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val isDarkTheme = when (userProfile.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> systemDark
            }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                MainAppScaffold(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val dealId = intent?.getStringExtra("deal_id")
        if (!dealId.isNullOrBlank()) {
            viewModel.openDealById(dealId)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(
    viewModel: DealRadarViewModel,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    var currentTab by rememberSaveable { mutableStateOf(MainAppTab.FEED) }
    val selectedScoredDeal by viewModel.selectedScoredDeal.collectAsStateWithLifecycle()
    val ext = WorthItTheme.extendedColors

    if (selectedScoredDeal != null) {
        DealDetailScreen(
            scoredDeal = selectedScoredDeal!!,
            viewModel = viewModel,
            onBack = { viewModel.closeDealDetail() }
        )
    } else {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ext.chromeBarBg,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                WorthItBrandIconBadge(size = 28.dp)
                                Spacer(modifier = Modifier.width(9.dp))
                                Text(
                                    text = "WorthIt",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "US DEALS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        },
                        actions = {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                                border = BorderStroke(0.5.dp, ext.hairline),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                IconButton(
                                    onClick = { viewModel.toggleLightDarkTheme(isDarkTheme) },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("theme_toggle_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkTheme) WorthItIcons.SunMode else WorthItIcons.MoonMode,
                                        contentDescription = if (isDarkTheme) "Switch to Light Theme" else "Switch to Dark Theme",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = CircleShape,
                                border = BorderStroke(0.5.dp, ext.hairline),
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                IconButton(
                                    onClick = { currentTab = MainAppTab.ALGORITHM },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("app_bar_tune_btn")
                                ) {
                                    Icon(
                                        imageVector = WorthItIcons.SlidersOutlined,
                                        contentDescription = "Open Push Engine settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = ext.hairline)
                }
            },
            bottomBar = {
                Column {
                    HorizontalDivider(thickness = 0.5.dp, color = ext.hairline)
                    NavigationBar(
                        containerColor = ext.chromeBarBg,
                        tonalElevation = 0.dp,
                        modifier = Modifier.testTag("bottom_navigation_bar")
                    ) {
                        MainAppTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentTab = tab },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = { Text(tab.title, maxLines = 1) },
                                modifier = Modifier.testTag("tab_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    MainAppTab.FEED -> FeedScreen(viewModel = viewModel)
                    MainAppTab.COMPARE_SEARCH -> MultiPlatformSearchScreen(viewModel = viewModel)
                    MainAppTab.RADAR -> RadarLiveScreen(viewModel = viewModel)
                    MainAppTab.SAVED -> SavedDealsScreen(viewModel = viewModel)
                    MainAppTab.ALGORITHM -> AlgorithmProfileScreen(viewModel = viewModel)
                }
            }
        }
    }
}
