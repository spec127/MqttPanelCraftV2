package com.example.mqttpanelcraft.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.ComponentData
import java.util.Locale

class TopicAdapter(
    context: Context,
    private var items: List<ComponentData>,
    private val anchorView: android.widget.AutoCompleteTextView? = null
) : ArrayAdapter<ComponentData>(context, R.layout.item_topic_dropdown, items) {

    private val inflater = LayoutInflater.from(context)
    private var filteredItems: List<ComponentData> = items

    init {
        updatePopupBounds()
    }

    fun updateData(newItems: List<ComponentData>) {
        this.items = newItems
        this.filteredItems = newItems
        updatePopupBounds()
        notifyDataSetChanged()
    }

    private fun updatePopupBounds() {
        val atv = anchorView ?: return
        val count = filteredItems.size
        if (count == 0) return

        val density = context.resources.displayMetrics.density
        val visibleCount = minOf(count, 5)
        // Each item in item_topic_dropdown is exactly 42dp high
        val itemHeightPx = (42 * density).toInt()
        val totalHeightPx = visibleCount * itemHeightPx

        val atvHeightPx = if (atv.height > 0) atv.height else (40 * density).toInt()
        val gapPx = (4 * density).toInt()

        atv.dropDownHeight = totalHeightPx
        // Negative vertical offset makes the dropdown pop upwards above the input box
        atv.dropDownVerticalOffset = -(totalHeightPx + atvHeightPx + gapPx)
    }

    override fun getCount(): Int = filteredItems.size
    override fun getItem(position: Int): ComponentData? = filteredItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_topic_dropdown, parent, false)
        val item = getItem(position) ?: return view

        val tvTopic = view.findViewById<TextView>(R.id.tvDropdownTopic)
        val tvLabel = view.findViewById<TextView>(R.id.tvDropdownLabel)

        tvTopic.text = item.topicConfig
        tvLabel.text = item.label

        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.lowercase(Locale.getDefault()) ?: ""
                val results = FilterResults()
                
                val matches = if (query.isEmpty()) {
                    items
                } else {
                    items.filter {
                        it.topicConfig.lowercase(Locale.getDefault()).contains(query) ||
                        it.label.lowercase(Locale.getDefault()).contains(query)
                    }
                }
                
                results.values = matches
                results.count = matches.size
                return results
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems = results?.values as? List<ComponentData> ?: emptyList()
                updatePopupBounds()
                if (results != null && results.count > 0) {
                    notifyDataSetChanged()
                } else {
                    notifyDataSetInvalidated()
                }
            }
            
            override fun convertResultToString(resultValue: Any?): CharSequence {
                return (resultValue as? ComponentData)?.topicConfig ?: ""
            }
        }
    }
}
