package net.zomis.games.common

fun <K, V> Map<K, V>.mergeWith(other: Map<K, V>, merger: (V?, V?) -> V): Map<K, V> {
    return (this.keys + other.keys).associateWith {
        merger(this[it], other[it])
    }
}

