package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.EpisodeItem
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.ui.DetailUiState
import com.example.ui.components.MediaSlideshowView
import com.example.ui.theme.CinemaRed
import com.example.ui.theme.ImdbGold

fun sanitizeStreamText(raw: String?): String? {
    return raw
}

fun formatProviderDisplay(raw: String?): String {
    if (raw.isNullOrBlank()) return "HD Hub"
    val lower = raw.lowercase()
    return when {
        lower.contains("pengu") || lower.contains("pingu") || lower.contains("provider 2") -> "PenguPlay"
        lower.contains("yas") || lower.contains("provider 3") -> "YAS Stream"
        else -> "HD Hub"
    }
}

data class StreamBadgePair(
    val providerName: String,
    val addonName: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailSheet(
    detailState: DetailUiState,
    onClose: () -> Unit,
    onToggleSave: (StremioCatalogMeta) -> Unit,
    onToggleMute: () -> Unit,
    onSelectQuality: (String) -> Unit,
    onSelectProvider: (String) -> Unit = {},
    onSelectSeason: (Int) -> Unit = {},
    onSelectEpisode: (EpisodeItem) -> Unit = {},
    onAddCustomStream: (StremioStream) -> Unit = {},
    onRemoveStream: (StremioStream, String?) -> Unit = { _, _ -> },
    onSaveEpisodeStreams: (String, List<StremioStream>) -> Unit = { _, _ -> },
    onSaveCustomEdits: (List<StremioStream>, StremioCatalogMeta) -> Unit = { _, _ -> },
    onSaveCustomEditsWithSlideshow: (List<StremioStream>, StremioCatalogMeta, List<String>?) -> Unit = { _, _, _ -> },
    onRestoreOriginal: () -> Unit = {},
    showDebugLine: Boolean = false,
    modifier: Modifier = Modifier
) {
    val meta = detailState.meta ?: return
    val context = LocalContext.current
    var isOverviewExpanded by remember { mutableStateOf(false) }
    var isEditingProviders by remember { mutableStateOf(false) }
    var showAddProviderDialog by remember { mutableStateOf(false) }
    var showManageSlideshowDialog by remember { mutableStateOf(false) }
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    val isSeries = meta.type.equals("series", ignoreCase = true)

    val initialSlideshowList = remember(meta) {
        val userPrefs = com.example.data.pref.UserPreferences(context)
        val savedSlideshow = userPrefs.getCustomSlideshow(meta.id)
            ?: userPrefs.getCustomSlideshow(meta.displayImdbId)
            ?: userPrefs.getCustomSlideshow("name_${meta.name.trim().lowercase()}")
        val customImages = if (savedSlideshow != null) {
            runCatching {
                kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<String>>(savedSlideshow)
            }.getOrNull() ?: emptyList()
        } else emptyList()

        val list = mutableListOf<String>()
        list.addAll(customImages)
        meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        meta.logo?.takeIf { it.isNotBlank() }?.let { list.add(it) }
        meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() && !it.startsWith("poster_") }?.let { list.addAll(it) }

        val cleanImdb = meta.displayImdbId.trim()
        if (cleanImdb.startsWith("tt")) {
            list.add("https://images.metahub.space/background/medium/$cleanImdb/img")
            list.add("https://images.metahub.space/logo/medium/$cleanImdb/img")
            list.add("https://images.metahub.space/poster/medium/$cleanImdb/img")
        }
        if (list.size < 3) {
            list.addAll(listOf(
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1200",
                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1200",
                "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?q=80&w=1200"
            ))
        }
        list.distinct().toMutableList()
    }
    var localSlideshowList by remember(meta) { mutableStateOf(initialSlideshowList) }

    var activeEpisodeDetail by remember(meta) { mutableStateOf<EpisodeItem?>(null) }

    // Backup original lists for restore/undo functionality
    val originalSeasons = remember(meta) { meta.seasons.ifEmpty { listOf(1) } }
    val originalEpisodes = remember(meta, detailState.selectedSeason) {
        meta.getEpisodesForSeason(detailState.selectedSeason).ifEmpty {
            listOf(
                EpisodeItem("ep1", "Episode 1: The Beginning", 1, detailState.selectedSeason, "An incredible journey begins as new alliances form.", null, "2024"),
                EpisodeItem("ep2", "Episode 2: Dark Shadows", 2, detailState.selectedSeason, "Secrets unravel and challenges escalate.", null, "2024")
            )
        }
    }

    var localSeasonsList by remember(meta) { mutableStateOf(originalSeasons.toMutableList()) }
    var localEpisodesList by remember(meta, detailState.selectedSeason) { mutableStateOf(originalEpisodes.toMutableList()) }

    LaunchedEffect(meta.videos, detailState.selectedSeason) {
        val s = meta.seasons
        if (s.isNotEmpty()) {
            localSeasonsList = s.toMutableList()
            val eps = meta.getEpisodesForSeason(detailState.selectedSeason)
            if (eps.isNotEmpty()) {
                localEpisodesList = eps.toMutableList()
            }
        }
    }

    BackHandler {
        if (activeEpisodeDetail != null) {
            activeEpisodeDetail = null
        } else {
            onClose()
        }
    }

    val availableQualities = listOf("All Qualities", "4K 2160p", "1080p FHD", "720p HD")
    val filteredStreams = remember(detailState.streams, detailState.selectedQualityFilter, detailState.selectedProviderFilter) {
        val initialFiltered = detailState.streams
            .filterNot { it.isBelow720p }
            .filter { stream ->
                val matchesQuality = if (detailState.selectedQualityFilter == "All" || detailState.selectedQualityFilter == "All Qualities") true
                else stream.cleanQuality.contains(detailState.selectedQualityFilter.take(5), ignoreCase = true)

                val selectedP = detailState.selectedProviderFilter
                val matchesProvider = when {
                    selectedP == "All" || selectedP == "All Providers" || selectedP == "All Streamers" -> true
                    selectedP.contains("Provider 1", ignoreCase = true) || selectedP.contains("HD Hub", ignoreCase = true) -> stream.streamerNumber == 1
                    selectedP.contains("Provider 2", ignoreCase = true) || selectedP.contains("Pengu", ignoreCase = true) || selectedP.contains("Pingu", ignoreCase = true) -> stream.streamerNumber == 2
                    selectedP.contains("Provider 3", ignoreCase = true) || selectedP.contains("YAS", ignoreCase = true) -> stream.streamerNumber == 3
                    else -> true
                }
                matchesQuality && matchesProvider
            }

        initialFiltered.sortedWith(
            compareBy<StremioStream> { it.streamerNumber }
                .thenByDescending { stream ->
                    when {
                        stream.cleanQuality.contains("4k", ignoreCase = true) || stream.cleanQuality.contains("2160p") -> 3
                        stream.cleanQuality.contains("1080p") -> 2
                        stream.cleanQuality.contains("720p") -> 1
                        else -> 0
                    }
                }
        )
    }

    var hasUserEditedStreams by remember(detailState.selectedEpisode?.id ?: meta.id) { mutableStateOf(false) }
    var localStreamsList by remember(detailState.selectedEpisode?.id ?: meta.id) { mutableStateOf(filteredStreams.toMutableList()) }

    LaunchedEffect(filteredStreams) {
        if (!isEditingProviders && !hasUserEditedStreams) {
            localStreamsList = filteredStreams.toMutableList()
        } else if (!isEditingProviders) {
            // Keep user-added custom streams and merge newly arrived provider streams
            val customOnly = localStreamsList.filter { it.isCustomStream }
            val merged = (customOnly + filteredStreams).distinctBy { it.playableUrl }
            localStreamsList = merged.toMutableList()
        }
    }
    var verificationUrl by remember { mutableStateOf<String?>(null) }

    val p1Streams = remember(localStreamsList) { localStreamsList.filter { it.streamerNumber == 1 } }
    val p2Streams = remember(localStreamsList) { localStreamsList.filter { it.streamerNumber == 2 } }
    val p3Streams = remember(localStreamsList) { localStreamsList.filter { it.streamerNumber == 3 } }
    val additionalProviders = remember(localStreamsList) {
        (4..10).mapNotNull { num ->
            val list = localStreamsList.filter { it.streamerNumber == num }
            if (list.isNotEmpty()) Pair(num, list) else null
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // BODY CONTENT - LazyColumn spans entire height under floating header
            if (isSeries && activeEpisodeDetail != null) {
                // EPISODE DETAIL VIEW
                val ep = activeEpisodeDetail!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    // Slideshow (8 images) edge-to-edge behind status bar
                    item {
                        val epImages = remember(ep, meta) {
                            val list = mutableListOf<String>()
                            ep.thumbnail?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            val cleanImdb = meta.displayImdbId.trim()
                            if (cleanImdb.startsWith("tt")) {
                                list.add("https://images.metahub.space/background/medium/$cleanImdb/img")
                                list.add("https://images.metahub.space/poster/medium/$cleanImdb/img")
                                list.add("https://images.metahub.space/logo/medium/$cleanImdb/img")
                            }
                            meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() }?.let { list.addAll(it) }
                            val defaultHdBackdrops = listOf(
                                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1200",
                                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1200",
                                "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?q=80&w=1200",
                                "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?q=80&w=1200",
                                "https://images.unsplash.com/photo-1594909122845-11baa439b7bf?q=80&w=1200"
                            )
                            for (fallback in defaultHdBackdrops) {
                                if (list.size < 5) list.add(fallback)
                            }
                            list.distinct()
                        }
                        MediaSlideshowView(
                            images = (epImages + localSlideshowList).distinct(),
                            movieTitle = ep.name,
                            isEditingMode = isEditingProviders,
                            onAddOrEditClick = { showManageSlideshowDialog = true }
                        )
                    }

                    // Episode Title & Info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "S${ep.season} E${ep.number}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }

                                ep.released?.takeIf { it.isNotBlank() }?.let { date ->
                                    Text(
                                        text = date,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = ep.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            ep.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                                Text(
                                    text = overview,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    // Available Streams Header for this Episode
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Episode Stream Links",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (detailState.isLoadingStreams) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Loading Providers...",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Text(
                                            text = "${localStreamsList.size} Available",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (isEditingProviders) {
                                Surface(
                                    onClick = { showAddProviderDialog = true },
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Add Custom Provider for Episode",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Three Provider Categories for Episode (vanishes when empty unless in edit mode)
                    if (p1Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 1",
                            streams = p1Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = "${meta.name} S${ep.season}E${ep.number}",
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, ep.id)
                                onSaveEpisodeStreams(ep.id, localStreamsList)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    if (p2Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 2",
                            streams = p2Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = "${meta.name} S${ep.season}E${ep.number}",
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, ep.id)
                                onSaveEpisodeStreams(ep.id, localStreamsList)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    if (p3Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 3",
                            streams = p3Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = "${meta.name} S${ep.season}E${ep.number}",
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, ep.id)
                                onSaveEpisodeStreams(ep.id, localStreamsList)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    // Render Additional Providers (Up to 10 total) for Episode
                    additionalProviders.forEach { (num, streams) ->
                        val pName = streams.firstOrNull()?.addonSource?.takeIf { it.isNotBlank() } ?: "Provider $num"
                        renderProviderSection(
                            categoryTitle = pName,
                            streams = streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = "${meta.name} S${ep.season}E${ep.number}",
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveEpisodeStreams(ep.id, list)
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, ep.id)
                                onSaveEpisodeStreams(ep.id, localStreamsList)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }
                }
            } else {
                // MAIN DETAIL VIEW (Movie or Series Overview)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    // Slideshow Header
                    item {
                        val slideshowImages = remember(meta) {
                            val list = mutableListOf<String>()
                            meta.poster?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.background?.takeIf { it.isNotBlank() }?.let { list.add(it) }
                            meta.videos?.mapNotNull { it.thumbnail }?.filter { it.isNotBlank() }?.let { list.addAll(it) }
                            list.distinct()
                        }
                        MediaSlideshowView(
                            images = localSlideshowList,
                            movieTitle = meta.name,
                            isEditingMode = isEditingProviders,
                            onAddOrEditClick = { showManageSlideshowDialog = true }
                        )
                    }

                    // Info Section
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp)
                            ) {
                                if (!meta.year.isNullOrBlank()) {
                                    Text(
                                        text = meta.year,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                meta.runtime?.let {
                                    Text(
                                        text = "• $it",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = meta.type.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // IMDb Rating Bar
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF1E212D),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF32384B))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(2.dp),
                                            color = ImdbGold
                                        ) {
                                            Text(
                                                text = "IMDb",
                                                color = Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = ImdbGold,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = meta.imdbRating?.takeIf { it.isNotBlank() } ?: "8.2",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Genres
                            meta.genre?.let { genres ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 2.dp)
                                ) {
                                    genres.forEach { g ->
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = g,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Description / Overview
                            meta.description?.let { desc ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isOverviewExpanded = !isOverviewExpanded }
                                ) {
                                    Text(
                                        text = desc,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        lineHeight = 19.sp,
                                        maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (desc.length > 120) {
                                        Text(
                                            text = if (isOverviewExpanded) "Show Less" else "Read More",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // IF SERIES: Show Seasons & Episodes list (No providers at bottom!)
                    if (isSeries) {
                        val currentSeason = detailState.selectedSeason

                        // Seasons Header & Chips
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Seasons (${localSeasonsList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    if (isEditingProviders) {
                                        Surface(
                                            onClick = {
                                                val nextSeason = (localSeasonsList.maxOrNull() ?: 0) + 1
                                                localSeasonsList = (localSeasonsList + nextSeason).toMutableList()
                                                onSelectSeason(nextSeason)
                                                Toast.makeText(context, "Season $nextSeason added!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Season", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }

                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(localSeasonsList) { seasonNum ->
                                        val isSelected = seasonNum == currentSeason
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { onSelectSeason(seasonNum) },
                                                label = {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        Text(
                                                            text = "Season $seasonNum",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            fontSize = 12.sp
                                                        )
                                                        if (isEditingProviders && localSeasonsList.size > 1) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Close,
                                                                contentDescription = "Delete Season",
                                                                tint = CinemaRed,
                                                                modifier = Modifier
                                                                    .size(14.dp)
                                                                    .clickable {
                                                                        localSeasonsList = localSeasonsList.filter { it != seasonNum }.toMutableList()
                                                                        Toast.makeText(context, "Season $seasonNum deleted", Toast.LENGTH_SHORT).show()
                                                                    }
                                                            )
                                                        }
                                                    }
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Episodes Section Header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Episodes (${localEpisodesList.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    if (isEditingProviders) {
                                        Surface(
                                            onClick = {
                                                val nextEpNum = localEpisodesList.size + 1
                                                val newEp = EpisodeItem(
                                                    id = "custom_ep_${System.currentTimeMillis()}",
                                                    name = "Episode $nextEpNum: Custom Title",
                                                    number = nextEpNum,
                                                    season = currentSeason,
                                                    overview = "A newly added episode filled with exciting plot twists and high-definition scenes.",
                                                    thumbnail = meta.poster ?: meta.background,
                                                    released = "2024"
                                                )
                                                localEpisodesList = (localEpisodesList + newEp).toMutableList()
                                                Toast.makeText(context, "Episode $nextEpNum added to Season $currentSeason!", Toast.LENGTH_SHORT).show()
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add Episode", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Vertical Episodes List with Reorder Up/Down handle icons beside cross icon
                        if (localEpisodesList.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No episodes in Season $currentSeason. Click 'Add Episode' to create one.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            itemsIndexed(localEpisodesList, key = { idx, ep -> "${ep.id.ifBlank { "ep_${ep.season}_${ep.number}" }}_$idx" }) { idx, ep ->
                                VerticalEpisodeRowCard(
                                    episode = ep,
                                    fallbackBackdrop = meta.poster ?: meta.background,
                                    isEditingMode = isEditingProviders,
                                    canMoveUp = idx > 0,
                                    canMoveDown = idx < localEpisodesList.size - 1,
                                    onMoveUp = {
                                        val list = localEpisodesList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx - 1]
                                        list[idx - 1] = temp
                                        localEpisodesList = list
                                    },
                                    onMoveDown = {
                                        val list = localEpisodesList.toMutableList()
                                        val temp = list[idx]
                                        list[idx] = list[idx + 1]
                                        list[idx + 1] = temp
                                        localEpisodesList = list
                                    },
                                    onRemoveEpisode = {
                                        localEpisodesList = localEpisodesList.filter { it.id != ep.id }.toMutableList()
                                        Toast.makeText(context, "${ep.name} removed", Toast.LENGTH_SHORT).show()
                                    },
                                    onUpdateThumbnail = { newThumb ->
                                        localEpisodesList = localEpisodesList.map { item ->
                                            if (item.id == ep.id || (item.season == ep.season && item.number == ep.number)) {
                                                item.copy(thumbnail = newThumb)
                                            } else item
                                        }.toMutableList()
                                    },
                                    onClick = {
                                        onSelectEpisode(ep)
                                        activeEpisodeDetail = ep
                                    }
                                )
                            }
                        }
                    } else {
                        // MOVIE ONLY: Show Available Streams at the bottom
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Available Streams",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (detailState.isLoadingStreams) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = if (localStreamsList.isNotEmpty()) "${localStreamsList.size} found (more loading...)" else "Loading HDHub...",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        } else {
                                            Text(
                                                text = "${localStreamsList.size} Available Providers",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                if (isEditingProviders) {
                                    Surface(
                                        onClick = { showAddProviderDialog = true },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Add Custom Provider / Local File",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                    // Three Provider Categories for Movie (vanishes when empty unless in edit mode)
                    if (p1Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 1",
                            streams = p1Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = meta.name,
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, meta.displayImdbId)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    if (p2Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 2",
                            streams = p2Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = meta.name,
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, meta.displayImdbId)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    if (p3Streams.isNotEmpty() || isEditingProviders) {
                        renderProviderSection(
                            categoryTitle = "Provider 3",
                            streams = p3Streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = meta.name,
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, meta.displayImdbId)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }

                    // Render Additional Providers (Up to 10 total) for Movie
                    additionalProviders.forEach { (num, streams) ->
                        val pName = streams.firstOrNull()?.addonSource?.takeIf { it.isNotBlank() } ?: "Provider $num"
                        renderProviderSection(
                            categoryTitle = pName,
                            streams = streams,
                            allStreams = localStreamsList,
                            isLoading = detailState.isLoadingStreams,
                            titleText = meta.name,
                            context = context,
                            isEditingProviders = isEditingProviders,
                            onMoveUp = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx > 0) {
                                    val temp = list[idx]
                                    list[idx] = list[idx - 1]
                                    list[idx - 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onMoveDown = { stream ->
                                val list = localStreamsList.toMutableList()
                                val idx = list.indexOf(stream)
                                if (idx >= 0 && idx < list.size - 1) {
                                    val temp = list[idx]
                                    list[idx] = list[idx + 1]
                                    list[idx + 1] = temp
                                    localStreamsList = list
                                    onSaveCustomEdits(list, meta)
                                }
                            },
                            onRemoveStream = { s ->
                                localStreamsList = localStreamsList.filter { it != s }.toMutableList()
                                onRemoveStream(s, meta.displayImdbId)
                                onSaveCustomEdits(localStreamsList, meta)
                            },
                            onOpenVerification = { url -> verificationUrl = url }
                        )
                    }
                    }
                }
            }

            // Floating Top App Bar Overlay with Bleeding Translucent Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.85f),
                                Color.Black.copy(alpha = 0.40f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            if (activeEpisodeDetail != null) {
                                activeEpisodeDetail = null
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = if (activeEpisodeDetail != null) "${meta.name} • Ep ${activeEpisodeDetail?.number}" else meta.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    )

                    // TOP RIGHT BUTTONS: Tick -> History (Restore) -> Pencil -> Library (Far Right)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEditingProviders) {
                            // 1. Tick / Save Checkmark Icon
                            IconButton(
                                onClick = {
                                    val updatedMeta = meta.copy(
                                        poster = localSlideshowList.firstOrNull() ?: meta.poster,
                                        background = localSlideshowList.getOrNull(1) ?: meta.background,
                                        videos = localEpisodesList
                                    )
                                    activeEpisodeDetail?.let { ep ->
                                        onSaveEpisodeStreams(ep.id, localStreamsList)
                                    }
                                    onSaveCustomEdits(localStreamsList, updatedMeta)
                                    onSaveCustomEditsWithSlideshow(localStreamsList, updatedMeta, localSlideshowList)
                                    isEditingProviders = false
                                    Toast.makeText(context, "Layout, custom order & slideshow saved!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("detail_save_check_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Save Changes",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 2. Restore Clock / History Icon (Always visible beside pencil icon)
                        IconButton(
                            onClick = { showRestoreConfirmDialog = true },
                            modifier = Modifier.testTag("detail_restore_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Restore Original Format",
                                tint = Color.White
                            )
                        }

                        // 3. Pencil / Edit Button
                        IconButton(
                            onClick = { isEditingProviders = !isEditingProviders },
                            modifier = Modifier.testTag("detail_pencil_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Providers & Episodes",
                                tint = if (isEditingProviders) CinemaRed else Color.White
                            )
                        }

                        // 4. Library / Save to Library Button (Far right hand side)
                        IconButton(
                            onClick = {
                                val nextSaved = !detailState.isSaved
                                onToggleSave(meta)
                                Toast.makeText(
                                    context,
                                    if (nextSaved) "Saved to Library" else "Removed from Library",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("detail_save_button")
                        ) {
                            Icon(
                                imageVector = if (detailState.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (detailState.isSaved) "Saved to Library" else "Save to Library",
                                tint = if (detailState.isSaved) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // Restore Confirm Dialog (Screenshot 393)
        if (showRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showRestoreConfirmDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Restore Original Format", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text("Do you want to restore all deleted seasons, episodes, and stream providers back to their original layout and order?", fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRestoreOriginal()
                            localSeasonsList = originalSeasons.toMutableList()
                            localEpisodesList = originalEpisodes.toMutableList()
                            localStreamsList = filteredStreams.toMutableList()
                            localSlideshowList = initialSlideshowList.toMutableList()
                            showRestoreConfirmDialog = false
                            Toast.makeText(context, "Restored original seasons, episodes & providers!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showAddProviderDialog) {
            AddCustomProviderDialog(
                onDismiss = { showAddProviderDialog = false },
                onAdd = { newStream ->
                    onAddCustomStream(newStream)
                    val updated = (listOf(newStream) + localStreamsList).toMutableList()
                    localStreamsList = updated
                    onSaveCustomEdits(updated, meta)
                    showAddProviderDialog = false
                    Toast.makeText(context, "New stream provider added & saved!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (showManageSlideshowDialog) {
            ManageSlideshowImagesDialog(
                images = localSlideshowList,
                onDismiss = { showManageSlideshowDialog = false },
                onSaveImages = { newList ->
                    localSlideshowList = newList.toMutableList()
                    val updatedMeta = meta.copy(
                        poster = newList.firstOrNull() ?: meta.poster,
                        background = newList.getOrNull(1) ?: newList.firstOrNull() ?: meta.background
                    )
                    onSaveCustomEditsWithSlideshow(localStreamsList, updatedMeta, newList)
                    showManageSlideshowDialog = false
                    Toast.makeText(context, "Slideshow images updated & saved everywhere!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        if (verificationUrl != null) {
            VerificationBrowserDialog(
                initialUrl = verificationUrl!!,
                onDismiss = { verificationUrl = null },
                onVerified = { resolved ->
                    verificationUrl = null
                    executeStreamPlayback(context, resolved)
                }
            )
        }
    }
}

// Vertical episode row card with thumbnail image, momentary tap feedback, and up/down reorder buttons
@Composable
private fun VerticalEpisodeRowCard(
    episode: EpisodeItem,
    fallbackBackdrop: String?,
    isEditingMode: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemoveEpisode: () -> Unit = {},
    onUpdateThumbnail: (String) -> Unit = {},
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageModel = episode.thumbnail ?: fallbackBackdrop

    val episodeImagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val savedPath = com.example.ui.components.persistPickedImage(context, uri)
            onUpdateThumbnail(savedPath)
            Toast.makeText(context, "Episode ${episode.number} image updated!", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag("episode_card_${episode.number}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail Box (16:9)
            Box(
                modifier = Modifier
                    .width(115.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                if (!imageModel.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(com.example.ui.components.resolveCoilData(context, imageModel))
                            .crossfade(true)
                            .build(),
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
                    }
                }

                // Episode badge
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // In Edit Mode: Show + icon overlay to upload local image as episode poster (Screenshot 417)
                if (isEditingMode) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xDD000000),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                            .clickable {
                                episodeImagePickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            }
                            .testTag("upload_episode_image_${episode.number}")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Upload Episode Image",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Episode Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = episode.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                episode.released?.takeIf { it.isNotBlank() }?.let { date ->
                    Text(
                        text = date,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Reorder handles & Delete icon in Edit Mode
            if (isEditingMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Up / Down reorder buttons
                    Column {
                        if (canMoveUp) {
                            IconButton(onClick = onMoveUp, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move Up", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                        if (canMoveDown) {
                            IconButton(onClick = onMoveDown, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move Down", tint = Color.LightGray, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    IconButton(
                        onClick = onRemoveEpisode,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove Episode",
                            tint = CinemaRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.renderProviderSection(
    categoryTitle: String,
    streams: List<StremioStream>,
    allStreams: List<StremioStream>,
    isLoading: Boolean,
    titleText: String,
    context: Context,
    isEditingProviders: Boolean,
    onMoveUp: (StremioStream) -> Unit,
    onMoveDown: (StremioStream) -> Unit,
    onRemoveStream: (StremioStream) -> Unit,
    onOpenVerification: (String) -> Unit
) {
    item(key = "title_${categoryTitle}_$titleText") {
        Text(
            text = categoryTitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp)
        )
    }

    if (streams.isEmpty() && isLoading) {
        item(key = "loading_${categoryTitle}_$titleText") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    } else if (streams.isEmpty() && !isLoading) {
        item(key = "empty_${categoryTitle}_$titleText") {
            Text(
                text = "No add-on stream links found under $categoryTitle.",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }
    } else {
        itemsIndexed(
            items = streams,
            key = { idx, stream -> "${stream.streamerNumber}_${stream.name}_${stream.title}_${stream.playableUrl}_$idx" }
        ) { _, stream ->
            val overallIdx = allStreams.indexOf(stream)
            StreamSourceItemCard(
                stream = stream,
                title = titleText,
                context = context,
                isEditingProviders = isEditingProviders,
                canMoveUp = overallIdx > 0,
                canMoveDown = overallIdx < allStreams.size - 1,
                onMoveUp = { onMoveUp(stream) },
                onMoveDown = { onMoveDown(stream) },
                onRemoveStream = { onRemoveStream(stream) },
                onOpenVerification = onOpenVerification
            )
        }

        if (isLoading) {
            item(key = "fetching_${categoryTitle}_$titleText") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Loading add-ons...",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamSourceItemCard(
    stream: StremioStream,
    title: String,
    context: Context,
    isEditingProviders: Boolean = false,
    canMoveUp: Boolean = false,
    canMoveDown: Boolean = false,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onRemoveStream: (StremioStream) -> Unit = {},
    onOpenVerification: (String) -> Unit = {}
) {
    var isActivating by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .testTag("stream_card_streamer_${stream.streamerNumber}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Horizontal animated thin pill loading bar when activating/resolving
            AnimatedVisibility(
                visible = isActivating,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF1E212A))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pill_stream_loading")
                    val frac by infiniteTransition.animateFloat(
                        initialValue = -0.3f,
                        targetValue = 1.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(850, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "stream_frac"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .fillMaxHeight()
                            .align(Alignment.CenterStart)
                            .graphicsLayer { translationX = frac * 320f }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(CinemaRed.copy(alpha = 0.3f), CinemaRed, Color(0xFF38BDF8))
                                )
                            )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Resolution / Quality Badge Only (e.g. 1080p FHD, 4K 2160p)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0288D1)
                ) {
                    Text(
                        text = stream.cleanQuality.ifBlank { "1080p FHD" },
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Right Side Edit Controls: Up and Down arrow buttons beside the crossbar
                if (isEditingProviders) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (canMoveUp) Color(0xFF2D323F) else Color(0xFF1E2026),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (canMoveUp) Color(0xFF4C5366) else Color(0xFF2C2E36)),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = canMoveUp, onClick = onMoveUp)
                                .testTag("provider_move_up_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowUp,
                                    contentDescription = "Move Up",
                                    tint = if (canMoveUp) Color.White else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (canMoveDown) Color(0xFF2D323F) else Color(0xFF1E2026),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (canMoveDown) Color(0xFF4C5366) else Color(0xFF2C2E36)),
                            modifier = Modifier
                                .size(32.dp)
                                .clickable(enabled = canMoveDown, onClick = onMoveDown)
                                .testTag("provider_move_down_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "Move Down",
                                    tint = if (canMoveDown) Color.White else Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = CinemaRed,
                            modifier = Modifier
                                .size(32.dp)
                                .clickable { onRemoveStream(stream) }
                                .testTag("remove_provider_cross_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Remove Provider",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Stream Details (Single gray sentence only - no dark white title, no provider name clutter)
            val desc = stream.description?.trim().orEmpty()
                .replace("Direct Stream & Download Link", "", ignoreCase = true)
                .replace("Direct Stream Link", "", ignoreCase = true)
                .replace("Direct Stream", "", ignoreCase = true)
                .trim(' ', '•', '-', ':')
            val sentence = if (desc.isNotBlank()) {
                desc
            } else {
                val t = stream.title?.trim().orEmpty()
                val lines = t.lines().map { it.trim() }.filter { it.isNotBlank() }
                if (lines.size > 1) {
                    lines.drop(1).joinToString(" • ")
                } else {
                    val isGeneric = t.startsWith("MoviesDrives", ignoreCase = true) ||
                            t.startsWith("MovieBox", ignoreCase = true) ||
                            t.startsWith("VegaMovies", ignoreCase = true) ||
                            t.startsWith("HDHub4u", ignoreCase = true) ||
                            t.startsWith("HD Hub", ignoreCase = true) ||
                            t.startsWith("PenguPlay", ignoreCase = true) ||
                            t.startsWith("YAS", ignoreCase = true)
                    if (isGeneric && (t.contains("1080p") || t.contains("720p") || t.contains("4K"))) {
                        stream.cleanQuality
                    } else t
                }
            }
            if (sentence.isNotBlank()) {
                Text(
                    text = sentence,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val isVerificationLink = remember(stream.playableUrl) {
                val u = stream.playableUrl.orEmpty().lowercase()
                u.contains("hubcloud") || u.contains("verify") || u.contains("captcha") || u.contains("robot")
            }
            if (isVerificationLink) {
                Surface(
                    onClick = { onOpenVerification(stream.playableUrl ?: "") },
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF1E3A5F),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Solve Human / Robot Verification in Browser",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val playable = stream.playableUrl
                        if (!playable.isNullOrBlank()) {
                            if (playable.contains("hubcloud") || playable.contains("verify") || playable.contains("captcha") || playable.contains("robot")) {
                                onOpenVerification(playable)
                            } else {
                                coroutineScope.launch {
                                    isActivating = true
                                    kotlinx.coroutines.delay(200)
                                    executeStreamPlayback(context, playable, "$title • ${stream.name ?: stream.addonSource}")
                                    kotlinx.coroutines.delay(400)
                                    isActivating = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "Stream link not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("play_stream_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val playable = stream.playableUrl
                        if (!playable.isNullOrBlank()) {
                            val targetUrl = com.example.data.model.ensureMagnetTrackers(playable)
                            val isLocal = targetUrl.startsWith("content://") || targetUrl.startsWith("file://") || targetUrl.startsWith("/")
                            val finalUri: Uri = if (isLocal) {
                                val filePath = if (targetUrl.startsWith("file://")) targetUrl.removePrefix("file://") else targetUrl
                                val file = java.io.File(filePath)
                                try {
                                    if (targetUrl.startsWith("content://")) {
                                        val pUri = Uri.parse(targetUrl)
                                        runCatching {
                                            context.contentResolver.takePersistableUriPermission(pUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        pUri
                                    } else if (file.exists()) {
                                        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    } else {
                                        Uri.parse(targetUrl)
                                    }
                                } catch (_: Exception) {
                                    Uri.parse(targetUrl)
                                }
                            } else {
                                Uri.parse(targetUrl)
                            }

                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                if (isLocal) {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, finalUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                } else {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "$title Stream Link")
                                    putExtra(Intent.EXTRA_TEXT, targetUrl)
                                    putExtra("title", "$title • ${stream.name ?: stream.addonSource}")
                                }
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            val shareChooser = Intent.createChooser(sendIntent, "Share").apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(shareChooser)
                        } else {
                            Toast.makeText(context, "Stream link not available", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.testTag("share_stream_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontSize = 13.sp)
                }
            }
        }
    }
}

private fun executeStreamPlayback(context: Context, playableUrl: String, streamTitle: String = "Video") {
    val targetUrl = com.example.data.model.ensureMagnetTrackers(playableUrl)
    val isMagnet = targetUrl.startsWith("magnet:", ignoreCase = true)
    val isLocal = targetUrl.startsWith("content://") || targetUrl.startsWith("file://") || targetUrl.startsWith("/")

    val finalUri: Uri = if (isLocal) {
        val filePath = if (targetUrl.startsWith("file://")) targetUrl.removePrefix("file://") else targetUrl
        val file = java.io.File(filePath)
        try {
            if (targetUrl.startsWith("content://")) {
                val pUri = Uri.parse(targetUrl)
                runCatching {
                    context.contentResolver.takePersistableUriPermission(pUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                pUri
            } else if (file.exists()) {
                androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.parse(targetUrl)
            }
        } catch (_: Exception) {
            Uri.parse(targetUrl)
        }
    } else {
        Uri.parse(targetUrl)
    }

    val headersBundle = android.os.Bundle().apply {
        putString("User-Agent", "Stremio/1.6.11 (Linux; Android)")
        putString("Accept", "*/*")
        putString("Connection", "keep-alive")
    }

    val playIntent = Intent(Intent.ACTION_VIEW).apply {
        if (isMagnet) {
            data = finalUri
        } else {
            setDataAndType(finalUri, "video/*")
        }
        clipData = android.content.ClipData.newRawUri(streamTitle, finalUri)
        putExtra("title", streamTitle)
        putExtra("displayName", streamTitle)
        putExtra(Intent.EXTRA_TITLE, streamTitle)
        putExtra("android.media.intent.extra.TITLE", streamTitle)
        putExtra("return_result", true)
        putExtra("headers", arrayOf("User-Agent: Stremio/1.6.11 (Linux; Android)"))
        putExtra("android.media.intent.extra.HTTP_HEADERS", headersBundle)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    // Explicitly grant read URI permission to all potential video players for local content/file URIs
    if (isLocal) {
        try {
            val resList = context.packageManager.queryIntentActivities(playIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resList) {
                val pkg = resolveInfo.activityInfo.packageName
                context.grantUriPermission(pkg, finalUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {}
    }

    // Always create a clean text/plain sendIntent so Android OS keeps the "PLAY WITH VIDEO PLAYER OR SHARE" chooser
    // (Screenshot 165319) rather than morphing into the system media share sheet (Screenshot 164858)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, streamTitle)
        putExtra(Intent.EXTRA_TEXT, if (isLocal) finalUri.toString() else targetUrl)
        putExtra("title", streamTitle)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        val chooser = Intent.createChooser(playIntent, "Play with video player or share").apply {
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(sendIntent))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        try {
            val shareChooser = Intent.createChooser(sendIntent, "Share").apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(shareChooser)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open stream", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AddCustomProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (StremioStream) -> Unit
) {
    val context = LocalContext.current
    var isLocalFile by remember { mutableStateOf(false) }
    var providerName by remember { mutableStateOf("") }
    var urlOrPath by remember { mutableStateOf("") }
    var selectedResolution by remember { mutableStateOf("1080p FHD") }
    var fileSize by remember { mutableStateOf("2.1 GB") }
    var descriptionDetails by remember { mutableStateOf("BluRay, x264, Dual Audio") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            urlOrPath = uri.toString()
            val candidateName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Video"
            if (providerName.isBlank()) {
                providerName = candidateName.take(30)
            }
            Toast.makeText(context, "Selected video file: $candidateName", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Stream Provider",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isLocalFile,
                        onClick = { isLocalFile = false },
                        label = { Text("Provider Link (URL)", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = isLocalFile,
                        onClick = { isLocalFile = true },
                        label = { Text("Local Video File", fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.White
                        )
                    )
                }

                androidx.compose.material3.OutlinedTextField(
                    value = providerName,
                    onValueChange = { providerName = it },
                    label = { Text("Provider Name (e.g. Provider 3)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.material3.OutlinedTextField(
                    value = urlOrPath,
                    onValueChange = { urlOrPath = it },
                    label = { Text(if (isLocalFile) "Video File Path (e.g. /storage/...)" else "Stream URL (http://...)") },
                    trailingIcon = {
                        if (isLocalFile) {
                            IconButton(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier.testTag("pick_video_plus_icon")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Pick Video from Storage",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLocalFile) {
                    Button(
                        onClick = { videoPickerLauncher.launch("video/*") },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("choose_video_storage_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Choose Local Video File (+)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("1080p FHD", "4K 2160p", "720p HD").forEach { res ->
                        FilterChip(
                            selected = selectedResolution == res,
                            onClick = { selectedResolution = res },
                            label = { Text(res, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                androidx.compose.material3.OutlinedTextField(
                    value = fileSize,
                    onValueChange = { fileSize = it },
                    label = { Text("File Size (e.g. 2.1 GB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                androidx.compose.material3.OutlinedTextField(
                    value = descriptionDetails,
                    onValueChange = { descriptionDetails = it },
                    label = { Text("Format / Description (e.g. BluRay, x264)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (urlOrPath.isNotBlank()) {
                        val pName = providerName.ifBlank { if (isLocalFile) "Local Video" else "Custom Provider" }
                        val stream = StremioStream(
                            name = pName,
                            title = "$pName • $selectedResolution",
                            description = "[$pName] • $fileSize • $descriptionDetails",
                            url = urlOrPath.trim(),
                            streamerNumber = 3,
                            addonSource = pName,
                            subProvider = pName
                        )
                        onAdd(stream)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Add Provider", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageSlideshowImagesDialog(
    images: List<String>,
    onDismiss: () -> Unit,
    onSaveImages: (List<String>) -> Unit
) {
    val context = LocalContext.current
    var currentList by remember(images) { mutableStateOf(images.toMutableList()) }
    var inputUrl by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val newPaths = uris.map { com.example.ui.components.persistPickedImage(context, it) }
            currentList = (newPaths + currentList).distinct().toMutableList()
            Toast.makeText(context, "${uris.size} slideshow images added!", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Manage Slideshow Images", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Add Image URL or Local Path") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (inputUrl.isNotBlank()) {
                                currentList = (listOf(inputUrl.trim()) + currentList).toMutableList()
                                inputUrl = ""
                            }
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add URL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick Images (up to 10)", fontSize = 11.sp)
                    }
                }

                Text("Slideshow Images (${currentList.size}):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                currentList.forEachIndexed { idx, imgUrl ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(70.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.DarkGray)
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(com.example.ui.components.resolveCoilData(context, imgUrl))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Text(
                                text = imgUrl,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (idx > 0) {
                                    IconButton(
                                        onClick = {
                                            val list = currentList.toMutableList()
                                            val temp = list[idx]
                                            list[idx] = list[idx - 1]
                                            list[idx - 1] = temp
                                            currentList = list
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (idx < currentList.size - 1) {
                                    IconButton(
                                        onClick = {
                                            val list = currentList.toMutableList()
                                            val temp = list[idx]
                                            list[idx] = list[idx + 1]
                                            list[idx + 1] = temp
                                            currentList = list
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        currentList = currentList.filterIndexed { i, _ -> i != idx }.toMutableList()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = null, tint = CinemaRed, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentList.size < 3) {
                        Toast.makeText(context, "Please provide at least 3 images for the slideshow", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    onSaveImages(currentList)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Apply Images", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VerificationBrowserDialog(
    initialUrl: String,
    onDismiss: () -> Unit,
    onVerified: (String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Provider Verification (HD Hub / Pingu / YAS)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = CinemaRed)
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
            ) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { ctx ->
                        android.webkit.WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            webViewClient = object : android.webkit.WebViewClient() {
                                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    if (url != null) {
                                        currentUrl = url
                                        if (url.contains(".mp4") || url.contains(".m3u8") || url.contains("drive.google.com") || url.contains("playable")) {
                                            onVerified(url)
                                        }
                                    }
                                }
                            }
                            loadUrl(initialUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onVerified(currentUrl)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Use Resolved Link", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
