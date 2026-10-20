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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.pokecollect.ui.theme.PokeColors
import com.pixel.pokecollect.ui.theme.PokeShapes
import com.pixel.pokecollect.ui.theme.brutal
import com.pixel.pokecollect.ui.theme.monoFontFamily
import kotlinx.coroutines.CancellationException
import pokecollect.shared.generated.resources.Res

/** One line on the About screen: what, its licence or notice, and where to read it. */
internal data class Credit(val name: String, val detail: String, val url: String? = null)

/**
 * What ships with the platform's model. Android: Gemma's notice and terms (the Gemma Terms of Use
 * require both when the model is redistributed) and the Play library that downloads it. iOS: the
 * OS owns the model, so nothing.
 */
internal expect val modelCredits: List<Credit>

/** A licence or notice shipped inside the app, as `files/licenses/<file>` in the Compose resources. */
internal data class LicenseText(val title: String, val file: String)

/**
 * The full texts the app carries. Android: the NOTICE, the Gemma Terms of Use (required when Gemma
 * is redistributed), the Apache License and the font licences, copied in at build time (task
 * `licenseTexts` in shared/build.gradle.kts). iOS: none yet.
 */
internal expect val bundledLicenses: List<LicenseText>

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
@OptIn(ExperimentalComposeUiApi::class) // BackHandler
@Composable
fun AboutScreen(onBack: () -> Unit) {
    // A licence text opens over the list; Back returns to the list.
    var openFile by rememberSaveable { mutableStateOf<String?>(null) }
    // Outlives the licence view, so Back returns to the same place in the list.
    val listState = rememberLazyListState()
    val open = bundledLicenses.firstOrNull { it.file == openFile }
    if (open != null) {
        BackHandler { openFile = null }
        LicenseTextScreen(open, onBack = { openFile = null })
        return
    }
    LazyColumn(
        state = listState,
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
        if (bundledLicenses.isNotEmpty()) {
            item {
                Section("Licence texts") {
                    bundledLicenses.forEach { license -> LicenseRow(license.title) { openFile = license.file } }
                }
            }
        }
    }
}

@Composable
private fun LicenseTextScreen(license: LicenseText, onBack: () -> Unit) {
    val text by produceState<String?>(null, license.file) {
        value = try {
            reflow(Res.readBytes("files/licenses/${license.file}").decodeToString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "Couldn't open ${license.file}."
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(PokeColors.Background).safeDrawingPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { TopBar(license.title, onBack) }
        item {
            Section(license.file) {
                text?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall.copy(fontFamily = monoFontFamily(), fontSize = 12.sp, lineHeight = 17.sp))
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Read", modifier = Modifier.size(20.dp))
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

private val Separator = Regex("""^\s*[-=*_]{3,}\s*$""")
private val ListItem = Regex("""^\s*([-*•]|\(?[A-Za-z0-9]{1,3}[.)])\s""")

/**
 * Licence files are hard-wrapped at 70–100 columns; a phone shows ~55, so every line would break
 * twice. Rejoins the lines of each paragraph and keeps the breaks that mean something: headings
 * and other short lines, separators, list items and lines ending in ":".
 */
internal fun reflow(text: String): String {
    val lines = text.replace("\r\n", "\n").lines()
    val out = StringBuilder()
    for ((i, line) in lines.withIndex()) {
        out.append(if (out.isEmpty() || out.endsWith("\n")) line else line.trimStart())
        val next = lines.getOrNull(i + 1) ?: break
        val join = line.trim().length >= 40 && next.isNotBlank() &&
            !line.trimEnd().endsWith(":") && !Separator.matches(line) && !Separator.matches(next) && !ListItem.containsMatchIn(next)
        out.append(if (join) " " else "\n")
    }
    return out.toString()
}
