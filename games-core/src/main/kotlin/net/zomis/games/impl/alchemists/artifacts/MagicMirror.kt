package net.zomis.games.impl.alchemists.artifacts

object MagicMirror : Artifact {
    override val name: String
        get() = "Magic Mirror"
    override val description: String
        get() = "When scoring artifacts, this is worth 1 victory point for every 5 reputation points you had at the end of the final round."
    override val level: Int
        get() = 3
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 0
}