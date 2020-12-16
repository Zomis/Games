package net.zomis.games.impl.alchemists.artifacts

object WisdomIdol : Artifact {
    override val name: String
        get() = "Wisdom Idol"
    override val description: String
        get() = "At the end of the game, Wisdom Idol is worth 1 point for each seal you have on a correct theory."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 0
}