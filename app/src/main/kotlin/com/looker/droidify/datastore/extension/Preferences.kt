package com.looker.droidify.datastore.extension

import android.content.Context
import android.content.res.Configuration
import com.looker.droidify.datastore.model.SortOrder
import com.looker.droidify.datastore.model.Theme
import com.looker.droidify.utility.common.SdkCheck
import com.looker.droidify.R.string as stringRes
import com.looker.droidify.R.style as styleRes

val Configuration.isNightMode: Boolean
    get() = (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

@Suppress("UNUSED_PARAMETER")
fun Configuration.isDarkTheme(theme: Theme): Boolean = true

@Suppress("UNUSED_PARAMETER")
fun Configuration.isAmoledTheme(theme: Theme): Boolean = false

@Suppress("UNUSED_PARAMETER")
fun Configuration.getThemeRes(theme: Theme, dynamicTheme: Boolean): Int = when (theme) {
    Theme.FOXHOLE -> styleRes.Theme_Fox_Foxhole
    Theme.MAGENTA -> styleRes.Theme_Fox_Magenta
    Theme.AMBER -> styleRes.Theme_Fox_Amber
    Theme.GRUEN -> styleRes.Theme_Fox_Gruen
    Theme.POLIZEI -> styleRes.Theme_Fox_Polizei
}

fun Context?.sortOrderName(sortOrder: SortOrder) = this?.let {
    when (sortOrder) {
        SortOrder.UPDATED -> getString(stringRes.recently_updated)
        SortOrder.ADDED -> getString(stringRes.whats_new)
        SortOrder.NAME -> getString(stringRes.name)
        SortOrder.SIZE -> getString(stringRes.size)
    }
} ?: ""
