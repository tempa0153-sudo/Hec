package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.MainViewModel
import com.example.ui.Screen
import com.example.ui.components.PillNavigationBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MovieDetailSheet
import com.example.ui.screens.ProfileDialog
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.MoviesAndSeriesTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = display
                val modes = currentDisplay?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null && maxRefreshMode.refreshRate >= 90f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxRefreshMode.modeId
                    window.attributes = params
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = window.attributes
                params.preferredRefreshRate = 120f
                window.attributes = params
            }
        } catch (_: Exception) {
            // Fallback
        }

        setContent {
            StreamingApp(viewModel = viewModel)
        }
    }
}

@Composable
fun StreamingApp(viewModel: MainViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
    val homeState by viewModel.homeState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchType by viewModel.searchType.collectAsStateWithLifecycle()
    val savedMedia by viewModel.savedMedia.collectAsStateWithLifecycle()
    val detailState by viewModel.detailState.collectAsStateWithLifecycle()
    val isProfileOpen by viewModel.isProfileOpen.collectAsStateWithLifecycle()
    val isHomeEditMode by viewModel.isHomeEditMode.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    MoviesAndSeriesTheme(accentColorHex = userProfile.accentColorHex) {
        BackHandler(enabled = currentScreen !is Screen.Home && detailState.meta == null && !isProfileOpen && !isHomeEditMode) {
            viewModel.navigateTo(Screen.Home)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)) togetherWith
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy))
                },
                label = "screen_navigation"
            ) { screen ->
                when (screen) {
                    is Screen.Home -> {
                        HomeScreen(
                            homeState = homeState,
                            userProfile = userProfile,
                            onOpenProfile = { viewModel.openProfile() },
                            onOpenSearch = { viewModel.navigateTo(Screen.Search) },
                            onRefresh = { viewModel.loadCatalogData() },
                            onSelectHeroIndex = { viewModel.setHeroIndex(it) },
                            onSelectPlatformFilter = { viewModel.filterPlatform(it) },
                            onClickMovie = { viewModel.openMovieDetail(it) },
                            isHomeEditMode = isHomeEditMode,
                            onSaveHomeCustomizations = { viewModel.saveHomeCustomizations() },
                            onRestoreHomeOriginal = { viewModel.restoreHomeOriginal() },
                            onExitHomeEditMode = { viewModel.setHomeEditMode(false) },
                            onMoveCategoryMovie = { catId, from, to -> viewModel.moveCategoryMovie(catId, from, to) },
                            onDeleteCategoryMovie = { catId, movieId -> viewModel.deleteCategoryMovie(catId, movieId) },
                            onAddCategoryMovie = { catId, movie -> viewModel.addCategoryMovie(catId, movie) },
                            onEditCategoryMovie = { catId, movieId, movie -> viewModel.editCategoryMovie(catId, movieId, movie) },
                            onShuffleCategory = { catId -> viewModel.shuffleCategoryMovies(catId) },
                            onUpdateHeroMovies = { heroes -> viewModel.updateHeroMovies(heroes) },
                            onUpdateHeroCustomImage = { hero, newUrl -> viewModel.updateHeroMovieImage(hero, newUrl) }
                        )
                    }

                    is Screen.Search -> {
                        SearchScreen(
                            searchQuery = searchQuery,
                            searchResults = searchResults,
                            isSearching = isSearching,
                            selectedType = searchType,
                            onTypeSelected = { viewModel.setSearchType(it) },
                            onQueryChanged = { viewModel.onSearchQueryChanged(it) },
                            onClickMovie = { viewModel.openMovieDetail(it) }
                        )
                    }

                    is Screen.Library -> {
                        LibraryScreen(
                            savedItems = savedMedia,
                            sortOrder = userProfile.librarySortOrder,
                            onSortOrderSelected = { viewModel.setLibrarySortOrder(it) },
                            selectedTileSize = userProfile.libraryTileSize,
                            onTileSizeSelected = { viewModel.setLibraryTileSize(it) },
                            onMoveItem = { from, to -> viewModel.moveSavedItem(from, to) },
                            onCommitReorder = { viewModel.reorderSavedItems(it) },
                            onSaveLayoutAndOrder = { viewModel.saveLibraryLayoutAndOrder() },
                            onDeleteItem = { viewModel.deleteSavedItem(it) },
                            onClickMovie = { viewModel.openMovieDetail(it) },
                            onAddCustomMediaWithMap = { title, type, poster, backdrop, year, rating, desc, genres, seasons, perSeasonMap ->
                                viewModel.saveCustomMedia(title, type, poster, backdrop, year, rating, desc, genres, seasons, perSeasonMap)
                            }
                        )
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = detailState.meta == null && !(homeState.isLoading && homeState.categories.isEmpty()),
                enter = fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PillNavigationBar(
                    currentScreen = currentScreen,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = detailState.meta != null,
                enter = androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMedium)),
                exit = androidx.compose.animation.slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = Spring.DampingRatioNoBouncy)
                ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
            ) {
                MovieDetailSheet(
                    detailState = detailState,
                    onClose = { viewModel.closeMovieDetail() },
                    onToggleSave = { viewModel.toggleSaveMovie(it) },
                    onToggleMute = { viewModel.toggleTrailerMute() },
                    onSelectQuality = { viewModel.setQualityFilter(it) },
                    onSelectProvider = { viewModel.setProviderFilter(it) },
                    onSelectSeason = { viewModel.selectSeason(it) },
                    onSelectEpisode = { viewModel.selectEpisode(it) },
                    onAddCustomStream = { viewModel.addCustomStream(it) },
                    onRemoveStream = { s, targetId -> viewModel.removeStream(s, targetId) },
                    onSaveEpisodeStreams = { epId, streams -> viewModel.saveEpisodeStreams(epId, streams) },
                    onSaveCustomEdits = { streams, meta ->
                        viewModel.saveCustomEdits(streams, meta)
                    },
                    onSaveCustomEditsWithSlideshow = { streams, meta, slideshow ->
                        viewModel.saveCustomEdits(streams, meta, slideshow)
                    },
                    onRestoreOriginal = {
                        detailState.meta?.let { m ->
                            viewModel.restoreMovieOriginal(m.id, m.displayImdbId)
                        }
                    },
                    showDebugLine = userProfile.showTrailerDebug
                )
            }

            if (isProfileOpen) {
                ProfileDialog(
                    userProfile = userProfile,
                    preferences = viewModel.preferences,
                    onDismiss = { viewModel.closeProfile() },
                    onEnterHomeEditMode = { viewModel.setHomeEditMode(true) }
                )
            }

            // Root-Level Master Smooth Pulsing Splash Screen: 100% eliminates startup black screens
            var showSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2200)
                showSplash = false
            }

            AnimatedVisibility(
                visible = showSplash,
                enter = fadeIn(animationSpec = tween(100)),
                exit = fadeOut(animationSpec = tween(350)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0C0C0F)),
                    contentAlignment = Alignment.Center
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "root_splash_pulse")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.88f,
                        targetValue = 1.08f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "splash_scale"
                    )
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.82f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "splash_alpha"
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "Movies and Series Splash Logo",
                        modifier = Modifier
                            .size(150.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                                alpha = pulseAlpha
                            }
                    )
                }
            }
        }
    }
}
