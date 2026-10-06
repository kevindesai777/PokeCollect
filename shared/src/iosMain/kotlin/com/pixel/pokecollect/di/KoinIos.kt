package com.pixel.pokecollect.di

import com.pixel.pokecollect.data.buildDatabase
import com.pixel.pokecollect.data.databaseBuilder
import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.model.NativeLanguageModel
import com.pixel.pokecollect.model.SwiftLocalModel
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { buildDatabase(databaseBuilder()) }
}

/**
 * Swift entry point: `KoinIosKt.doInitKoinForIos(model: FoundationModelsBridge())`. The class name
 * comes from this file name; default arguments do not cross into Swift. The model is passed in
 * because only Swift can talk to Foundation Models.
 */
fun initKoinForIos(model: NativeLanguageModel) = initKoin {
    modules(module { single<LocalModel> { SwiftLocalModel(model) } })
}
