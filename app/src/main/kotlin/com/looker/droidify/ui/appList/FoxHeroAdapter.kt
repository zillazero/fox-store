package com.looker.droidify.ui.appList

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import com.google.android.material.imageview.ShapeableImageView
import com.looker.droidify.R
import com.looker.droidify.model.ProductItem
import com.looker.droidify.model.Repository
import com.looker.droidify.utility.common.extension.authentication
import com.looker.droidify.utility.common.extension.inflate
import com.looker.droidify.utility.common.nullIfEmpty

/** FOX Store: Hero-Bereich „FOX Apps“ oben auf der Startseite (horizontale Karten, TV-fokussierbar). */
class FoxHeroAdapter(
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<FoxHeroAdapter.Holder>() {

    var items: List<ProductItem> = emptyList()
        set(value) {
            val wasVisible = field.isNotEmpty()
            field = value
            cards.items = value
            when {
                wasVisible && value.isEmpty() -> notifyItemRemoved(0)
                !wasVisible && value.isNotEmpty() -> notifyItemInserted(0)
            }
        }

    var repository: Repository? = null
        set(value) {
            field = value
            cards.repository = value
        }

    private val cards = Cards()

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemCount() = if (items.isEmpty()) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = parent.inflate(R.layout.fox_hero_section)
        view.findViewById<RecyclerView>(R.id.fox_hero_list).apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = cards
            setHasFixedSize(false)
        }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = Unit

    private inner class Cards : RecyclerView.Adapter<Cards.CardHolder>() {
        var items: List<ProductItem> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }
        var repository: Repository? = null
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        inner class CardHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon = view.findViewById<ShapeableImageView>(R.id.icon)!!
            val name = view.findViewById<TextView>(R.id.name)!!
            val status = view.findViewById<TextView>(R.id.status)!!

            init {
                // D-Pad/TV: fokussierte Karte hervorheben
                view.setOnFocusChangeListener { v, hasFocus ->
                    val scale = if (hasFocus) 1.08f else 1f
                    v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                    (v as? com.google.android.material.card.MaterialCardView)?.strokeWidth =
                        if (hasFocus) (3 * v.resources.displayMetrics.density).toInt()
                        else (1 * v.resources.displayMetrics.density).toInt()
                }
                view.setOnClickListener {
                    items.getOrNull(bindingAdapterPosition)?.let { onClick(it.packageName) }
                }
            }
        }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            CardHolder(parent.inflate(R.layout.fox_hero_card))

        override fun onBindViewHolder(holder: CardHolder, position: Int) {
            val item = items[position]
            holder.name.text = item.name
            val installed = item.installedVersion.nullIfEmpty()
            holder.status.text = when {
                item.canUpdate -> "Update " + item.version
                installed != null -> "Installiert"
                else -> item.version
            }
            val repo = repository
            if (repo != null) {
                holder.icon.load(item.icon(view = holder.icon, repository = repo)) {
                    authentication(repo.authentication)
                }
            }
        }
    }
}

/** Trenner „Weitere freie Apps“. */
class FoxSectionHeaderAdapter : RecyclerView.Adapter<FoxHeroAdapter.Holder>() {
    var visible: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) notifyItemInserted(0) else notifyItemRemoved(0)
        }

    override fun getItemCount() = if (visible) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        FoxHeroAdapter.Holder(parent.inflate(R.layout.fox_section_header))
    override fun onBindViewHolder(holder: FoxHeroAdapter.Holder, position: Int) = Unit
}
