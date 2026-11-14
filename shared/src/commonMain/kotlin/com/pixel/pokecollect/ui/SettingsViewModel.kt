package com.pixel.pokecollect.ui

import androidx.lifecycle.ViewModel
import com.pixel.pokecollect.settings.Appearance
import com.pixel.pokecollect.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide settings: the theme for every screen, and the controls on the About screen. No Loading
 * state: settings are read synchronously, so the first frame already has them.
 */
class SettingsViewModel(private val settings: SettingsRepository) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance

    fun setAppearance(value: Appearance) = settings.setAppearance(value)
}
