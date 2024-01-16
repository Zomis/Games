package net.zomis.games.ecsmodel

import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener

object EcsGameImpl {

    fun <T : GameModelEntity> withSpec(game: EcsGameSpec<T>): EcsGameImplEntry<T> {
        return EcsGameImplEntry(game)
    }

}

class EcsGameImplEntry<T : GameModelEntity>(game: EcsGameSpec<T>) {

    private val context = EcsGameImplContext<T>()

    init {
        game.dsl.invoke(context)
        check(context.playerCount.all { it > 0 }) { "playerCounts for game ${game.name} (${context.playerCount}) must contain only positive values" }
    }

    fun createGame(playerCount: Int, listeners: context(Game) () -> List<GameListener>): EcsGame<T> {
        require(playerCount in context.playerCount) { "playerCount ($playerCount) must be in range ${context.playerCount}" }
        val game = EcsGameState()
        val root = context.invokeFactory(game)
        return EcsGame<T>(root, game, playerCount)
    }

    // pre-start config + post-start config?
    // post-start config for *after* game is initialized with amount of players -- setup player decks, choose characters...
    // pre-start config for things that is needed while creating game. e.g. Hanabi colors

}

internal class EcsGameImplContext<T : GameModelEntity> : EcsGameSpecScope<T> {
    private var factory: context(Game) EcsModelGameSetupScope.() -> T = { throw IllegalStateException("No factory set") }
    var playerCount: IntRange = 0..0

    internal fun invokeFactory(game: Game): T {
        return this.factory.invoke(game, object : EcsModelGameSetupScope {})
    }

    override fun playerCount(range: IntRange) {
        this.playerCount = range
    }

    override fun create(dsl: context(Game) EcsModelGameSetupScope.() -> T) {
        this.factory = dsl
    }
}