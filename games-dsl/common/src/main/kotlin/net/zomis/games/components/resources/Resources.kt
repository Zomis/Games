package net.zomis.games.components.resources

interface GameResource {
    fun defaultValue(): Int = 0
    fun toResourceMap(value: Int = 1): ResourceMap = TODO()
    operator fun plus(other: GameResource): ResourceMap = this.toResourceMap().plus(other.toResourceMap())
    operator fun times(value: Int): ResourceMap = this.toResourceMap(value)
}

object Resources {
}