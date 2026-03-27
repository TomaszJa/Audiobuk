package com.example.audiobuk.util

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.example.audiobuk.viewmodel.SortOrder

class PreferenceManager(context: Context) {
    private val prefs = context.getSharedPreferences("audiobuk_prefs", Context.MODE_PRIVATE)

    var rootUri: Uri?
        get() = prefs.getString("root_uri", null)?.toUri()
        set(value) = prefs.edit { putString("root_uri", value.toString()) }

    var sortOrder: SortOrder
        get() = SortOrder.valueOf(prefs.getString("sort_order", SortOrder.ASCENDING.name) ?: SortOrder.ASCENDING.name)
        set(value) = prefs.edit { putString("sort_order", value.name) }

    var isGridView: Boolean
        get() = prefs.getBoolean("is_grid_view", true)
        set(value) = prefs.edit { putBoolean("is_grid_view", value) }

    var hideFinished: Boolean
        get() = prefs.getBoolean("hide_finished", false)
        set(value) = prefs.edit { putBoolean("hide_finished", value) }

    fun isFirstRun(version: String): Boolean {
        val key = "first_run_$version"
        val isFirst = prefs.getBoolean(key, true)
        if (isFirst) {
            prefs.edit { putBoolean(key, false) }
        }
        return isFirst
    }
}
