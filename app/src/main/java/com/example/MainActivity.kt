package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.algorithm.DealPushRecommendationEngine
import com.example.model.AppThemeMode
import com.example.ui.components.FloatingFeedbackPill
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
import kotlinx.coroutines.launch

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
    val savedDeals by viewModel.savedDeals.collectAsStateWithLifecycle()
    val uiFeedbackToast by viewModel.uiFeedbackToast.collectAsStateWithLifecycle()
    val ext = WorthItTheme.extendedColors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // Preserve scroll positions across tab switches AND detail screen visits
    val feedListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val radarListState = rememberLazyListState()
    val savedListState = rememberLazyListState()

    // Auto-collapsing TopAppBar reclaims vertical reading viewport on scroll
    val topAppBarScrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // Cache last viewed deal so slide-out exit animation renders cleanly instead of snapping blank
    var cachedDetailDeal by remember { mutableStateOf<DealPushRecommendationEngine.ScoredDeal?>(null) }
    LaunchedEffect(selectedScoredDeal) {
        if (selectedScoredDeal != null) {
            cachedDetailDeal = selectedScoredDeal
        }
    }

    // Always restore top bar smoothly when switching tabs
    LaunchedEffect(currentTab) {
        topAppBarScrollBehavior.state.heightOffset = 0f
    }

    // Pressing system Back on any non-home tab returns cleanly to Deals instead of closing the app
    BackHandler(enabled = currentTab != MainAppTab.FEED && selectedScoredDeal == null) {
        currentTab = MainAppTab.FEED
    }

    val isDetailOpen = selectedScoredDeal != null
    val backgroundScale by animateFloatAsState(
        targetValue = if (isDetailOpen) 0.965f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "detailBackgroundScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // Primary App Shell stays mounted so all list scroll states remain intact
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)
                .graphicsLayer {
                    scaleX = backgroundScale
                    scaleY = backgroundScale
                },
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                Column {
                    TopAppBar(
                        scrollBehavior = topAppBarScrollBehavior,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = ext.chromeBarBg,
                            scrolledContainerColor = ext.chromeBarBg,
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
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleLightDarkTheme(isDarkTheme)
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("theme_toggle_btn")
                                ) {
                                    AnimatedContent(
                                        targetState = isDarkTheme,
                                        transitionSpec = {
                                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.72f)) togetherWith
                                                (fadeOut(tween(140)) + scaleOut(targetScale = 0.72f))
                                        },
                                        label = "themeIconTransition"
                                    ) { dark ->
                                        Icon(
                                            imageVector = if (dark) WorthItIcons.SunMode else WorthItIcons.MoonMode,
                                            contentDescription = if (dark) "Switch to Light Theme" else "Switch to Dark Theme",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }
                                }
                            }
                            Surface(
                                color = if (currentTab == MainAppTab.ALGORITHM) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = CircleShape,
                                border = BorderStroke(0.5.dp, ext.hairline),
                                modifier = Modifier.padding(end = 10.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        currentTab = MainAppTab.ALGORITHM
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .minimumInteractiveComponentSize()
                                        .testTag("app_bar_tune_btn")
                                ) {
                                    Icon(
                                        imageVector = if (currentTab == MainAppTab.ALGORITHM) {
                                            WorthItIcons.SlidersFilled
                                        } else {
                                            WorthItIcons.SlidersOutlined
                                        },
                                        contentDescription = "Open Push Engine settings",
                                        tint = if (currentTab == MainAppTab.ALGORITHM) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
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
                            val iconScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.08f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "tabIconScale_${tab.name}"
                            )
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (isSelected) {
                                        // Re-tapping active tab smoothly scrolls to top & reveals top bar
                                        topAppBarScrollBehavior.state.heightOffset = 0f
                                        scope.launch {
                                            when (tab) {
                                                MainAppTab.FEED -> feedListState.animateScrollToItem(0)
                                                MainAppTab.COMPARE_SEARCH -> searchListState.animateScrollToItem(0)
                                                MainAppTab.RADAR -> radarListState.animateScrollToItem(0)
                                                MainAppTab.SAVED -> savedListState.animateScrollToItem(0)
                                                MainAppTab.ALGORITHM -> Unit
                                            }
                                        }
                                    } else {
                                        currentTab = tab
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = {
                                    val iconModifier = Modifier
                                        .size(22.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                    if (tab == MainAppTab.SAVED && savedDeals.isNotEmpty()) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                ) {
                                                    Text(
                                                        text = if (savedDeals.size > 99) "99+" else "${savedDeals.size}",
                                                        style = MaterialTheme.typography.labelSmall
                                                    )
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.title,
                                                modifier = iconModifier
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.title,
                                            modifier = iconModifier
                                        )
                                    }
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
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith
                            fadeOut(tween(130, easing = FastOutSlowInEasing))
                    },
                    label = "mainTabContent"
                ) { tab ->
                    when (tab) {
                        MainAppTab.FEED -> FeedScreen(
                            viewModel = viewModel,
                            listState = feedListState,
                            onSearchAllStores = { query ->
                                currentTab = MainAppTab.COMPARE_SEARCH
                                viewModel.submitPlatformSearchNow(query, forceRefresh = false)
                            }
                        )
                        MainAppTab.COMPARE_SEARCH -> MultiPlatformSearchScreen(
                            viewModel = viewModel,
                            listState = searchListState
                        )
                        MainAppTab.RADAR -> RadarLiveScreen(
                            viewModel = viewModel,
                            listState = radarListState
                        )
                        MainAppTab.SAVED -> SavedDealsScreen(
                            viewModel = viewModel,
                            listState = savedListState,
                            onExploreDeals = { currentTab = MainAppTab.FEED }
                        )
                        MainAppTab.ALGORITHM -> AlgorithmProfileScreen(viewModel = viewModel)
                    }
                }
            }
        }

        // Subtle scrim behind sliding Deal Detail Screen
        AnimatedVisibility(
            visible = isDetailOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        }

        // Smooth Apple-style Slide-In / Slide-Out Deal Detail Overlay
        AnimatedVisibility(
            visible = isDetailOpen,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            ) + fadeIn(tween(180)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            ) + fadeOut(tween(160))
        ) {
            val activeDetail = selectedScoredDeal ?: cachedDetailDeal
            if (activeDetail != null) {
                DealDetailScreen(
                    scoredDeal = activeDetail,
                    viewModel = viewModel,
                    onBack = { viewModel.closeDealDetail() }
                )
            }
        }

        // Global Non-Jumping Floating Toast Overlay (Bookmark Undo, Coupon Copied, Settings Saved)
        FloatingFeedbackPill(
            visible = uiFeedbackToast != null,
            message = uiFeedbackToast?.message.orEmpty(),
            actionLabel = uiFeedbackToast?.actionLabel,
            onActionClick = uiFeedbackToast?.undoDealId?.let { dealId ->
                { viewModel.undoLastBookmarkToggle(dealId) }
            },
            onDismiss = { viewModel.dismissUiToast() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = if (isDetailOpen) 84.dp else 92.dp)
        )
    }
}
