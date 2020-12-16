package net.zomis.games.impl.alchemists.artifacts

interface Artifact {

    val name: String
    val description: String
    val level: Int
    val cost: Int
    val victoryPoints: Int

}