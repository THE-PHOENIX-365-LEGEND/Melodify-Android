package dev.melodify.uranophilelab.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import dev.melodify.uranophilelab.BaseApplicationClass
import dev.melodify.uranophilelab.model.AlbumItem
import dev.melodify.uranophilelab.network.ApiManager
import dev.melodify.uranophilelab.network.utility.RequestNetwork
import dev.melodify.uranophilelab.records.ArtistAllSongs
import dev.melodify.uranophilelab.records.SongResponse
import dev.melodify.uranophilelab.records.SongSearch
import java.util.Collections

object RecommendationManager {
    private const val TAG = "RecommendationManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    // Memory cache of recently recommended song IDs to prevent repetition within session
    private val recentlyRecommendedIds: MutableSet<String> = Collections.synchronizedSet(LinkedHashSet<String>())
    private const val MAX_RECENT_CACHE_SIZE = 100

    /**
     * Builds a comprehensive set of excluded song IDs to prevent repetition.
     * Excludes:
     * 1. Seed song ID
     * 2. Currently playing song ID
     * 3. Track queue items
     * 4. Recently played songs from history
     * 5. Songs recommended in the current session
     */
    private fun getExcludedSongIds(seedSongId: String? = null): Set<String> {
        val excluded = HashSet<String>()
        seedSongId?.let { excluded.add(it) }

        // Currently playing song
        MusicPlayerManager.MUSIC_ID?.let { excluded.add(it) }
        MusicPlayerManager.CURRENT_TRACK?.data?.firstOrNull()?.id?.let { excluded.add(it) }

        // Active queue songs
        MusicPlayerManager.trackQueue?.filterNotNull()?.let { excluded.addAll(it) }

        // Song history (recently played)
        val prefManager = BaseApplicationClass.sharedPreferenceManager
        prefManager?.songHistory?.take(30)?.forEach { item ->
            item.id?.let { excluded.add(it) }
        }

        // Session recommendation memory cache
        synchronized(recentlyRecommendedIds) {
            excluded.addAll(recentlyRecommendedIds)
        }

        return excluded
    }

    /**
     * Filters a list of songs to ensure NO repetition:
     * - Removes excluded IDs
     * - Ensures distinct song IDs
     * - Ensures distinct normalized title + primary artist pairs (avoids re-releases / remastered duplicates)
     */
    private fun filterUniqueSongs(
        songs: List<SongResponse.Song>,
        excludedIds: Set<String>,
        limit: Int
    ): List<SongResponse.Song> {
        val uniqueSongs = ArrayList<SongResponse.Song>()
        val seenTrackKeys = HashSet<String>()

        for (song in songs) {
            val songId = song.id ?: continue
            if (excludedIds.contains(songId)) continue

            val primaryArtist = song.artists?.primary?.firstOrNull()?.name() ?: ""
            val trackKey = "${song.name().lowercase().trim()}|${primaryArtist.lowercase().trim()}"

            if (seenTrackKeys.contains(trackKey)) continue

            seenTrackKeys.add(trackKey)
            uniqueSongs.add(song)

            // Cache ID in session memory
            rememberRecommendedSong(songId)

            if (uniqueSongs.size >= limit) break
        }

        return uniqueSongs
    }

    private fun rememberRecommendedSong(songId: String) {
        synchronized(recentlyRecommendedIds) {
            recentlyRecommendedIds.add(songId)
            if (recentlyRecommendedIds.size > MAX_RECENT_CACHE_SIZE) {
                val iterator = recentlyRecommendedIds.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Clears session repetition cache (e.g. when app restarts or queue resets).
     */
    fun clearSessionRecommendationCache() {
        synchronized(recentlyRecommendedIds) {
            recentlyRecommendedIds.clear()
        }
    }

    /**
     * Retrieves intelligent, non-repeating song recommendations for a given song.
     */
    fun getRecommendationsForSong(
        context: Context,
        song: SongResponse.Song,
        limit: Int = 30,
        callback: (List<SongResponse.Song>) -> Unit
    ) {
        val songId = song.id ?: run {
            mainHandler.post { callback(emptyList()) }
            return
        }

        val excludedIds = getExcludedSongIds(songId)
        val apiManager = ApiManager(context)

        // Step 1: Try song suggestions API
        apiManager.retrieveSongSuggestions(songId, limit * 2, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val songResponse = Gson().fromJson(response, SongResponse::class.java)
                    val suggestions = songResponse?.data?.filterNotNull() ?: emptyList()
                    val filtered = filterUniqueSongs(suggestions, excludedIds, limit)

                    if (songResponse?.success == true && filtered.isNotEmpty()) {
                        mainHandler.post { callback(filtered) }
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Song suggestions parsing failed: ${e.message}")
                }
                fallbackToArtistTopSongs(apiManager, song, excludedIds, limit, callback)
            }

            override fun onErrorResponse(tag: String?, message: String?) {
                fallbackToArtistTopSongs(apiManager, song, excludedIds, limit, callback)
            }
        })
    }

    private fun fallbackToArtistTopSongs(
        apiManager: ApiManager,
        song: SongResponse.Song,
        excludedIds: Set<String>,
        limit: Int,
        callback: (List<SongResponse.Song>) -> Unit
    ) {
        val primaryArtist = song.artists?.primary?.firstOrNull()
        val artistId = primaryArtist?.id

        if (!artistId.isNullOrEmpty()) {
            apiManager.retrieveArtistSongs(artistId, 0, ApiManager.SortBy.popularity, ApiManager.SortOrder.desc, object : RequestNetwork.RequestListener {
                override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                    try {
                        val artistSongs = Gson().fromJson(response, ArtistAllSongs::class.java)
                        val songs = artistSongs?.data?.songs?.filterNotNull() ?: emptyList()
                        val filtered = filterUniqueSongs(songs, excludedIds, limit)

                        if (artistSongs?.success == true && filtered.isNotEmpty()) {
                            mainHandler.post { callback(filtered) }
                            return
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Artist top songs parsing failed: ${e.message}")
                    }
                    fallbackToSearch(apiManager, song, excludedIds, limit, callback)
                }

                override fun onErrorResponse(tag: String?, message: String?) {
                    fallbackToSearch(apiManager, song, excludedIds, limit, callback)
                }
            })
        } else {
            fallbackToSearch(apiManager, song, excludedIds, limit, callback)
        }
    }

    private fun fallbackToSearch(
        apiManager: ApiManager,
        song: SongResponse.Song,
        excludedIds: Set<String>,
        limit: Int,
        callback: (List<SongResponse.Song>) -> Unit
    ) {
        val primaryArtistName = song.artists?.primary?.firstOrNull()?.name()
        val songLanguage = song.language

        val query = when {
            !primaryArtistName.isNullOrBlank() && !songLanguage.isNullOrBlank() -> "$primaryArtistName $songLanguage"
            !primaryArtistName.isNullOrBlank() -> primaryArtistName
            !songLanguage.isNullOrBlank() -> "$songLanguage top songs"
            else -> song.name()
        }

        apiManager.searchSongs(query, 0, limit * 2, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val searchResult = Gson().fromJson(response, SongSearch::class.java)
                    val songs = searchResult?.data?.results?.filterNotNull() ?: emptyList()
                    val filtered = filterUniqueSongs(songs, excludedIds, limit)

                    if (searchResult?.success == true && filtered.isNotEmpty()) {
                        mainHandler.post { callback(filtered) }
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Search fallback failed: ${e.message}")
                }
                mainHandler.post { callback(emptyList()) }
            }

            override fun onErrorResponse(tag: String?, message: String?) {
                mainHandler.post { callback(emptyList()) }
            }
        })
    }

    /**
     * Generates personalized home recommendations based on listening history or favorites without repetition.
     */
    fun getHomePersonalizedRecommendations(
        context: Context,
        callback: (title: String, subtitle: String, items: List<AlbumItem>) -> Unit
    ) {
        val prefManager = BaseApplicationClass.sharedPreferenceManager
        val songHistory = prefManager?.songHistory ?: emptyList()
        val favoriteSongs = prefManager?.favoriteSongs ?: emptyList()
        val apiManager = ApiManager(context)

        val sectionTitle = "Recommended For You"

        val seedSongHistory = songHistory.firstOrNull() ?: favoriteSongs.firstOrNull()
        if (seedSongHistory != null) {
            val songId = seedSongHistory.id
            val songName = seedSongHistory.title ?: "your taste"
            val artistName = seedSongHistory.artist ?: ""

            val sectionSubtitle = if (artistName.isNotBlank() && !artistName.equals("Unknown", ignoreCase = true)) {
                if (songHistory.isNotEmpty()) "Because you listened to $artistName" else "Based on your favorite $artistName"
            } else {
                "Based on $songName"
            }

            if (!songId.isNullOrEmpty()) {
                val song = SongResponse.Song(
                    id = songId,
                    name = songName,
                    type = "song",
                    year = null,
                    releaseDate = null,
                    duration = null,
                    label = null,
                    explicitContent = false,
                    playCount = null,
                    language = null,
                    hasLyrics = false,
                    lyricsId = null,
                    lyrics = null,
                    url = null,
                    copyright = null,
                    album = null,
                    artists = SongResponse.Artists(
                        primary = if (artistName.isNotBlank()) mutableListOf(SongResponse.Artist(null, artistName, null, null, null, null)) else null,
                        featured = null,
                        all = null
                    ),
                    image = if (!seedSongHistory.imageUrl.isNullOrEmpty()) mutableListOf(SongResponse.Image(null, seedSongHistory.imageUrl)) else null,
                    downloadUrl = null,
                    source = "saavn"
                )

                getRecommendationsForSong(context, song, limit = 15) { recommendedSongs ->
                    if (recommendedSongs.isNotEmpty()) {
                        val items = recommendedSongs.map { s ->
                            val img = s.image?.lastOrNull()?.url ?: ""
                            AlbumItem(s.name(), "${s.language ?: ""} ${s.year ?: ""}".trim(), img, s.id, "song")
                        }
                        mainHandler.post { callback(sectionTitle, sectionSubtitle, items) }
                    } else {
                        fallbackHomeTrending(apiManager, callback)
                    }
                }
            } else {
                fallbackHomeTrending(apiManager, callback)
            }
        } else {
            fallbackHomeTrending(apiManager, callback)
        }
    }

    private fun fallbackHomeTrending(
        apiManager: ApiManager,
        callback: (title: String, subtitle: String, items: List<AlbumItem>) -> Unit
    ) {
        val excludedIds = getExcludedSongIds()
        val trendingQueries = listOf("Trending", "Hits", "Top Songs", "Featured", "Romance")
        val query = trendingQueries.random()

        apiManager.searchSongs(query, 0, 30, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val songSearch = Gson().fromJson(response, SongSearch::class.java)
                    val results = songSearch?.data?.results?.filterNotNull() ?: emptyList()
                    val filtered = filterUniqueSongs(results, excludedIds, 15)

                    if (songSearch?.success == true && filtered.isNotEmpty()) {
                        val items = filtered.map { s ->
                            val img = s.image?.lastOrNull()?.url ?: ""
                            AlbumItem(s.name(), "${s.language ?: ""} ${s.year ?: ""}".trim(), img, s.id, "song")
                        }
                        mainHandler.post { callback("Recommended For You", "Curated Selection", items) }
                        return
                    }
                } catch (_: Exception) {}
                mainHandler.post { callback("Recommended For You", "Curated Selection", emptyList()) }
            }

            override fun onErrorResponse(tag: String?, message: String?) {
                mainHandler.post { callback("Recommended For You", "Curated Selection", emptyList()) }
            }
        })
    }
}
