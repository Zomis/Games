package net.zomis.games.impl.alchemists.artifacts

import net.zomis.games.dsl.GameSerializable

interface Artifact: GameSerializable {

    val name: String
    val description: String
    val level: Int
    val cost: Int
    val victoryPoints: Int

    override fun serialize(): String = name
}
