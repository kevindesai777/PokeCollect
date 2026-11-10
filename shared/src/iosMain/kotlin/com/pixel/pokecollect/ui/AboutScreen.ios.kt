package com.pixel.pokecollect.ui

// Apple's Foundation Models is part of iOS: nothing is shipped or downloaded with the app.
internal actual val modelCredits: List<Credit> = emptyList()

// Copied in at build time by the `iosLicenseTexts` task (shared/build.gradle.kts).
internal actual val bundledLicenses: List<LicenseText> = listOf(
    LicenseText("Notices", "NOTICE.txt"),
    LicenseText("Apache License 2.0", "Apache-2.0.txt"),
    LicenseText("SIL Open Font License 1.1 · Space Grotesk", "SpaceGrotesk-OFL.txt"),
    LicenseText("SIL Open Font License 1.1 · JetBrains Mono", "JetBrainsMono-OFL.txt"),
)
