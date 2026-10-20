package com.pixel.pokecollect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal

/** One line on the About screen: what, its licence or notice, and where to read it. */
internal data class Credit(val name: String, val detail: String, val url: String? = null)

/**
 * What ships with the platform's model. Android: Gemma's notice and terms (the Gemma Terms of Use
 * require both when the model is redistributed) and the Play library that downloads it. iOS: the
 * OS owns the model, so nothing.
 */
internal expect val modelCredits: List<Credit>

private const val PRIVACY_POLICY_URL = "https://github.com/kevindesai777/PokeCollect/blob/main/play/PRIVACY.md"

// Where the Gemma use restrictions become a term of using the app.
private const val TERMS_URL = "https://github.com/kevindesai777/PokeCollect/blob/main/play/TERMS.md"

private val Credits = listOf(
    Credit("PokeCollect", "Apache License 2.0. Copyright 2026 Kevin Desai.", "https://github.com/kevindesai777/PokeCollect"),
    Credit("Pokémon data", "PokéAPI, BSD 3-Clause License.", "https://github.com/PokeAPI/pokeapi"),
    Credit("Sprites", "From PokeAPI/sprites. Pokémon sprites © 1995–2026 Nintendo / Creatures Inc. / GAME FREAK inc.", "https://github.com/PokeAPI/sprites"),
    Credit("Space Grotesk, JetBrains Mono", "SIL Open Font License 1.1.", "https://openfontlicense.org"),
    Credit(
        "Compose Multiplatform, AndroidX (Room, SQLite, Lifecycle), Kotlin libraries, Koin",
        "Apache License 2.0.",
        "https://www.apache.org/licenses/LICENSE-2.0",
    ),
    Credit("neubrutalism-compose-multiplatform", "MIT License.", "https://github.com/tamtom/neubrutalism-compose-multiplatform"),
)

/** The fan-app disclaimer, what happens to your data, and the licences. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PokeColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar("About", onBack) }
        item {
            Section("Unofficial fan app") {
                Body(
                    "PokeCollect is a free, unofficial fan app. It isn't affiliated with, endorsed or sponsored by " +
                        "Nintendo, Creatures Inc., GAME FREAK or The Pokémon Company. Pokémon and Pokémon character " +
                        "names are trademarks of their owners.",
                )
            }
        }
        item {
            Section("Your data") {
                Body(
                    "PokeCollect collects nothing. Your collection, your questions and your voice stay on this phone: " +
                        "speech is recognized on the device and the model runs on the device.",
                )
                Link(Credit("Privacy policy", "", PRIVACY_POLICY_URL))
                Link(Credit("Terms of use", "Including the restrictions on using Gemma.", TERMS_URL))
            }
        }
        if (modelCredits.isNotEmpty()) {
            item { Section("On-device model") { modelCredits.forEach { Link(it) } } }
        }
        item { Section("Open source") { Credits.forEach { Link(it) } } }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .brutal(PokeColors.Surface, PokeShapes.Large, offset = 4.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Body(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/** A credit; tapping it opens [Credit.url] in the browser. */
@Composable
private fun Link(credit: Credit) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (credit.url != null) Modifier.clickable { uriHandler.openUri(credit.url) } else Modifier),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(credit.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            if (credit.detail.isNotEmpty()) {
                Text(credit.detail, style = MaterialTheme.typography.bodySmall, color = PokeColors.OnSurfaceVariant)
            }
        }
        if (credit.url != null) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(18.dp))
        }
    }
}
