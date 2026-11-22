package com.musicplayer.lytrix.ui.cinema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TmdbCinemaRepository(
    private val apiKey: String = DEFAULT_API_KEY,
    private val client: OkHttpClient = defaultClient,
) : CinemaRepository {

    companion object {
        // Standard TMDB v3 API Key for open-source / educational client usage
        const val DEFAULT_API_KEY = "4e44d9029b1270a757cddc766a1bcb63"
        private const val BASE_URL = "https://api.themoviedb.org/3"
        private const val IMAGE_BASE_POSTER = "https://image.tmdb.org/t/p/w500"
        private const val IMAGE_BASE_BACKDROP = "https://image.tmdb.org/t/p/w1280"
        private const val IMAGE_BASE_STILL = "https://image.tmdb.org/t/p/w500"
        private const val IMAGE_BASE_PROFILE = "https://image.tmdb.org/t/p/w185"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        private val GENRE_MAP = mapOf(
            28 to "Action",
            12 to "Adventure",
            16 to "Animation",
            35 to "Comedy",
            80 to "Crime",
            99 to "Documentary",
            18 to "Drama",
            10751 to "Family",
            14 to "Fantasy",
            36 to "History",
            27 to "Horror",
            10402 to "Music",
            9648 to "Mystery",
            10749 to "Romance",
            878 to "Sci-Fi",
            10770 to "TV Movie",
            53 to "Thriller",
            10752 to "War",
            37 to "Western",
            10759 to "Action & Adventure",
            10762 to "Kids",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics"
        )
    }

    private var cachedFeed: CinemaFeed? = null
    private val detailCache = ConcurrentHashMap<String, CinemaDetail>()
    private val seasonEpisodesCache = ConcurrentHashMap<String, List<Episode>>()

    override suspend fun loadFeed(): CinemaFeed = withContext(Dispatchers.IO) {
        cachedFeed?.let { return@withContext it }

        coroutineScope {
            // ---------------------------------------------------------------
            // Exactly 8 Movies Categories
            // ---------------------------------------------------------------
            val trendingMoviesDeferred = async { fetchItems("/trending/movie/week", CinemaKind.MOVIE) }
            val superheroMoviesDeferred = async { fetchItems("/discover/movie?with_genres=28,878&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val actionMoviesDeferred = async { fetchItems("/discover/movie?with_genres=28,53&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val sciFiMoviesDeferred = async { fetchItems("/discover/movie?with_genres=878&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val horrorMoviesDeferred = async { fetchItems("/discover/movie?with_genres=27,9648&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val bollywoodMoviesDeferred = async { fetchItems("/discover/movie?with_original_language=hi|te|ta&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val romcomMoviesDeferred = async { fetchItems("/discover/movie?with_genres=35,10749&sort_by=popularity.desc", CinemaKind.MOVIE) }
            val topRatedMoviesDeferred = async { fetchItems("/movie/top_rated", CinemaKind.MOVIE) }

            // ---------------------------------------------------------------
            // Exactly 8 TV Series Categories
            // ---------------------------------------------------------------
            val trendingTvDeferred = async { fetchItems("/trending/tv/week", CinemaKind.TV) }
            val mysteryTvDeferred = async { fetchItems("/discover/tv?with_genres=80,9648&sort_by=popularity.desc", CinemaKind.TV) }
            val fantasyTvDeferred = async { fetchItems("/discover/tv?with_genres=10765&sort_by=popularity.desc", CinemaKind.TV) }
            val koreanTvDeferred = async { fetchItems("/discover/tv?with_original_language=ko&sort_by=popularity.desc", CinemaKind.TV) }
            val mafiaTvDeferred = async { fetchItems("/discover/tv?with_genres=80,18&sort_by=popularity.desc", CinemaKind.TV) }
            val indianTvDeferred = async { fetchItems("/discover/tv?with_original_language=hi|te|ta&sort_by=popularity.desc", CinemaKind.TV) }
            val sitcomTvDeferred = async { fetchItems("/discover/tv?with_genres=35&sort_by=popularity.desc", CinemaKind.TV) }
            val topRatedTvDeferred = async { fetchItems("/tv/top_rated", CinemaKind.TV) }

            // ---------------------------------------------------------------
            // Exactly 6 Anime Categories
            // ---------------------------------------------------------------
            val animeTrendingDeferred = async { fetchItems("/discover/tv?with_genres=16&with_original_language=ja&sort_by=popularity.desc", CinemaKind.ANIME) }
            val animeShonenDeferred = async { fetchItems("/discover/tv?with_genres=16,10759&with_original_language=ja&sort_by=popularity.desc", CinemaKind.ANIME) }
            val animeIsekaiDeferred = async { fetchItems("/discover/tv?with_genres=16,10765&with_original_language=ja&sort_by=popularity.desc", CinemaKind.ANIME) }
            val animePsychologicalDeferred = async { fetchItems("/discover/tv?with_genres=16,9648&with_original_language=ja&sort_by=popularity.desc", CinemaKind.ANIME) }
            val animeRomanceDeferred = async { fetchItems("/discover/tv?with_genres=16,35&with_original_language=ja&sort_by=popularity.desc", CinemaKind.ANIME) }
            val animeMoviesDeferred = async { fetchItems("/discover/movie?with_genres=16&with_original_language=ja&sort_by=vote_average.desc&vote_count.gte=500", CinemaKind.ANIME) }

            // Await Movies
            val trendingMovies = trendingMoviesDeferred.await()
            val superheroMovies = superheroMoviesDeferred.await()
            val actionMovies = actionMoviesDeferred.await()
            val sciFiMovies = sciFiMoviesDeferred.await()
            val horrorMovies = horrorMoviesDeferred.await()
            val bollywoodMovies = bollywoodMoviesDeferred.await()
            val romcomMovies = romcomMoviesDeferred.await()
            val topRatedMovies = topRatedMoviesDeferred.await()

            // Await TV Series
            val trendingTv = trendingTvDeferred.await()
            val mysteryTv = mysteryTvDeferred.await()
            val fantasyTv = fantasyTvDeferred.await()
            val koreanTv = koreanTvDeferred.await()
            val mafiaTv = mafiaTvDeferred.await()
            val indianTv = indianTvDeferred.await()
            val sitcomTv = sitcomTvDeferred.await()
            val topRatedTv = topRatedTvDeferred.await()

            // Await Anime
            val animeTrending = animeTrendingDeferred.await()
            val animeShonen = animeShonenDeferred.await()
            val animeIsekai = animeIsekaiDeferred.await()
            val animePsychological = animePsychologicalDeferred.await()
            val animeRomance = animeRomanceDeferred.await()
            val animeMovies = animeMoviesDeferred.await()

            // Spotlight: Top items with valid high-resolution backdrop
            val spotlight = (trendingMovies.take(3) + trendingTv.take(3) + animeTrending.take(2))
                .filter { it.backdropUrl.isNotBlank() }
                .distinctBy { it.id }

            val rows = mutableListOf<CinemaRow>()

            // 1. Trending Headers (All view mix)
            if (trendingMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "trending_movies", title = "Trending Blockbusters Worldwide", items = trendingMovies))
            }
            if (trendingTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "trending_series", title = "Trending Global Series", items = trendingTv))
            }
            if (animeTrending.isNotEmpty()) {
                rows.add(CinemaRow(id = "trending_anime", title = "Trending & Top Airing Anime", items = animeTrending))
            }

            // 2. Action, Superhero & Shonen
            if (superheroMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "superhero_movies", title = "Superhero & Comic Universe", items = superheroMovies))
            }
            if (actionMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "action_movies", title = "Action Thriller & Heist", items = actionMovies))
            }
            if (animeShonen.isNotEmpty()) {
                rows.add(CinemaRow(id = "shonen_anime", title = "Action Shonen & Battles", items = animeShonen))
            }

            // 3. Sci-Fi, Fantasy & Isekai
            if (sciFiMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "scifi_movies", title = "Sci-Fi & Future Worlds", items = sciFiMovies))
            }
            if (fantasyTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "fantasy_series", title = "Epic Fantasy & Medieval Worlds", items = fantasyTv))
            }
            if (animeIsekai.isNotEmpty()) {
                rows.add(CinemaRow(id = "isekai_anime", title = "Isekai & Fantasy Adventures", items = animeIsekai))
            }

            // 4. Regional Hits (Bollywood, K-Drama, Indian OTT)
            if (bollywoodMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "bollywood_movies", title = "Bollywood & Indian Hits", items = bollywoodMovies))
            }
            if (koreanTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "korean_series", title = "Trending K-Dramas (Korean)", items = koreanTv))
            }
            if (indianTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "indian_series", title = "Indian OTT Original Series", items = indianTv))
            }

            // 5. Mystery, Crime & Dark/Psychological
            if (mysteryTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "mystery_series", title = "Mystery Suspense & Crime", items = mysteryTv))
            }
            if (mafiaTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "mafia_series", title = "Underworld Mafia & Cartels", items = mafiaTv))
            }
            if (animePsychological.isNotEmpty()) {
                rows.add(CinemaRow(id = "psychological_anime", title = "Dark Fantasy & Psychological", items = animePsychological))
            }
            if (horrorMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "horror_movies", title = "Horror & Supernatural", items = horrorMovies))
            }

            // 6. Romance, Comedy & Sitcoms
            if (romcomMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "romcom_movies", title = "Romantic Hits & Feel-Good Comedy", items = romcomMovies))
            }
            if (sitcomTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "sitcom_series", title = "Binge-Worthy Classic Sitcoms", items = sitcomTv))
            }
            if (animeRomance.isNotEmpty()) {
                rows.add(CinemaRow(id = "slice_of_life_anime", title = "Romance & Slice of Life", items = animeRomance))
            }

            // 7. Top Rated & Masterpieces
            if (topRatedMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "top_rated_movies", title = "Oscar & Top-Rated Masterpieces", items = topRatedMovies))
            }
            if (topRatedTv.isNotEmpty()) {
                rows.add(CinemaRow(id = "top_rated_series", title = "Top Rated Series of All Time", items = topRatedTv))
            }
            if (animeMovies.isNotEmpty()) {
                rows.add(CinemaRow(id = "masterpiece_anime", title = "Masterpiece Anime Movies", items = animeMovies))
            }

            if (spotlight.isEmpty() && rows.isEmpty()) {
                throw IllegalStateException("Failed to load cinema feed from TMDB")
            }

            val feed = CinemaFeed(spotlight = spotlight, rows = rows)
            cachedFeed = feed
            feed
        }
    }

    override suspend fun search(query: String): List<CinemaItem> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val encodedQuery = java.net.URLEncoder.encode(query.trim(), "UTF-8")
        val url = "$BASE_URL/search/multi?api_key=$apiKey&query=$encodedQuery&include_adult=false"

        val json = httpGetJson(url) ?: return@withContext emptyList()
        val results = json.optJSONArray("results") ?: return@withContext emptyList()

        val items = mutableListOf<CinemaItem>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val mediaType = obj.optString("media_type")
            if (mediaType != "movie" && mediaType != "tv") continue

            val kind = if (mediaType == "movie") {
                CinemaKind.MOVIE
            } else {
                val genreIds = parseGenreIds(obj.optJSONArray("genre_ids"))
                val origLang = obj.optString("original_language")
                if (16 in genreIds && origLang == "ja") CinemaKind.ANIME else CinemaKind.TV
            }

            val item = parseCinemaItem(obj, kind)
            if (item.posterUrl.isNotBlank() || item.backdropUrl.isNotBlank()) {
                items.add(item)
            }
        }
        items
    }

    override suspend fun loadDetail(item: CinemaItem): CinemaDetail = withContext(Dispatchers.IO) {
        detailCache[item.id]?.let { return@withContext it }

        val detail = if (item.kind == CinemaKind.MOVIE) {
            loadMovieDetail(item)
        } else {
            loadTvDetail(item)
        }

        detailCache[item.id] = detail
        detail
    }

    private suspend fun loadMovieDetail(item: CinemaItem): CinemaDetail = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/movie/${item.id}?api_key=$apiKey&append_to_response=credits,videos"
        val json = httpGetJson(url)

        val tagline = json?.optString("tagline")?.takeIf { it.isNotBlank() }
        val overview = json?.optString("overview") ?: ""
        val runtimeMin = json?.optInt("runtime", 0) ?: 0
        val duration = if (runtimeMin > 0) formatMinutes(runtimeMin) else item.duration

        val genres = parseGenresArray(json?.optJSONArray("genres"))
        val updatedItem = item.copy(
            duration = duration,
            genres = if (genres.isNotEmpty()) genres else item.genres
        )

        val cast = parseCast(json?.optJSONObject("credits")?.optJSONArray("cast"))
        val trailerKey = parseYoutubeTrailer(json?.optJSONObject("videos")?.optJSONArray("results"))

        val status = json?.optString("status", "") ?: ""
        val releaseDate = json?.optString("release_date", "") ?: ""
        val isFutureRelease = try {
            if (releaseDate.length >= 10) {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
                releaseDate > today
            } else false
        } catch (_: Exception) { false }
        val isUnreleased = isFutureRelease || (status.isNotBlank() && !status.equals("Released", ignoreCase = true))
        val imdbId = json?.optString("imdb_id")?.takeIf { it.isNotBlank() }

        CinemaDetail(
            item = updatedItem,
            tagline = tagline,
            overview = overview,
            cast = cast,
            seasons = emptyList(),
            trailerYoutubeKey = trailerKey,
            isUnreleased = isUnreleased,
            imdbId = imdbId
        )
    }

    private suspend fun loadTvDetail(item: CinemaItem): CinemaDetail = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/tv/${item.id}?api_key=$apiKey&append_to_response=credits,videos,external_ids"
        val json = httpGetJson(url)
        val imdbId = json?.optJSONObject("external_ids")?.optString("imdb_id")?.takeIf { it.isNotBlank() }

        val tagline = json?.optString("tagline")?.takeIf { it.isNotBlank() }
        val overview = json?.optString("overview") ?: ""
        val genres = parseGenresArray(json?.optJSONArray("genres"))

        val cast = parseCast(json?.optJSONObject("credits")?.optJSONArray("cast"))
        val trailerKey = parseYoutubeTrailer(json?.optJSONObject("videos")?.optJSONArray("results"))

        val rawSeasons = json?.optJSONArray("seasons")
        val seasonsList = mutableListOf<Season>()

        if (rawSeasons != null) {
            for (i in 0 until rawSeasons.length()) {
                val sObj = rawSeasons.optJSONObject(i) ?: continue
                val seasonNum = sObj.optInt("season_number", -1)
                val seasonName = sObj.optString("name", "Season $seasonNum")
                val epCount = sObj.optInt("episode_count", 0)

                if (seasonNum < 1) continue // Skip specials (season 0)

                // Load episodes for this season
                val episodes = loadSeasonEpisodes(item.id, seasonNum)
                seasonsList.add(
                    Season(
                        seasonNumber = seasonNum,
                        name = seasonName,
                        episodes = episodes
                    )
                )
            }
        }

        val updatedItem = item.copy(
            seasonsCount = seasonsList.size,
            genres = if (genres.isNotEmpty()) genres else item.genres
        )

        CinemaDetail(
            item = updatedItem,
            tagline = tagline,
            overview = overview,
            cast = cast,
            seasons = seasonsList,
            trailerYoutubeKey = trailerKey,
            imdbId = imdbId
        )
    }

    private suspend fun loadSeasonEpisodes(tvId: String, seasonNumber: Int): List<Episode> = withContext(Dispatchers.IO) {
        val cacheKey = "$tvId-$seasonNumber"
        seasonEpisodesCache[cacheKey]?.let { return@withContext it }

        val url = "$BASE_URL/tv/$tvId/season/$seasonNumber?api_key=$apiKey"
        val json = httpGetJson(url)
        val episodesJson = json?.optJSONArray("episodes") ?: return@withContext emptyList()

        val episodes = mutableListOf<Episode>()
        for (i in 0 until episodesJson.length()) {
            val epObj = episodesJson.optJSONObject(i) ?: continue
            val epId = epObj.optString("id", i.toString())
            val epNum = epObj.optInt("episode_number", i + 1)
            val title = epObj.optString("name", "Episode $epNum")
            val epOverview = epObj.optString("overview", "")
            val stillPath = epObj.optString("still_path")
            val stillUrl = if (stillPath.isNotBlank() && stillPath != "null") "$IMAGE_BASE_STILL$stillPath" else null
            val runtime = epObj.optInt("runtime", 0).takeIf { it > 0 }
            val airDate = epObj.optString("air_date").takeIf { it.isNotBlank() && it != "null" }

            episodes.add(
                Episode(
                    id = epId,
                    episodeNumber = epNum,
                    title = title,
                    overview = epOverview,
                    stillUrl = stillUrl,
                    durationMin = runtime,
                    airDate = airDate
                )
            )
        }

        seasonEpisodesCache[cacheKey] = episodes
        episodes
    }

    private fun fetchItems(endpoint: String, kind: CinemaKind): List<CinemaItem> {
        val separator = if (endpoint.contains("?")) "&" else "?"
        val url = "$BASE_URL$endpoint${separator}api_key=$apiKey"
        val json = httpGetJson(url) ?: return emptyList()
        val results = json.optJSONArray("results") ?: return emptyList()

        val items = mutableListOf<CinemaItem>()
        for (i in 0 until results.length()) {
            val obj = results.optJSONObject(i) ?: continue
            val item = parseCinemaItem(obj, kind)
            if (item.posterUrl.isNotBlank() || item.backdropUrl.isNotBlank()) {
                items.add(item)
            }
        }
        return items
    }

    private fun parseCinemaItem(obj: JSONObject, defaultKind: CinemaKind): CinemaItem {
        val id = obj.optInt("id", 0).toString()
        val title = obj.optString("title", "").ifBlank { obj.optString("name", "") }
        val posterPath = obj.optString("poster_path")
        val backdropPath = obj.optString("backdrop_path")
        val rating = obj.optDouble("vote_average", 0.0)
        val releaseDate = obj.optString("release_date", "").ifBlank { obj.optString("first_air_date", "") }
        val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else ""

        val posterUrl = if (posterPath.isNotBlank() && posterPath != "null") "$IMAGE_BASE_POSTER$posterPath" else ""
        val backdropUrl = if (backdropPath.isNotBlank() && backdropPath != "null") "$IMAGE_BASE_BACKDROP$backdropPath" else ""

        val genreIds = parseGenreIds(obj.optJSONArray("genre_ids"))
        val genres = genreIds.mapNotNull { GENRE_MAP[it] }

        return CinemaItem(
            id = id,
            title = title,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            rating = rating,
            year = year,
            kind = defaultKind,
            genres = genres
        )
    }

    private fun parseCast(castArray: JSONArray?): List<CastMember> {
        if (castArray == null) return emptyList()
        val castList = mutableListOf<CastMember>()
        val limit = minOf(castArray.length(), 16)
        for (i in 0 until limit) {
            val c = castArray.optJSONObject(i) ?: continue
            val id = c.optInt("id", 0).toString()
            val name = c.optString("name", "")
            val character = c.optString("character", "")
            val profilePath = c.optString("profile_path")
            val profileUrl = if (profilePath.isNotBlank() && profilePath != "null") "$IMAGE_BASE_PROFILE$profilePath" else null
            if (name.isNotBlank()) {
                castList.add(CastMember(id = id, name = name, character = character, profileUrl = profileUrl))
            }
        }
        return castList
    }

    private fun parseYoutubeTrailer(videosArray: JSONArray?): String? {
        if (videosArray == null) return null
        for (i in 0 until videosArray.length()) {
            val v = videosArray.optJSONObject(i) ?: continue
            val site = v.optString("site")
            val type = v.optString("type")
            val key = v.optString("key")
            if (site.equals("YouTube", ignoreCase = true) &&
                (type.equals("Trailer", ignoreCase = true) || type.equals("Teaser", ignoreCase = true)) &&
                key.isNotBlank()
            ) {
                return key
            }
        }
        return null
    }

    private fun parseGenreIds(array: JSONArray?): List<Int> {
        if (array == null) return emptyList()
        val list = mutableListOf<Int>()
        for (i in 0 until array.length()) {
            list.add(array.optInt(i))
        }
        return list
    }

    private fun parseGenresArray(genresArray: JSONArray?): List<String> {
        if (genresArray == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until genresArray.length()) {
            val g = genresArray.optJSONObject(i) ?: continue
            val name = g.optString("name")
            if (name.isNotBlank()) list.add(name)
        }
        return list
    }

    private fun formatMinutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun httpGetJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JSONObject(body)
            }
        } catch (_: Exception) {
            null
        }
    }
}
