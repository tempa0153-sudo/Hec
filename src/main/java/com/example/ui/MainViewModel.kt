package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.SavedMediaEntity
import com.example.data.model.EpisodeItem
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.data.pref.UserPreferences
import com.example.data.pref.UserProfile
import com.example.data.remote.StremioClient
import com.example.data.repository.CategorySection
import com.example.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch

sealed class Screen {
    object Home : Screen()
    object Search : Screen()
    object Library : Screen()
}

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val heroMovies: List<StremioCatalogMeta> = emptyList(),
    val currentHeroIndex: Int = 0,
    val categories: List<CategorySection> = emptyList(),
    val selectedPlatform: String = "All"
)

data class DetailUiState(
    val meta: StremioCatalogMeta? = null,
    val isLoadingDetails: Boolean = false,
    val streams: List<StremioStream> = emptyList(),
    val isLoadingStreams: Boolean = false,
    val isSaved: Boolean = false,
    val isTrailerMuted: Boolean = false,
    val selectedQualityFilter: String = "All",
    val selectedProviderFilter: String = "All",
    val selectedSeason: Int = 1,
    val selectedEpisode: EpisodeItem? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = StremioClient()
    private val db = AppDatabase.getInstance(application)
    val preferences = UserPreferences(application)
    val repository = MediaRepository(client, db.savedMediaDao(), preferences)

    private val _currentScreen = MutableStateFlow<Screen>(Screen.Home)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<StremioCatalogMeta>>(emptyList())
    val searchResults: StateFlow<List<StremioCatalogMeta>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchType = MutableStateFlow("movie")
    val searchType: StateFlow<String> = _searchType.asStateFlow()

    private val _savedMedia = MutableStateFlow<List<SavedMediaEntity>>(emptyList())
    val savedMedia: StateFlow<List<SavedMediaEntity>> = _savedMedia.asStateFlow()

    private val _detailState = MutableStateFlow(DetailUiState())
    val detailState: StateFlow<DetailUiState> = _detailState.asStateFlow()

    private val _isProfileOpen = MutableStateFlow(false)
    val isProfileOpen: StateFlow<Boolean> = _isProfileOpen.asStateFlow()

    private val _isHomeEditMode = MutableStateFlow(false)
    val isHomeEditMode: StateFlow<Boolean> = _isHomeEditMode.asStateFlow()

    val userProfile: StateFlow<UserProfile> = preferences.userProfile

    private var heroShuffleJob: Job? = null
    private var searchJob: Job? = null
    private var savedObserverJob: Job? = null
    private var streamJob: Job? = null

    init {
        val initialCategories = repository.getCustomHomeCategories().orEmpty()
        if (initialCategories.isNotEmpty()) {
            val initialHeroes = repository.build20HeroList(initialCategories)
            _homeState.value = _homeState.value.copy(
                isLoading = false,
                heroMovies = initialHeroes,
                categories = initialCategories
            )
        }
        loadCatalogData()
        observeSavedMedia()
        startHeroAutoShuffle()
    }

    fun navigateTo(screen: Screen) {
        _currentScreen.value = screen
        if (screen !is Screen.Home) {
            pauseHeroAutoShuffle()
        } else {
            resumeHeroAutoShuffle()
        }
    }

    fun openProfile() {
        _isProfileOpen.value = true
        pauseHeroAutoShuffle()
    }

    fun closeProfile() {
        _isProfileOpen.value = false
        if (_currentScreen.value is Screen.Home && _detailState.value.meta == null) {
            resumeHeroAutoShuffle()
        }
    }

    fun loadCatalogData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (_homeState.value.categories.isEmpty() || forceRefresh) {
                _homeState.value = _homeState.value.copy(
                    isLoading = _homeState.value.categories.isEmpty(),
                    error = null
                )
            }
            try {
                repository.getCategoriesFlow(forceRefresh = forceRefresh).collect { categories ->
                    val heroCandidates = repository.build20HeroList(categories)

                    _homeState.value = _homeState.value.copy(
                        isLoading = false,
                        heroMovies = heroCandidates,
                        categories = categories
                    )
                    // Defer background details prefetching to prevent startup contention
                    viewModelScope.launch(Dispatchers.IO) {
                        delay(3500)
                        repository.prefetchMovieDetails(heroCandidates)
                    }
                }

            } catch (e: Exception) {
                if (_homeState.value.categories.isEmpty()) {
                    _homeState.value = _homeState.value.copy(
                        isLoading = false,
                        error = "Failed to connect to catalog. Please retry."
                    )
                }
            }
        }
    }

    fun pauseHeroAutoShuffle() {
        heroShuffleJob?.cancel()
        heroShuffleJob = null
    }

    fun resumeHeroAutoShuffle() {
        if (heroShuffleJob == null || !heroShuffleJob!!.isActive) {
            startHeroAutoShuffle()
        }
    }

    private fun startHeroAutoShuffle() {
        heroShuffleJob?.cancel()
        heroShuffleJob = viewModelScope.launch {
            while (true) {
                delay(6000)
                if (_currentScreen.value is Screen.Home && _detailState.value.meta == null && !_isProfileOpen.value) {
                    val movies = _homeState.value.heroMovies
                    if (movies.isNotEmpty()) {
                        val nextIdx = (_homeState.value.currentHeroIndex + 1) % movies.size
                        _homeState.value = _homeState.value.copy(currentHeroIndex = nextIdx)
                    }
                }
            }
        }
    }

    fun setHeroIndex(index: Int) {
        val movies = _homeState.value.heroMovies
        if (index in movies.indices) {
            _homeState.value = _homeState.value.copy(currentHeroIndex = index)
        }
    }

    fun filterPlatform(platform: String) {
        _homeState.value = _homeState.value.copy(selectedPlatform = platform)
    }

    fun setSearchType(type: String) {
        _searchType.value = type
        if (_searchQuery.value.isNotBlank()) {
            executeSearch(_searchQuery.value, type)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        executeSearch(query, _searchType.value)
    }

    private fun executeSearch(query: String, type: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            val results = repository.search(query, type)
            _searchResults.value = results.getOrDefault(emptyList())
            _isSearching.value = false
        }
    }

    private fun observeSavedMedia() {
        savedObserverJob?.cancel()
        savedObserverJob = viewModelScope.launch {
            userProfile.collectLatest { profile ->
                repository.observeSavedMedia(profile.librarySortOrder).collectLatest { list ->
                    _savedMedia.value = list
                }
            }
        }
    }

    fun setLibrarySortOrder(sortOrder: String) {
        preferences.updateLibrarySortOrder(sortOrder)
    }

    fun setLibraryTileSize(size: String) {
        preferences.updateLibraryTileSize(size)
    }

    fun reorderSavedItems(newList: List<SavedMediaEntity>) {
        _savedMedia.value = newList
        viewModelScope.launch {
            repository.updateCustomOrder(newList)
            val currentSort = userProfile.value.librarySortOrder
            val customSort = when {
                currentSort.startsWith("date_asc") -> "date_asc_custom"
                currentSort.startsWith("title_asc") -> "title_asc_custom"
                currentSort.startsWith("rating_desc") -> "rating_desc_custom"
                currentSort.startsWith("rating_asc") -> "rating_asc_custom"
                else -> "date_desc_custom"
            }
            preferences.updateLibrarySortOrder(customSort)
        }
    }

    fun saveLibraryLayoutAndOrder() {
        val current = _savedMedia.value
        viewModelScope.launch {
            val currentSort = userProfile.value.librarySortOrder
            val customSort = when {
                currentSort.startsWith("date_asc") -> "date_asc_custom"
                currentSort.startsWith("title_asc") -> "title_asc_custom"
                currentSort.startsWith("rating_desc") -> "rating_desc_custom"
                currentSort.startsWith("rating_asc") -> "rating_asc_custom"
                else -> "date_desc_custom"
            }
            preferences.updateLibrarySortOrder(customSort)
            repository.updateCustomOrder(current)
        }
    }

    fun moveSavedItem(fromIndex: Int, toIndex: Int) {
        val current = _savedMedia.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            _savedMedia.value = current
            val currentSort = userProfile.value.librarySortOrder
            val customSort = when {
                currentSort.startsWith("date_asc") -> "date_asc_custom"
                currentSort.startsWith("title_asc") -> "title_asc_custom"
                currentSort.startsWith("rating_desc") -> "rating_desc_custom"
                currentSort.startsWith("rating_asc") -> "rating_asc_custom"
                else -> "date_desc_custom"
            }
            if (currentSort != customSort) {
                preferences.updateLibrarySortOrder(customSort)
            }
            viewModelScope.launch {
                repository.updateCustomOrder(current)
            }
        }
    }

    fun deleteSavedItem(id: String) {
        viewModelScope.launch {
            repository.deleteSaved(id)
        }
    }

    fun openMovieDetail(meta: StremioCatalogMeta) {
        pauseHeroAutoShuffle()
        val customMeta = repository.getCustomMeta(meta.displayImdbId) 
            ?: repository.getCustomMeta(meta.id) 
            ?: repository.getCustomMetaByName(meta.name)
        val cached = repository.getCachedMovieDetails(meta.displayImdbId)
        val initialMeta = customMeta ?: cached ?: meta
        val isSeries = initialMeta.type.equals("series", ignoreCase = true)
        val initialSeason = if (isSeries) initialMeta.seasons.firstOrNull() ?: 1 else 1
        val initialEpisode = if (isSeries) {
            initialMeta.getEpisodesForSeason(initialSeason).firstOrNull()
                ?: EpisodeItem(
                    id = "${initialMeta.displayImdbId}:$initialSeason:1",
                    name = "Episode 1",
                    season = initialSeason,
                    number = 1,
                    overview = initialMeta.description,
                    thumbnail = initialMeta.poster ?: initialMeta.background,
                    released = initialMeta.year
                )
        } else null

        _detailState.value = DetailUiState(
            meta = initialMeta,
            isLoadingDetails = cached == null && customMeta == null,
            isLoadingStreams = true,
            isTrailerMuted = userProfile.value.trailerMuted,
            selectedSeason = initialSeason,
            selectedEpisode = initialEpisode
        )

        viewModelScope.launch {
            repository.isSaved(meta.id, meta.displayImdbId, meta.name).collectLatest { saved ->
                _detailState.value = _detailState.value.copy(isSaved = saved)
            }
        }

        // 1. START FETCHING STREAMS IMMEDIATELY ON THE VERY FIRST CLICK!
        if (isSeries) {
            if (initialEpisode != null) {
                fetchStreamsForEpisode(initialEpisode)
            }
        } else {
            fetchStreamsForMovie(initialMeta.displayImdbId)
        }

        // 2. Fetch Cinemeta series/movie enriched details concurrently in background
        viewModelScope.launch(Dispatchers.IO) {
            val enriched = repository.getMovieDetails(meta.type, meta.displayImdbId)
            val updatedMeta = enriched.getOrNull()
            if (updatedMeta != null) {
                val finalMeta = customMeta?.let { custom ->
                    updatedMeta.copy(
                        poster = custom.poster ?: updatedMeta.poster,
                        background = custom.background ?: updatedMeta.background,
                        videos = custom.videos ?: updatedMeta.videos
                    )
                } ?: updatedMeta

                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (_detailState.value.meta?.displayImdbId == meta.displayImdbId) {
                        val currentEp = _detailState.value.selectedEpisode
                        val updatedEpisodes = finalMeta.getEpisodesForSeason(_detailState.value.selectedSeason)
                        val matchingEp = updatedEpisodes.firstOrNull { it.number == currentEp?.number }
                            ?: updatedEpisodes.firstOrNull() ?: currentEp

                        _detailState.value = _detailState.value.copy(
                            meta = finalMeta,
                            isLoadingDetails = false,
                            selectedEpisode = matchingEp
                        )
                    }
                }
            } else {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    _detailState.value = _detailState.value.copy(isLoadingDetails = false)
                }
            }
        }

        // 3. Resolve Trailer asynchronously in background - NEVER blocks stream fetching or UI
        viewModelScope.launch(Dispatchers.IO) {
            if (initialMeta.allTrailerIds.isEmpty()) {
                val resolvedId = repository.resolveTrailerId(initialMeta)
                if (!resolvedId.isNullOrBlank()) {
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        val current = _detailState.value.meta
                        if (current != null && current.displayImdbId == initialMeta.displayImdbId && current.allTrailerIds.isEmpty()) {
                            val withTrailer = current.copy(
                                trailers = listOf(com.example.data.model.TrailerItem(source = resolvedId, type = "Trailer"))
                            )
                            _detailState.value = _detailState.value.copy(meta = withTrailer)
                        }
                    }
                }
            }
        }
    }

    private fun fetchStreamsForMovie(id: String) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _detailState.value = _detailState.value.copy(
                isLoadingStreams = true,
                streams = emptyList()
            )
            repository.getStreamsProgressive("movie", id)
                .onCompletion {
                    _detailState.value = _detailState.value.copy(isLoadingStreams = false)
                }
                .collect { progressiveStreams ->
                    _detailState.value = _detailState.value.copy(
                        streams = progressiveStreams
                    )
                }
        }
    }

    private fun fetchStreamsForEpisode(episode: EpisodeItem) {
        streamJob?.cancel()
        streamJob = viewModelScope.launch {
            _detailState.value = _detailState.value.copy(
                isLoadingStreams = true,
                streams = emptyList()
            )
            repository.getStreamsProgressive("series", episode.id)
                .onCompletion {
                    _detailState.value = _detailState.value.copy(isLoadingStreams = false)
                }
                .collect { progressiveStreams ->
                    _detailState.value = _detailState.value.copy(
                        streams = progressiveStreams
                    )
                }
        }
    }

    fun setProviderFilter(provider: String) {
        _detailState.value = _detailState.value.copy(selectedProviderFilter = provider)
    }

    fun selectSeason(season: Int) {
        val meta = _detailState.value.meta ?: return
        val episodes = meta.getEpisodesForSeason(season)
        val firstEp = episodes.firstOrNull()

        _detailState.value = _detailState.value.copy(
            selectedSeason = season,
            selectedEpisode = firstEp
        )

        if (firstEp != null) {
            fetchStreamsForEpisode(firstEp)
        }
    }

    fun selectEpisode(episode: EpisodeItem) {
        _detailState.value = _detailState.value.copy(
            selectedEpisode = episode
        )
        fetchStreamsForEpisode(episode)
    }

    fun closeMovieDetail() {
        streamJob?.cancel()
        _detailState.value = DetailUiState(meta = null)
        if (_currentScreen.value is Screen.Home) {
            resumeHeroAutoShuffle()
        }
    }

    fun toggleSaveMovie(meta: StremioCatalogMeta) {
        val current = _detailState.value.isSaved
        _detailState.value = _detailState.value.copy(isSaved = !current)
        viewModelScope.launch {
            val isNowSaved = repository.toggleSave(meta)
            _detailState.value = _detailState.value.copy(isSaved = isNowSaved)
        }
    }

    fun toggleTrailerMute() {
        val newMute = !_detailState.value.isTrailerMuted
        _detailState.value = _detailState.value.copy(isTrailerMuted = newMute)
        preferences.updateTrailerMuted(newMute)
    }

    fun addCustomStream(stream: StremioStream) {
        val current = _detailState.value.streams.toMutableList()
        current.add(0, stream)
        _detailState.value = _detailState.value.copy(streams = current)
    }

    fun removeStream(stream: StremioStream, targetId: String? = null) {
        val id = targetId ?: _detailState.value.selectedEpisode?.id ?: _detailState.value.meta?.displayImdbId ?: return
        repository.hideStream(id, stream)
        val current = _detailState.value.streams.toMutableList()
        current.remove(stream)
        _detailState.value = _detailState.value.copy(streams = current)
        repository.saveCustomStreamEdits(id, current)
    }

    fun saveEpisodeStreams(episodeId: String, streams: List<StremioStream>) {
        viewModelScope.launch {
            repository.saveCustomStreamEdits(episodeId, streams)
            _detailState.value = _detailState.value.copy(streams = streams)
        }
    }

    fun saveCustomEdits(
        streams: List<StremioStream>,
        meta: StremioCatalogMeta,
        slideshowImages: List<String>? = null,
        targetEpisodeId: String? = null
    ) {
        viewModelScope.launch {
            val effectiveEpisodeId = targetEpisodeId ?: _detailState.value.selectedEpisode?.id
            if (effectiveEpisodeId != null && effectiveEpisodeId.isNotBlank()) {
                repository.saveCustomStreamEdits(effectiveEpisodeId, streams)
            }
            repository.saveCustomStreamEdits(meta.id, streams)
            repository.saveCustomStreamEdits(meta.displayImdbId, streams)
            repository.saveCustomMetaEdits(meta)
            if (slideshowImages != null) {
                repository.saveCustomSlideshow(meta.id, slideshowImages)
                repository.saveCustomSlideshow(meta.displayImdbId, slideshowImages)
                val norm = meta.name.trim().lowercase()
                if (norm.isNotBlank()) {
                    repository.saveCustomSlideshow("name_$norm", slideshowImages)
                }
            }
            _detailState.value = _detailState.value.copy(
                meta = meta,
                streams = streams
            )
            // Immediately reflect in homeState heroMovies and categories
            fun matchesTarget(candId: String, candDisplayId: String, candName: String): Boolean {
                if (candId.isNotBlank() && (candId == meta.id || candId == meta.displayImdbId)) return true
                if (candDisplayId.isNotBlank() && (candDisplayId == meta.id || candDisplayId == meta.displayImdbId)) return true
                val tName = meta.name.trim().lowercase()
                val cName = candName.trim().lowercase()
                return tName.isNotBlank() && (cName == tName || cName.contains(tName) || tName.contains(cName))
            }

            val updatedHeroes = _homeState.value.heroMovies.map {
                if (matchesTarget(it.id, it.displayImdbId, it.name)) {
                    it.copy(
                        name = meta.name.takeIf { n -> n.isNotBlank() } ?: it.name,
                        poster = meta.poster ?: it.poster,
                        background = meta.background ?: it.background,
                        description = meta.description ?: it.description
                    )
                } else it
            }
            val updatedCategories = _homeState.value.categories.map { cat ->
                cat.copy(items = cat.items.map {
                    if (matchesTarget(it.id, it.displayImdbId, it.name)) {
                        it.copy(
                            name = meta.name.takeIf { n -> n.isNotBlank() } ?: it.name,
                            poster = meta.poster ?: it.poster,
                            background = meta.background ?: it.background,
                            description = meta.description ?: it.description
                        )
                    } else it
                })
            }
            _homeState.value = _homeState.value.copy(
                heroMovies = updatedHeroes,
                categories = updatedCategories
            )
            repository.saveCustomHomeHeroes(updatedHeroes)
            repository.saveCustomHomeCategories(updatedCategories)

            val posterImg = meta.poster ?: meta.background ?: ""
            if (posterImg.isNotBlank()) {
                repository.updateSavedMediaImage(meta.id, meta.displayImdbId, meta.name, posterImg)
            }
        }
    }

    fun setHomeEditMode(enabled: Boolean) {
        _isHomeEditMode.value = enabled
    }

    fun restoreMovieOriginal(id: String, displayImdbId: String) {
        viewModelScope.launch {
            repository.restoreMediaOriginal(id, displayImdbId)
            val meta = _detailState.value.meta ?: return@launch
            _detailState.value = _detailState.value.copy(
                streams = emptyList(),
                isLoadingStreams = true
            )
            if (meta.type.equals("series", ignoreCase = true)) {
                val ep = _detailState.value.selectedEpisode ?: meta.videos?.firstOrNull()
                if (ep != null) {
                    fetchStreamsForEpisode(ep)
                }
            } else {
                fetchStreamsForMovie(displayImdbId)
            }
        }
    }

    fun restoreHomeOriginal() {
        viewModelScope.launch {
            repository.restoreHomeOriginal()
            loadCatalogData(forceRefresh = true)
        }
    }

    fun moveCategoryMovie(categoryId: String, fromIndex: Int, toIndex: Int) {
        val updatedCategories = _homeState.value.categories.map { cat ->
            if (cat.id == categoryId) {
                val list = cat.items.toMutableList()
                if (fromIndex in list.indices && toIndex in list.indices) {
                    val item = list.removeAt(fromIndex)
                    list.add(toIndex, item)
                    cat.copy(items = list)
                } else cat
            } else cat
        }
        _homeState.value = _homeState.value.copy(categories = updatedCategories)
    }

    fun deleteCategoryMovie(categoryId: String, movieId: String) {
        val updatedCategories = _homeState.value.categories.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(items = cat.items.filterNot { it.id == movieId })
            } else cat
        }
        _homeState.value = _homeState.value.copy(categories = updatedCategories)
    }

    fun addCategoryMovie(categoryId: String, movie: StremioCatalogMeta) {
        val updatedCategories = _homeState.value.categories.map { cat ->
            if (cat.id == categoryId) {
                val list = cat.items.toMutableList()
                list.add(0, movie)
                cat.copy(items = list)
            } else cat
        }
        _homeState.value = _homeState.value.copy(categories = updatedCategories)
    }

    fun editCategoryMovie(categoryId: String, movieId: String, updatedMovie: StremioCatalogMeta) {
        val updatedCategories = _homeState.value.categories.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(items = cat.items.map { if (it.id == movieId) updatedMovie else it })
            } else cat
        }
        _homeState.value = _homeState.value.copy(categories = updatedCategories)
    }

    fun shuffleCategoryMovies(categoryId: String) {
        val updatedCategories = _homeState.value.categories.map { cat ->
            if (cat.id == categoryId) {
                cat.copy(items = cat.items.shuffled())
            } else cat
        }
        _homeState.value = _homeState.value.copy(categories = updatedCategories)
    }

    fun updateHeroMovies(newHeroes: List<StremioCatalogMeta>) {
        _homeState.value = _homeState.value.copy(heroMovies = newHeroes)
    }

    fun updateHeroMovieImage(hero: StremioCatalogMeta, newImageUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = repository.saveHeroCustomImage(hero, newImageUrl)
            val currentHeroes = _homeState.value.heroMovies.map {
                if (it.id == hero.id || it.displayImdbId == hero.displayImdbId || it.name.equals(hero.name, ignoreCase = true)) {
                    updated
                } else it
            }
            repository.saveCustomHomeHeroes(currentHeroes)

            // Update all categories in current state and persist to storage so images update everywhere
            val updatedCategories = _homeState.value.categories.map { cat ->
                cat.copy(
                    items = cat.items.map { item ->
                        if (item.id == hero.id || item.displayImdbId == hero.displayImdbId || item.name.equals(hero.name, ignoreCase = true)) {
                            item.copy(poster = newImageUrl, background = newImageUrl)
                        } else item
                    }
                )
            }
            repository.saveCustomHomeCategories(updatedCategories)

            // Update in SavedMedia (Room Database) if saved in library
            repository.updateSavedMediaImage(hero.id, hero.displayImdbId, hero.name, newImageUrl)

            kotlinx.coroutines.withContext(Dispatchers.Main) {
                _homeState.value = _homeState.value.copy(
                    heroMovies = currentHeroes,
                    categories = updatedCategories
                )
            }
        }
    }

    fun saveHomeCustomizations() {
        val cats = _homeState.value.categories
        val heroes = _homeState.value.heroMovies
        repository.saveCustomHomeCategories(cats)
        repository.saveCustomHomeHeroes(heroes)
        _isHomeEditMode.value = false
    }

    fun saveCustomMedia(
        title: String,
        type: String,
        poster: String?,
        backdrop: String?,
        year: String?,
        rating: String?,
        description: String?,
        genres: String?,
        seasonsCount: Int = 1,
        perSeasonMap: Map<Int, Int> = emptyMap()
    ) {
        viewModelScope.launch {
            val customId = "custom_${System.currentTimeMillis()}"
            val effectivePoster = poster?.ifBlank { backdrop?.ifBlank { null } }
                ?: "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1200"
            val effectiveBackdrop = backdrop?.ifBlank { effectivePoster } ?: effectivePoster

            val generatedVideos = if (type.equals("series", ignoreCase = true)) {
                val epList = mutableListOf<EpisodeItem>()
                val actualSeasons = seasonsCount.coerceIn(1, 100)
                for (s in 1..actualSeasons) {
                    val epsInSeason = perSeasonMap[s] ?: 12
                    val actualEps = epsInSeason.coerceIn(1, 500)
                    for (ep in 1..actualEps) {
                        epList.add(
                            EpisodeItem(
                                id = "${customId}:$s:$ep",
                                name = "Episode $ep: Chapter $ep",
                                season = s,
                                number = ep,
                                overview = "An exciting episode of $title in Season $s featuring intense plot developments.",
                                thumbnail = effectivePoster,
                                released = year?.ifBlank { "2024" }
                            )
                        )
                    }
                }
                epList
            } else null

            val meta = StremioCatalogMeta(
                id = customId,
                name = title,
                type = type,
                poster = effectivePoster,
                background = effectiveBackdrop,
                year = year?.ifBlank { "2024" },
                imdbRating = rating?.ifBlank { "8.0" },
                description = description?.ifBlank { "A custom added $type with rich storyline and exclusive episodes." },
                genre = genres?.split(",")?.map { it.trim() },
                videos = generatedVideos
            )
            repository.saveMedia(meta, 0)
        }
    }

    fun setQualityFilter(quality: String) {
        _detailState.value = _detailState.value.copy(selectedQualityFilter = quality)
    }
}
