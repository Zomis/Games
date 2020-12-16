package net.zomis.games.impl.alchemists.artifacts

object CrystalCabinet : Artifact {
    override val name: String
        get() = "Crystal Cabinet"
    override val description: String
        get() = "When scoring artifacts, this is worth 2 points for each artifact you own, including this one."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 5
    override val victoryPoints: Int
        get() = 0
}