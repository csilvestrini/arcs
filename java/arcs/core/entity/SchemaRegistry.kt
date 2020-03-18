/*
 * Copyright 2020 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */
package arcs.core.entity

import arcs.core.data.Schema

typealias SchemaHash = String

/**
 * A registry for generated [Schema]s and [EntitySpec]s.
 */
object SchemaRegistry {
    private val schemas = mutableMapOf<SchemaHash, Schema>()
    private val entitySpecs = mutableMapOf<SchemaHash, EntitySpec<out Entity>>()

    /** Stores a [Schema] and [EntitySpec] in the registry. */
    fun register(entitySpec: EntitySpec<out Entity>) {
        val schema = entitySpec.SCHEMA
        schemas[schema.hash] = schema
        entitySpecs[schema.hash] = entitySpec
    }

    /** Returns the [Schema] for the given [SchemaHash], null otherwise. */
    fun getSchema(hash: SchemaHash) = schemas[hash]

    /** Returns the [EntitySpec] for the given [SchemaHash], null otherwise. */
    fun getEntitySpec(hash: SchemaHash) = entitySpecs[hash]

    /** Clears the registry, for testing purposes. */
    fun clearForTest() {
        schemas.clear()
        entitySpecs.clear()
    }
}
