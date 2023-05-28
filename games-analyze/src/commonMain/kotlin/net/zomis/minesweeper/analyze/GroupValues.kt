package net.zomis.minesweeper.analyze

class GroupValues<T> {
    private var bufferedHash = 0
    private val _data: MutableMap<FieldGroup<T>, Int>
    val data: Map<FieldGroup<T>, Int> get() = _data

    constructor(values: GroupValues<T>) {
        _data = values.data.toMutableMap()
    }

    constructor() {
        _data = mutableMapOf()
    }

    override fun hashCode(): Int {
        if (bufferedHash == 0) {
            bufferedHash = data.hashCode()
        }
        return bufferedHash
    }

    override fun equals(arg0: Any?): Boolean {
        if (arg0 !is GroupValues<*>) {
            return false
        }
        return data == arg0.data
    }

    fun calculateHash(): Int {
        bufferedHash = 0
        return this.hashCode()
    }

    fun entrySet(): Set<Map.Entry<FieldGroup<T>, Int>> {
        return data.entries
    }

    fun keySet(): Set<FieldGroup<T>> {
        return data.keys
    }

    fun put(group: FieldGroup<T>, value: Int) {
        _data[group] = value
    }

    operator fun get(group: FieldGroup<T>): Int? {
        return data[group]
    }

    fun size(): Int {
        return data.size
    }

    val isEmpty: Boolean
        get() = data.isEmpty()

    fun remove(group: FieldGroup<T>) {
        _data.remove(group)
    }

    override fun toString(): String {
        return data.toString()
    }
}