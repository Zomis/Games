package net.zomis.games.components.resources

interface ResourceEntry {
    val resource: GameResource
    val value: Int
    operator fun component1(): GameResource = resource
    operator fun component2(): Int = value
}
interface MutableResourceEntry: ResourceEntry {
    override var value: Int
    operator fun plusAssign(value: Int)
    operator fun minusAssign(value: Int)
    operator fun timesAssign(value: Int)
    fun coerceAtMost(value: Int)
    fun coerceAtLeast(value: Int)
}

class ResourceEntryImpl(override val resource: GameResource, override var value: Int): MutableResourceEntry {
    override fun plusAssign(value: Int) {
        this.value += value
    }

    override fun minusAssign(value: Int) {
        this.value -= value
    }

    override fun timesAssign(value: Int) {
        this.value *= value
    }

    override fun coerceAtMost(value: Int) {
        this.value = this.value.coerceAtMost(value)
    }

    override fun coerceAtLeast(value: Int) {
        this.value = this.value.coerceAtLeast(value)
    }

    override fun hashCode(): Int = resource.hashCode() + 17 * value.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other !is ResourceEntry) return false
        return this.resource == other.resource && this.value == other.value
    }

    override fun toString(): String = "$resource=$value"
}
