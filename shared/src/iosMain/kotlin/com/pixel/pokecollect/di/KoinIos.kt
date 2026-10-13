package com.pixel.pokecollect.di

import com.pixel.pokecollect.data.buildDatabase
import com.pixel.pokecollect.data.databaseBuilder
import com.pixel.pokecollect.model.LocalModel
import com.pixel.pokecollect.model.NativeLanguageModel
import com.pixel.pokecollect.model.SwiftLocalModel
import com.pixel.pokecollect.voice.NativeSpeechRecognizer
import com.pixel.pokecollect.voice.SpeechToText
import com.pixel.pokecollect.voice.SwiftSpeechToText
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { buildDatabase(databaseBuilder()) }
}

/**
 * Swift entry point: `KoinIosKt.doInitKoinForIos(model: FoundationModelsBridge(), speech: SpeechRecognizerBridge())`.
 * The class name comes from this file name; default arguments do not cross into Swift. The model and
 * the recognizer are passed in because Swift drives Foundation Models and the Speech framework.
 */
fun initKoinForIos(model: NativeLanguageModel, speech: NativeSpeechRecognizer) = initKoin {
    modules(
        module {
            single<LocalModel> { SwiftLocalModel(model) }
            single<SpeechToText> { SwiftSpeechToText(speech) }
        },
    )
}
