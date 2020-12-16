package net.zomis.games.impl.alchemists.Artifacts

object MagicMortar : Artifact {
    override val name: String
        get() = "Magic Mortar"
    override val description: String
        get() = "When you mix a potion, discard only one of the ingredients. A colleague chooses it randomly."
    override val level: Int
        get() = 1
    override val cost: Int
        get() = 3
    override val points: Int
        get() = 1
}