package com.example.mqttpanelcraft.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogAdapter : RecyclerView.Adapter<LogAdapter.LogViewHolder>() {

    class ChunkSubItem(
        val idx: Int,
        val total: Int,
        val dataPart: String,
        val rawText: String,
        var isExpanded: Boolean = false
    ) {
        val dataLen: Int get() = dataPart.length
    }

    class ChunkPacketGroup(
        val prefix: String,
        val total: Int,
        var latestIdx: Int,
        val subItems: MutableList<ChunkSubItem> = mutableListOf(),
        var isExpanded: Boolean = false
    )

    class StandardEntry(
        val prefix: String,
        val fullText: String,
        val isCollapsible: Boolean,
        var isExpanded: Boolean = false
    )

    sealed class TopLevelItem {
        data class Group(val group: ChunkPacketGroup) : TopLevelItem()
        data class Standard(val entry: StandardEntry) : TopLevelItem()
    }

    sealed class DisplayItem {
        data class Layer1(val group: ChunkPacketGroup) : DisplayItem()
        data class Layer2(val group: ChunkPacketGroup, val sub: ChunkSubItem) : DisplayItem()
        data class Layer3(val sub: ChunkSubItem) : DisplayItem()
        data class Normal(val entry: StandardEntry) : DisplayItem()
    }

    private val topItems = mutableListOf<TopLevelItem>()

    fun addLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val raw = "[$timestamp] $message"

        val chunkIdx = raw.indexOf("CHUNK:")
        if (chunkIdx != -1) {
            try {
                val prefix = raw.substring(0, chunkIdx)
                val chunkPart = raw.substring(chunkIdx + 6)
                val colonIdx = chunkPart.indexOf(':')
                if (colonIdx != -1) {
                    val header = chunkPart.substring(0, colonIdx)
                    val dataPart = chunkPart.substring(colonIdx + 1)
                    val parts = header.split("/")
                    val idx = parts[0].toIntOrNull() ?: 1
                    val total = parts.getOrNull(1)?.toIntOrNull() ?: 1

                    val sub = ChunkSubItem(idx, total, dataPart, raw)
                    val lastTop = topItems.lastOrNull()

                    if (lastTop is TopLevelItem.Group && lastTop.group.total == total && lastTop.group.prefix == prefix) {
                        lastTop.group.subItems.add(sub)
                        lastTop.group.latestIdx = idx
                    } else {
                        val newGroup = ChunkPacketGroup(prefix, total, idx, mutableListOf(sub))
                        topItems.add(TopLevelItem.Group(newGroup))
                    }
                    trimTopItems()
                    notifyDataSetChanged()
                    return
                }
            } catch (_: Exception) {}
        }

        if (raw.length > 150) {
            val prefixEnd = raw.indexOf("]: ")
            val prefix = if (prefixEnd != -1) raw.substring(0, prefixEnd + 3) else raw.substring(0, kotlin.math.min(30, raw.length))
            val std = StandardEntry(prefix, raw, true, false)
            topItems.add(TopLevelItem.Standard(std))
        } else {
            val std = StandardEntry("", raw, false, false)
            topItems.add(TopLevelItem.Standard(std))
        }
        trimTopItems()
        notifyDataSetChanged()
    }

    private fun trimTopItems() {
        if (topItems.size > 150) {
            topItems.removeAt(0)
        }
    }

    fun setLogs(newLogs: List<String>) {
        topItems.clear()
        for (log in newLogs) {
            // Re-feed through addLog logic without timestamping if already formatted
            val cleanMsg = if (log.startsWith("[")) log.substringAfter("] ").trim() else log
            addLog(cleanMsg)
        }
    }

    fun getAllLogs(): String {
        val sb = StringBuilder()
        for (item in topItems) {
            when (item) {
                is TopLevelItem.Group -> {
                    for (sub in item.group.subItems) {
                        sb.appendLine(sub.rawText)
                    }
                }
                is TopLevelItem.Standard -> {
                    sb.appendLine(item.entry.fullText)
                }
            }
        }
        return sb.toString().trimEnd()
    }

    private fun getDisplayItems(): List<DisplayItem> {
        val list = mutableListOf<DisplayItem>()
        for (item in topItems) {
            when (item) {
                is TopLevelItem.Group -> {
                    val g = item.group
                    list.add(DisplayItem.Layer1(g))
                    if (g.isExpanded) {
                        for (sub in g.subItems) {
                            list.add(DisplayItem.Layer2(g, sub))
                            if (sub.isExpanded) {
                                list.add(DisplayItem.Layer3(sub))
                            }
                        }
                    }
                }
                is TopLevelItem.Standard -> {
                    list.add(DisplayItem.Normal(item.entry))
                }
            }
        }
        return list
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
        return LogViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogViewHolder, position: Int) {
        val displayList = getDisplayItems()
        if (position >= displayList.size) return
        val item = displayList[position]
        holder.textView.textSize = 12f

        when (item) {
            is DisplayItem.Layer1 -> {
                val g = item.group
                if (g.isExpanded) {
                    holder.textView.text = "${g.prefix}[影像分片傳輸 ${g.latestIdx}/${g.total} - 點擊收納清單]"
                } else {
                    holder.textView.text = "${g.prefix}[影像分片傳輸 ${g.latestIdx}/${g.total} - 點擊展開 ${g.total} 筆分片清單]"
                }
                holder.textView.setTextColor(Color.parseColor("#4DD0E1"))
                holder.itemView.setOnClickListener {
                    g.isExpanded = !g.isExpanded
                    notifyDataSetChanged()
                }
            }
            is DisplayItem.Layer2 -> {
                val sub = item.sub
                if (sub.isExpanded) {
                    holder.textView.text = "  [-] [分片 ${sub.idx}/${sub.total} (${sub.dataLen}B) - 點擊收納內容]"
                } else {
                    holder.textView.text = "  [+] [分片 ${sub.idx}/${sub.total} (${sub.dataLen}B) - 點擊展開內容]"
                }
                holder.textView.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.sidebar_text_primary))
                holder.itemView.setOnClickListener {
                    sub.isExpanded = !sub.isExpanded
                    notifyDataSetChanged()
                }
            }
            is DisplayItem.Layer3 -> {
                val sub = item.sub
                holder.textView.text = "    -> ${sub.rawText}"
                holder.textView.setTextColor(Color.parseColor("#B0BEC5"))
                holder.itemView.setOnClickListener {
                    sub.isExpanded = !sub.isExpanded
                    notifyDataSetChanged()
                }
            }
            is DisplayItem.Normal -> {
                val entry = item.entry
                if (entry.isCollapsible) {
                    if (entry.isExpanded) {
                        holder.textView.text = "${entry.fullText}\n[點擊收納]"
                        holder.textView.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.sidebar_text_primary))
                    } else {
                        holder.textView.text = "${entry.prefix}[長篇幅資料 (${entry.fullText.length}B) - 點擊展開]"
                        holder.textView.setTextColor(Color.parseColor("#4DD0E1"))
                    }
                    holder.itemView.setOnClickListener {
                        entry.isExpanded = !entry.isExpanded
                        notifyDataSetChanged()
                    }
                } else {
                    holder.textView.text = entry.fullText
                    holder.textView.setTextColor(androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.sidebar_text_primary))
                    holder.itemView.setOnClickListener(null)
                }
            }
        }
    }

    override fun getItemCount(): Int = getDisplayItems().size

    class LogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(android.R.id.text1)
    }
}
