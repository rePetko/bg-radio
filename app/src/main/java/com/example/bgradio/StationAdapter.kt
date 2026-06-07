package com.example.bgradio

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class StationAdapter(
    private val isFavorite: (Station) -> Boolean,
    private val onPlay: (Station) -> Unit,
    private val onToggleFavorite: (Station) -> Unit,
) : ListAdapter<Station, StationAdapter.VH>(DIFF) {

    class VH(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.station_name)
        val meta: TextView = view.findViewById(R.id.station_meta)
        val play: ImageButton = view.findViewById(R.id.station_play)
        val star: ImageButton = view.findViewById(R.id.station_star)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_station, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val station = getItem(position)
        holder.name.text = station.name
        holder.meta.text = buildMeta(station)
        holder.star.setImageResource(
            if (isFavorite(station)) R.drawable.ic_favorite_on
            else R.drawable.ic_favorite_off
        )
        holder.star.contentDescription = if (isFavorite(station))
            holder.itemView.context.getString(R.string.remove_favorite)
        else
            holder.itemView.context.getString(R.string.add_favorite)
        holder.play.contentDescription = holder.itemView.context.getString(R.string.play_station)
        holder.itemView.setOnClickListener { onPlay(station) }
        holder.play.setOnClickListener { onPlay(station) }
        holder.star.setOnClickListener { onToggleFavorite(station) }
    }

    private fun buildMeta(station: Station): String {
        val parts = mutableListOf<String>()
        if (station.codec.isNotBlank()) parts += station.codec
        if (station.bitrate > 0) parts += "${station.bitrate} kbps"
        if (station.tags.isNotBlank()) parts += station.tags.split(',').firstOrNull()?.trim().orEmpty()
        return parts.filter { it.isNotBlank() }.joinToString(" · ")
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Station>() {
            override fun areItemsTheSame(a: Station, b: Station) = a.uuid == b.uuid
            override fun areContentsTheSame(a: Station, b: Station) = a == b
        }
    }
}
