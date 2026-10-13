package com.pixel.pokecollect.di

import com.pixel.pokecollect.data.AppDatabase
import com.pixel.pokecollect.data.CollectionRepository
import com.pixel.pokecollect.query.QueryPlanner
import com.pixel.pokecollect.ui.AskViewModel
import com.pixel.pokecollect.ui.CollectionViewModel
import com.pixel.pokecollect.voice.CardResolver
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/**
 * Provides the [AppDatabase], the [com.pixel.pokecollect.model.LocalModel] and the
 * [com.pixel.pokecollect.voice.SpeechToText]: the things only a platform can build.
 */
expect val platformModule: Module

val dataModule = module {
    single { get<AppDatabase>().pokemonDao() }
    singleOf(::CollectionRepository)
    single { QueryPlanner(get()) } // explicit: singleOf can't use the constructor's default argument
    singleOf(::CardResolver)
}

val viewModelModule = module {
    viewModelOf(::CollectionViewModel)
    viewModelOf(::AskViewModel)
}

/** Call once at process start: from the Application on Android, from the SwiftUI App on iOS. */
fun initKoin(config: KoinAppDeclaration = {}) {
    startKoin {
        config()
        modules(platformModule, dataModule, viewModelModule)
    }
}
