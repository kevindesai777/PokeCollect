package com.pixel.pokecollect.ui

internal actual val modelCredits: List<Credit> = listOf(
    // The notice text is fixed by the Gemma Terms of Use (section 3.1).
    Credit(
        "Gemma 3 1B",
        "Gemma is provided under and subject to the Gemma Terms of Use found at ai.google.dev/gemma/terms",
        "https://ai.google.dev/gemma/terms",
    ),
    Credit(
        "Gemma Prohibited Use Policy",
        "Using Gemma in PokeCollect means agreeing not to use it in the ways this policy lists.",
        "https://ai.google.dev/gemma/prohibited_use_policy",
    ),
    Credit("LiteRT-LM", "Runs Gemma on the phone. Apache License 2.0.", "https://github.com/google-ai-edge/LiteRT-LM"),
    Credit(
        "Play AI delivery",
        "Downloads the model from Google Play. Play Core Software Development Kit Terms of Service.",
        "https://developer.android.com/guide/playcore/license",
    ),
)
