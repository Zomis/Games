package net.zomis.games.impl.alchemists.Artifacts

object SealOfAuthority : Artifact {
    override val name: String
        get() = "Seal of Authority"
    override val description: String
        get() = "When you publish or endorse a theory, gain 2 additional points of reputation."
    override val level: Int
        get() = 2
    override val cost: Int
        get() = 4
    override val victoryPoints: Int
        get() = 0
}