package com.pixel.pokecollect.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Reference data. Replaced wholesale when the bundled dataset version changes. */
@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val slug: String,
    val generation: Int,
    val type1: String,
    val type2: String?,
    val imageUrl: String,
)

/** User data. Never touched by a dataset upgrade. */
@Entity(tableName = "collection")
data class CollectionEntity(
    @PrimaryKey val pokemonId: Int,
    val caught: Boolean,
)

@Entity(tableName = "dataset_meta")
data class DatasetMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

data class PokemonWithCollection(
    @Embedded val pokemon: PokemonEntity,
    val caught: Boolean,
)
