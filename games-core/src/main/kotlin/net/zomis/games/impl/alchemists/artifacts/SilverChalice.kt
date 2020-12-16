package net.zomis.games.impl.alchemists.artifacts

object SilverChalice : Artifact {
    override val name: String
        get() = "Silver Chalice"
    override val description: String
        get() = "This artifact has no special effect, but will earn you victory points"
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 6
}