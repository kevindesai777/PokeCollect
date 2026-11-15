package com.pixel.pokecollect.data

/** Which cards the collection list shows, by whether they're caught. */
enum class CaughtFilter {
    All, Have, Missing;

    fun matches(caught: Boolean): Boolean = when (this) {
        All -> true
        Have -> caught
        Missing -> !caught
    }
}

/**
 * The list's order. Card numbers run generation by generation, so number order is generation
 * order too; there is no separate "by generation" order.
 */
enum class CardOrder { NumberUp, NumberDown, NameUp, NameDown }

/** What the collection list shows. The defaults are where every launch starts. */
data class CardView(
    /** Null for every generation. */
    val generation: Int? = null,
    val show: CaughtFilter = CaughtFilter.All,
    val order: CardOrder = CardOrder.NumberUp,
)

/**
 * The cards [view] picks out of [cards], in its order.
 *
 * [keep] holds cards ticked or unticked in the list since the view last changed. They stay even
 * when they no longer match [CardView.show], so with Missing on a card doesn't vanish from under
 * the finger the moment it's ticked. They still have to be in the chosen generation.
 *
 * Names sort the way search compares them (case, accents and punctuation ignored: Flabébé sits
 * between Flaaffy and Flapple), ties by number (Nidoran♀ before Nidoran♂). Z–A is A–Z reversed.
 */
internal fun applyView(
    cards: List<PokemonWithCollection>,
    view: CardView,
    keep: Set<Int> = emptySet(),
): List<PokemonWithCollection> {
    val shown = cards.filter { card ->
        (view.generation == null || card.pokemon.generation == view.generation) &&
            (view.show.matches(card.caught) || card.pokemon.id in keep)
    }
    return when (view.order) {
        CardOrder.NumberUp -> shown.sortedBy { it.pokemon.id }
        CardOrder.NumberDown -> shown.sortedByDescending { it.pokemon.id }
        CardOrder.NameUp -> byName(shown)
        CardOrder.NameDown -> byName(shown).asReversed()
    }
}

private fun byName(cards: List<PokemonWithCollection>): List<PokemonWithCollection> = cards
    .map { it to searchKey(it.pokemon.name) }
    .sortedWith(compareBy({ it.second }, { it.first.pokemon.id }))
    .map { it.first }
