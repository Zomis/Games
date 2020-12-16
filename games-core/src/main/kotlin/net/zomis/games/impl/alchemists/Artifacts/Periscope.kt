package net.zomis.games.impl.alchemists.Artifacts

object Periscope : Artifact {
    override val name: String
        get() = "Periscope"
    override val description: String
        get() = "Immediately after a colleague sells or tests a potion, you may look at one of the ingredients. Choose It randomly. Limit once per round."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 3
    override val points: Int
        get() = 1
}