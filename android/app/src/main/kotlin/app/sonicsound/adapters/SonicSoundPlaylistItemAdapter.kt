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
    private var pendingFocusIndex: Int = -1

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
        viewHolder.firstLine.isSelected = true
        viewHolder.secondLine.isSelected = true
        viewHolder.image.loadUrl(dataSet[position].image)
        viewHolder.image.clipToOutline = true
        applySelected(viewHolder, position == selectedIndex)
        viewHolder.container.setOnClickListener {
            val idx = viewHolder.bindingAdapterPosition
            if (idx != RecyclerView.NO_POSITION) onItemClick?.invoke(idx)
        }
        viewHolder.container.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                viewHolder.firstLine.isSelected = true
                viewHolder.secondLine.isSelected = true
                if (position != selectedIndex) {
                    viewHolder.container.background =
                        ContextCompat.getDrawable(context, R.drawable.round_outline_selector)
                }
            } else {
                applySelected(viewHolder, position == selectedIndex)
            }
        }
        if (position == pendingFocusIndex) {
            pendingFocusIndex = -1
            viewHolder.container.post { viewHolder.container.requestFocus() }
        }
    }

    private fun applySelected(viewHolder: ViewHolder, selected: Boolean) {
        viewHolder.container.background = ContextCompat.getDrawable(
            context,
            if (selected) R.drawable.round_outline_selected else R.drawable.round_outline
        )
    }

    fun updateSelected(index: Int, keepFocus: Boolean = false) {
        if (index < 0 || index >= dataSet.size) return
        val lastSelected = selectedIndex
        selectedIndex = index
        if (keepFocus) pendingFocusIndex = index
        notifyItemChanged(index)
        if (lastSelected >= 0 && lastSelected != index) notifyItemChanged(lastSelected)
        layoutManager.scrollToPositionWithOffset(
            selectedIndex,
            recyclerView.height / 2 - (itemHeight / 2)
        )
        if (keepFocus) {
            recyclerView.post {
                val holder = recyclerView.findViewHolderForAdapterPosition(index)
                holder?.itemView?.requestFocus()
            }
        }
    }

    fun focusItem(index: Int) {
        if (index < 0 || index >= dataSet.size) return
        pendingFocusIndex = index
        notifyItemChanged(index)
        recyclerView.post {
            val holder = recyclerView.findViewHolderForAdapterPosition(index) ?: return@post
            val card = holder.itemView.findViewById<View>(R.id.cv_playlist_item_container)
            (card ?: holder.itemView).requestFocus()
        }
    }

    fun sameEntries(newSet: List<ICardViewModel>): Boolean {
        if (newSet.size != dataSet.size) return false
        return newSet.indices.all { i ->
            newSet[i].firstLine() == dataSet[i].firstLine() &&
                newSet[i].secondLine() == dataSet[i].secondLine()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun setNewDataSet(newSet: List<ICardViewModel>) {
        dataSet = newSet
        notifyDataSetChanged()
    }

    override fun getItemCount() = dataSet.size
}
