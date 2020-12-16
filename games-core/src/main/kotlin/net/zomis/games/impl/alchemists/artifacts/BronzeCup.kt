package net.zomis.games.impl.alchemists.artifacts

object BronzeCup : Artifact {
    override val name: String
        get() = "Bronze Cup"
    override val description: String
        get() = "This artifact has no special effect, but will earn you victory points."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 4
}