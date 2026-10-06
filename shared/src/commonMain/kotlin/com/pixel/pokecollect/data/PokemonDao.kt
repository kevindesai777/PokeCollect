package com.pixel.pokecollect.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PokemonDao {

    @Query(
        """
        SELECT p.*, COALESCE(c.caught, 0) AS caught
        FROM pokemon p LEFT JOIN collection c ON c.pokemonId = p.id
        ORDER BY p.id
        """
    )
    abstract fun observeAll(): Flow<List<PokemonWithCollection>>

    /** Answers a [com.pixel.pokecollect.query.CollectionQuery]. A null argument means "any". */
    @Query(
        """
        SELECT p.*, COALESCE(c.caught, 0) AS caught
        FROM pokemon p LEFT JOIN collection c ON c.pokemonId = p.id
        WHERE (:generation IS NULL OR p.generation = :generation)
          AND (:type IS NULL OR p.type1 = :type OR p.type2 = :type)
          AND (:caught IS NULL OR COALESCE(c.caught, 0) = :caught)
        ORDER BY p.id
        """
    )
    abstract fun observeMatching(generation: Int?, type: String?, caught: Boolean?): Flow<List<PokemonWithCollection>>

    @Query("SELECT MAX(generation) FROM pokemon")
    abstract suspend fun maxGeneration(): Int?

    @Query("SELECT type1 FROM pokemon UNION SELECT type2 FROM pokemon WHERE type2 IS NOT NULL ORDER BY 1")
    abstract suspend fun types(): List<String>

    @Query("SELECT value FROM dataset_meta WHERE `key` = 'version'")
    abstract suspend fun datasetVersion(): String?

    @Upsert
    abstract suspend fun upsertCollection(entry: CollectionEntity)

    @Upsert
    protected abstract suspend fun upsertPokemon(pokemon: List<PokemonEntity>)

    @Upsert
    protected abstract suspend fun upsertMeta(meta: DatasetMetaEntity)

    @Query("DELETE FROM pokemon WHERE id NOT IN (:keep)")
    protected abstract suspend fun deletePokemonNotIn(keep: List<Int>)

    /** Swap in a new reference dataset atomically. The collection table is left alone. */
    @Transaction
    open suspend fun replaceDataset(version: String, pokemon: List<PokemonEntity>) {
        upsertPokemon(pokemon)
        deletePokemonNotIn(pokemon.map { it.id })
        upsertMeta(DatasetMetaEntity("version", version))
    }
}
