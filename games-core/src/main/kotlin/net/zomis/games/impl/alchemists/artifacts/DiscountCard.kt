package net.zomis.games.impl.alchemists.artifacts

object DiscountCard : Artifact {
    override val name: String
        get() = "Discount Card"
    override val description: String
        get() = "Your next artifact costs 2 gold less. After that, artifacts cost you 1 gold less."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 3
    override val victoryPoints: Int
        get() = 1
}