package net.zomis.games.impl.alchemists.Artifacts

object RobeOfRespect : Artifact {
    override val name: String
        get() = "Robe of Respect"
    override val description: String
        get() = "Whenever you gain reputation points, gain 1 more. This does not apply in the final round."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 4
    override val points: Int
        get() = 0
}