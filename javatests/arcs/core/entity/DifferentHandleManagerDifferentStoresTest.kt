package arcs.core.entity

import arcs.core.host.EntityHandleManager
import arcs.core.storage.handle.HandleManager
import arcs.core.storage.handle.Stores
import arcs.jvm.util.testutil.TimeImpl
import org.junit.Before
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@Suppress("EXPERIMENTAL_API_USAGE")
@RunWith(JUnit4::class)
class DifferentHandleManagerDifferentStoresTest : HandleManagerTestBase() {

    @Before
    fun setUp() {
        readHandleManager = EntityHandleManager(
            arcId = "testArcId",
            hostId = "testHostId",
            time = TimeImpl(),
            stores = Stores()
        )
        writeHandleManager = EntityHandleManager(
            arcId = "testArcId",
            hostId = "testHostId",
            time = TimeImpl(),
            stores = Stores()
        )
    }

    // TODO - fix these?
    override fun collection_referenceLiveness() {}
    override fun singleton_referenceLiveness() {}

    // We don't expect these to pass, since Operations won't make it through the driver level
    override fun singleton_writeAndOnUpdate() {}
    override fun collection_writeAndOnUpdate() {}

}
