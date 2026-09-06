package com.example.mqttpanelcraft.ui.components

import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.mqttpanelcraft.R

/** Stable component categories used by the registry, sidebar, and component chrome. */
enum class ComponentGroup(
        @StringRes val titleResId: Int,
        @ColorRes val colorResId: Int,
        @DrawableRes val iconResId: Int
) {
    CONTROL(
            R.string.project_cat_control,
            R.color.vivid_blue,
            android.R.drawable.ic_menu_preferences
    ),
    SENSOR(
            R.string.project_cat_sensor,
            R.color.warm_amber,
            android.R.drawable.ic_menu_compass
    ),
    DISPLAY(
            R.string.project_sidebar_category_display,
            R.color.soft_purple,
            android.R.drawable.ic_menu_gallery
    )
}
