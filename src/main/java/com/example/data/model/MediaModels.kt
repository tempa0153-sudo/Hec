package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaItem(
    val id: String,
    val type: String = "movie", // "movie" or "series"
    val title: String,
    val poster: String? = null,
    val banner: String? = null,
    val year: String? = null,
    val genre: List<String> = emptyList(),
    val description: String = "",
    val rating: String = "8.5",
    val trailerYtId: String? = null,
    val imdbId: String? = null,
    val platform: String = "All" // "Netflix", "Apple TV+", "HBO Max", "Disney+", "Prime Video"
)

@Serializable
data class CategorySection(
    val id: String,
    val title: String,
    val platform: String,
    val items: List<MediaItem>
)

@Serializable
data class StreamSource(
    val id: String,
    val streamerTag: String, // "HDHub4u", "PenguPlay"
    val streamerNumber: Int,  // 1 for HDHub, 2 for PenguPlay
    val resolution: String,   // "4K 2160p", "1080p FHD", "720p HD", etc.
    val url: String,
    val details: String = "",
    val isExternal: Boolean = false,
    val rawProviderHint: String? = null
) {
    val displayTitle: String
        get() = "$streamerTag • $resolution"
}

@Serializable
data class UserProfile(
    val username: String = "Cinema Fan",
    val profilePictureUri: String? = null,
    val accentColorHex: String = "#1A73E8" // Google Blue
)

@Serializable
data class SavedLibraryItem(
    val id: String,
    val mediaId: String,
    val title: String,
    val poster: String?,
    val type: String,
    val rating: String,
    val year: String?,
    val platform: String = "All",
    val customOrder: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)
