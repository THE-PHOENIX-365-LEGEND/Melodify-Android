package dev.melodify.uranophilelab.utils

import dev.melodify.uranophilelab.records.SongResponse
import java.util.Random

object ShuffleUtil {

    /**
     * Emulates Spotify's shuffle algorithm:
     * 1) PRNG seeding (optional deterministic seed)
     * 2) Fisher-Yates shuffle
     * 3) Artist-gap heuristic (prevents back-to-back songs from the same artist)
     *
     * @param items List of items to shuffle.
     * @param seed Optional seed for reproducible shuffle.
     * @param getArtistId Extractor function returning the artist ID or name for an item.
     */
    fun <T> spotifyStyleShuffle(
        items: List<T>,
        seed: Long? = null,
        getArtistId: (T) -> String?
    ): List<T> {
        if (items.size <= 2) return items.toList()

        // 1. Seed PRNG
        val random = if (seed != null) Random(seed) else Random()

        // 2. Fisher-Yates Shuffle
        val shuffled = items.toMutableList()
        for (i in shuffled.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val temp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = temp
        }

        // 3. Artist-Gap Heuristic (Smart Shuffle)
        val finalQueue = mutableListOf<T>()
        val pendingQueue = ArrayDeque(shuffled)
        var lastArtist: String? = null
        var consecutiveSkips = 0

        while (pendingQueue.isNotEmpty()) {
            val currentItem = pendingQueue.removeFirst()
            val artist = getArtistId(currentItem)?.takeIf { it.isNotBlank() }

            // Defer track if it matches the previous artist and other distinct candidates remain
            if (artist != null && artist == lastArtist && pendingQueue.isNotEmpty() && consecutiveSkips < pendingQueue.size) {
                pendingQueue.addLast(currentItem)
                consecutiveSkips++
            } else {
                finalQueue.add(currentItem)
                if (artist != null) {
                    lastArtist = artist
                }
                consecutiveSkips = 0
            }
        }

        return finalQueue
    }

    /**
     * Convenience method to shuffle a list of [SongResponse.Song] objects using Spotify-style shuffle.
     */
    fun shuffleSongs(
        songs: List<SongResponse.Song>,
        seed: Long? = null
    ): List<SongResponse.Song> {
        return spotifyStyleShuffle(songs, seed) { song ->
            val primaryArtist = song.artists?.primary?.firstOrNull()
            primaryArtist?.id ?: primaryArtist?.name()
        }
    }

    /**
     * Convenience method to shuffle a list of track IDs with an optional artist map or song map.
     */
    fun shuffleTrackIds(
        trackIds: List<String>,
        artistMap: Map<String, String?>? = null,
        songMap: Map<String, SongResponse.Song>? = null,
        seed: Long? = null
    ): List<String> {
        return spotifyStyleShuffle(trackIds, seed) { trackId ->
            artistMap?.get(trackId)
                ?: songMap?.get(trackId)?.artists?.primary?.firstOrNull()?.let { it.id ?: it.name() }
                ?: if (trackId.contains(":")) trackId.split(":")[0] else null
        }
    }
}
