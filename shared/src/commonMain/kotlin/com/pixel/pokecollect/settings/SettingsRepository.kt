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

/** The person's settings: the appearance, and whether catching a card plays its type's effect. */
class SettingsRepository(private val store: SettingsStore) {
    private val _appearance = MutableStateFlow(
        store.getString(APPEARANCE)?.let { saved -> Appearance.entries.firstOrNull { it.name == saved } } ?: Appearance.System,
    )
    val appearance: StateFlow<Appearance> = _appearance.asStateFlow()

    fun setAppearance(value: Appearance) {
        store.putString(APPEARANCE, value.name)
        _appearance.value = value
    }

    // On unless it was switched off: only a saved "false" turns it off.
    private val _catchEffects = MutableStateFlow(store.getString(CATCH_EFFECTS) != false.toString())

    /** Whether ticking a card plays the effect for its type. On by default. */
    val catchEffects: StateFlow<Boolean> = _catchEffects.asStateFlow()

    fun setCatchEffects(on: Boolean) {
        store.putString(CATCH_EFFECTS, on.toString())
        _catchEffects.value = on
    }

    private companion object {
        const val APPEARANCE = "appearance"
        const val CATCH_EFFECTS = "catch_effects"
    }
}
