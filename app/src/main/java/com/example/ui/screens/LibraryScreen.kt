package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.SavedMediaEntity
import com.example.data.model.StremioCatalogMeta
import com.example.ui.theme.ImdbGold

enum class GridTileSize(val label: String, val minSize: Int, val fixedColumns: Int) {
    COMPACT("Compact (4 per row)", 68, 4),
    SMALL("Small (3 per row)", 95, 3),
    MEDIUM("Medium (2 per row)", 135, 2),
    LARGE("Large (1-2 per row)", 180, 0)
}

@Composable
fun LibraryScreen(
    savedItems: List<SavedMediaEntity>,
    sortOrder: String,
    onSortOrderSelected: (String) -> Unit,
    selectedTileSize: String = "compact",
    onTileSizeSelected: (String) -> Unit = {},
    onMoveItem: (Int, Int) -> Unit,
    onCommitReorder: (List<SavedMediaEntity>) -> Unit = {},
    onSaveLayoutAndOrder: () -> Unit = {},
    onDeleteItem: (String) -> Unit,
    onClickMovie: (StremioCatalogMeta) -> Unit,
    onAddCustomMediaWithMap: (title: String, type: String, poster: String, backdrop: String, year: String, rating: String, desc: String, genres: String, seasons: Int, perSeasonMap: Map<Int, Int>) -> Unit = { _, _, _, _, _, _, _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    var showAddMediaDialog by remember { mutableStateOf(false) }
    val currentGridSize = remember(selectedTileSize) {
        when (selectedTileSize.lowercase()) {
            "compact", "extra_small", "4" -> GridTileSize.COMPACT
            "small", "3" -> GridTileSize.SMALL
            "large", "1" -> GridTileSize.LARGE
            "medium", "2" -> GridTileSize.MEDIUM
            else -> GridTileSize.COMPACT
        }
    }
    val isCustomOrder = sortOrder.endsWith("_custom") || sortOrder == "custom"

    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 33) true
            else ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasStoragePermission = isGranted
        if (isGranted) {
            Toast.makeText(context, "Storage permission enabled: Library configs saved", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "My Library",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "${savedItems.size}",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            // Top right corner: Pencil icon + Sort / Reorder & Layout Menu Button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = { showAddMediaDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.testTag("add_custom_media_pencil_button")
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Add Movie or Series",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Box {
                    Surface(
                        onClick = { showSortMenu = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCustomOrder) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        modifier = Modifier.testTag("library_sort_menu_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Sort,
                                contentDescription = "Order & Layout",
                                tint = if (isCustomOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Order",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isCustomOrder) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Text(
                            text = "SORT ORDER",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        val isDateDesc = sortOrder == "date_desc" || sortOrder == "date_desc_custom" || sortOrder == "custom"
                        val isDateDescCustom = sortOrder == "date_desc_custom" || (sortOrder == "custom" && isDateDesc)
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Date Added (Newest First)", fontWeight = if (isDateDesc) FontWeight.Bold else FontWeight.Normal)
                                        if (isDateDescCustom) {
                                            Text(
                                                text = "Custom Sorting Enabled",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isDateDesc) {
                                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onSortOrderSelected("date_desc")
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_date_desc")
                        )

                        val isDateAsc = sortOrder == "date_asc" || sortOrder == "date_asc_custom"
                        val isDateAscCustom = sortOrder == "date_asc_custom"
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Date Added (Oldest First)", fontWeight = if (isDateAsc) FontWeight.Bold else FontWeight.Normal)
                                        if (isDateAscCustom) {
                                            Text(
                                                text = "Custom Sorting Enabled",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isDateAsc) {
                                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onSortOrderSelected("date_asc")
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_date_asc")
                        )

                        val isTitleAsc = sortOrder == "title_asc" || sortOrder == "title_asc_custom"
                        val isTitleAscCustom = sortOrder == "title_asc_custom"
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Title (A to Z)", fontWeight = if (isTitleAsc) FontWeight.Bold else FontWeight.Normal)
                                        if (isTitleAscCustom) {
                                            Text(
                                                text = "Custom Sorting Enabled",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isTitleAsc) {
                                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onSortOrderSelected("title_asc")
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_title_asc")
                        )

                        val isRatingDesc = sortOrder == "rating_desc" || sortOrder == "rating_desc_custom"
                        val isRatingDescCustom = sortOrder == "rating_desc_custom"
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("IMDb Rating (Highest)", fontWeight = if (isRatingDesc) FontWeight.Bold else FontWeight.Normal)
                                        if (isRatingDescCustom) {
                                            Text(
                                                text = "Custom Sorting Enabled",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isRatingDesc) {
                                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onSortOrderSelected("rating_desc")
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_rating_desc")
                        )

                        val isRatingAsc = sortOrder == "rating_asc" || sortOrder == "rating_asc_custom"
                        val isRatingAscCustom = sortOrder == "rating_asc_custom"
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("IMDb Rating (Lowest)", fontWeight = if (isRatingAsc) FontWeight.Bold else FontWeight.Normal)
                                        if (isRatingAscCustom) {
                                            Text(
                                                text = "Custom Sorting Enabled",
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    if (isRatingAsc) {
                                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                onSortOrderSelected("rating_asc")
                                showSortMenu = false
                            },
                            modifier = Modifier.testTag("sort_rating_asc")
                        )

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = "POSTER SIZE",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )

                        for (size in GridTileSize.entries) {
                            val isSelected = currentGridSize == size
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(size.label)
                                        if (isSelected) {
                                            Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                onClick = {
                                    onTileSizeSelected(size.name.lowercase())
                                    showSortMenu = false
                                },
                                modifier = Modifier.testTag("grid_size_${size.name.lowercase()}")
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Save Layout & Order",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "Save tile size and movie positions locally",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onTileSizeSelected(currentGridSize.name.lowercase())
                                onSaveLayoutAndOrder()
                                showSortMenu = false
                                Toast.makeText(context, "Saved: ${currentGridSize.label} & movie positions saved locally!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("save_library_layout_order_button")
                        )
                    }
                }
            }
        }

        if (savedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 90.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BookmarkBorder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        modifier = Modifier.size(56.dp)
                    )
                    Text(
                        text = "Your library is empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Save movies and series to your library to track and watch them anytime.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            var localItems by remember(savedItems) { mutableStateOf(savedItems) }
            var activeDraggingId by remember { mutableStateOf<String?>(null) }
            var hoverTargetId by remember { mutableStateOf<String?>(null) }
            var dragOffsetX by remember { mutableFloatStateOf(0f) }
            var dragOffsetY by remember { mutableFloatStateOf(0f) }
            val gridState = rememberLazyGridState()

            LazyVerticalGrid(
                columns = if (currentGridSize.fixedColumns > 0) GridCells.Fixed(currentGridSize.fixedColumns) else GridCells.Adaptive(minSize = currentGridSize.minSize.dp),
                state = gridState,
                contentPadding = PaddingValues(start = 5.dp, end = 5.dp, bottom = 95.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(localItems, key = { _, item -> item.id }) { index, item ->
                    val isBeingDragged = activeDraggingId == item.id
                    val isHoverTarget = hoverTargetId == item.id && activeDraggingId != item.id

                    DragDropGridCard(
                        item = item,
                        index = index,
                        isCustomOrder = isCustomOrder,
                        isBeingDragged = isBeingDragged,
                        isHoverTarget = isHoverTarget,
                        dragOffsetX = if (isBeingDragged) dragOffsetX else 0f,
                        dragOffsetY = if (isBeingDragged) dragOffsetY else 0f,
                        gridSize = currentGridSize,
                        onEnableCustomOrder = {
                            val nextSort = when {
                                sortOrder.startsWith("date_asc") -> "date_asc_custom"
                                sortOrder.startsWith("title_asc") -> "title_asc_custom"
                                sortOrder.startsWith("rating_desc") -> "rating_desc_custom"
                                sortOrder.startsWith("rating_asc") -> "rating_asc_custom"
                                else -> "date_desc_custom"
                            }
                            onSortOrderSelected(nextSort)
                        },
                        onDragStart = {
                            activeDraggingId = item.id
                            hoverTargetId = null
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDrag = { dragAmount ->
                            dragOffsetX += dragAmount.x
                            dragOffsetY += dragAmount.y

                            val currentInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == item.id }
                            if (currentInfo != null) {
                                val currentCenterX = currentInfo.offset.x + currentInfo.size.width / 2 + dragOffsetX
                                val currentCenterY = currentInfo.offset.y + currentInfo.size.height / 2 + dragOffsetY

                                val targetInfo = gridState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
                                    info.key != item.id &&
                                    currentCenterX in (info.offset.x.toFloat())..(info.offset.x + info.size.width).toFloat() &&
                                    currentCenterY in (info.offset.y.toFloat())..(info.offset.y + info.size.height).toFloat()
                                }

                                hoverTargetId = targetInfo?.key as? String
                            }
                        },
                        onDragEnd = {
                            val targetId = hoverTargetId
                            val draggingId = activeDraggingId
                            if (targetId != null && draggingId != null && targetId != draggingId) {
                                val fromIdx = localItems.indexOfFirst { it.id == draggingId }
                                val toIdx = localItems.indexOfFirst { it.id == targetId }
                                if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                                    val updated = localItems.toMutableList()
                                    val moved = updated.removeAt(fromIdx)
                                    updated.add(toIdx, moved)
                                    localItems = updated
                                    onCommitReorder(updated)
                                }
                            }
                            activeDraggingId = null
                            hoverTargetId = null
                            dragOffsetX = 0f
                            dragOffsetY = 0f
                        },
                        onDelete = { onDeleteItem(item.id) },
                        onClick = {
                            if (activeDraggingId == null) {
                                onClickMovie(
                                    StremioCatalogMeta(
                                        id = item.id,
                                        name = item.title,
                                        type = item.type,
                                        poster = item.poster,
                                        background = item.backdrop,
                                        year = item.year,
                                        imdbRating = item.imdbRating,
                                        description = item.description,
                                        genre = item.genres?.split(",")?.map { it.trim() }
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }

        if (showAddMediaDialog) {
            AddMediaDialog(
                onDismiss = { showAddMediaDialog = false },
                onAddMedia = { title, type, poster, backdrop, year, rating, desc, genres, seasons, perSeasonMap ->
                    onAddCustomMediaWithMap(title, type, poster, backdrop, year, rating, desc, genres, seasons, perSeasonMap)
                    showAddMediaDialog = false
                    Toast.makeText(context, "$title added to Library!", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun DragDropGridCard(
    item: SavedMediaEntity,
    index: Int,
    isCustomOrder: Boolean,
    isBeingDragged: Boolean,
    isHoverTarget: Boolean = false,
    dragOffsetX: Float,
    dragOffsetY: Float,
    gridSize: GridTileSize,
    onEnableCustomOrder: () -> Unit = {},
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isBeingDragged) 1.08f else if (isHoverTarget) 1.03f else 1.0f,
        label = "drag_scale"
    )

    val dragModifier = Modifier.pointerInput(item.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = {
                if (!isCustomOrder) {
                    onEnableCustomOrder()
                }
                onDragStart()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                onDrag(dragAmount)
            },
            onDragEnd = { onDragEnd() },
            onDragCancel = { onDragEnd() }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragOffsetX
                translationY = dragOffsetY
                scaleX = scale
                scaleY = scale
                shadowElevation = if (isBeingDragged) 12f else 0f
            }
            .zIndex(if (isBeingDragged) 50f else if (isHoverTarget) 10f else 1f)
            .then(dragModifier)
            .clickable(onClick = onClick)
            .testTag("saved_grid_item_${item.id}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isBeingDragged) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = when {
            isBeingDragged -> androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isHoverTarget -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF10B981))
            else -> null
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isBeingDragged) 8.dp else 0.dp)
    ) {
        val context = LocalContext.current
        val prefs = remember { com.example.data.pref.UserPreferences(context) }
        val customSlideshow = remember(item.id, item.title) {
            prefs.getCustomSlideshow(item.id) ?: prefs.getCustomSlideshow("name_${item.title.trim().lowercase()}")
        }
        val parsedSlide = remember(customSlideshow) {
            if (customSlideshow != null) {
                runCatching {
                    kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<List<String>>(customSlideshow)
                }.getOrNull()?.firstOrNull()
            } else null
        }
        val effectivePoster = item.poster?.takeIf { it.isNotBlank() } ?: parsedSlide ?: item.backdrop

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 2.82f)
            ) {
                if (!effectivePoster.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(com.example.ui.components.resolveCoilData(context, effectivePoster))
                            .size(240, 340)
                            .allowRgb565(true)
                            .crossfade(true)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.DarkGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Movie, null, tint = Color.LightGray)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (isBeingDragged) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Drop to Move",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                } else if (isHoverTarget) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF10B981).copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF10B981)
                        ) {
                            Text(
                                text = "Insert Here",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    if (!item.imdbRating.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = ImdbGold,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = item.imdbRating,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (!item.year.isNullOrBlank()) {
                        Text(
                            text = item.year,
                            color = Color.LightGray,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
            ) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (gridSize == GridTileSize.COMPACT) 10.sp else if (gridSize == GridTileSize.SMALL) 11.sp else 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AddMediaDialog(
    onDismiss: () -> Unit,
    onAddMedia: (title: String, type: String, poster: String, backdrop: String, year: String, rating: String, desc: String, genres: String, seasons: Int, perSeasonMap: Map<Int, Int>) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("movie") }
    var posterUrl by remember { mutableStateOf("") }
    var backdropUrl by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2024") }
    var rating by remember { mutableStateOf("8.5") }
    var description by remember { mutableStateOf("") }
    var genres by remember { mutableStateOf("Action, Anime, Thriller") }
    var seasonsCountStr by remember { mutableStateOf("1") }
    var setEpisodesPerSeasonMode by remember { mutableStateOf("bulk") } // "bulk" or "manual"
    var bulkEpisodesStr by remember { mutableStateOf("12") }
    var perSeasonEpsRawMap by remember { mutableStateOf(mapOf(1 to "12")) }

    val posterPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val savedPaths = uris.map { com.example.ui.components.persistPickedImage(context, it) }
            val firstSaved = savedPaths.first()
            posterUrl = firstSaved
            if (backdropUrl.isBlank()) {
                backdropUrl = firstSaved
            }
            Toast.makeText(context, "${uris.size} poster image(s) saved permanently!", Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Custom Movie or Series", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Type:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = type == "movie",
                            onClick = { type = "movie" },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text("Movie", fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = type == "series",
                            onClick = { type = "series" },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Text("Series", fontSize = 13.sp)
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = posterUrl,
                        onValueChange = {
                            posterUrl = it
                            if (backdropUrl.isBlank()) backdropUrl = it
                        },
                        label = { Text("Poster Image URL or Path") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    posterPickerLauncher.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
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
                    Button(
                        onClick = {
                            posterPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pick Poster Image(s) from Storage (+)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = rating,
                        onValueChange = { rating = it },
                        label = { Text("IMDb Rating") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = genres,
                    onValueChange = { genres = it },
                    label = { Text("Genres (comma separated)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (type == "series") {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Seasons & Episode Configuration", fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            OutlinedTextField(
                                value = seasonsCountStr,
                                onValueChange = {
                                    seasonsCountStr = it
                                    val count = it.toIntOrNull() ?: 1
                                    val newMap = (1..count.coerceIn(1, 100)).associateWith { seasonNum ->
                                        perSeasonEpsRawMap[seasonNum] ?: bulkEpisodesStr.ifBlank { "12" }
                                    }
                                    perSeasonEpsRawMap = newMap
                                },
                                label = { Text("Number of Seasons") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Episodes per season setup:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = setEpisodesPerSeasonMode == "bulk",
                                    onClick = { setEpisodesPerSeasonMode = "bulk" }
                                )
                                Text("Same episode count for all seasons", fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = setEpisodesPerSeasonMode == "manual",
                                    onClick = { setEpisodesPerSeasonMode = "manual" }
                                )
                                Text("Configure each season manually", fontSize = 12.sp)
                            }

                            if (setEpisodesPerSeasonMode == "bulk") {
                                OutlinedTextField(
                                    value = bulkEpisodesStr,
                                    onValueChange = { bulkEpisodesStr = it },
                                    label = { Text("Episodes per season (e.g. 12)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val sCount = (seasonsCountStr.toIntOrNull() ?: 1).coerceIn(1, 50)
                                Text("Specify episodes for each season:", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                (1..sCount).forEach { sNum ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Season $sNum:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(75.dp))
                                        OutlinedTextField(
                                            value = perSeasonEpsRawMap[sNum] ?: "12",
                                            onValueChange = { inputStr ->
                                                perSeasonEpsRawMap = perSeasonEpsRawMap.toMutableMap().apply { put(sNum, inputStr) }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (~40-50 words)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val sCount = (seasonsCountStr.toIntOrNull() ?: 1).coerceIn(1, 100)
                        val mapToUse = if (setEpisodesPerSeasonMode == "bulk") {
                            val bEps = bulkEpisodesStr.toIntOrNull() ?: 12
                            (1..sCount).associateWith { bEps }
                        } else {
                            (1..sCount).associateWith { sNum ->
                                perSeasonEpsRawMap[sNum]?.toIntOrNull() ?: 12
                            }
                        }
                        onAddMedia(
                            title.trim(),
                            type,
                            posterUrl.trim(),
                            backdropUrl.trim().ifEmpty { posterUrl.trim() },
                            year.trim(),
                            rating.trim(),
                            description.trim().ifEmpty { "An exciting custom $type experience filled with gripping storytelling and compelling character development." },
                            genres.trim(),
                            sCount,
                            mapToUse
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save & Add to Library")
            }
        },
        dismissButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text("Cancel")
            }
        }
    )
}
