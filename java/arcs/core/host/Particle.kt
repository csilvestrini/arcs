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

package arcs.core.host

import arcs.core.storage.api.Handle

/** Base interface for all particles. */
interface Particle {

    /**
     * This field contains a reference to all of the [Particle]'s handles that were declared in
     * the manifest.
     */
    val handles: HandleHolder

    /**
     * Called the first time this [Particle] is instantiated in an [Arc].
     *
     * A typical example of the use of [onCreate] is to initialize handles to default values needed
     * before particle startup.
     */
    suspend fun onCreate() = Unit

    /**
     * Called after handles are synced the first time, override to provide initial processing.
     * This will be called after [onCreate], but will not wait for [onCreate] to finish.
     */
    suspend fun onReady() = Unit

    /**
     * React to handle updates.
     *
     * Called for handles when change events are received from the backing store.
     *
     * @param handle Singleton or Collection handle
     */
    suspend fun onHandleUpdate(handle: Handle) = Unit

    /**
     *  Called when an [Arc] is shutdown.
     *
     *  Usually this method is unneeded, however if a platform-specific particle in an external
     *  host is holding on to an expensive resource, for example a UI or service connection on
     *  Android, thus method is provided as a way to release platform specific resources.
     */
    fun onShutdown() = Unit
}
