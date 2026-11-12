package com.example.data.local

data class SavedMediaEntity(
    val id: String, // e.g. "tt1375666"
    val title: String,
    val type: String, // "movie" or "series"
    val poster: String?,
    val backdrop: String?,
    val year: String?,
    val imdbRating: String?,
    val genres: String?,
    val description: String?,
    val customOrderIndex: Int,
    val addedTimestamp: Long = System.currentTimeMillis()
)
