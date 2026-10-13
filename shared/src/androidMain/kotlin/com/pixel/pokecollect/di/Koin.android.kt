package com.pixel.pokecollect.di

import com.pixel.pokecollect.data.buildDatabase
import com.pixel.pokecollect.data.databaseBuilder
import com.pixel.pokecollect.model.LiteRtLmModel
import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.voice.AndroidSpeechToText
import com.pixel.pokecollect.voice.SpeechToText
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { buildDatabase(databaseBuilder(androidContext())) }
    single<LocalModel> { LiteRtLmModel(androidContext()) }
    single<SpeechToText> { AndroidSpeechToText(androidContext()) }
}
