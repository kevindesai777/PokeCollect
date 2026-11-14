package com.pixel.pokecollect.settings

import platform.Foundation.NSUserDefaults

class UserDefaultsSettingsStore : SettingsStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String) = defaults.setObject(value, forKey = key)
}
