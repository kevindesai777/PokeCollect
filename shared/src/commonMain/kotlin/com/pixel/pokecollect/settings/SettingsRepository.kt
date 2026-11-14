package com.pixel.pokecollect.settings

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Light or dark: follow the phone, or the person's choice. */
enum class Appearance { System, Light, Dark }

/**
 * A few small values kept across launches: SharedPreferences on Android, NSUserDefaults on iOS.
 * Read synchronously, so the first frame already has the right theme (DataStore is asynchronous,
 * and Room would need its first migration for one value).
 */
interface SettingsStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** The person's settings. Only the appearance so far. */
class SettingsRepository(private val store: SettingsStore) {
    private val _appearance = MutableStateFlow(
        store.getString(APPEARANCE)?.let { saved -> Appearance.entries.firstOrNull { it.name == saved } } ?: Appearance.System,
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setAppearance(value: Appearance) {
        store.putString(APPEARANCE, value.name)
        _appearance.value = value
    }

    private companion object {
        const val APPEARANCE = "appearance"
    }
}
