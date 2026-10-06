package com.pixel.pokecollect

import android.app.Application
import com.pixel.pokecollect.di.initKoin
import org.koin.android.ext.koin.androidContext

class PokeCollectApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@PokeCollectApplication) }
    }
}
