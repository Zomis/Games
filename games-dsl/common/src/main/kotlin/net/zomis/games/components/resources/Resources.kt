package net.zomis.games.components.resources

interface GameResource {
    val name: String
    fun defaultValue(): Int = 0
    fun toResourceMap(value: Int = 1): ResourceMap = ResourceMapImpl(
        mutableMapOf(this to ResourceEntryImpl(this, value))
    )
    operator fun plus(other: GameResource): ResourceMap = this.toResourceMap().plus(other.toResourceMap())
    operator fun times(value: Int): ResourceMap = this.toResourceMap(value)
    companion object {
        fun withName(name: String): GameResource = SimpleGameResource(name)
    }
}

class SimpleGameResource(override val name: String): GameResource {
    override fun toString(): String = name
}
