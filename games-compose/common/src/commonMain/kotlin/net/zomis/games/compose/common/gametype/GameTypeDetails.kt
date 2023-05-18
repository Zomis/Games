package net.zomis.games.compose.common.gametype

import androidx.compose.runtime.Composable
import net.zomis.games.dsl.GameEntryPoint

interface TimeExpression {
    fun timeNeeded(players: Int, gameConfig: Any): IntRange
}

interface GameTypeDetails {
    val gameType: String
    val name: String
    val playersCount: IntRange
    val timeNeeded: TimeExpression
    val description: String
    val gameConfig: @Composable (Any) -> Unit
    val component: @Composable (Any) -> Unit
    val logRenderers: Map<String, @Composable (Any) -> Unit>
}

class GameTypeDetailsImpl(
    override val gameType: String,
    name: String? = null,
    val gameEntryPoint: GameEntryPoint<Any>,
    description: String? = null,
    override val logRenderers: Map<String, @Composable (Any) -> Unit> = emptyMap(),
    override val gameConfig: @Composable (Any) -> Unit = {},
    override val component: @Composable (Any) -> Unit,
) : GameTypeDetails {
    override val name: String = name ?: gameType
    override val description: String = description ?: "Description for $gameType"
    override val playersCount: IntRange = gameEntryPoint.setup().playersCount
    override val timeNeeded: TimeExpression = object : TimeExpression {
        override fun timeNeeded(players: Int, gameConfig: Any): IntRange = 0..300
    }

}