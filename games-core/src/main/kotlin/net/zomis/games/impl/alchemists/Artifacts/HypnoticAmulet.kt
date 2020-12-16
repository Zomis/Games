package net.zomis.games.impl.alchemists.Artifacts

object HypnoticAmulet : Artifact {
    override val name: String
        get() = "Hypnotic Amulet"
    override val description: String
        get() = "Immediate effect: Draw 4 favor cards."
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 3
    override val victoryPoints: Int
        get() = 1
}