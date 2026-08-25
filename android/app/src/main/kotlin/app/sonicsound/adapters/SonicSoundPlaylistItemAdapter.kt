package app.sonicsound.adapters

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.sonicsound.R
import app.sonicsound.extensions.loadUrl
import app.sonicsound.models.ICardViewModel

class SonicSoundPlaylistItemAdapter(
    private var dataSet: List<ICardViewModel>,
    private val context: Context,
    private val recyclerView: RecyclerView,
    private val layoutManager: LinearLayoutManager,
    private val itemHeight: Int,
    private val onItemClick: ((Int) -> Unit)? = null,
) : RecyclerView.Adapter<SonicSoundPlaylistItemAdapter.ViewHolder>() {
    private var selectedIndex: Int = -1

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.iv_playlist_item_image)
        val firstLine: TextView = view.findViewById(R.id.tv_playlist_item_first_line)
        val secondLine: TextView = view.findViewById(R.id.tv_playlist_item_second_line)
        val container: CardView = view.findViewById(R.id.cv_playlist_item_container)
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(viewGroup.context)
            .inflate(R.layout.playlist_item, viewGroup, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        viewHolder.firstLine.text = dataSet[position].firstLine()
        viewHolder.secondLine.text = dataSet[position].secondLine()
        viewHolder.image.loadUrl(dataSet[position].image)
        viewHolder.image.clipToOutline = true
        applySelected(viewHolder, position == selectedIndex)
        viewHolder.container.setOnClickListener {
            val idx = viewHolder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onItemClick?.invoke(idx)
        }
        viewHolder.container.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && position != selectedIndex) {
                viewHolder.container.background =
                    ContextCompat.getDrawable(context, R.drawable.round_outline_selector)
            } else {
                applySelected(viewHolder, position == selectedIndex)
            }
        }
    }

    private fun applySelected(viewHolder: ViewHolder, selected: Boolean) {
        viewHolder.container.background = ContextCompat.getDrawable(
            context,
            if (selected) R.drawable.round_outline_selected else R.drawable.round_outline
        )
    }

    fun updateSelected(index: Int) {
        if (index >= 0 && index < dataSet.size) {
            val lastSelected = selectedIndex
            selectedIndex = index
            notifyItemChanged(index)
            if (lastSelected >= 0) notifyItemChanged(lastSelected)
            layoutManager.scrollToPositionWithOffset(
                selectedIndex,
                recyclerView.height / 2 - (itemHeight / 2)
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setNewDataSet(newSet: List<ICardViewModel>) {
        dataSet = newSet
        notifyDataSetChanged()
    }

    override fun getItemCount() = dataSet.size
}
