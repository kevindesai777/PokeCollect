package com.pixel.pokecollect.data

import com.pixel.pokecollect.query.Catalog
import com.pixel.pokecollect.query.CollectionQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** The only class that knows about Room. ViewModels talk to this, never to the DAO. */
class CollectionRepository(private val dao: PokemonDao) {

    private val seeder = DatasetSeeder(dao)

    /**
     * Every species with its caught flag, re-emitted whenever either table changes.
     * Seeds the bundled dataset first, so the first emission is always the full list.
     */
    fun observeCollection(): Flow<List<PokemonWithCollection>> = flow {
        seeder.seedIfNeeded()
        emitAll(dao.observeAll())
    }

    /** Live results for a planned query; re-emits when the collection changes. */
    fun observeMatching(query: CollectionQuery): Flow<List<PokemonWithCollection>> =
        dao.observeMatching(query.generation, query.type, query.caught)

    suspend fun catalog(): Catalog {
        seeder.seedIfNeeded()
        return Catalog(maxGeneration = dao.maxGeneration() ?: 0, types = dao.types())
    }

    suspend fun setCaught(pokemonId: Int, caught: Boolean) {
        dao.upsertCollection(CollectionEntity(pokemonId, caught))
    }
}
