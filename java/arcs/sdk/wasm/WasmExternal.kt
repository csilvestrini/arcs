package arcs.sdk.wasm

import kotlin.native.Retain
import kotlin.native.internal.ExportForCppRuntime
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointed
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.NativePtr
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.toLong

/** External functions provided by either Kotlin Native or the Arcs runtime. */
object WasmExternal {
    @SymbolName("Kotlin_Arrays_getByteArrayAddressOfElement")
    external fun ByteArray.addressOfElement(index: Int): CPointer<ByteVar>

    // these are exported methods in the C++ runtime
    @SymbolName("Kotlin_interop_malloc")
    external fun kotlinMalloc(size: Long, align: Int): NativePtr

    @SymbolName("Kotlin_interop_free")
    external fun kotlinFree(ptr: NativePtr)

    @SymbolName("abort")
    external fun abort()

    @SymbolName("_singletonSet")
    external fun singletonSet(particlePtr: WasmAddress, handlePtr: WasmAddress, stringPtr: WasmString)

    @SymbolName("_singletonClear")
    external fun singletonClear(particlePtr: WasmAddress, handlePtr: WasmAddress)

    @SymbolName("_collectionStore")
    external fun collectionStore(
        particlePtr: WasmAddress,
        handlePtr: WasmAddress,
        stringPtr: WasmString
    ): WasmString

    @SymbolName("_collectionRemove")
    external fun collectionRemove(
        particlePtr: WasmAddress,
        handlePtr: WasmAddress,
        stringPtr: WasmString
    )

    @SymbolName("_collectionClear")
    external fun collectionClear(particlePtr: WasmAddress, handlePtr: WasmAddress)

    @SymbolName("_onRenderOutput")
    external fun onRenderOutput(
        particlePtr: WasmAddress,
        templatePtr: WasmNullableString,
        modelPtr: WasmNullableString
    )

    @SymbolName("_serviceRequest")
    external fun serviceRequest(
        particlePtr: WasmAddress,
        callPtr: WasmString,
        argsPtr: WasmString,
        tagPtr: WasmString
    )

    @SymbolName("_resolveUrl")
    external fun resolveUrl(urlPtr: WasmString): WasmString

    @SymbolName("write")
    external fun write(msg: WasmString)

    @SymbolName("flush")
    external fun flush()
}