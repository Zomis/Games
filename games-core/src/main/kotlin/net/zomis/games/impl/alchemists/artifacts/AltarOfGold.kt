package net.zomis.games.impl.alchemists.artifacts

object AltarOfGold : Artifact {
    override val name: String
        get() = "Altar of Gold"
    override val description: String
        get() = "Immediate effect: Pay 1 to 8 gold pieces. Gain that many points of reputation."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 1
    override val victoryPoints: Int
        get() = 0
}