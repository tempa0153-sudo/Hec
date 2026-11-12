package com.example.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StremioManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val description: String? = null,
    val catalogs: List<StremioCatalogDefinition>? = emptyList()
)

@Serializable
data class StremioCatalogDefinition(
    val id: String,
    val type: String,
    val name: String
)

@Serializable
data class TrailerItem(
    val source: String? = null, // YouTube Video ID, e.g. "ZkN2M4ODdys"
    val type: String? = null
)

@Serializable
data class EpisodeItem(
    val id: String = "", // e.g. "tt0903747:1:1"
    val name: String = "",
    val season: Int = 1,
    val number: Int = 1,
    val overview: String? = null,
    val thumbnail: String? = null,
    val rating: String? = null,
    val released: String? = null
)

@Serializable
data class TrailerResolutionInfo(
    val youtubeId: String? = null,
    val source: String = "none", // "meta", "TMDB", "none"
    val tmdbHttpStatus: Int? = null,
    val candidateIds: List<String> = emptyList()
)

@Serializable
data class StremioCatalogMeta(
    val id: String = "",
    val name: String = "",
    val type: String = "movie",
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val year: String? = null,
    val imdbRating: String? = null,
    val genre: List<String>? = null,
    val runtime: String? = null,
    val director: List<String>? = null,
    val cast: List<String>? = null,
    val trailers: List<TrailerItem>? = null,
    val videos: List<EpisodeItem>? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    val trailerResolution: TrailerResolutionInfo? = null
) {
    val displayImdbId: String
        get() = imdbId?.takeIf { it.isNotBlank() } ?: id

    val youtubeTrailerId: String?
        get() = trailers?.firstOrNull { !it.source.isNullOrBlank() }?.source

    val allTrailerIds: List<String>
        get() = trailers?.mapNotNull { it.source?.trim() }?.filter { it.isNotBlank() }?.distinct() ?: emptyList()

    val seasons: List<Int>
        get() = videos?.map { it.season }?.distinct()?.sorted()?.filter { it > 0 } ?: emptyList()

    fun getEpisodesForSeason(season: Int): List<EpisodeItem> {
        return videos?.filter { it.season == season }?.sortedBy { it.number } ?: emptyList()
    }
}

@Serializable
data class CatalogResponse(
    val metas: List<StremioCatalogMeta> = emptyList()
)

@Serializable
data class MetaResponse(
    val meta: StremioCatalogMeta? = null
)

@Serializable
data class StremioStream(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val streamerNumber: Int = 1,
    val addonSource: String = "HDHub4u",
    val subProvider: String? = null
) {
    val cleanQuality: String
        get() {
            val text = "${name.orEmpty()} ${title.orEmpty()} ${description.orEmpty()}".lowercase()
            return when {
                text.contains("2160p") || text.contains("4k") || text.contains("uhd") -> "4K 2160p"
                text.contains("1080p") || text.contains("fhd") -> "1080p FHD"
                text.contains("720p") || text.contains("hd") -> "720p HD"
                text.contains("480p") || text.contains("sd") -> "480p SD"
                else -> "1080p FHD"
            }
        }

    val isBelow720p: Boolean
        get() {
            val text = "${name.orEmpty()} ${title.orEmpty()} ${description.orEmpty()}".lowercase()
            return text.contains("480p") || text.contains("360p") || text.contains("240p") ||
                    text.contains("camrip") || text.contains("hdcam") || text.contains("telesync") || text.contains("dvdrip")
        }

    val streamerDisplayTitle: String
        get() = "Streamer $streamerNumber • $cleanQuality"

    val isCustomStream: Boolean
        get() = subProvider?.contains("Custom", ignoreCase = true) == true ||
                addonSource.contains("Custom", ignoreCase = true) ||
                (url?.startsWith("content://") == true || url?.startsWith("file://") == true)

    val playableUrl: String?
        get() {
            if (!url.isNullOrBlank()) {
                return if (url.startsWith("magnet:", ignoreCase = true)) ensureMagnetTrackers(url) else url
            }
            if (!infoHash.isNullOrBlank()) {
                val dn = (title ?: name ?: "video").substringBefore("\n").trim()
                val encodedDn = java.net.URLEncoder.encode(dn, "UTF-8")
                return buildMagnetUrl(infoHash, encodedDn)
            }
            return null
        }
}

val DEFAULT_MAGNET_TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://tracker.bittor.pw:1337/announce",
    "udp://public.popcorn-tracker.org:6969/announce",
    "udp://tracker.dler.org:6969/announce",
    "udp://exodus.desync.com:6969/announce",
    "udp://open.demonii.com:1337/announce",
    "udp://explodie.org:6969/announce",
    "udp://9.rarbg.to:2920/announce",
    "udp://tracker.coppersurfer.tk:6969/announce",
    "http://tracker.openbittorrent.com:80/announce"
)

fun buildMagnetUrl(infoHash: String, encodedDn: String): String {
    val trParams = DEFAULT_MAGNET_TRACKERS.joinToString("") { tr ->
        "&tr=${java.net.URLEncoder.encode(tr, "UTF-8")}"
    }
    return "magnet:?xt=urn:btih:$infoHash&dn=$encodedDn$trParams"
}

fun ensureMagnetTrackers(rawMagnet: String): String {
    if (!rawMagnet.startsWith("magnet:", ignoreCase = true) || rawMagnet.contains("&tr=")) {
        return rawMagnet
    }
    val trParams = DEFAULT_MAGNET_TRACKERS.joinToString("") { tr ->
        "&tr=${java.net.URLEncoder.encode(tr, "UTF-8")}"
    }
    return "$rawMagnet$trParams"
}

@Serializable
data class StreamResponse(
    val streams: List<StremioStream>? = emptyList()
)

@Serializable
data class StreamProviderConfig(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true
)

