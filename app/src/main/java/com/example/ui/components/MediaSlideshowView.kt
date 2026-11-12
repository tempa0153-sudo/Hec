package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

@Composable
fun MediaSlideshowView(
    images: List<String>,
    movieTitle: String,
    isEditingMode: Boolean = false,
    onAddOrEditClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val fallbackStills = remember {
        listOf(
            "https://images.unsplash.com/photo-1536440136628-849c177e76a1?q=80&w=1200",
            "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?q=80&w=1200",
            "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?q=80&w=1200",
            "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?q=80&w=1200",
            "https://images.unsplash.com/photo-1518676590629-3dcbd9c5a5c9?q=80&w=1200",
            "https://images.unsplash.com/photo-1478720568477-152d9b164e26?q=80&w=1200",
            "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?q=80&w=1200",
            "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?q=80&w=1200"
        )
    }

    val cleanImages = remember(images) {
        val provided = images.filter { it.isNotBlank() }.distinct()
        if (provided.isNotEmpty()) {
            provided
        } else {
            fallbackStills
        }
    }

    val pagerState = rememberPagerState(pageCount = { cleanImages.size })

    // Auto-advance slideshow every 3.5 seconds
    LaunchedEffect(cleanImages.size) {
        if (cleanImages.size > 1) {
            while (true) {
                delay(3500)
                runCatching {
                    if (pagerState.pageCount > 0) {
                        val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                        pagerState.animateScrollToPage(
                            nextPage,
                            animationSpec = tween(durationMillis = 600)
                        )
                    }
                }
            }
        }
    }

    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .testTag("media_slideshow_view")
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val imageUrl = cleanImages[page]
            Box(modifier = Modifier.fillMaxSize()) {
                val slideRequest = remember(imageUrl) {
                    buildOptimizedImageRequest(context, imageUrl, 1280, 720)
                }
                AsyncImage(
                    model = slideRequest,
                    contentDescription = "$movieTitle slide ${page + 1}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )

        // Bottom Deep Bleeding Gradient Effect (matching home screen hero banner)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            bgColor.copy(alpha = 0.6f),
                            bgColor
                        )
                    )
                )
        )

        // Floating Plus (+) Button when in edit mode (Screenshot 531)
        if (isEditingMode) {
            Surface(
                onClick = onAddOrEditClick,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 12.dp)
                    .testTag("slideshow_add_image_button")
            ) {
                Box(
                    modifier = Modifier.padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Manage Slideshow Images",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

