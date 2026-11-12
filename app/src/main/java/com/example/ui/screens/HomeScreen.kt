package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.example.data.model.StremioCatalogMeta
import com.example.data.pref.UserProfile
import com.example.data.repository.CategorySection
import com.example.ui.HomeUiState
import com.example.ui.components.HeroBanner
import com.example.ui.components.MovieCard
import com.example.ui.theme.CinemaRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    homeState: HomeUiState,
    userProfile: UserProfile,
    onOpenProfile: () -> Unit,
    onOpenSearch: () -> Unit,
    onRefresh: () -> Unit,
    onSelectHeroIndex: (Int) -> Unit,
    onSelectPlatformFilter: (String) -> Unit,
    onClickMovie: (StremioCatalogMeta) -> Unit,
    isHomeEditMode: Boolean = false,
    onSaveHomeCustomizations: () -> Unit = {},
    onRestoreHomeOriginal: () -> Unit = {},
    onExitHomeEditMode: () -> Unit = {},
    onMoveCategoryMovie: (categoryId: String, from: Int, to: Int) -> Unit = { _, _, _ -> },
    onDeleteCategoryMovie: (categoryId: String, movieId: String) -> Unit = { _, _ -> },
    onAddCategoryMovie: (categoryId: String, StremioCatalogMeta) -> Unit = { _, _ -> },
    onEditCategoryMovie: (categoryId: String, movieId: String, StremioCatalogMeta) -> Unit = { _, _, _ -> },
    onShuffleCategory: (categoryId: String) -> Unit = {},
    onUpdateHeroMovies: (List<StremioCatalogMeta>) -> Unit = {},
    onUpdateHeroCustomImage: (StremioCatalogMeta, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val categories = homeState.categories

    var showAddMovieForCategory by remember { mutableStateOf<String?>(null) }
    var editingMoviePair by remember { mutableStateOf<Pair<String, StremioCatalogMeta>?>(null) }
    var showHeroEditorDialog by remember { mutableStateOf(false) }
    var showHomeRestoreConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = isHomeEditMode) {
        onExitHomeEditMode()
    }

    // Pre-calculate display categories
    val displayCategories = remember(categories, homeState.selectedPlatform) {
        if (homeState.selectedPlatform == "All") {
            categories
        } else {
            categories.filter { it.platformName.equals(homeState.selectedPlatform, ignoreCase = true) }
        }
    }

    val onMovieClickWithHaptic = remember(onClickMovie, userProfile.hapticEnabled, userProfile.hapticIntensity, context) {
        { movie: StremioCatalogMeta ->
            com.example.ui.util.HapticUtil.performHaptic(context, userProfile.hapticEnabled, userProfile.hapticIntensity)
            onClickMovie(movie)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // Top Hero Banner with translucent poster extending behind user profile and name
            item(key = "hero_banner", contentType = "hero") {
                Box {
                    HeroBanner(
                        movies = homeState.heroMovies,
                        currentIndex = homeState.currentHeroIndex,
                        onSelectIndex = onSelectHeroIndex,
                        onClickMovie = onMovieClickWithHaptic,
                        topContent = {
                            Surface(
                                shape = RoundedCornerShape(28.dp),
                                color = Color(0xEE28282E),
                                border = androidx.compose.foundation.BorderStroke(1.2.dp, Color(0xFF4A4D55)),
                                shadowElevation = 6.dp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(28.dp))
                                    .clickable {
                                        com.example.ui.util.HapticUtil.performHaptic(context, userProfile.hapticEnabled, userProfile.hapticIntensity)
                                        onOpenProfile()
                                    }
                                    .testTag("home_profile_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFF181F2C),
                                        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                        shadowElevation = 4.dp,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (!userProfile.customAvatarPath.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = userProfile.customAvatarPath,
                                                    contentDescription = "Custom User Avatar",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Filled.Person,
                                                    contentDescription = "User Profile",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Column(modifier = Modifier.padding(end = 8.dp)) {
                                        Text(
                                            text = "Heyo,\n${userProfile.name}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            lineHeight = 14.sp
                                        )
                                    }
                                }
                            }
                        }
                    )

                    // In Edit Mode: Show Edit Hero Slideshow button
                    if (isHomeEditMode) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xEE1A1A1E),
                            border = androidx.compose.foundation.BorderStroke(1.dp, CinemaRed),
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 16.dp)
                                .clickable { showHeroEditorDialog = true }
                                .testTag("edit_hero_slideshow_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit Hero Slideshow",
                                    tint = CinemaRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Edit 20 Heroes",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }

            // All Categories catalog tabs with brand colors
            item(key = "platform_chips", contentType = "platform_chips") {
                val platforms = listOf("All Catalogs", "Netflix", "Apple TV+", "Disney+", "Prime Video", "HBO Max")
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 0.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(platforms) { p ->
                        val selected = (p == "All Catalogs" && homeState.selectedPlatform == "All") || p.equals(homeState.selectedPlatform, ignoreCase = true)
                        val brandColor = when (p) {
                            "Netflix" -> Color(0xFFE50914)
                            "Apple TV+" -> Color(0xFFE2E8F0)
                            "Disney+" -> Color(0xFF0063E5)
                            "Prime Video" -> Color(0xFF00A8E1)
                            "HBO Max" -> Color(0xFF8B5CF6)
                            else -> MaterialTheme.colorScheme.primary
                        }
                        val containerColor = if (selected) brandColor else brandColor.copy(alpha = 0.20f)
                        val textColor = when {
                            p == "Apple TV+" && selected -> Color.Black
                            selected -> Color.White
                            else -> brandColor
                        }

                        FilterChip(
                            selected = selected,
                            onClick = { onSelectPlatformFilter(if (p == "All Catalogs") "All" else p) },
                            label = { Text(p, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandColor,
                                selectedLabelColor = textColor,
                                containerColor = containerColor,
                                labelColor = textColor
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.Transparent,
                                selectedBorderColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }

            // Categories horizontal carousels with 106dp spacious poster dimensions
            items(
                displayCategories,
                key = { it.id },
                contentType = { "category_section" }
            ) { category ->
                CategorySectionView(
                    category = category,
                    onClickMovie = onMovieClickWithHaptic,
                    isEditMode = isHomeEditMode,
                    onAddMovieClick = { showAddMovieForCategory = category.id },
                    onDeleteMovie = { movieId -> onDeleteCategoryMovie(category.id, movieId) },
                    onMoveMovie = { from, to -> onMoveCategoryMovie(category.id, from, to) },
                    onEditMovie = { movie -> editingMoviePair = Pair(category.id, movie) },
                    onShuffleCategory = { onShuffleCategory(category.id) }
                )
            }
        }

        // Top Bleeding Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.85f),
                            Color.Black.copy(alpha = 0.30f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom Bleeding Transparent Gradient Overlay (behind pill navbar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .align(Alignment.BottomCenter)
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.60f),
                            Color.Black.copy(alpha = 0.90f),
                            Color.Black
                        )
                    )
                )
        )

        // Top Right Floating Home Edit Mode Bar (Screenshot 780): Glowing Red Pencil + Tick Save Icon
        if (isHomeEditMode) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF018181C),
                border = androidx.compose.foundation.BorderStroke(1.2.dp, CinemaRed),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 6.dp)
                    .testTag("home_edit_mode_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Glowing Pencil Edit Indicator
                    Surface(
                        shape = CircleShape,
                        color = CinemaRed.copy(alpha = 0.25f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit Mode Active",
                                tint = CinemaRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Text(
                        text = "Edit Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // 2. Tick / Save Checkmark Icon
                    IconButton(
                        onClick = {
                            onSaveHomeCustomizations()
                            Toast.makeText(context, "Home layout and posters saved!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("home_edit_save_tick_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save Layout",
                            tint = Color(0xFF4ADE80),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3. Restore / History Clock Icon (Always beside the pencil & tick)
                    IconButton(
                        onClick = { showHomeRestoreConfirmDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("home_restore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Restore Original Home Screen",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    // 4. Cancel / Exit Icon
                    IconButton(
                        onClick = onExitHomeEditMode,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Exit Edit Mode",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Home Screen Restore Confirmation Dialog
        if (showHomeRestoreConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showHomeRestoreConfirmDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Restore Home Screen", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                },
                text = {
                    Text("Do you want to restore all categories, 20 hero movies, and posters back to their original Netflix catalog layout and order?", fontSize = 13.sp)
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRestoreHomeOriginal()
                            showHomeRestoreConfirmDialog = false
                            onExitHomeEditMode()
                            Toast.makeText(context, "Restored original categories & 20 hero movies!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore All", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showHomeRestoreConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

    }

    // DIALOG: Add Movie / Series to Category
    showAddMovieForCategory?.let { catId ->
        var newTitle by remember { mutableStateOf("") }
        var newPosterUrl by remember { mutableStateOf("") }
        var newYear by remember { mutableStateOf("2024") }
        var newRating by remember { mutableStateOf("8.5") }
        var newType by remember { mutableStateOf("series") }

        val addPosterPickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
        ) { uris: List<android.net.Uri> ->
            if (uris.isNotEmpty()) {
                newPosterUrl = uris.first().toString()
                Toast.makeText(context, "${uris.size} poster image(s) selected!", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { showAddMovieForCategory = null },
            title = {
                Text(
                    text = "Add Movie or Series",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Insert a title into this category row.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Quick Preset Chips for famous anime & shows
                    Text(text = "Quick Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val presets = listOf(
                            Triple("Seven Deadly Sins", "poster_seven_deadly_sins", "8.1"),
                            Triple("Solo Leveling", "poster_solo_leveling", "8.5"),
                            Triple("The Originals", "poster_the_originals", "8.3"),
                            Triple("Jujutsu Kaisen", "poster_jujutsu_kaisen", "8.6"),
                            Triple("Godzilla Minus One", "poster_godzilla_minus_one", "7.6")
                        )
                        items(presets) { (pTitle, pPoster, pRating) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable {
                                    newTitle = pTitle
                                    newPosterUrl = pPoster
                                    newRating = pRating
                                }
                            ) {
                                Text(
                                    text = pTitle,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = newPosterUrl,
                        onValueChange = { newPosterUrl = it },
                        label = { Text("Poster URL or Asset Name") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    addPosterPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Images", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newYear,
                            onValueChange = { newYear = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newRating,
                            onValueChange = { newRating = it },
                            label = { Text("IMDb") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            val newMovie = StremioCatalogMeta(
                                id = "custom_${System.currentTimeMillis()}",
                                name = newTitle.trim(),
                                type = newType,
                                poster = newPosterUrl.ifBlank { "poster_seven_deadly_sins" },
                                background = newPosterUrl.ifBlank { "poster_seven_deadly_sins" },
                                year = newYear.ifBlank { "2024" },
                                imdbRating = newRating.ifBlank { "8.0" },
                                description = "Added to category."
                            )
                            onAddCategoryMovie(catId, newMovie)
                            showAddMovieForCategory = null
                            Toast.makeText(context, "Added $newTitle to category", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Add Title")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMovieForCategory = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Edit Movie / Series Title and Poster
    editingMoviePair?.let { (catId, movie) ->
        var editTitle by remember { mutableStateOf(movie.name) }
        var editPoster by remember { mutableStateOf(movie.poster ?: "") }
        var editYear by remember { mutableStateOf(movie.year ?: "") }
        var editRating by remember { mutableStateOf(movie.imdbRating ?: "") }

        val editPosterPickerLauncher = rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
        ) { uris: List<android.net.Uri> ->
            if (uris.isNotEmpty()) {
                val permanentPath = com.example.ui.components.persistPickedImage(context, uris.first())
                editPoster = permanentPath
                Toast.makeText(context, "${uris.size} poster image(s) saved permanently!", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { editingMoviePair = null },
            title = {
                Text(
                    text = "Edit Title & Poster",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPoster,
                        onValueChange = { editPoster = it },
                        label = { Text("Poster URL or Asset Name") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    editPosterPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Images", tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = editYear,
                            onValueChange = { editYear = it },
                            label = { Text("Year") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = editRating,
                            onValueChange = { editRating = it },
                            label = { Text("IMDb") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = movie.copy(
                            name = editTitle.trim().ifBlank { movie.name },
                            poster = editPoster.trim().ifBlank { movie.poster },
                            background = editPoster.trim().ifBlank { movie.background },
                            year = editYear.trim().ifBlank { movie.year },
                            imdbRating = editRating.trim().ifBlank { movie.imdbRating }
                        )
                        onEditCategoryMovie(catId, movie.id, updated)
                        editingMoviePair = null
                        Toast.makeText(context, "Updated ${updated.name}", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMoviePair = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Edit 20 Hero Slideshow Movies
    if (showHeroEditorDialog) {
        var localHeroes by remember { mutableStateOf(homeState.heroMovies.toMutableList()) }

        var heroToEditImage by remember { mutableStateOf<StremioCatalogMeta?>(null) }
        var customHeroImageUrlInput by remember { mutableStateOf("") }
        var showHeroImageChooserDialog by remember { mutableStateOf(false) }

        val heroImagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let { pickedUri ->
                heroToEditImage?.let { targetHero ->
                    val savedImagePath = runCatching {
                        val input = context.contentResolver.openInputStream(pickedUri)
                        val outFile = java.io.File(context.filesDir, "custom_hero_${targetHero.id.filter { it.isLetterOrDigit() }}_${System.currentTimeMillis()}.jpg")
                        if (input != null) {
                            outFile.outputStream().use { out -> input.copyTo(out) }
                            outFile.absolutePath
                        } else pickedUri.toString()
                    }.getOrDefault(pickedUri.toString())

                    val updated = targetHero.copy(
                        poster = savedImagePath,
                        background = savedImagePath
                    )
                    localHeroes = localHeroes.map { if (it.id == targetHero.id || it.name.equals(targetHero.name, ignoreCase = true)) updated else it }.toMutableList()
                    onUpdateHeroCustomImage(targetHero, savedImagePath)
                    Toast.makeText(context, "Custom image set for ${targetHero.name}!", Toast.LENGTH_SHORT).show()
                }
                showHeroImageChooserDialog = false
                heroToEditImage = null
            }
        }

        AlertDialog(
            onDismissRequest = { showHeroEditorDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Edit 20 Hero Slideshow",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    IconButton(onClick = { showHeroEditorDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Seven Deadly Sins is fixed at #1. Reorder or customize images below:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    localHeroes.forEachIndexed { idx, hero ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (idx == 0) CinemaRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    val currentImg = hero.poster ?: hero.background
                                    if (!currentImg.isNullOrBlank()) {
                                        AsyncImage(
                                            model = com.example.ui.components.buildOptimizedImageRequest(context, currentImg, 56, 56, false),
                                            contentDescription = hero.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                        )
                                    }
                                    Text(
                                        text = hero.name,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    // PLUS ICON: Set custom poster/slideshow image for this title
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clickable {
                                                heroToEditImage = hero
                                                customHeroImageUrlInput = hero.poster ?: hero.background ?: ""
                                                showHeroImageChooserDialog = true
                                            }
                                            .testTag("hero_add_image_${hero.id}")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = "Set custom image for ${hero.name}",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    if (idx > 1) {
                                        IconButton(
                                            onClick = {
                                                val list = localHeroes.toMutableList()
                                                val temp = list[idx]
                                                list[idx] = list[idx - 1]
                                                list[idx - 1] = temp
                                                localHeroes = list
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    if (idx > 0 && idx < localHeroes.size - 1) {
                                        IconButton(
                                            onClick = {
                                                val list = localHeroes.toMutableList()
                                                val temp = list[idx]
                                                list[idx] = list[idx + 1]
                                                list[idx + 1] = temp
                                                localHeroes = list
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                        }
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
                        onUpdateHeroMovies(localHeroes)
                        showHeroEditorDialog = false
                        Toast.makeText(context, "Hero slideshow updated!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Apply Heroes")
                }
            },
            dismissButton = {
                TextButton(onClick = { showHeroEditorDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        // Subdialog to choose or upload image for the selected hero
        if (showHeroImageChooserDialog && heroToEditImage != null) {
            val target = heroToEditImage!!
            AlertDialog(
                onDismissRequest = {
                    showHeroImageChooserDialog = false
                    heroToEditImage = null
                },
                title = {
                    Text(
                        text = "Custom Image for ${target.name}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Set a custom Home Screen slideshow poster for ${target.name}. It is shown specifically for the Home Screen slideshow.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = { heroImagePickerLauncher.launch("image/*") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Choose from Storage", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            text = "or paste direct image URL:",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = customHeroImageUrlInput,
                            onValueChange = { customHeroImageUrlInput = it },
                            label = { Text("Image URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val url = customHeroImageUrlInput.trim()
                            if (url.isNotBlank()) {
                                val updated = target.copy(poster = url, background = url)
                                localHeroes = localHeroes.map { if (it.id == target.id || it.name.equals(target.name, ignoreCase = true)) updated else it }.toMutableList()
                                onUpdateHeroCustomImage(target, url)
                                Toast.makeText(context, "Custom image saved for ${target.name}!", Toast.LENGTH_SHORT).show()
                            }
                            showHeroImageChooserDialog = false
                            heroToEditImage = null
                        }
                    ) {
                        Text("Save Image")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showHeroImageChooserDialog = false
                        heroToEditImage = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SplashPulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Box(
        modifier = Modifier
            .size(150.dp)
            .graphicsLayer {
                scaleX = pulseScale
                scaleY = pulseScale
            },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_app_logo),
            contentDescription = "App Icon Splash",
            modifier = Modifier.size(142.dp)
        )
    }
}

@Composable
private fun CategorySectionView(
    category: CategorySection,
    onClickMovie: (StremioCatalogMeta) -> Unit,
    isEditMode: Boolean = false,
    onAddMovieClick: () -> Unit = {},
    onDeleteMovie: (String) -> Unit = {},
    onMoveMovie: (from: Int, to: Int) -> Unit = { _, _ -> },
    onEditMovie: (StremioCatalogMeta) -> Unit = {},
    onShuffleCategory: () -> Unit = {}
) {
    val platformColor = when (category.platformName) {
        "Netflix" -> Color(0xFFE50914)
        "Apple TV+" -> Color(0xFFE2E8F0)
        "HBO Max" -> Color(0xFF8B5CF6)
        "Disney+" -> Color(0xFF0063E5)
        "Prime Video" -> Color(0xFF00A8E1)
        "Cinemeta" -> Color(0xFF6366F1)
        else -> MaterialTheme.colorScheme.primary
    }

    val platformTextColor = when (category.platformName) {
        "Apple TV+" -> Color(0xFF0F172A)
        else -> Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 1.dp, bottom = 3.dp)
    ) {
        // Section Header: D-shaped box (flat left edge, rounded right edge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 0.dp, end = 8.dp, top = 0.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 20.dp, bottomEnd = 20.dp),
                color = Color(0xEE2A2A30),
                shadowElevation = 2.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = platformColor
                    ) {
                        Text(
                            text = category.platformName.uppercase(),
                            color = platformTextColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = category.title.substringAfter(" — ", category.title),
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // In Edit Mode: Category Shuffle Icon
            if (isEditMode) {
                IconButton(
                    onClick = onShuffleCategory,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("shuffle_category_${category.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle Category",
                        tint = CinemaRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Horizontal items row with 84.dp posters (proportional, sleek poster cards)
        val sanitizedItems = remember(category.items) {
            category.items.distinctBy { it.id }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // First item in Edit Mode: Add Movie (+) Card
            if (isEditMode) {
                item(key = "add_to_${category.id}") {
                    AddMoviePlaceholderCard(
                        onClick = onAddMovieClick,
                        cardWidth = 84.dp
                    )
                }
            }

            itemsIndexed(
                sanitizedItems,
                key = { _, movie -> "${category.id}_${movie.id}" },
                contentType = { _, _ -> "movie_card" }
            ) { idx, movie ->
                MovieCard(
                    meta = movie,
                    onClick = { onClickMovie(movie) },
                    cardWidth = 84.dp,
                    isEditMode = isEditMode,
                    onDelete = { onDeleteMovie(movie.id) },
                    onMoveLeft = { if (idx > 0) onMoveMovie(idx, idx - 1) },
                    onMoveRight = { if (idx < sanitizedItems.size - 1) onMoveMovie(idx, idx + 1) },
                    onEdit = { onEditMovie(movie) }
                )
            }
        }
    }
}

@Composable
private fun AddMoviePlaceholderCard(
    onClick: () -> Unit,
    cardWidth: androidx.compose.ui.unit.Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(cardWidth)
            .clickable(onClick = onClick)
            .testTag("add_movie_placeholder_card"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 2.82f)
                .clip(RoundedCornerShape(12.dp))
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Add Movie",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Text(
                        text = "Add Title",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Text(
            text = "+ New Title",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = "Insert item",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
