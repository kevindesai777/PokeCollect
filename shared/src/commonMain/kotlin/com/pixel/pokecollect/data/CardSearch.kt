package com.pixel.pokecollect.data

/**
 * Finds cards from what's typed in the search bar, without the model: finding one card by number
 * or name is a lookup, not a question.
 *
 * A dex number ("883", "#0883", "no. 883") matches that card only. Anything else matches names
 * containing it, ignoring case, accents and punctuation ("mr mime" finds Mr. Mime, "farfetchd"
 * finds Farfetch’d), with names that start with it first. Text with no letters or digits matches
 * everything.
 */
internal fun searchCards(entries: List<PokemonWithCollection>, text: String): List<PokemonWithCollection> {
    DexNumber.matchEntire(text.trim().lowercase())?.let { match ->
        val id = match.groupValues[1].toInt()
        return entries.filter { it.pokemon.id == id }
    }
    val wanted = searchKey(text)
    if (wanted.isEmpty()) return entries
    return entries
        .map { it to searchKey(it.pokemon.name) }
        .filter { (_, name) -> wanted in name }
        .sortedBy { (_, name) -> if (name.startsWith(wanted)) 0 else 1 } // stable: dex order within each group
        .map { (entry, _) -> entry }
}

private val DexNumber = Regex("""(?:#|no\.?)?\s*0*(\d{1,5})""")

private const val Accented = "àáâäèéêëìíîïòóôöùúûü"
private const val Plain = "aaaaeeeeiiiioooouuuu"

/** Lowercase letters and digits only, accents folded: "Flabébé" → "flabebe", "Mr. Mime" → "mrmime". */
private fun searchKey(text: String): String = buildString {
    for (c in text.lowercase()) {
        val folded = Accented.indexOf(c).let { if (it >= 0) Plain[it] else c }
        if (folded.isLetterOrDigit()) append(folded)
    }
}
