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

internal actual val bundledLicenses: List<LicenseText> = listOf(
    LicenseText("Notices", "NOTICE.txt"),
    LicenseText("Gemma Terms of Use", "Gemma-Terms-of-Use.txt"),
    LicenseText("Apache License 2.0", "Apache-2.0.txt"),
    LicenseText("SIL Open Font License 1.1 · Space Grotesk", "SpaceGrotesk-OFL.txt"),
    LicenseText("SIL Open Font License 1.1 · JetBrains Mono", "JetBrainsMono-OFL.txt"),
)
