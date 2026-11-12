package com.example.data.repository

import com.example.data.local.SavedMediaDao
import com.example.data.local.SavedMediaEntity
import com.example.data.model.EpisodeItem
import com.example.data.model.StremioCatalogMeta
import com.example.data.model.StremioStream
import com.example.data.pref.UserPreferences
import com.example.data.remote.StremioClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.flow

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@kotlinx.serialization.Serializable
data class CategorySection(
    val id: String,
    val title: String,
    val platformName: String,
    val type: String,
    val items: List<StremioCatalogMeta>
)

class MediaRepository(
    private val client: StremioClient,
    private val savedDao: SavedMediaDao,
    private val userPreferences: UserPreferences
) {
    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var cachedCategories: List<CategorySection>? = null

    private val movieDetailsCache = java.util.concurrent.ConcurrentHashMap<String, StremioCatalogMeta>()
    private val customStreamsMap = java.util.concurrent.ConcurrentHashMap<String, List<StremioStream>>()
    private val customMetaMap = java.util.concurrent.ConcurrentHashMap<String, StremioCatalogMeta>()

    fun hideStream(id: String, stream: StremioStream) {
        val key = "${stream.streamerNumber}_${stream.name}_${stream.title}_${stream.playableUrl}"
        userPreferences.hideStream(id, key)
    }

    fun isStreamHidden(id: String, stream: StremioStream): Boolean {
        val key = "${stream.streamerNumber}_${stream.name}_${stream.title}_${stream.playableUrl}"
        val hiddenForId = userPreferences.getHiddenStreams(id)
        if (hiddenForId.contains(key)) return true
        if (id.contains(":")) {
            val hiddenForParent = userPreferences.getHiddenStreams(id.substringBefore(":"))
            if (hiddenForParent.contains(key)) return true
        }
        return false
    }

    fun saveCustomHomeCategories(categories: List<CategorySection>) {
        cachedCategories = categories
        runCatching {
            val json = jsonParser.encodeToString(categories)
            userPreferences.saveCustomHomeCategories(json)
        }
    }

    fun getCustomHomeCategories(): List<CategorySection>? {
        cachedCategories?.let { return it }
        val saved = userPreferences.getCustomHomeCategories() ?: return null
        return runCatching {
            jsonParser.decodeFromString<List<CategorySection>>(saved)
        }.getOrNull()?.also { cachedCategories = it }
    }

    fun saveCustomHomeHeroes(heroes: List<StremioCatalogMeta>) {
        runCatching {
            val json = jsonParser.encodeToString(heroes)
            userPreferences.saveCustomHomeHeroes(json)
        }
    }

    fun getCustomHomeHeroes(): List<StremioCatalogMeta>? {
        val saved = userPreferences.getCustomHomeHeroes() ?: return null
        return runCatching {
            jsonParser.decodeFromString<List<StremioCatalogMeta>>(saved)
        }.getOrNull()
    }

    fun saveCustomStreamEdits(id: String, streams: List<StremioStream>) {
        customStreamsMap[id] = streams
        runCatching {
            val json = jsonParser.encodeToString(streams)
            userPreferences.saveCustomStreams(id, json)
        }
    }

    fun getCustomStreamEdits(id: String): List<StremioStream>? {
        if (customStreamsMap.containsKey(id)) return customStreamsMap[id]
        val savedJson = userPreferences.getCustomStreams(id) ?: return null
        return runCatching {
            jsonParser.decodeFromString<List<StremioStream>>(savedJson)
        }.getOrNull()?.also {
            customStreamsMap[id] = it
        }
    }

    fun saveCustomMetaEdits(meta: StremioCatalogMeta) {
        customMetaMap[meta.id] = meta
        customMetaMap[meta.displayImdbId] = meta
        val norm = meta.name.trim().lowercase()
        if (norm.isNotBlank()) {
            customMetaMap["name_$norm"] = meta
        }
        movieDetailsCache[meta.id] = meta
        movieDetailsCache[meta.displayImdbId] = meta
        runCatching {
            val json = jsonParser.encodeToString(meta)
            userPreferences.saveCustomMeta(meta.id, json)
            userPreferences.saveCustomMeta(meta.displayImdbId, json)
            if (norm.isNotBlank()) {
                userPreferences.saveCustomMeta("name_$norm", json)
            }
        }
    }

    fun getCustomMeta(id: String): StremioCatalogMeta? {
        if (customMetaMap.containsKey(id)) return customMetaMap[id]
        val savedJson = userPreferences.getCustomMeta(id) ?: return null
        return runCatching {
            jsonParser.decodeFromString<StremioCatalogMeta>(savedJson)
        }.getOrNull()?.also {
            customMetaMap[id] = it
            movieDetailsCache[id] = it
        }
    }

    fun getCustomMetaByName(name: String): StremioCatalogMeta? {
        val norm = name.trim().lowercase()
        if (norm.isBlank()) return null
        val key = "name_$norm"
        if (customMetaMap.containsKey(key)) return customMetaMap[key]
        val savedJson = userPreferences.getCustomMeta(key) ?: return null
        return runCatching {
            jsonParser.decodeFromString<StremioCatalogMeta>(savedJson)
        }.getOrNull()?.also {
            customMetaMap[key] = it
            movieDetailsCache[key] = it
        }
    }

    suspend fun updateSavedMediaImage(id: String, displayImdbId: String, title: String, newImageUrl: String) {
        savedDao.updateImageByIdOrTitle(id, displayImdbId, title, newImageUrl)
    }

    /**
     * Applies any custom edited poster, background, or title from user customizations.
     * Also guarantees that any title with missing poster/background instantly gets valid Metahub URLs.
     */
    fun applyCustomEditsToMeta(meta: StremioCatalogMeta): StremioCatalogMeta {
        val custom = getCustomMeta(meta.id)
            ?: getCustomMeta(meta.displayImdbId)
            ?: getCustomMetaByName(meta.name)
        val customSlideshow = getCustomSlideshow(meta.id)
            ?: getCustomSlideshow(meta.displayImdbId)
            ?: getCustomSlideshow("name_${meta.name.trim().lowercase()}")

        val base = if (custom != null) {
            meta.copy(
                name = custom.name.takeIf { it.isNotBlank() } ?: meta.name,
                poster = custom.poster?.takeIf { it.isNotBlank() } ?: meta.poster,
                background = custom.background?.takeIf { it.isNotBlank() } ?: meta.background,
                year = custom.year ?: meta.year,
                imdbRating = custom.imdbRating ?: meta.imdbRating,
                description = custom.description ?: meta.description,
                genre = custom.genre ?: meta.genre
            )
        } else meta

        val withSlide = if (!customSlideshow.isNullOrEmpty()) {
            val firstImg = customSlideshow.first()
            base.copy(
                poster = custom?.poster?.takeIf { it.isNotBlank() } ?: firstImg,
                background = custom?.background?.takeIf { it.isNotBlank() } ?: firstImg
            )
        } else base

        // Guarantee instant poster fetching: If poster or background is missing, derive it directly from displayImdbId
        val imdbId = withSlide.displayImdbId.takeIf { it.startsWith("tt") }
        val resolvedPoster = withSlide.poster?.takeIf { it.isNotBlank() }
            ?: if (imdbId != null) "https://images.metahub.space/poster/medium/$imdbId/img" else withSlide.background
        val resolvedBackground = withSlide.background?.takeIf { it.isNotBlank() && it != withSlide.poster }
            ?: if (imdbId != null) "https://images.metahub.space/background/medium/$imdbId/img" else resolvedPoster

        return withSlide.copy(
            poster = resolvedPoster,
            background = resolvedBackground
        )
    }

    fun saveHeroCustomImage(hero: StremioCatalogMeta, newImageUrl: String): StremioCatalogMeta {
        val updated = hero.copy(
            poster = newImageUrl,
            background = newImageUrl
        )
        saveCustomMetaEdits(updated)
        saveCustomSlideshow(updated.id, listOf(newImageUrl))
        saveCustomSlideshow(updated.displayImdbId, listOf(newImageUrl))
        val norm = updated.name.trim().lowercase()
        if (norm.isNotBlank()) {
            saveCustomSlideshow("name_$norm", listOf(newImageUrl))
        }

        // Update in cachedCategories and persist so catalog shows updated image everywhere
        val baseCats = cachedCategories ?: getCustomHomeCategories()
        if (baseCats != null) {
            val updatedCats = baseCats.map { cat ->
                cat.copy(
                    items = cat.items.map { item ->
                        if (item.id == hero.id || item.displayImdbId == hero.displayImdbId || item.name.equals(hero.name, ignoreCase = true)) {
                            item.copy(poster = newImageUrl, background = newImageUrl)
                        } else item
                    }
                )
            }
            cachedCategories = updatedCats
            saveCustomHomeCategories(updatedCats)
        }
        return updated
    }

    fun saveCustomSlideshow(id: String, images: List<String>) {
        runCatching {
            val json = jsonParser.encodeToString(images)
            userPreferences.saveCustomSlideshow(id, json)
        }
    }

    fun getCustomSlideshow(id: String): List<String>? {
        val savedJson = userPreferences.getCustomSlideshow(id) ?: return null
        return runCatching {
            jsonParser.decodeFromString<List<String>>(savedJson)
        }.getOrNull()
    }

    fun restoreMediaOriginal(id: String, displayImdbId: String) {
        customStreamsMap.remove(id)
        customStreamsMap.remove(displayImdbId)
        customMetaMap.remove(id)
        customMetaMap.remove(displayImdbId)
        val norm = id.trim().lowercase()
        customMetaMap.remove("name_$norm")
        movieDetailsCache.remove(id)
        movieDetailsCache.remove(displayImdbId)
        movieDetailsCache.remove("name_$norm")
        userPreferences.clearCustomStreams(id)
        userPreferences.clearCustomStreams(displayImdbId)
        userPreferences.clearCustomMeta(id)
        userPreferences.clearCustomMeta(displayImdbId)
        userPreferences.clearCustomMeta("name_$norm")
        userPreferences.clearCustomSlideshow(id)
        userPreferences.clearCustomSlideshow(displayImdbId)
        userPreferences.clearCustomSlideshow("name_$norm")
        userPreferences.clearHiddenStreams(id)
        userPreferences.clearHiddenStreams(displayImdbId)
    }

    fun restoreHomeOriginal() {
        cachedCategories = null
        userPreferences.clearCustomHomeCategories()
        userPreferences.clearCustomHomeHeroes()
    }

    private fun createDefaultEpisodes(seriesId: String, seasonsMap: Map<Int, Int>, seriesTitle: String, thumb: String?): List<EpisodeItem> {
        val list = mutableListOf<EpisodeItem>()
        seasonsMap.forEach { (season, count) ->
            for (ep in 1..count) {
                list.add(
                    EpisodeItem(
                        id = "$seriesId:$season:$ep",
                        name = "Episode $ep",
                        season = season,
                        number = ep,
                        overview = "$seriesTitle - Season $season, Episode $ep.",
                        thumbnail = thumb,
                        released = "2024"
                    )
                )
            }
        }
        return list
    }

    fun getPermanentTop8HeroList(): List<StremioCatalogMeta> {
        return listOf(
            StremioCatalogMeta(
                id = "tt3909224",
                name = "The Seven Deadly Sins",
                type = "series",
                poster = "poster_seven_deadly_sins",
                background = "poster_seven_deadly_sins",
                year = "2014",
                imdbRating = "7.8",
                genre = listOf("Anime", "Action", "Adventure"),
                description = "The Seven Deadly Sins are a band of knights in the land of Britannia who disbanded ten years earlier after being framed for plotting a coup against the Liones Kingdom. Princess Elizabeth embarks on a journey to find the Sins to help reclaim her kingdom.",
                videos = createDefaultEpisodes("tt3909224", mapOf(1 to 24, 2 to 24, 3 to 24, 4 to 24), "The Seven Deadly Sins", "poster_seven_deadly_sins")
            ),
            StremioCatalogMeta(
                id = "tt2632424",
                name = "The Originals",
                type = "series",
                poster = "poster_the_originals",
                background = "poster_the_originals",
                year = "2013",
                imdbRating = "8.3",
                genre = listOf("Drama", "Fantasy", "Horror"),
                description = "A family of power-hungry thousand-year-old vampires look to take back the city that they built and dominate all those who have done them wrong in the supernatural cauldron of New Orleans.",
                videos = createDefaultEpisodes("tt2632424", mapOf(1 to 22, 2 to 22, 3 to 22, 4 to 13, 5 to 13), "The Originals", "poster_the_originals")
            ),
            StremioCatalogMeta(
                id = "tt21209876",
                name = "Solo Leveling",
                type = "series",
                poster = "poster_solo_leveling",
                background = "poster_solo_leveling",
                year = "2024",
                imdbRating = "8.4",
                genre = listOf("Anime", "Action", "Fantasy"),
                description = "In a world where hunters, humans who possess magical powers, must battle deadly monsters to protect mankind from annihilation, a notoriously weak hunter named Sung Jinwoo finds himself in a seamless struggle for survival inside a mysterious double dungeon.",
                videos = createDefaultEpisodes("tt21209876", mapOf(1 to 12), "Solo Leveling", "poster_solo_leveling")
            ),
            StremioCatalogMeta(
                id = "tt1190080",
                name = "2012",
                type = "movie",
                poster = "poster_2012",
                background = "poster_2012",
                year = "2009",
                imdbRating = "5.9",
                genre = listOf("Action", "Adventure", "Sci-Fi"),
                description = "Dr. Adrian Helmsley, part of a worldwide geophysical team investigating the effect on Earth of radiation from unprecedented solar storms, learns that the Earth's core is heating up. He warns U.S. President Thomas Wilson that the crust of the planet is becoming unstable, leading to a catastrophic global apocalypse."
            ),
            StremioCatalogMeta(
                id = "tt1663662",
                name = "Pacific Rim",
                type = "movie",
                poster = "poster_pacific_rim",
                background = "poster_pacific_rim",
                year = "2013",
                imdbRating = "6.9",
                genre = listOf("Action", "Adventure", "Sci-Fi"),
                description = "When legions of monstrous creatures, known as Kaiju, started rising from the sea, a war began that would take millions of lives and consume humanity's resources. To combat the giant Kaiju, human scientists designed colossal robots called Jaegers."
            ),
            StremioCatalogMeta(
                id = "tt0409591",
                name = "Naruto",
                type = "series",
                poster = "poster_naruto",
                background = "poster_naruto",
                year = "2002",
                imdbRating = "8.4",
                genre = listOf("Anime", "Action", "Adventure"),
                description = "Naruto Uzumaki, a hyperactive and knuckle-headed ninja, lives in Konohagakure, the Hidden Leaf Village. Moments before his birth, a huge demon known as the Nine-Tailed Fox attacked the village and caused havoc, forcing the leader to seal the spirit inside newborn Naruto.",
                videos = createDefaultEpisodes("tt0409591", mapOf(1 to 52, 2 to 52, 3 to 53, 4 to 38, 5 to 25), "Naruto", "poster_naruto")
            ),
            StremioCatalogMeta(
                id = "tt12343534",
                name = "Jujutsu Kaisen",
                type = "series",
                poster = "poster_jujutsu_kaisen",
                background = "poster_jujutsu_kaisen",
                year = "2020",
                imdbRating = "8.6",
                genre = listOf("Anime", "Action", "Supernatural"),
                description = "Yuji Itadori, a kind-hearted teenager, joins his school's Occult Club for fun, but discovers that its members are actual sorcerers who can manipulate energy between beings for their own use. He swallows a cursed finger to protect his friends and becomes possessed by Ryomen Sukuna, the King of Curses.",
                videos = createDefaultEpisodes("tt12343534", mapOf(1 to 24, 2 to 23, 3 to 24, 4 to 24), "Jujutsu Kaisen", "poster_jujutsu_kaisen")
            ),
            StremioCatalogMeta(
                id = "tt4574334",
                name = "Stranger Things",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt4574334/img",
                background = "https://images.metahub.space/background/medium/tt4574334/img",
                year = "2016",
                imdbRating = "8.7",
                genre = listOf("Sci-Fi", "Drama", "Fantasy", "Mystery"),
                description = "When a young boy vanishes, a small town uncovers a mystery involving secret experiments, terrifying supernatural forces and one strange little girl in Hawkins, Indiana.",
                videos = createDefaultEpisodes("tt4574334", mapOf(1 to 8, 2 to 9, 3 to 8, 4 to 9), "Stranger Things", "https://images.metahub.space/poster/medium/tt4574334/img")
            )
        )
    }

    /**
     * Strictly detects if a title is a documentary, docuseries, biography, autobiography, or biopic
     */
    fun isDocumentaryOrAutobiography(item: StremioCatalogMeta): Boolean {
        val genres = item.genre?.map { it.lowercase().trim() } ?: emptyList()
        val name = item.name.lowercase().trim()
        val desc = (item.description ?: "").lowercase().trim()

        val forbiddenKeywords = listOf(
            "documentary",
            "docuseries",
            "docu-series",
            "autobiography",
            "biography",
            "biopic",
            "memoir",
            "true crime"
        )

        if (genres.any { g ->
            g.contains("documentary") || g.contains("biography") || g.contains("autobiography") || g.contains("docuseries")
        }) {
            return true
        }

        return forbiddenKeywords.any { kw ->
            genres.any { it.contains(kw) } || name.contains(kw) || desc.contains(kw)
        }
    }

    /**
     * Builds a 20-item Hero Slideshow for the home screen that:
     * 1. Strictly EXCLUDES any documentary or autobiography/biography.
     * 2. Showcases movies and series from EACH AND EVERY category.
     * 3. Maximizes genre diversity across the carousel.
     */
    fun build20HeroList(categories: List<CategorySection>): List<StremioCatalogMeta> {
        val savedHeroes = getCustomHomeHeroes()
        if (savedHeroes != null && savedHeroes.isNotEmpty()) {
            val cleanSaved = savedHeroes.filterNot { isDocumentaryOrAutobiography(it) }
            if (cleanSaved.isNotEmpty()) {
                return cleanSaved.take(20)
            }
        }

        val permanentList = getPermanentTop8HeroList().filterNot { isDocumentaryOrAutobiography(it) }
        val selectedIds = mutableSetOf<String>()
        val selectedGenres = mutableSetOf<String>()
        val full20List = mutableListOf<StremioCatalogMeta>()

        // Helper to format high-res metahub background only when not customized
        fun formatHeroMeta(cand: StremioCatalogMeta): StremioCatalogMeta {
            val bg = cand.background.orEmpty()
            val isCustom = bg.startsWith("http://", true) || bg.startsWith("https://", true) || bg.startsWith("file://", true) || bg.startsWith("/") || bg.startsWith("content://", true)
            return if (!isCustom && (bg.isBlank() || bg == cand.poster) && cand.displayImdbId.startsWith("tt")) {
                cand.copy(background = "https://images.metahub.space/background/medium/${cand.displayImdbId}/img")
            } else cand
        }

        // Add curated permanent blockbusters with user custom edits applied
        for (item in permanentList) {
            val edited = applyCustomEditsToMeta(item)
            val formatted = formatHeroMeta(edited)
            full20List.add(formatted)
            selectedIds.add(formatted.id)
            selectedIds.add(formatted.displayImdbId)
            formatted.genre?.firstOrNull()?.let { selectedGenres.add(it.lowercase()) }
        }

        if (categories.isNotEmpty()) {
            // PASS 1: Guarantee 1 top-tier item from EACH AND EVERY category
            // prioritizing diverse, unseen genres and strictly excluding documentaries
            for (cat in categories) {
                if (full20List.size >= 20) break

                val validCandidates = cat.items
                    .filter { it.id !in selectedIds && !isDocumentaryOrAutobiography(it) }

                // Prefer candidate with a genre not yet heavily represented in the slideshow
                val bestCandidate = validCandidates.firstOrNull { cand ->
                    val primaryGenre = cand.genre?.firstOrNull()?.lowercase().orEmpty()
                    primaryGenre.isNotBlank() && primaryGenre !in selectedGenres
                } ?: validCandidates.maxByOrNull {
                    (it.year?.toIntOrNull() ?: 0) * 10 + (it.imdbRating?.toDoubleOrNull()?.times(10)?.toInt() ?: 0)
                }

                if (bestCandidate != null) {
                    val formatted = formatHeroMeta(bestCandidate)
                    full20List.add(formatted)
                    selectedIds.add(formatted.id)
                    formatted.genre?.firstOrNull()?.let { selectedGenres.add(it.lowercase()) }
                }
            }

            // PASS 2: If we have remaining slots up to 20, cycle categories round-robin
            // picking movies and series of varied genres
            var round = 0
            while (full20List.size < 20 && round < 5) {
                var addedInRound = false
                for (cat in categories) {
                    if (full20List.size >= 20) break
                    val validCandidates = cat.items
                        .filter { it.id !in selectedIds && !isDocumentaryOrAutobiography(it) }
                        .sortedWith(
                            compareByDescending<StremioCatalogMeta> { cand ->
                                val g = cand.genre?.firstOrNull()?.lowercase().orEmpty()
                                if (g.isNotBlank() && g !in selectedGenres) 1 else 0
                            }.thenByDescending { it.imdbRating?.toDoubleOrNull() ?: 0.0 }
                             .thenByDescending { it.year?.toIntOrNull() ?: 0 }
                        )

                    val candidate = validCandidates.firstOrNull()
                    if (candidate != null) {
                        val formatted = formatHeroMeta(candidate)
                        full20List.add(formatted)
                        selectedIds.add(formatted.id)
                        formatted.genre?.firstOrNull()?.let { selectedGenres.add(it.lowercase()) }
                        addedInRound = true
                    }
                }
                if (!addedInRound) break
                round++
            }
        }

        // Curated backup blockbusters (100% non-documentary, diverse genres) in case categories were small or offline
        val backupCurated = listOf(
            StremioCatalogMeta(
                id = "tt0903747",
                name = "Breaking Bad",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt0903747/img",
                background = "https://images.metahub.space/background/medium/tt0903747/img",
                year = "2008",
                imdbRating = "9.5",
                genre = listOf("Crime", "Drama", "Thriller"),
                description = "A chemistry teacher diagnosed with inoperable lung cancer turns to manufacturing and selling methamphetamine with a former student in order to secure his family's future."
            ),
            StremioCatalogMeta(
                id = "tt0468569",
                name = "The Dark Knight",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt0468569/img",
                background = "https://images.metahub.space/background/medium/tt0468569/img",
                year = "2008",
                imdbRating = "9.0",
                genre = listOf("Action", "Crime", "Drama"),
                description = "When the menace known as the Joker wreaks havoc and chaos on Gotham, Batman must accept one of the greatest psychological and physical tests of his ability to fight injustice."
            ),
            StremioCatalogMeta(
                id = "tt1375666",
                name = "Inception",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt1375666/img",
                background = "https://images.metahub.space/background/medium/tt1375666/img",
                year = "2010",
                imdbRating = "8.8",
                genre = listOf("Action", "Adventure", "Sci-Fi"),
                description = "A thief who steals corporate secrets through the use of dream-sharing technology is given the inverse task of planting an idea into the mind of a C.E.O."
            ),
            StremioCatalogMeta(
                id = "tt0816692",
                name = "Interstellar",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt0816692/img",
                background = "https://images.metahub.space/background/medium/tt0816692/img",
                year = "2014",
                imdbRating = "8.7",
                genre = listOf("Adventure", "Drama", "Sci-Fi"),
                description = "When Earth becomes uninhabitable in the future, a farmer and ex-NASA pilot, Joseph Cooper, is tasked to pilot a spacecraft, along with a team of researchers, to find a new planet for humans."
            ),
            StremioCatalogMeta(
                id = "tt2560140",
                name = "Attack on Titan",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt2560140/img",
                background = "https://images.metahub.space/background/medium/tt2560140/img",
                year = "2013",
                imdbRating = "9.1",
                genre = listOf("Anime", "Action", "Adventure"),
                description = "After his hometown is destroyed and his mother is killed, young Eren Jaeger vows to cleanse the earth of the giant humanoid Titans that have brought humanity to the brink of extinction."
            ),
            StremioCatalogMeta(
                id = "tt11198330",
                name = "House of the Dragon",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt11198330/img",
                background = "https://images.metahub.space/background/medium/tt11198330/img",
                year = "2022",
                imdbRating = "8.4",
                genre = listOf("Action", "Adventure", "Fantasy"),
                description = "An internal succession contest within House Targaryen at the height of its power, 172 years before the birth of Daenerys Targaryen."
            ),
            StremioCatalogMeta(
                id = "tt11126994",
                name = "Arcane",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt11126994/img",
                background = "https://images.metahub.space/background/medium/tt11126994/img",
                year = "2021",
                imdbRating = "9.0",
                genre = listOf("Animation", "Action", "Sci-Fi"),
                description = "Set in utopian Piltover and the oppressed underground of Zaun, the story follows the origins of two iconic League champions-and the power that will tear them apart."
            ),
            StremioCatalogMeta(
                id = "tt0133093",
                name = "The Matrix",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt0133093/img",
                background = "https://images.metahub.space/background/medium/tt0133093/img",
                year = "1999",
                imdbRating = "8.7",
                genre = listOf("Action", "Sci-Fi"),
                description = "When a beautiful stranger leads computer hacker Neo to a forbidding underworld, he discovers the shocking truth--the life he knows is the elaborate deception of an evil cyber-intelligence."
            ),
            StremioCatalogMeta(
                id = "tt3581920",
                name = "The Last of Us",
                type = "series",
                poster = "https://images.metahub.space/poster/medium/tt3581920/img",
                background = "https://images.metahub.space/background/medium/tt3581920/img",
                year = "2023",
                imdbRating = "8.8",
                genre = listOf("Action", "Adventure", "Drama"),
                description = "After a global pandemic destroys civilization, a hardened survivor takes charge of a 14-year-old girl who may be humanity's last hope."
            ),
            StremioCatalogMeta(
                id = "tt6751668",
                name = "Parasite",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt6751668/img",
                background = "https://images.metahub.space/background/medium/tt6751668/img",
                year = "2019",
                imdbRating = "8.5",
                genre = listOf("Drama", "Thriller"),
                description = "Greed and class discrimination threaten the newly formed symbiotic relationship between the wealthy Park family and the destitute Kim clan."
            ),
            StremioCatalogMeta(
                id = "tt1877830",
                name = "The Batman",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt1877830/img",
                background = "https://images.metahub.space/background/medium/tt1877830/img",
                year = "2022",
                imdbRating = "7.8",
                genre = listOf("Action", "Crime", "Mystery"),
                description = "When a sadistic serial killer begins murdering key political figures in Gotham, the Batman is forced to investigate the city's hidden corruption and question his family's involvement."
            ),
            StremioCatalogMeta(
                id = "tt6587046",
                name = "Spider-Man: Into the Spider-Verse",
                type = "movie",
                poster = "https://images.metahub.space/poster/medium/tt6587046/img",
                background = "https://images.metahub.space/background/medium/tt6587046/img",
                year = "2018",
                imdbRating = "8.4",
                genre = listOf("Animation", "Action", "Adventure"),
                description = "Teen Miles Morales becomes the new Spider-Man and joins other Spider-Heroes from parallel dimensions to stop a threat to all reality."
            )
        )

        for (item in backupCurated) {
            if (full20List.size >= 20) break
            if (item.id !in selectedIds && !isDocumentaryOrAutobiography(item)) {
                full20List.add(formatHeroMeta(item))
                selectedIds.add(item.id)
            }
        }

        return full20List.take(20)
    }

    fun getCategoriesFlow(forceRefresh: Boolean = false): Flow<List<CategorySection>> = flow {
        val saved = getCustomHomeCategories()
        if (!forceRefresh && !saved.isNullOrEmpty()) {
            val sanitizedSaved = saved.map { cat -> cat.copy(items = cat.items.map { applyCustomEditsToMeta(it) }) }
            cachedCategories = sanitizedSaved
            emit(sanitizedSaved)
            return@flow
        }

        if (!forceRefresh && !cachedCategories.isNullOrEmpty()) {
            val sanitizedCached = cachedCategories!!.map { cat -> cat.copy(items = cat.items.map { applyCustomEditsToMeta(it) }) }
            emit(sanitizedCached)
            return@flow
        }

        val accumulated = mutableListOf<CategorySection>()

        val visibleBatch = coroutineScope {
            val nfxMoviesDeferred = async { client.getCatalogItems("movie", "nfx").getOrDefault(emptyList()) }
            val nfxSeriesDeferred = async { client.getCatalogItems("series", "nfx").getOrDefault(emptyList()) }
            val nfxMovies = nfxMoviesDeferred.await()
            val nfxSeries = nfxSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (nfxMovies.isNotEmpty()) {
                list.add(CategorySection("nfx_movie", "Netflix — Popular Movies & Hits", "Netflix", "movie", nfxMovies.map { applyCustomEditsToMeta(it) }))
            }
            if (nfxSeries.isNotEmpty()) {
                list.add(CategorySection("nfx_series", "Netflix — Trending Series & Binge", "Netflix", "series", nfxSeries.map { applyCustomEditsToMeta(it) }))
            }
            list
        }

        if (visibleBatch.isNotEmpty()) {
            accumulated.addAll(visibleBatch)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }

        val batchA = coroutineScope {
            val atpMoviesDeferred = async { client.getCatalogItems("movie", "atp").getOrDefault(emptyList()) }
            val atpSeriesDeferred = async { client.getCatalogItems("series", "atp").getOrDefault(emptyList()) }
            val dnpMoviesDeferred = async { client.getCatalogItems("movie", "dnp").getOrDefault(emptyList()) }
            val dnpSeriesDeferred = async { client.getCatalogItems("series", "dnp").getOrDefault(emptyList()) }

            val atpMovies = atpMoviesDeferred.await()
            val atpSeries = atpSeriesDeferred.await()
            val dnpMovies = dnpMoviesDeferred.await()
            val dnpSeries = dnpSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (atpMovies.isNotEmpty()) {
                list.add(CategorySection("atp_movie", "Apple TV+ — Award-Winning Cinema", "Apple TV+", "movie", atpMovies.map { applyCustomEditsToMeta(it) }))
            }
            if (atpSeries.isNotEmpty()) {
                list.add(CategorySection("atp_series", "Apple TV+ — Acclaimed Original Series", "Apple TV+", "series", atpSeries.map { applyCustomEditsToMeta(it) }))
            }
            if (dnpMovies.isNotEmpty()) {
                list.add(CategorySection("dnp_movie", "Disney+ — Premier Blockbusters & Animation", "Disney+", "movie", dnpMovies.map { applyCustomEditsToMeta(it) }))
            }
            if (dnpSeries.isNotEmpty()) {
                list.add(CategorySection("dnp_series", "Disney+ — Hit Franchises & Series", "Disney+", "series", dnpSeries.map { applyCustomEditsToMeta(it) }))
            }
            list
        }

        if (batchA.isNotEmpty()) {
            accumulated.addAll(batchA)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }

        val batchB = coroutineScope {
            val ampMoviesDeferred = async { client.getCatalogItems("movie", "amp").getOrDefault(emptyList()) }
            val ampSeriesDeferred = async { client.getCatalogItems("series", "amp").getOrDefault(emptyList()) }
            val hbmMoviesDeferred = async { client.getCatalogItems("movie", "hbm").getOrDefault(emptyList()) }
            val hbmSeriesDeferred = async { client.getCatalogItems("series", "hbm").getOrDefault(emptyList()) }

            val ampMovies = ampMoviesDeferred.await()
            val ampSeries = ampSeriesDeferred.await()
            val hbmMovies = hbmMoviesDeferred.await()
            val hbmSeries = hbmSeriesDeferred.await()

            val list = mutableListOf<CategorySection>()
            if (ampMovies.isNotEmpty()) {
                list.add(CategorySection("amp_movie", "Prime Video — Top Movies & Blockbusters", "Prime Video", "movie", ampMovies.map { applyCustomEditsToMeta(it) }))
            }
            if (ampSeries.isNotEmpty()) {
                list.add(CategorySection("amp_series", "Prime Video — Exclusive Prime Series", "Prime Video", "series", ampSeries.map { applyCustomEditsToMeta(it) }))
            }
            if (hbmMovies.isNotEmpty()) {
                list.add(CategorySection("hbm_movie", "HBO Max — Box Office Hits & Cinema", "HBO Max", "movie", hbmMovies.map { applyCustomEditsToMeta(it) }))
            }
            if (hbmSeries.isNotEmpty()) {
                list.add(CategorySection("hbm_series", "HBO Max — Acclaimed Prestige Drama", "HBO Max", "series", hbmSeries.map { applyCustomEditsToMeta(it) }))
            }
            list
        }

        if (batchB.isNotEmpty()) {
            accumulated.addAll(batchB)
            cachedCategories = accumulated.toList()
            emit(accumulated.toList())
        }
    }

    private val permanentHeroMap: Map<String, StremioCatalogMeta> by lazy {
        getPermanentTop8HeroList().associateBy { it.id }
    }

    suspend fun prefetchMovieDetails(items: List<StremioCatalogMeta>) = withContext(Dispatchers.IO) {
        items.take(8).forEach { item ->
            if (!movieDetailsCache.containsKey(item.id)) {
                val res = client.getMovieDetails(item.type, item.displayImdbId)
                res.getOrNull()?.let { movieDetailsCache[item.id] = it }
            }
        }
    }

    fun getCachedMovieDetails(id: String): StremioCatalogMeta? {
        val custom = getCustomMeta(id) ?: getCustomMetaByName(id)
        if (custom != null) return custom
        val cached = movieDetailsCache[id]
        if (cached != null) return applyCustomEditsToMeta(cached)
        val perm = permanentHeroMap[id]
        if (perm != null) return applyCustomEditsToMeta(perm)
        return null
    }

    suspend fun getMovieDetails(type: String, id: String): Result<StremioCatalogMeta> {
        val custom = getCustomMeta(id) ?: getCustomMetaByName(id)
        val permItem = permanentHeroMap[id]

        var remote = movieDetailsCache[id]
        if (remote == null) {
            val remoteRes = client.getMovieDetails(type, id)
            remote = remoteRes.getOrNull()
        }

        // If remote has no videos and this is a series, query Cinemeta search fallback to get real series with full seasons and authentic episodes
        val seriesTitle = custom?.name ?: permItem?.name ?: remote?.name
        val isSeries = type.equals("series", ignoreCase = true) || permItem?.type == "series" || remote?.type == "series"
        if (isSeries && (remote == null || remote.videos.isNullOrEmpty()) && !seriesTitle.isNullOrBlank()) {
            val searchRes = client.searchCatalog(seriesTitle, "series").getOrNull()
            val matched = searchRes?.firstOrNull { it.name.equals(seriesTitle, ignoreCase = true) }
                ?: searchRes?.firstOrNull()
            if (matched != null) {
                val detailRes = client.getMovieDetails(matched.type, matched.id).getOrNull()
                if (detailRes != null && !detailRes.videos.isNullOrEmpty()) {
                    remote = detailRes
                }
            }
        }

        val baseMeta = remote ?: permItem ?: custom
        if (baseMeta != null) {
            val combinedVideos = remote?.videos?.takeIf { it.isNotEmpty() }
                ?: permItem?.videos?.takeIf { it.isNotEmpty() }
                ?: custom?.videos

            // Ensure episodes have real Metahub thumbnails if default or missing
            val cleanImdb = (remote?.imdbId ?: permItem?.imdbId ?: id).trim()
            val enrichedVideos = combinedVideos?.map { ep ->
                val epThumb = ep.thumbnail?.takeIf { it.isNotBlank() && !it.startsWith("poster_") }
                    ?: if (cleanImdb.startsWith("tt")) "https://episodes.metahub.space/$cleanImdb/${ep.season}/${ep.number}/w500.jpg"
                    else "https://images.metahub.space/episode/${ep.id}/img"
                ep.copy(thumbnail = epThumb)
            }

            val finalMeta = baseMeta.copy(
                id = custom?.id ?: permItem?.id ?: baseMeta.id,
                imdbId = baseMeta.imdbId ?: permItem?.imdbId,
                name = custom?.name?.takeIf { it.isNotBlank() } ?: permItem?.name ?: baseMeta.name,
                type = permItem?.type ?: baseMeta.type,
                poster = custom?.poster?.takeIf { it.isNotBlank() } ?: permItem?.poster ?: baseMeta.poster,
                background = custom?.background?.takeIf { it.isNotBlank() } ?: permItem?.background ?: baseMeta.background,
                description = custom?.description ?: permItem?.description ?: baseMeta.description,
                year = custom?.year ?: permItem?.year ?: baseMeta.year,
                imdbRating = custom?.imdbRating ?: permItem?.imdbRating ?: baseMeta.imdbRating,
                genre = custom?.genre ?: permItem?.genre ?: baseMeta.genre,
                videos = enrichedVideos
            )
            val withCustom = applyCustomEditsToMeta(finalMeta)
            movieDetailsCache[id] = withCustom
            return Result.success(withCustom)
        }
        return Result.failure(Exception("Metadata not found"))
    }

    suspend fun resolveTrailerInfo(meta: StremioCatalogMeta): com.example.data.model.TrailerResolutionInfo = withContext(Dispatchers.IO) {
        client.resolveTrailerDetailed(
            imdbId = meta.displayImdbId,
            title = meta.name,
            year = meta.year,
            type = meta.type,
            metaTrailers = meta.trailers ?: emptyList()
        )
    }

    suspend fun resolveTrailerId(meta: StremioCatalogMeta): String? = withContext(Dispatchers.IO) {
        val info = resolveTrailerInfo(meta)
        info.youtubeId
    }

    fun getStreamsFlow(type: String, id: String): Flow<List<StremioStream>> = flow {
        val custom = getCustomStreamEdits(id)
        if (custom != null) {
            val filtered = custom.filterNot { isStreamHidden(id, it) }
            if (filtered.isNotEmpty()) {
                emit(filtered)
            }
        }
        val profile = userPreferences.userProfile.value
        val providers = userPreferences.getStreamProviders()
        client.getStreamsProgressive(
            type = type,
            id = id,
            providers = providers,
            hdhubEnabled = profile.hdhubEnabled,
            penguEnabled = profile.penguEnabled
        ).collect { streams ->
            val updatedCustom = getCustomStreamEdits(id)?.filterNot { isStreamHidden(id, it) } ?: emptyList()
            val filteredStreams = streams.filterNot { isStreamHidden(id, it) }
            val merged = (updatedCustom + filteredStreams).distinctBy { it.playableUrl }
            emit(merged)
        }
    }

    fun getStreamsProgressive(type: String, id: String): Flow<List<StremioStream>> = getStreamsFlow(type, id)

    suspend fun search(query: String, type: String = "movie"): Result<List<StremioCatalogMeta>> = withContext(Dispatchers.IO) {
        if (type.equals("all", ignoreCase = true)) {
            coroutineScope {
                val moviesDeferred = async { client.searchCatalog(query, "movie").getOrDefault(emptyList()) }
                val seriesDeferred = async { client.searchCatalog(query, "series").getOrDefault(emptyList()) }
                val movies = moviesDeferred.await()
                val series = seriesDeferred.await()
                val merged = (movies + series).distinctBy { it.displayImdbId }
                Result.success(merged)
            }
        } else {
            client.searchCatalog(query, type)
        }
    }

    fun observeSavedMedia(sortOrder: String = "custom"): Flow<List<SavedMediaEntity>> {
        return when {
            sortOrder.endsWith("_custom") || sortOrder == "custom" -> savedDao.getAllByCustomOrder()
            sortOrder == "date_desc" -> savedDao.getAllByDateDesc()
            sortOrder == "date_asc" -> savedDao.getAllByDateAsc()
            sortOrder == "title_asc" -> savedDao.getAllByTitleAsc()
            sortOrder == "rating_desc" -> savedDao.getAllByRatingDesc()
            sortOrder == "rating_asc" -> savedDao.getAllByRatingAsc()
            else -> savedDao.getAllByCustomOrder()
        }
    }

    fun isSaved(id: String, displayImdbId: String = id, title: String = ""): Flow<Boolean> =
        savedDao.isSavedFlow(id, displayImdbId, title)

    suspend fun toggleSave(meta: StremioCatalogMeta): Boolean {
        val exists = savedDao.exists(meta.id, meta.displayImdbId, meta.name)
        if (exists) {
            savedDao.deleteByIdOrTitle(meta.id, meta.displayImdbId, meta.name)
            return false
        } else {
            savedDao.shiftCustomOrderIndices()
            saveMedia(meta, 0)
            return true
        }
    }

    suspend fun saveMedia(meta: StremioCatalogMeta, customOrder: Int = 0) {
        movieDetailsCache[meta.id] = meta
        movieDetailsCache[meta.displayImdbId] = meta
        val entity = SavedMediaEntity(
            id = meta.id,
            title = meta.name,
            type = meta.type,
            poster = meta.poster,
            backdrop = meta.background,
            year = meta.year,
            imdbRating = meta.imdbRating,
            description = meta.description,
            genres = meta.genre?.joinToString(", "),
            customOrderIndex = customOrder,
            addedTimestamp = System.currentTimeMillis()
        )
        savedDao.insert(entity)
    }

    suspend fun deleteSaved(id: String) {
        savedDao.deleteById(id)
    }

    suspend fun updateCustomOrder(items: List<SavedMediaEntity>) {
        val updated = items.mapIndexed { index, entity ->
            entity.copy(customOrderIndex = index)
        }
        savedDao.updateAll(updated)
    }
}
