package app.sonicsound.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.TvActivity
import app.sonicsound.extensions.loadUrl
import app.sonicsound.models.Album
import app.sonicsound.models.Artist
import app.sonicsound.models.ICardViewModel
import app.sonicsound.models.InternetRadioStation
import app.sonicsound.models.Playlist
import app.sonicsound.models.Song
import app.sonicsound.models.YoutubeVideo

class SonicSoundCardAdapter(
    private var dataSet: List<ICardViewModel>,
    private val recyclerView: RecyclerView,
    private val bind: TvActivity.TvActivityBind,
    private val onItem: ((ICardViewModel) -> Unit)? = null,
) : RecyclerView.Adapter<SonicSoundCardAdapter.ViewHolder>(), View.OnClickListener {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.iv_album_card_image)
        val firstLine: TextView = view.findViewById(R.id.tv_album_card_first_line)
        val secondLine: TextView = view.findViewById(R.id.tv_album_card_second_line)
        val container: RelativeLayout = view.findViewById(R.id.rl_card_container)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.album_card, viewGroup, false)
        val ret = ViewHolder(view)
        ret.itemView.setOnClickListener(this)
        return ret
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.firstLine.text = dataSet[position].firstLine()
        viewHolder.secondLine.text = dataSet[position].secondLine()
        viewHolder.image.loadUrl(dataSet[position].image)
        viewHolder.image.clipToOutline = true
    }

    override fun getItemCount() = dataSet.size

    override fun onClick(v: View?) {
        if (v == null) return
        val pos = recyclerView.getChildAdapterPosition(v)
        if (pos == RecyclerView.NO_POSITION) return
        val item = dataSet[pos]
        onItem?.invoke(item)
        when (item) {
            is Album -> bind.showAlbum(item.id, item.name)
            is Song -> bind.playRadio(item.id)
            is Playlist -> bind.playPlaylist(item.id, 0)
            is InternetRadioStation -> bind.playInternetRadio(item.streamUrl, item.name)
            is Artist -> bind.showArtist(item.id, item.name)
            is YoutubeVideo -> { /* handled via onItem */ }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setNewDataSet(newSet: List<ICardViewModel>) {
        dataSet = newSet
        notifyDataSetChanged()
    }
}
