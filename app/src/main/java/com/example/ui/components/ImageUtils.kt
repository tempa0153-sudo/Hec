package com.example.ui.components

import android.content.Context
import coil.request.ImageRequest

fun getLocalDrawableRes(model: Any?): Int? {
    if (model == null) return null
    if (model is Int) return model
    val str = model.toString().trim()
    if (str.startsWith("http://", ignoreCase = true) ||
        str.startsWith("https://", ignoreCase = true) ||
        str.startsWith("file://", ignoreCase = true) ||
        str.startsWith("content://", ignoreCase = true) ||
        str.startsWith("/")) {
        return null
    }
    val lower = str.lowercase()
    val clean = lower.substringAfterLast("/").substringBefore(".")
    return when {
        clean == "poster_2012" || clean == "2012" || clean == "tt0998631" || clean == "tt1190080" -> com.example.R.drawable.poster_2012
        clean == "poster_seven_deadly_sins" || clean == "seven_deadly_sins" || clean == "tt3909224" -> com.example.R.drawable.poster_seven_deadly_sins
        clean == "poster_the_originals" || clean == "the_originals" || clean == "tt2632424" -> com.example.R.drawable.poster_the_originals
        clean == "poster_solo_leveling" || clean == "solo_leveling" || clean == "tt21209876" -> com.example.R.drawable.poster_solo_leveling
        clean == "poster_pacific_rim" || clean == "pacific_rim" || clean == "tt1663662" -> com.example.R.drawable.poster_pacific_rim
        clean == "poster_naruto" || clean == "naruto" || clean == "tt0409591" -> com.example.R.drawable.poster_naruto
        clean == "poster_jujutsu_kaisen" || clean == "jujutsu_kaisen" || clean == "tt10430968" || clean == "tt12343534" -> com.example.R.drawable.poster_jujutsu_kaisen
        clean == "poster_a_haunting" || clean == "a_haunting" -> com.example.R.drawable.poster_a_haunting
        else -> null
    }
}

fun persistPickedImage(context: Context, uri: android.net.Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return uri.toString()
        val dir = java.io.File(context.filesDir, "custom_images").apply { mkdirs() }
        val ext = context.contentResolver.getType(uri)?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "jpg"
        val dest = java.io.File(dir, "img_${System.currentTimeMillis()}_${(1000..9999).random()}.$ext")
        dest.outputStream().use { out -> inputStream.copyTo(out) }
        dest.absolutePath
    } catch (_: Exception) {
        uri.toString()
    }
}

fun resolveCoilData(context: Context, model: Any?): Any? {
    if (model == null) return null
    if (model is Int) return model
    if (model is java.io.File) return model

    val str = model.toString().trim()
    if (str.isBlank()) return null

    // Check direct static drawable mapping first (0ms, 100% reliable)
    val directRes = getLocalDrawableRes(str)
    if (directRes != null) return directRes

    // Check if it's a local file path saved from gallery picker
    if (str.startsWith("/") || str.startsWith("file://")) {
        val path = str.removePrefix("file://")
        val file = java.io.File(path)
        if (file.exists() && file.length() > 0) {
            return file
        }
    }

    val cleanName = when {
        str.startsWith("poster_") -> str.substringBefore(".")
        str.contains("poster_") -> str.substringAfterLast("/").substringBefore(".")
        else -> null
    }

    if (cleanName != null) {
        val resId = context.resources.getIdentifier(cleanName, "drawable", context.packageName)
        if (resId != 0) return resId
        val fallbackId = context.resources.getIdentifier(cleanName, "drawable", "com.example")
        if (fallbackId != 0) return fallbackId
    }
    return str
}

fun buildOptimizedImageRequest(
    context: Context,
    data: Any?,
    width: Int = 220,
    height: Int = 330,
    enableCrossfade: Boolean = false
): ImageRequest {
    val resolved = resolveCoilData(context, data)
    return ImageRequest.Builder(context)
        .data(resolved)
        .size(width, height)
        .precision(coil.size.Precision.INEXACT)
        .allowRgb565(true)
        .allowHardware(true)
        .crossfade(enableCrossfade)
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        .build()
}
