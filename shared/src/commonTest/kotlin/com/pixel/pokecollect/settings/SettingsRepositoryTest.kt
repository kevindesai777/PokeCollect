package com.pixel.pokecollect.settings

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsRepositoryTest {
    private class MapStore(val values: MutableMap<String, String> = mutableMapOf()) : SettingsStore {
        override fun getString(key: String) = values[key]
        override fun putString(key: String, value: String) {
            values[key] = value
        }
    }

    @Test
    fun followsThePhoneUntilSomethingIsChosen() {
        assertEquals(Appearance.System, SettingsRepository(MapStore()).appearance.value)
    }

    @Test
    fun theChoiceSurvivesARestart() {
        val store = MapStore()
        SettingsRepository(store).setAppearance(Appearance.Dark)
        assertEquals(Appearance.Dark, SettingsRepository(store).appearance.value)
    }

    @Test
    fun anUnknownSavedValueFallsBackToSystem() {
        val store = MapStore(mutableMapOf("appearance" to "Sepia"))
        assertEquals(Appearance.System, SettingsRepository(store).appearance.value)
    }
}
