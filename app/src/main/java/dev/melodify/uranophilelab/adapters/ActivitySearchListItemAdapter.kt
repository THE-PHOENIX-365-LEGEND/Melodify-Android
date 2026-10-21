package dev.melodify.uranophilelab.adapters

import android.content.Intent
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.card.MaterialCardView
import com.google.gson.Gson
import com.bumptech.glide.Glide
import dev.melodify.uranophilelab.R
import dev.melodify.uranophilelab.activities.ArtistProfileActivity
import dev.melodify.uranophilelab.activities.ListActivity
import dev.melodify.uranophilelab.activities.MusicOverviewActivity
import dev.melodify.uranophilelab.model.AlbumItem
import dev.melodify.uranophilelab.model.BasicDataRecord
import dev.melodify.uranophilelab.model.SearchListItem
import dev.melodify.uranophilelab.model.history.SongHistoryItem
import dev.melodify.uranophilelab.records.sharedpref.SavedLibraries
import dev.melodify.uranophilelab.utils.AnimatedMenuHelper
import dev.melodify.uranophilelab.utils.MusicPlayerManager
import dev.melodify.uranophilelab.utils.SharedPreferenceManager

class ActivitySearchListItemAdapter(private val data: MutableList<SearchListItem>) :
    RecyclerView.Adapter<ActivitySearchListItemAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView? = itemView.findViewById(R.id.title)
        val artistView: TextView? = itemView.findViewById(R.id.artist)
        val coverCard: MaterialCardView? = itemView.findViewById(R.id.coverCard)
        val coverImage: ImageView? = itemView.findViewById(R.id.coverImage)
        val moreIcon: ImageView? = itemView.findViewById(R.id.more)
        val shimmer: ShimmerFrameLayout? = itemView.findViewById(R.id.shimmer)

        init {
            titleView?.isSelected = true
            artistView?.isSelected = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutRes = if (viewType == 0) R.layout.activity_list_song_item else R.layout.activity_list_shimmer
        val view = LayoutInflater.from(parent.context).inflate(layoutRes, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) == 1) {
            holder.shimmer?.startShimmer()
            return
        }

        val item = data[position]
        val parsedTitle = item.title()
        val parsedSubtitle = item.subtitle()

        val isCurrentPlaying = item.type == SearchListItem.Type.SONG && !item.id.isNullOrEmpty() && (item.id == MusicPlayerManager.MUSIC_ID)
        holder.titleView?.text = parsedTitle
        if (isCurrentPlaying) {
            holder.titleView?.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.spotify_green))
            holder.titleView?.setTypeface(null, Typeface.BOLD)
        } else {
            holder.titleView?.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.text_light))
            holder.titleView?.setTypeface(null, Typeface.NORMAL)
        }

        val typePrefix = when (item.type) {
            SearchListItem.Type.SONG -> "Song"
            SearchListItem.Type.ALBUM -> "Album"
            SearchListItem.Type.PLAYLIST -> "Playlist"
            SearchListItem.Type.ARTIST -> "Artist"
            else -> ""
        }
        val formattedSubtitle = if (typePrefix.isNotEmpty() && parsedSubtitle.isNotEmpty()) {
            "$typePrefix • $parsedSubtitle"
        } else if (typePrefix.isNotEmpty()) {
            typePrefix
        } else {
            parsedSubtitle
        }
        holder.artistView?.text = formattedSubtitle

        val coverCard = holder.coverCard
        if (coverCard != null) {
            val density = coverCard.context.resources.displayMetrics.density
            val radiusPx = if (item.type == SearchListItem.Type.ARTIST) (20f * density) else (4f * density)
            coverCard.radius = radiusPx
        }

        val favIcon = holder.itemView.findViewById<ImageView>(R.id.favorite_item_icon)
        val prefs = SharedPreferenceManager.getInstance(holder.itemView.context)

        if (item.type == SearchListItem.Type.SONG) {
            val isFav = !item.id.isNullOrEmpty() && prefs.isFavorite(item.id)
            favIcon?.visibility = if (isFav) View.VISIBLE else View.GONE
            favIcon?.setImageResource(R.drawable.favorite_24px)
        } else {
            favIcon?.visibility = View.GONE
        }

        val moreIcon = holder.moreIcon
        if (moreIcon != null) {
            when (item.type) {
                SearchListItem.Type.SONG -> {
                    moreIcon.visibility = View.VISIBLE
                    moreIcon.setOnClickListener { v ->
                        val songId = item.id ?: return@setOnClickListener
                        AnimatedMenuHelper.showAnimatedSongMenu(
                            context = v.context,
                            songId = songId,
                            title = parsedTitle,
                            artist = parsedSubtitle,
                            coverUrl = item.coverImage ?: ""
                        )
                    }
                }

                SearchListItem.Type.ALBUM, SearchListItem.Type.PLAYLIST -> {
                    moreIcon.visibility = View.VISIBLE
                    moreIcon.setOnClickListener { v ->
                        val albumItem = AlbumItem(
                            parsedTitle,
                            parsedSubtitle,
                            item.coverImage,
                            item.id,
                            if (item.type == SearchListItem.Type.PLAYLIST) "playlist" else "album"
                        )
                        AnimatedMenuHelper.showAnimatedAlbumMenu(
                            context = v.context,
                            albumItem = albumItem
                        )
                    }
                }

                else -> {
                    moreIcon.visibility = View.GONE
                }
            }
        }

        val coverImageView = holder.coverImage
        if (coverImageView != null) {
            val rawImageUrl: String? = item.coverImage
            val imageUrl = if (!rawImageUrl.isNullOrEmpty() && rawImageUrl.contains("50x50")) {
                rawImageUrl.replace("50x50", "150x150")
            } else rawImageUrl ?: ""
            val isInvalid = imageUrl.isBlank() || imageUrl == "<shimmer>" || imageUrl.contains("default") || imageUrl.contains("artist-default")
            if (!isInvalid) {
                Glide.with(coverImageView.context).load(imageUrl)
                    .placeholder(R.drawable.headphone)
                    .error(R.drawable.headphone)
                    .centerCrop().into(coverImageView)
            } else {
                coverImageView.setImageResource(R.drawable.headphone)
            }
        }

        holder.itemView.setOnClickListener {
            val ctx = holder.itemView.context
            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(holder.itemView.windowToken, 0)
            holder.itemView.clearFocus()
            (ctx as? android.app.Activity)?.currentFocus?.clearFocus()

            val intent = Intent()
            intent.putExtra("id", item.id)
            when (item.type) {
                SearchListItem.Type.SONG -> {
                    if (item.id.isNullOrEmpty()) return@setOnClickListener
                    val songList = data.filter { it.type == SearchListItem.Type.SONG && !it.id.isNullOrEmpty() }
                    val songIds: ArrayList<String?> = ArrayList(songList.map { it.id })
                    val clickIndex = songIds.indexOf(item.id).let { if (it >= 0) it else 0 }

                    MusicPlayerManager.trackQueue = if (songIds.isNotEmpty()) songIds else arrayListOf(item.id)
                    MusicPlayerManager.track_position = clickIndex

                    intent.setClass(holder.itemView.context, MusicOverviewActivity::class.java)
                }

                SearchListItem.Type.ALBUM -> {
                    val albumItem =
                        AlbumItem(parsedTitle, parsedSubtitle, item.coverImage, item.id, "album")
                    intent.putExtra("data", Gson().toJson(albumItem))
                    intent.putExtra("type", "album")
                    intent.putExtra("id", item.id)
                    intent.setClass(holder.itemView.context, ListActivity::class.java)
                }

                SearchListItem.Type.PLAYLIST -> {
                    val albumItem =
                        AlbumItem(parsedTitle, parsedSubtitle, item.coverImage, item.id, "playlist")
                    intent.putExtra("data", Gson().toJson(albumItem))
                    intent.putExtra("type", "playlist")
                    intent.putExtra("id", item.id)
                    intent.setClass(holder.itemView.context, ListActivity::class.java)
                }

                SearchListItem.Type.ARTIST -> {
                    intent.setClass(holder.itemView.context, ArtistProfileActivity::class.java)
                    intent.putExtra(
                        "data",
                        Gson().toJson(BasicDataRecord(item.id, parsedTitle, "", item.coverImage))
                    )
                }

                else -> {}
            }
            holder.itemView.context.startActivity(intent)
        }
    }

    private class SearchDiffCallback(
        private val oldList: List<SearchListItem>,
        private val newList: List<SearchListItem>
    ) : DiffUtil.Callback() {
        override fun getOldListSize(): Int = oldList.size
        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldList[oldItemPosition]
            val newItem = newList[newItemPosition]
            if (oldItem.id == "<shimmer>" || newItem.id == "<shimmer>") {
                return oldItemPosition == newItemPosition
            }
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }

    fun updateData(newData: List<SearchListItem>) {
        val diffCallback = SearchDiffCallback(data, newData)
        val diffResult = DiffUtil.calculateDiff(diffCallback)
        data.clear()
        data.addAll(newData)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int {
        return data.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (data[position].id == "<shimmer>") 1 else 0
    }

    private fun isLibrarySaved(id: String?, savedLibraries: SavedLibraries?): Boolean {
        if (id.isNullOrBlank() || savedLibraries?.lists.isNullOrEmpty()) return false
        return savedLibraries?.lists?.any { it?.id == id } == true
    }

    private fun toggleSavedLibrary(
        id: String,
        title: String,
        subtitle: String,
        coverUrl: String,
        isAlbum: Boolean,
        prefs: SharedPreferenceManager
    ): Boolean {
        val saved = prefs.savedLibrariesData
        val list = saved?.lists
        if (list != null) {
            val index = list.indexOfFirst { it?.id == id }
            if (index != -1) {
                prefs.removeLibraryFromSavedLibraries(index)
                return false
            }
        }
        val library = SavedLibraries.Library(
            id,
            false,
            isAlbum,
            title,
            coverUrl,
            subtitle,
            ArrayList()
        )
        prefs.addLibraryToSavedLibraries(library)
        return true
    }
}
