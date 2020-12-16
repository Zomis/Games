package net.zomis.games.impl.alchemists.artifacts

object BootsOfSpeed : Artifact {
    override val name: String
        get() = "Boots of Speed"
    override val description: String
        get() = "On an action space whee you have at least one cube, you can perform that action again after everyone is done. Limi once per round. Can't be used to Sell Potions."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 2
}