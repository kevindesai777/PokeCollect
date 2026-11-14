package com.pixel.pokecollect.settings

import android.content.Context

/** `settings.xml` in shared_prefs; backed up with the database (data_extraction_rules.xml). */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
}
