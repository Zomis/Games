package net.zomis.games.impl.alchemists.artifacts

object WitchsTrunk : Artifact {
    override val name: String
        get() = "Witch's Trunk"
    override val description: String
        get() = "Immediate effect: Draw 7 ingredients. | You no longer draw ingredients when choosing play order."
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 3
    override val victoryPoints: Int
        get() = 2
}