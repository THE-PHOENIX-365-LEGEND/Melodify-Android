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

object RecommendationManager {
    private const val TAG = "RecommendationManager"
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Retrieves intelligent song recommendations for a given song.
     * Strategy:
     * 1. Try API song suggestions (/songs/{id}/suggestions).
     * 2. Fallback to Primary Artist's top songs (/artists/{artistId}/songs).
     * 3. Fallback to Search Songs by artist name + language/genre.
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

        val apiManager = ApiManager(context)

        // Step 1: Try song suggestions API
        apiManager.retrieveSongSuggestions(songId, limit, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val songResponse = Gson().fromJson(response, SongResponse::class.java)
                    val suggestions = songResponse?.data?.filterNotNull()?.filter { it.id != songId }
                    if (songResponse?.success == true && !suggestions.isNullOrEmpty()) {
                        mainHandler.post { callback(suggestions.take(limit)) }
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Song suggestions parsing failed: ${e.message}")
                }
                fallbackToArtistTopSongs(apiManager, song, limit, callback)
            }

            override fun onErrorResponse(tag: String?, message: String?) {
                fallbackToArtistTopSongs(apiManager, song, limit, callback)
            }
        })
    }

    private fun fallbackToArtistTopSongs(
        apiManager: ApiManager,
        song: SongResponse.Song,
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
                        val songs = artistSongs?.data?.songs?.filterNotNull()?.filter { it.id != song.id }
                        if (artistSongs?.success == true && !songs.isNullOrEmpty()) {
                            mainHandler.post { callback(songs.take(limit)) }
                            return
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Artist top songs parsing failed: ${e.message}")
                    }
                    fallbackToSearch(apiManager, song, limit, callback)
                }

                override fun onErrorResponse(tag: String?, message: String?) {
                    fallbackToSearch(apiManager, song, limit, callback)
                }
            })
        } else {
            fallbackToSearch(apiManager, song, limit, callback)
        }
    }

    private fun fallbackToSearch(
        apiManager: ApiManager,
        song: SongResponse.Song,
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

        apiManager.searchSongs(query, 0, limit + 5, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val searchResult = Gson().fromJson(response, SongSearch::class.java)
                    val songs = searchResult?.data?.results?.filterNotNull()?.filter { it.id != song.id }
                    if (searchResult?.success == true && !songs.isNullOrEmpty()) {
                        mainHandler.post { callback(songs.take(limit)) }
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
     * Generates personalized home recommendations based on listening history, favorites, or trending seeds.
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

        if (songHistory.isNotEmpty()) {
            val mostRecent = songHistory.first()
            val songId = mostRecent.id
            val songName = mostRecent.title ?: "your taste"
            val artistName = mostRecent.artist ?: ""

            val sectionSubtitle = if (artistName.isNotBlank() && !artistName.equals("Unknown", ignoreCase = true)) {
                "Because you listened to $artistName"
            } else {
                "Based on $songName"
            }

            if (!songId.isNullOrEmpty()) {
                apiManager.retrieveSongSuggestions(songId, 15, object : RequestNetwork.RequestListener {
                    override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                        try {
                            val songResponse = Gson().fromJson(response, SongResponse::class.java)
                            val suggestions = songResponse?.data?.filterNotNull()
                            if (songResponse?.success == true && !suggestions.isNullOrEmpty()) {
                                val items = suggestions.map { s ->
                                    val img = s.image?.lastOrNull()?.url ?: ""
                                    AlbumItem(s.name(), "${s.language ?: ""} ${s.year ?: ""}".trim(), img, s.id, "song")
                                }
                                mainHandler.post { callback(sectionTitle, sectionSubtitle, items) }
                                return
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Suggestions failed, fallback to search: ${e.message}")
                        }
                        fallbackSearchRecommendations(apiManager, songName, sectionTitle, sectionSubtitle, callback)
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {
                        fallbackSearchRecommendations(apiManager, songName, sectionTitle, sectionSubtitle, callback)
                    }
                })
            } else {
                fallbackSearchRecommendations(apiManager, songName, sectionTitle, sectionSubtitle, callback)
            }
        } else if (favoriteSongs.isNotEmpty()) {
            val favSong = favoriteSongs.first()
            val songName = favSong.title ?: "your favorites"
            val artistName = favSong.artist ?: ""

            val sectionSubtitle = if (artistName.isNotBlank()) {
                "Based on your favorite $artistName"
            } else {
                "Based on your favorites"
            }

            fallbackSearchRecommendations(apiManager, if (artistName.isNotBlank()) artistName else songName, sectionTitle, sectionSubtitle, callback)
        } else {
            fallbackHomeTrending(apiManager, callback)
        }
    }

    private fun fallbackSearchRecommendations(
        apiManager: ApiManager,
        query: String,
        title: String,
        subtitle: String,
        callback: (title: String, subtitle: String, items: List<AlbumItem>) -> Unit
    ) {
        apiManager.searchSongs(query, 0, 15, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val songSearch = Gson().fromJson(response, SongSearch::class.java)
                    val results = songSearch?.data?.results?.filterNotNull()
                    if (songSearch?.success == true && !results.isNullOrEmpty()) {
                        val items = results.map { s ->
                            val img = s.image?.lastOrNull()?.url ?: ""
                            AlbumItem(s.name(), "${s.language ?: ""} ${s.year ?: ""}".trim(), img, s.id, "song")
                        }
                        mainHandler.post { callback(title, subtitle, items) }
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fallback search error: ${e.message}")
                }
                fallbackHomeTrending(apiManager, callback)
            }

            override fun onErrorResponse(tag: String?, message: String?) {
                fallbackHomeTrending(apiManager, callback)
            }
        })
    }

    private fun fallbackHomeTrending(
        apiManager: ApiManager,
        callback: (title: String, subtitle: String, items: List<AlbumItem>) -> Unit
    ) {
        val trendingQueries = listOf("Trending", "Hits", "Top Songs", "Featured", "Romance")
        val query = trendingQueries.random()
        apiManager.searchSongs(query, 0, 15, object : RequestNetwork.RequestListener {
            override fun onResponse(tag: String?, response: String?, responseHeaders: HashMap<String?, Any?>?) {
                try {
                    val songSearch = Gson().fromJson(response, SongSearch::class.java)
                    val results = songSearch?.data?.results?.filterNotNull()
                    if (songSearch?.success == true && !results.isNullOrEmpty()) {
                        val items = results.map { s ->
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
