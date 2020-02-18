/*
 * Copyright 2019 Google LLC.
 *
 * This code may only be used under the BSD style license found at
 * http://polymer.github.io/LICENSE.txt
 *
 * Code distributed by Google as part of this project is also subject to an additional IP rights
 * grant found at
 * http://polymer.github.io/PATENTS.txt
 */

package arcs.sdk.wasm

object WasmRuntimeClient {
    fun <T : WasmEntity> singletonClear(
        particle: WasmParticleImpl,
        singleton: WasmSingletonImpl<T>
    ) = WasmExternal.singletonClear(particle.toAddress(), singleton.toAddress())

    fun <T : WasmEntity> singletonSet(
        particle: WasmParticleImpl,
        singleton: WasmSingletonImpl<T>,
        encoded: NullTermByteArray
    ) = WasmExternal.singletonSet(
        particle.toAddress(),
        singleton.toAddress(),
        encoded.bytes.toWasmAddress()
    )

    fun <T : WasmEntity> collectionRemove(
        particle: WasmParticleImpl,
        collection: WasmCollectionImpl<T>,
        encoded: NullTermByteArray
    ) = WasmExternal.collectionRemove(
        particle.toAddress(),
        collection.toAddress(),
        encoded.bytes.toWasmAddress()
    )

    fun <T : WasmEntity> collectionClear(
        particle: WasmParticleImpl,
        collection: WasmCollectionImpl<T>
    ) = WasmExternal.collectionClear(particle.toAddress(), collection.toAddress())

    fun <T : WasmEntity> collectionStore(
        particle: WasmParticleImpl,
        collection: WasmCollectionImpl<T>,
        encoded: NullTermByteArray
    ): String? {
        val wasmId = WasmExternal.collectionStore(
            particle.toAddress(),
            collection.toAddress(),
            encoded.bytes.toWasmAddress()
        )
        return wasmId.toNullableKString()?.let { _free(wasmId); it }
    }

    fun log(msg: String) = arcs.sdk.wasm.log(msg)

    fun onRenderOutput(particle: WasmParticleImpl, template: String?, model: NullTermByteArray?) =
        WasmExternal.onRenderOutput(
            particle.toAddress(),
            template.toWasmNullableString(),
            model?.bytes?.toWasmAddress() ?: 0
        )

    fun serviceRequest(
        particle: WasmParticleImpl,
        call: String,
        encoded: NullTermByteArray,
        tag: String
    ) = WasmExternal.serviceRequest(
        particle.toAddress(),
        call.toWasmString(),
        encoded.bytes.toWasmAddress(),
        tag.toWasmString()
    )

    fun resolveUrl(url: String): String {
        val r: WasmString = WasmExternal.resolveUrl(url.toWasmString())
        val resolved = r.toKString()
        _free(r)
        return resolved
    }
}
