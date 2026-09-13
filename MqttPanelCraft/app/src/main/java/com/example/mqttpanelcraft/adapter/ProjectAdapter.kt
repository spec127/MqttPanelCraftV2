package com.example.mqttpanelcraft.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.mqttpanelcraft.R
import com.example.mqttpanelcraft.model.Project
import com.example.mqttpanelcraft.mqtt.MqttConnectionState
import com.example.mqttpanelcraft.MqttRepository
import com.google.android.material.chip.Chip

class ProjectAdapter(
        private var projects: List<Project>,
        private val onProjectClick: (Project) -> Unit,
        private val onMenuClick: (Project, String) -> Unit
) : RecyclerView.Adapter<ProjectAdapter.ProjectViewHolder>() {

    class ProjectViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvProjectName: TextView = itemView.findViewById(R.id.tvProjectName)
        val tvBrokerUrl: TextView = itemView.findViewById(R.id.tvBrokerUrl)
        val chipType: Chip = itemView.findViewById(R.id.chipType)
        val viewStatus: View = itemView.findViewById(R.id.viewStatus)
        val tvStatusText: TextView = itemView.findViewById(R.id.tvStatusText)
        val ivMenu: ImageView = itemView.findViewById(R.id.ivMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProjectViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_project, parent, false)
        return ProjectViewHolder(view)
    }

    override fun onBindViewHolder(holder: ProjectViewHolder, position: Int) {
        val project = projects[position]
        holder.tvProjectName.text = project.name
        holder.tvBrokerUrl.text = project.broker
        holder.chipType.text =
                when (project.type.name) {
                    "HOME" -> "PANEL"
                    else -> project.type.name
                }

        // Status Dot Color & Text
        val state = if (MqttRepository.activeProjectId == project.id)
                MqttRepository.connectionState.value else MqttConnectionState.IDLE
        val label = when (state) {
            MqttConnectionState.CONNECTED -> R.string.msg_mqtt_connected
            MqttConnectionState.CONNECTING -> R.string.mqtt_notification_connecting
            MqttConnectionState.RECONNECTING -> R.string.mqtt_notification_reconnecting
            MqttConnectionState.FAILED -> R.string.project_msg_mqtt_failed
            else -> R.string.msg_mqtt_disconnected
        }
        val color = when (state) {
            MqttConnectionState.CONNECTED -> android.graphics.Color.rgb(46, 125, 50)
            MqttConnectionState.FAILED -> android.graphics.Color.rgb(198, 40, 40)
            else -> androidx.core.content.ContextCompat.getColor(holder.itemView.context, R.color.prop_text_secondary)
        }
        holder.tvStatusText.setText(label)
        holder.tvStatusText.setTextColor(color)
        holder.viewStatus.setBackgroundResource(R.drawable.shape_circle_green)
        holder.viewStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(color)

        holder.itemView.setOnClickListener { onProjectClick(project) }

        // Handle menu click
        holder.ivMenu.setOnClickListener { view ->
            val popup = PopupMenu(view.context, view)
            popup.menu.add(0, 1, 0, R.string.action_edit)
            popup.menu.add(0, 2, 0, R.string.common_btn_delete)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> onMenuClick(project, "EDIT")
                    2 -> onMenuClick(project, "DELETE")
                }
                true
            }
            popup.show()
        }
    }

    override fun getItemCount() = projects.size

    fun updateData(newProjects: List<Project>) {
        projects = newProjects
        notifyDataSetChanged()
    }
}
