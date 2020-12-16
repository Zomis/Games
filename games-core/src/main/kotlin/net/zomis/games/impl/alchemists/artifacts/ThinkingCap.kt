package net.zomis.games.impl.alchemists.Artifacts

object ThinkingCap : Artifact {
    override val name: String
        get() = "Thinking Cap"
    override val description: String
        get() = "Immediate effect: Test up to two separate pairs of ingredients in you hand. Do not discard them."
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 1
}