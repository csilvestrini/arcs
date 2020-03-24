package arcs.sdk.spec

import arcs.sdk.ReadWriteCollectionHandle
import arcs.sdk.ReadWriteSingletonHandle
import arcs.sdk.Reference
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

private typealias Child = ReferenceSpecParticle_SingletonChild
private typealias Parent = ReferenceSpecParticle_Parents

/** Specification tests for [Reference]. */
@ExperimentalCoroutinesApi
@RunWith(JUnit4::class)
class ReferenceSpecTest {

    class ReferenceSpecParticle : AbstractReferenceSpecParticle()

    @get:Rule
    val harness = ReferenceSpecParticleTestHarness { ReferenceSpecParticle() }

    private lateinit var singletonChild: ReadWriteSingletonHandle<Child>
    private lateinit var collectionChild: ReadWriteCollectionHandle<Child>
    private lateinit var parents: ReadWriteCollectionHandle<Parent>

    @Before
    fun setUp() = runBlockingTest {
        harness.start()
        val handles = harness.particle.handles
        singletonChild = handles.singletonChild
        collectionChild = handles.collectionChild
        parents = handles.parents
    }

    @Test
    fun createReference_singletonHandle() = runBlocking {
        val child = Child(age = 10.0)
        singletonChild.store(child)
        val ref = singletonChild.createReference(child)
        assertThat(ref.dereference()).isEqualTo(child)
    }

    @Test
    fun createReference_collectionHandle() = runBlocking {
        val child = Child(age = 10.0)
        collectionChild.store(child)
        val ref = collectionChild.createReference(child)
        assertThat(ref.dereference()).isEqualTo(child)
    }

    @Test
    fun storeReference_insideSingletonField() = runBlocking {
        val child = Child(age = 10.0)
        val childRef = createChildReference(child)
        val parent = Parent(age = 40.0, favorite = childRef)

        parents.store(parent)

        val parentOut = parents.fetchAll().single()
        println("parent: $parent")
        println("parentOut: $parentOut")
        assertThat(parentOut).isEqualTo(parent)

        println("parent.favorite: ${parent.favorite}")
        println("parentOut.favorite: ${parentOut.favorite}")

        assertThat(parentOut.favorite).isEqualTo(childRef)
        assertThat(parentOut.favorite?.dereference()).isEqualTo(child)
    }

    @Test
    fun storeReference_insideCollectionField() {}


    @Test
    fun storeReference_insideSingletonHandle() {}

    @Test
    fun storeReference_insideCollectionHandle() {}

    /** Creates a [Reference] for the given [Child]. */
    private suspend fun createChildReference(child: Child): Reference<Child> {
        collectionChild.store(child)
        return collectionChild.createReference(child)
    }
}
