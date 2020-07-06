package arcs.core.type

import arcs.core.data.FieldName

/** Represents an access to a part of the data (like a field). */
sealed class Selector {
    data class Field(val field: FieldName) : Selector() {
        override fun toString() = field
    }
    // TODO(bgogul): Indexing, Dereferencing(?).

    companion object {
        /** Selector names for each element of a tuple. Only supports tuples of size up to 5. */
        val TUPLE_INDEX_NAMES = listOf("first", "second", "third", "fourth", "fifth")

        fun tupleComponent(component: Int): Selector {
            require(component >= 0 && component < TUPLE_INDEX_NAMES.size) {
                "Only up to ${TUPLE_INDEX_NAMES.size} tuple components is allowed!"
            }
            return Field(TUPLE_INDEX_NAMES[component])
        }
    }
}

typealias SelectorList = List<Selector>
