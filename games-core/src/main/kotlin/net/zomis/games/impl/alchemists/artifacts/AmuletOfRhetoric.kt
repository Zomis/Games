package net.zomis.games.impl.alchemists.artifacts

object AmuletOfRhetoric : Artifact {
    override val name: String
        get() = "Amulet of Rhetoric"
    override val description: String
        get() = "Immediate effect: Gain 5 points of reputation."
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 0
}