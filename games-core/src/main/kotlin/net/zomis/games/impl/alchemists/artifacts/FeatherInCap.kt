package net.zomis.games.impl.alchemists.artifacts

object FeatherInCap : Artifact {
    override val name: String
        get() = "Feather in Cap"
    override val description: String
        get() = "During the exhibition: Set aside ingredients from potions you demonstrate successfully. When scoring artifacts, this cap is worth 1 point for each type of ingredient set aside."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 3
    override val victoryPoints: Int
        get() = 0
}