package com.example.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.data.model.StremioCatalogMeta
import com.example.ui.theme.ImdbGold

@Composable
fun HeroBanner(
    movies: List<StremioCatalogMeta>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onClickMovie: (StremioCatalogMeta) -> Unit,
    topContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentMovie = movies.getOrNull(currentIndex)
    val context = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(330.dp)
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .clickable { currentMovie?.let { onClickMovie(it) } }
            .testTag("hero_banner_container")
    ) {

        // Background Hero Poster - full-bleed HD banner filling the slideshow dimensions (Screenshot 171)
        Crossfade(
            targetState = currentMovie,
            animationSpec = tween(durationMillis = 400, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            label = "hero_crossfade"
        ) { movie ->
            val bg = movie?.background?.trim().orEmpty()
            val pos = movie?.poster?.trim().orEmpty()

            val isCustomBg = bg.isNotBlank() && (bg.startsWith("http://", true) || bg.startsWith("https://", true) || bg.startsWith("file://", true) || bg.startsWith("/") || bg.startsWith("content://", true))
            val isCustomPos = pos.isNotBlank() && (pos.startsWith("http://", true) || pos.startsWith("https://", true) || pos.startsWith("file://", true) || pos.startsWith("/") || pos.startsWith("content://", true))

            val localBgRes = if (isCustomBg || isCustomPos) null else (
                getLocalDrawableRes(movie?.background)
                    ?: getLocalDrawableRes(movie?.poster)
                    ?: getLocalDrawableRes(movie?.id)
                    ?: getLocalDrawableRes(movie?.displayImdbId)
            )

            val imageUrl = if (localBgRes != null) null
                else (bg.takeIf { it.isNotBlank() }
                    ?: pos.takeIf { it.isNotBlank() }
                    ?: if (movie?.displayImdbId?.startsWith("tt") == true) "https://images.metahub.space/background/medium/${movie.displayImdbId}/img"
                    else null)

            val resolvedBg = if (localBgRes != null) localBgRes else resolveCoilData(context, imageUrl)

            Box(modifier = Modifier.fillMaxSize()) {
                // If local bundled drawable exists and no custom image, render immediately with 0ms delay
                if (localBgRes != null) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = localBgRes),
                        contentDescription = movie?.name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (localBgRes == null && !imageUrl.isNullOrBlank()) {
                    val heroImageRequest = remember(imageUrl, resolvedBg) {
                        ImageRequest.Builder(context)
                            .data(resolvedBg)
                            .size(720, 405)
                            .precision(coil.size.Precision.INEXACT)
                            .allowRgb565(true)
                            .allowHardware(true)
                            .crossfade(200)
                            .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                            .build()
                    }
                    AsyncImage(
                        model = heroImageRequest,
                        contentDescription = movie?.name,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (localBgRes == null && resolvedBg !is Int) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF16181F))
                    )
                }
            }
        }

        val bgColor = MaterialTheme.colorScheme.background
        val gradientBrush = remember(bgColor) {
            Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.20f),
                    Color.Black.copy(alpha = 0.70f),
                    bgColor
                ),
                startY = 0f,
                endY = 1450f
            )
        }

        // Deep Gradient Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradientBrush)
        )

        // Ultra-subtle upper scrim allowing posters to draw clearly behind notification panel & status icons
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    )
                )
                .align(Alignment.TopCenter)
        )

        // Top bar overlay (Profile, Search, Greeting)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 10.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                .align(Alignment.TopCenter)
        ) {
            topContent()
        }

        // Hero Movie Content Info at bottom with High Contrast Text Shadows for light backdrops
        currentMovie?.let { movie ->
            val textShadow = remember { androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 14f) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Genre and Rating pill row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!movie.imdbRating.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E1E1E).copy(alpha = 0.92f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, ImdbGold.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = "IMDb rating",
                                    tint = ImdbGold,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = movie.imdbRating,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                                )
                            }
                        }
                    }

                    movie.year?.let {
                        Text(
                            text = it,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                        )
                    }

                    movie.genre?.firstOrNull()?.let {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                style = androidx.compose.ui.text.TextStyle(shadow = textShadow)
                            )
                        }
                    }
                }

                // Title
                Text(
                    text = movie.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge.copy(shadow = textShadow),
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Overview synopsis
                movie.description?.let { desc ->
                    Text(
                        text = desc,
                        color = Color.White.copy(alpha = 0.92f),
                        style = androidx.compose.ui.text.TextStyle(shadow = textShadow),
                        fontSize = 11.5.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

