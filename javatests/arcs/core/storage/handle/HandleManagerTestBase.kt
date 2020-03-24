package arcs.core.storage.handle

import arcs.core.data.FieldType
import arcs.core.data.RawEntity
import arcs.core.data.Schema
import arcs.core.data.SchemaFields
import arcs.core.data.SchemaName
import arcs.core.data.util.toReferencable
import arcs.core.entity.Entity
import arcs.core.entity.EntityDereferencerFactory
import arcs.core.entity.EntitySpec
import arcs.core.entity.SchemaRegistry
import arcs.core.storage.DriverFactory
import arcs.core.storage.Reference
import arcs.core.storage.StorageKey
import arcs.core.storage.driver.RamDisk
import arcs.core.storage.driver.RamDiskDriverProvider
import arcs.core.storage.keys.RamDiskStorageKey
import arcs.core.storage.referencemode.ReferenceModeStorageKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@Suppress("EXPERIMENTAL_API_USAGE")
open class HandleManagerTestBase {
    private val backingKey = RamDiskStorageKey("entities")

    val entity1 = RawEntity(
        "entity1",
        singletons = mapOf(
            "name" to "Jason".toReferencable(),
            "age" to 21.toReferencable(),
            "is_cool" to false.toReferencable(),
            "best_friend" to Reference("entity2", backingKey, null),
            "hat" to null
        ),
        collections = emptyMap()
    )

    val entity2 = RawEntity(
        "entity2",
        singletons = mapOf(
            "name" to "Jason".toReferencable(),
            "age" to 22.toReferencable(),
            "is_cool" to true.toReferencable(),
            "best_friend" to Reference("entity1", backingKey, null),
            "hat" to null
        ),
        collections = emptyMap()
    )

    private val schema = Schema(
        setOf(SchemaName("Person")),
        SchemaFields(
            singletons = mapOf(
                "name" to FieldType.Text,
                "age" to FieldType.Number,
                "is_cool" to FieldType.Boolean,
                "best_friend" to FieldType.EntityRef("person-hash"),
                "hat" to FieldType.EntityRef("hat-hash")
            ),
            collections = emptyMap()
        ),
        "person-hash"
    )
    private val entitySpec = object : EntitySpec<Entity> {
        override fun deserialize(data: RawEntity) = throw NotImplementedError()

        override val SCHEMA = schema
    }

    private val hatSchema = Schema(
        listOf(SchemaName("Hat")),
        SchemaFields(
            singletons = mapOf("style" to FieldType.Text),
            collections = emptyMap()
        ),
        "hat-hash"
    )
    private val hatEntitySpec = object : EntitySpec<Entity> {
        override fun deserialize(data: RawEntity) = throw NotImplementedError()

        override val SCHEMA = hatSchema
    }

    private val singletonRefKey = RamDiskStorageKey("single-ent")
    private val singletonKey = ReferenceModeStorageKey(
        backingKey = backingKey,
        storageKey = singletonRefKey
    )

    private val collectionRefKey = RamDiskStorageKey("set-ent")
    private val collectionKey = ReferenceModeStorageKey(
        backingKey = backingKey,
        storageKey = collectionRefKey
    )

    private val hatCollectionRefKey = RamDiskStorageKey("set-ent")
    private val hatCollectionKey = ReferenceModeStorageKey(
        backingKey = backingKey,
        storageKey = hatCollectionRefKey
    )


    lateinit var readHandleManager: HandleManager
    lateinit var writeHandleManager: HandleManager

    open var testRunner = { block: suspend CoroutineScope.() -> Unit ->
        runBlockingTest { this.block() }
    }

    @Before
    open fun setUp() {
        SchemaRegistry.register(entitySpec)
        SchemaRegistry.register(hatEntitySpec)
        DriverFactory.register(RamDiskDriverProvider())
    }

    @After
    open fun tearDown() {
        RamDisk.clear()
        DriverFactory.clearRegistrations()
        SchemaRegistry.clearForTest()
    }

    @Test
    fun singleton_writeAndReadBack() = testRunner {
        val writeHandle = createSingletonHandle(writeHandleManager, singletonKey)
        writeHandle.store(entity1)

        // Now read back from a different handle
        val readHandle = createSingletonHandle(readHandleManager, singletonKey)
        val readBack = readHandle.fetch()
        assertThat(readBack).isEqualTo(entity1)
    }

    @Test
    open fun singleton_writeAndOnUpdate() = testRunner {
        val writeHandle = createSingletonHandle(writeHandleManager, singletonKey)

        // Now read back from a different handle
        val readHandle = createSingletonHandle(readHandleManager, singletonKey)
        val updateDeferred = CompletableDeferred<RawEntity?>()
        readHandle.addOnUpdate {
            updateDeferred.complete(it)
        }
        writeHandle.store(entity1)
        assertThat(updateDeferred.await()).isEqualTo(entity1)
    }

    @Test
    open fun singleton_referenceLiveness() = testRunner {
        val writeHandle = writeHandleManager.referenceSingletonHandle(
            singletonRefKey,
            schema,
            "refhandle"
        )
        val entity1Ref = writeHandle.createReference(entity1, backingKey)
        writeHandle.store(entity1Ref)

        // Now read back from a different handle
        val readbackHandle = readHandleManager.referenceSingletonHandle(singletonRefKey, schema)
        val readBack = readbackHandle.fetch()!!
        assertThat(readBack).isEqualTo(entity1Ref)

        // Reference should be dead.
        assertThat(readBack.isAlive(coroutineContext)).isFalse()
        assertThat(readBack.isDead(coroutineContext)).isTrue()

        // Now write the entity via a different handle
        val entityWriteHandle = createSingletonHandle(
            writeHandleManager,
            singletonKey,
            name = "entHandle"
        )
        entityWriteHandle.store(entity1)

        // Reference should be alive.
        assertThat(readBack.isAlive(coroutineContext)).isTrue()
        assertThat(readBack.isDead(coroutineContext)).isFalse()

        // Now dereference our read-back reference.
        assertThat(readBack.dereference(coroutineContext)).isEqualTo(entity1)

        val modEntity1 = entity1.copy(
            singletons = entity1.singletons + ("name" to "Ben".toReferencable())
        )
        entityWriteHandle.store(modEntity1)

        // Reference should still be alive.
        assertThat(readBack.isAlive(coroutineContext)).isTrue()
        assertThat(readBack.isDead(coroutineContext)).isFalse()

        // Now dereference our read-back reference, should now be modEntity1
        assertThat(readBack.dereference(coroutineContext)).isEqualTo(modEntity1)
    }

    @Test
    fun singleton_dereferenceEntity() = testRunner {
        val writeHandle = createSingletonHandle(writeHandleManager, singletonKey)
        val readHandle = createSingletonHandle(readHandleManager, singletonKey)
        writeHandle.store(entity1)

        // Create a second handle for the second entity, so we can store it.
        val storageKey = ReferenceModeStorageKey(backingKey, RamDiskStorageKey("entity2"))
        val refWriteHandle = createSingletonHandle(writeHandleManager, storageKey)
        val refReadHandle = createSingletonHandle(readHandleManager, storageKey)
        refWriteHandle.store(entity2)

        // Now read back entity1, and dereference its best_friend.
        val dereferencedEntity2 =
            (readHandle.fetch()!!.singletons["best_friend"] as Reference)
                .also {
                    // Check that it's alive
                    assertThat(it.isAlive(coroutineContext)).isTrue()
                }
                .dereference(coroutineContext)
        assertThat(dereferencedEntity2).isEqualTo(entity2)

        // Do the same for entity2's best_friend
        val dereferencedEntity1 =
            (refReadHandle.fetch()!!.singletons["best_friend"] as Reference)
                .dereference(coroutineContext)
        assertThat(dereferencedEntity1).isEqualTo(entity1)
    }

    @Test
    fun singleton_dereferenceEntity_nestedReference() = testRunner {
        // Create a stylish new hat, and create a reference to it.
        val hatCollection = createCollectionHandle(writeHandleManager,
            hatCollectionKey,
            hatSchema
        )
        val fez = RawEntity(
            id = "fez-id",
            singletons = mapOf("style" to "fez".toReferencable()),
            collections = emptyMap()
        )
        hatCollection.store(fez)
        val fezRef = hatCollection.createReference(fez, backingKey)

        // Give the hat to an entity and store it.
        val singletons = entity1.singletons.toMutableMap()
        singletons["hat"] = fezRef
        val entityWithHat = entity1.copy(singletons = singletons)
        val writeHandle = createSingletonHandle(writeHandleManager, singletonKey)
        writeHandle.store(entityWithHat)

        // Read out the entity, and fetch its hat.
        val readHandle = createSingletonHandle(readHandleManager, singletonKey)
        val entityOut = readHandle.fetch()!!
        assertThat(entityOut).isEqualTo(entityWithHat)
        val hatRef = entityOut.singletons["hat"] as Reference
        val hat = hatRef.dereference(coroutineContext)
        assertThat(hat).isEqualTo(fez)
    }

    @Test
    fun collection_writeAndReadBack() = testRunner {
        val writeHandle = createCollectionHandle(writeHandleManager, collectionKey)
        writeHandle.store(entity1)
        writeHandle.store(entity2)

        // Now read back from a different handle
        val readHandle = createCollectionHandle(readHandleManager, collectionKey)
        val readBack = readHandle.fetchAll()
        assertThat(readBack).containsExactly(entity1, entity2)
    }

    @Test
    open fun collection_writeAndOnUpdate() = testRunner {
        val writeHandle = createCollectionHandle(writeHandleManager, singletonKey)

        // Now read back from a different handle
        val readHandle = createCollectionHandle(readHandleManager, singletonKey)
        val updateDeferred = CompletableDeferred<Set<RawEntity>>()
        writeHandle.store(entity1)
        readHandle.addOnUpdate {
            updateDeferred.complete(it)
        }
        writeHandle.store(entity2)
        assertThat(updateDeferred.await()).containsExactly(entity1, entity2)
    }

    @Test
    open fun collection_referenceLiveness() = testRunner {
        val writeHandle = writeHandleManager.referenceCollectionHandle(singletonRefKey, schema)
        val entity1Ref = writeHandle.createReference(entity1, backingKey)
        val entity2Ref = writeHandle.createReference(entity2, backingKey)
        writeHandle.store(entity1Ref)
        writeHandle.store(entity2Ref)

        // Now read back from a different handle
        val readHandle = readHandleManager.referenceCollectionHandle(singletonRefKey, schema)
        val readBack = readHandle.fetchAll()
        assertThat(readBack).containsExactly(entity1Ref, entity2Ref)

        // References should be dead.
        val readBackEntity1Ref = readBack.find { it.id == entity1.id }!!
        val readBackEntity2Ref = readBack.find { it.id == entity2.id }!!
        assertThat(readBackEntity1Ref.isAlive(coroutineContext)).isFalse()
        assertThat(readBackEntity1Ref.isDead(coroutineContext)).isTrue()
        assertThat(readBackEntity2Ref.isAlive(coroutineContext)).isFalse()
        assertThat(readBackEntity2Ref.isDead(coroutineContext)).isTrue()

        // Now write the entity via a different handle
        val entityHandle = createCollectionHandle(
            writeHandleManager,
            singletonKey,
            name = "entHandle"
        )
        entityHandle.store(entity1)
        entityHandle.store(entity2)

        // References should be alive.
        assertThat(readBackEntity1Ref.isAlive(coroutineContext)).isTrue()
        assertThat(readBackEntity1Ref.isDead(coroutineContext)).isFalse()
        assertThat(readBackEntity2Ref.isAlive(coroutineContext)).isTrue()
        assertThat(readBackEntity2Ref.isDead(coroutineContext)).isFalse()

        // Now dereference our read-back references.
        assertThat(readBackEntity1Ref.dereference(coroutineContext)).isEqualTo(entity1)
        assertThat(readBackEntity2Ref.dereference(coroutineContext)).isEqualTo(entity2)

        // Now mutate the entities
        val modEntity1 = entity1.copy(
            singletons = entity1.singletons + ("name" to "Ben".toReferencable())
        )
        entityHandle.store(modEntity1)

        val modEntity2 = entity2.copy(
            singletons = entity2.singletons + ("name" to "Ben".toReferencable())
        )
        entityHandle.store(modEntity2)

        // Now dereference our read-back references.
        assertThat(readBackEntity1Ref.dereference(coroutineContext)).isEqualTo(modEntity1)
        assertThat(readBackEntity2Ref.dereference(coroutineContext)).isEqualTo(modEntity2)
    }

    @Test
    fun collection_entityDereference() = testRunner {
        val writeHandle = createCollectionHandle(writeHandleManager, collectionKey)
        writeHandle.store(entity1)
        writeHandle.store(entity2)

        val readHandle = createCollectionHandle(readHandleManager, collectionKey)
        readHandle.fetchAll().also { assertThat(it).hasSize(2) }.forEach { entity ->
            val expectedBestFriend = if (entity.id == "entity1") entity2 else entity1
            val actualBestFriend = (entity.singletons["best_friend"] as Reference)
                .dereference(coroutineContext)
            assertThat(actualBestFriend).isEqualTo(expectedBestFriend)
        }
    }

    @Test
    fun collection_dereferenceEntity_nestedReference() = testRunner {
        // Create a stylish new hat, and create a reference to it.
        val hatCollection = createCollectionHandle(writeHandleManager, hatCollectionKey)
        val fez = RawEntity(
            id = "fez-id",
            singletons = mapOf("style" to "fez".toReferencable()),
            collections = emptyMap()
        )
        hatCollection.store(fez)
        val fezRef = hatCollection.createReference(fez, backingKey)

        // Give the hat to an entity and store it.
        val singletons = entity1.singletons.toMutableMap()
        singletons["hat"] = fezRef
        val entityWithHat = entity1.copy(singletons = singletons)
        val writeHandle = createCollectionHandle(writeHandleManager, collectionKey)
        writeHandle.store(entityWithHat)

        // Read out the entity, and fetch its hat.
        val readHandle = createCollectionHandle(readHandleManager, collectionKey)
        val entityOut = readHandle.fetchAll().single { it.id == "entity1" }
        assertThat(entityOut).isEqualTo(entityWithHat)
        val hatRef = entityOut.singletons["hat"] as Reference
        val hat = hatRef.dereference(coroutineContext)
        assertThat(hat).isEqualTo(fez)
    }

    /** Helper method for creating singleton handles. */
    private suspend fun createSingletonHandle(
        handleManager: HandleManager,
        storageKey: StorageKey,
        schema: Schema = this.schema,
        name: String = storageKey.toKeyString()
    ) = handleManager.rawEntitySingletonHandle(
        storageKey,
        schema,
        name,
        dereferencerFactory = EntityDereferencerFactory(handleManager.stores)
    )

    /** Helper method for creating collection handles. */
    private suspend fun createCollectionHandle(
        handleManager: HandleManager,
        storageKey: StorageKey,
        schema: Schema = this.schema,
        name: String = storageKey.toKeyString()
    ) = handleManager.rawEntityCollectionHandle(
        storageKey,
        schema,
        name,
        dereferencerFactory = EntityDereferencerFactory(handleManager.stores)
    )
}
