package dev.melodify.uranophilelab.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.gson.Gson
import dev.melodify.uranophilelab.R
import dev.melodify.uranophilelab.adapters.ActivitySearchListItemAdapter
import dev.melodify.uranophilelab.databinding.ActivitySearchBinding
import dev.melodify.uranophilelab.model.SearchListItem
import dev.melodify.uranophilelab.network.ApiManager
import dev.melodify.uranophilelab.network.utility.RequestNetwork
import dev.melodify.uranophilelab.records.GlobalSearch
import dev.melodify.uranophilelab.records.GlobalSearch.Data.TopQuery
import dev.melodify.uranophilelab.records.SongSearch
import dev.melodify.uranophilelab.records.AlbumsSearch
import dev.melodify.uranophilelab.records.PlaylistsSearch
import dev.melodify.uranophilelab.records.ArtistsSearch
import dev.melodify.uranophilelab.utils.MiniPlayerHelper
import dev.melodify.uranophilelab.utils.SharedPreferenceManager
import dev.melodify.uranophilelab.utils.attachSnapHelper
import me.everything.android.ui.overscroll.OverScrollDecoratorHelper
import java.util.Locale
import androidx.core.net.toUri

class SearchActivity : AppCompatActivity() {
    var binding: ActivitySearchBinding? = null
    private val TAG = "SearchActivity"
    var globalSearch: GlobalSearch? = null
    var songSearch: SongSearch? = null
    var albumsSearch: AlbumsSearch? = null
    var playlistsSearch: PlaylistsSearch? = null
    var artistsSearch: ArtistsSearch? = null
    private var searchAdapter: ActivitySearchListItemAdapter? = null
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private var currentQuery: String = ""

    // ── Search history ────────────────────────────────────────────────────────
    private val HISTORY_PREFS = "search_history"
    private val HISTORY_KEY   = "queries"
    private val MAX_HISTORY   = 8

    private fun loadHistory(): ArrayDeque<String> {
        val prefs = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        val set   = prefs.getStringSet(HISTORY_KEY, emptySet()) ?: emptySet()
        // The set is unordered – we store order as "0:query" prefixes
        return set
            .mapNotNull { entry ->
                val idx = entry.indexOf(':')
                if (idx < 0) null else Pair(entry.substring(0, idx).toIntOrNull() ?: 0, entry.substring(idx + 1))
            }
            .sortedByDescending { it.first }
            .map { it.second }
            .let { ArrayDeque(it) }
    }

    private fun saveHistory(history: ArrayDeque<String>) {
        val prefs = getSharedPreferences(HISTORY_PREFS, Context.MODE_PRIVATE)
        val set = history.mapIndexed { idx, q -> "${history.size - idx}:$q" }.toSet()
        prefs.edit().putStringSet(HISTORY_KEY, set).apply()
    }

    private fun addToHistory(query: String) {
        if (query.isBlank()) return
        val history = loadHistory()
        history.remove(query)          // de-dupe
        history.addFirst(query)        // most recent first
        while (history.size > MAX_HISTORY) history.removeLast()
        saveHistory(history)
    }

    private fun clearHistory() {
        saveHistory(ArrayDeque())
    }

    /** Rebuild history chips from SharedPreferences */
    private fun refreshHistoryPanel() {
        val history = loadHistory()
        val chipGroup = binding!!.historyChipGroup
        val container = binding!!.searchHistoryContainer
        chipGroup?.removeAllViews()
        if (history.isEmpty()) {
            container?.visibility = View.GONE
            return
        }
        history.forEach { query ->
            val chip = Chip(this)
            chip.text = query
            chip.isCheckable = false
            chip.chipBackgroundColor = ContextCompat.getColorStateList(this, R.color.chip_background_color)
            chip.setTextColor(ContextCompat.getColorStateList(this, R.color.chip_text_color))
            chip.chipStrokeColor = ContextCompat.getColorStateList(this, R.color.chip_stroke_color)
            chip.chipStrokeWidth = 1f
            chip.setOnClickListener {
                binding!!.edittext.setText(query)
                binding!!.edittext.setSelection(query.length)
                hideHistoryPanel()
                showData(query)
            }
            chipGroup?.addView(chip)
        }
        // Only show if the edit field is currently empty and focused
        val isEmpty = binding!!.edittext.text.isNullOrEmpty()
        container?.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    private fun showHistoryPanel() {
        refreshHistoryPanel()
    }

    private fun hideHistoryPanel() {
        binding!!.searchHistoryContainer?.visibility = View.GONE
    }
    // ─────────────────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding!!.getRoot())
        MiniPlayerHelper.initMiniPlayer(this)

        OverScrollDecoratorHelper.setUpOverScroll(binding!!.hscrollview)
        binding!!.recyclerView.layoutManager = LinearLayoutManager(this)
        binding!!.recyclerView.setHasFixedSize(true)
        binding!!.recyclerView.setItemViewCacheSize(20)

        binding!!.edittext.requestFocus()

        // Show history when field is focused and empty
        binding!!.edittext.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && binding!!.edittext.text.isNullOrEmpty()) {
                showHistoryPanel()
            } else if (!hasFocus) {
                hideHistoryPanel()
            }
        }

        // Clear all history
        binding!!.clearHistoryBtn?.setOnClickListener {
            clearHistory()
            hideHistoryPanel()
        }

        binding!!.chipGroup.setOnCheckedStateChangeListener { _: ChipGroup?, checkedIds: MutableList<Int?>? ->
            Log.i("SearchActivity", "checkedIds: $checkedIds")
            if (currentQuery.isBlank()) return@setOnCheckedStateChangeListener
            val checkedChipId = binding!!.chipGroup.checkedChipId
            when (checkedChipId) {
                R.id.chip_all -> if (globalSearch != null) refreshData() else fetchForCurrentChip(currentQuery, checkedChipId)
                R.id.chip_song -> if (songSearch != null) refreshData() else fetchForCurrentChip(currentQuery, checkedChipId)
                R.id.chip_albums -> if (albumsSearch != null) refreshData() else fetchForCurrentChip(currentQuery, checkedChipId)
                R.id.chip_playlists -> if (playlistsSearch != null) refreshData() else fetchForCurrentChip(currentQuery, checkedChipId)
                R.id.chip_artists -> if (artistsSearch != null) refreshData() else fetchForCurrentChip(currentQuery, checkedChipId)
            }
        }

        binding!!.edittext.setOnEditorActionListener { textView: TextView?, _: Int, _: KeyEvent? ->
            val q = textView!!.text.toString()
            addToHistory(q)
            showData(q)
            Log.i(TAG, "onCreate: $q")
            binding!!.edittext.clearFocus()
            hideKeyboard(binding!!.edittext)
            true
        }

        // Show/hide clear icon and history panel based on text input
        binding!!.edittext.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable) {
                if (s.toString().isEmpty()) {
                    binding!!.clearIcon.visibility = View.GONE
                    showHistoryPanel()
                } else {
                    binding!!.clearIcon.visibility = View.VISIBLE
                    hideHistoryPanel()
                }

                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                searchRunnable = Runnable {
                    val query = s.toString().trim()
                    if (query.isNotEmpty()) {
                        showData(query)
                    } else {
                        currentQuery = ""
                        searchAdapter?.updateData(mutableListOf())
                    }
                }
                searchHandler.postDelayed(searchRunnable!!, 300)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Clear input when clear icon is clicked
        binding!!.clearIcon.setOnClickListener {
            binding!!.edittext.setText("")
        }

        // Show history immediately if the field opens empty
        if (binding!!.edittext.text.isNullOrEmpty()) showHistoryPanel()
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun showData(query: String) {
        addToHistory(query)
        currentQuery = query
        globalSearch = null
        songSearch = null
        albumsSearch = null
        playlistsSearch = null
        artistsSearch = null
        showShimmerData()

        if ((query.startsWith("http") || query.startsWith("www")) && query.contains("jiosaavn.com")) {
            val intent = Intent(Intent.ACTION_VIEW, query.toUri())
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            intent.data = query.toUri()
            startActivity(intent)
            return
        }

        val checkedChipId = binding?.chipGroup?.checkedChipId ?: R.id.chip_all
        fetchForCurrentChip(query, checkedChipId)
    }

    private fun fetchForCurrentChip(query: String, checkedChipId: Int) {
        val apiManager = ApiManager(this)
        val sharedPreferenceManager = SharedPreferenceManager.getInstance(this@SearchActivity)

        when (checkedChipId) {
            R.id.chip_all -> {
                apiManager.globalSearch(query, object : RequestNetwork.RequestListener {
                    override fun onResponse(
                        tag: String?,
                        response: String?,
                        responseHeaders: HashMap<String?, Any?>?
                    ) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parsed = try { Gson().fromJson(response, GlobalSearch::class.java) } catch (e: Exception) { null }
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                globalSearch = parsed
                                if (globalSearch?.success == true) {
                                    sharedPreferenceManager.setSearchResultCache(query, globalSearch)
                                    refreshData()
                                }
                            }
                        }
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val resultOffline = sharedPreferenceManager.getSearchResult(query)
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                if (resultOffline != null) {
                                    globalSearch = resultOffline
                                    refreshData()
                                }
                            }
                        }
                    }
                })
            }
            R.id.chip_song -> {
                apiManager.searchSongs(query, 1, 50, object : RequestNetwork.RequestListener {
                    override fun onResponse(
                        tag: String?,
                        response: String?,
                        responseHeaders: HashMap<String?, Any?>?
                    ) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parsed = try { Gson().fromJson(response, SongSearch::class.java) } catch (e: Exception) { null }
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                songSearch = parsed
                                if (songSearch?.success == true) {
                                    refreshData()
                                }
                            }
                        }
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {}
                })
            }
            R.id.chip_albums -> {
                apiManager.searchAlbums(query, 1, 50, object : RequestNetwork.RequestListener {
                    override fun onResponse(
                        tag: String?,
                        response: String?,
                        responseHeaders: HashMap<String?, Any?>?
                    ) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parsed = try { Gson().fromJson(response, AlbumsSearch::class.java) } catch (e: Exception) { null }
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                albumsSearch = parsed
                                if (albumsSearch?.success == true) {
                                    refreshData()
                                }
                            }
                        }
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {}
                })
            }
            R.id.chip_playlists -> {
                apiManager.searchPlaylists(query, 1, 50, object : RequestNetwork.RequestListener {
                    override fun onResponse(
                        tag: String?,
                        response: String?,
                        responseHeaders: HashMap<String?, Any?>?
                    ) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parsed = try { Gson().fromJson(response, PlaylistsSearch::class.java) } catch (e: Exception) { null }
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                playlistsSearch = parsed
                                if (playlistsSearch?.success == true) {
                                    refreshData()
                                }
                            }
                        }
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {}
                })
            }
            R.id.chip_artists -> {
                apiManager.searchArtists(query, 1, 50, object : RequestNetwork.RequestListener {
                    override fun onResponse(
                        tag: String?,
                        response: String?,
                        responseHeaders: HashMap<String?, Any?>?
                    ) {
                        if (query != currentQuery) return
                        lifecycleScope.launch(Dispatchers.IO) {
                            val parsed = try { Gson().fromJson(response, ArtistsSearch::class.java) } catch (e: Exception) { null }
                            withContext(Dispatchers.Main) {
                                if (query != currentQuery) return@withContext
                                artistsSearch = parsed
                                if (artistsSearch?.success == true) {
                                    refreshData()
                                }
                            }
                        }
                    }

                    override fun onErrorResponse(tag: String?, message: String?) {}
                })
            }
        }
    }

    private fun refreshData() {
        val checkedChipId = binding?.chipGroup?.checkedChipId ?: R.id.chip_all
        val q = currentQuery
        lifecycleScope.launch(Dispatchers.Default) {
            val data: MutableList<SearchListItem?> = ArrayList()
            if (checkedChipId == R.id.chip_all) {
                globalSearch?.data?.topQuery?.results?.forEach { item: TopQuery.Results? ->
                    if (item == null) return@forEach
                    if (!(item.type == "song" || item.type == "album" || item.type == "playlist" || item.type == "artist")) return@forEach
                    
                    val images = item.image
                    val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                    
                    data.add(
                        SearchListItem(
                            item.id,
                            item.title(),
                            item.description(),
                            imageUrl,
                            SearchListItem.Type.valueOf(item.type.uppercase(Locale.getDefault()))
                        )
                    )
                }
                addSongsData(data)
                addAlbumsData(data)
                addPlaylistsData(data)
                addArtistsData(data)
            } else if (checkedChipId == R.id.chip_song) {
                addSongsData(data)
            } else if (checkedChipId == R.id.chip_albums) {
                addAlbumsData(data)
            } else if (checkedChipId == R.id.chip_playlists) {
                addPlaylistsData(data)
            } else if (checkedChipId == R.id.chip_artists) {
                addArtistsData(data)
            }

            val uniqueData = data.filterNotNull().distinctBy { it.id }.toMutableList()

            withContext(Dispatchers.Main) {
                if (q != currentQuery) return@withContext
                if (uniqueData.isNotEmpty()) {
                    if (searchAdapter == null || binding?.recyclerView?.adapter == null) {
                        searchAdapter = ActivitySearchListItemAdapter(uniqueData)
                        binding?.recyclerView?.adapter = searchAdapter
                    } else {
                        searchAdapter?.updateData(uniqueData)
                    }
                }
            }
        }
    }

    private fun addSongsData(data: MutableList<SearchListItem?>) {
        val songList = songSearch?.data?.results
        if (!songList.isNullOrEmpty()) {
            songList.forEach { item ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                val desc = item.album?.name()
                    ?: item.artists?.primary?.firstOrNull()?.name()
                    ?: item.year
                    ?: ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.name(),
                        desc,
                        imageUrl,
                        SearchListItem.Type.SONG
                    )
                )
            }
        } else {
            globalSearch?.data?.songs?.results?.forEach { item: GlobalSearch.Data.Songs.Results? ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.title(),
                        item.description(),
                        imageUrl,
                        SearchListItem.Type.SONG
                    )
                )
            }
        }
    }

    private fun addAlbumsData(data: MutableList<SearchListItem?>) {
        val albumList = albumsSearch?.data?.results
        if (!albumList.isNullOrEmpty()) {
            albumList.forEach { item ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.name(),
                        item.description(),
                        imageUrl,
                        SearchListItem.Type.ALBUM
                    )
                )
            }
        } else {
            globalSearch?.data?.albums?.results?.forEach { item: GlobalSearch.Data.Albums.Results? ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.title(),
                        item.description(),
                        imageUrl,
                        SearchListItem.Type.ALBUM
                    )
                )
            }
        }
    }

    private fun addPlaylistsData(data: MutableList<SearchListItem?>) {
        val playlistList = playlistsSearch?.data?.results
        if (!playlistList.isNullOrEmpty()) {
            playlistList.forEach { item ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                val songCountStr = if (item.songCount > 0) "${item.songCount} Songs" else item.language ?: ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.name(),
                        songCountStr,
                        imageUrl,
                        SearchListItem.Type.PLAYLIST
                    )
                )
            }
        } else {
            globalSearch?.data?.playlists?.results?.forEach { item: GlobalSearch.Data.Playlists.Results? ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.title(),
                        item.description(),
                        imageUrl,
                        SearchListItem.Type.PLAYLIST
                    )
                )
            }
        }
    }

    private fun addArtistsData(data: MutableList<SearchListItem?>) {
        val artistList = artistsSearch?.data?.results
        if (!artistList.isNullOrEmpty()) {
            artistList.forEach { item ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.name(),
                        item.role ?: "Artist",
                        imageUrl,
                        SearchListItem.Type.ARTIST
                    )
                )
            }
        } else {
            globalSearch?.data?.artists?.results?.forEach { item: GlobalSearch.Data.Artists.Results? ->
                if (item == null) return@forEach
                val images = item.image
                val imageUrl = if (!images.isNullOrEmpty()) images[images.size - 1]?.url ?: "" else ""
                data.add(
                    SearchListItem(
                        item.id,
                        item.title(),
                        item.description(),
                        imageUrl,
                        SearchListItem.Type.ARTIST
                    )
                )
            }
        }
    }

    private fun showShimmerData() {
        val shimmerList = List(10) {
            SearchListItem("<shimmer>", "", "", "", SearchListItem.Type.SONG)
        }.toMutableList()

        if (searchAdapter == null || binding?.recyclerView?.adapter == null) {
            searchAdapter = ActivitySearchListItemAdapter(shimmerList)
            binding?.recyclerView?.adapter = searchAdapter
        } else {
            searchAdapter?.updateData(shimmerList)
        }
    }

    override fun onResume() {
        super.onResume()
        MiniPlayerHelper.onActivityResume(this)
    }

    override fun onPause() {
        super.onPause()
        binding?.edittext?.let { et ->
            et.clearFocus()
            hideKeyboard(et)
        }
        MiniPlayerHelper.onActivityPause(this)
    }

    override fun onDestroy() {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
        binding = null
    }

    fun backPress(view: View?) {
        finish()
    }
}
