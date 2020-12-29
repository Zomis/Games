package net.zomis.games.server2.djl

import ai.djl.modality.rl.ActionSpace
import ai.djl.modality.rl.ReplayBuffer
import ai.djl.modality.rl.env.RlEnv
import ai.djl.ndarray.NDArray
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import kotlinx.coroutines.runBlocking
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.impl.Game

interface DJLHandler<T: Any, S> {
    fun createGame(): GameReplayableImpl<T>
    fun moveToAction(game: Game<T>, move: Int): Actionable<T, Any>

    fun createSnapshot(t: Game<T>): S
    fun observation(snapshot: S, manager: NDManager): NDList
    fun actionSpace(snapshot: S, manager: NDManager): ActionSpace
}
class DJLGame<T: Any, S: Any>(
    val handler: DJLHandler<T, S>,
    val manager: NDManager,
    val replayBuffer: ReplayBuffer
): RlEnv {

    private lateinit var game: GameReplayableImpl<T>
    private lateinit var state: S

    val replayable get() = game
    fun getGame(): Game<T> = game.game

    override fun close() = manager.close()
    override fun getBatch(): Array<RlEnv.Step> = replayBuffer.batch

    override fun reset() {
        game = handler.createGame()
        state = handler.createSnapshot(game.game)
    }

    override fun getObservation(): NDList = handler.observation(state, manager)
    override fun getActionSpace(): ActionSpace = handler.actionSpace(state, manager)
    fun updateState() {
        this.state = handler.createSnapshot(game.game)
    }

    override fun step(action: NDList, training: Boolean): RlEnv.Step {
        val move = action.singletonOrThrow().getInt()
        val actionable = handler.moveToAction(game.game, move)

        val pre = handler.createSnapshot(game.game)
        runBlocking {
            game.perform(actionable)
        }
        this.state = handler.createSnapshot(game.game)

        val elimination = game.game.eliminations.eliminations().find { it.playerIndex == actionable.playerIndex }
        val reward = elimination?.winResult?.result?.toFloat() ?: 0f
        val step = Step(manager.newSubManager(), handler, pre, this.state, action, reward, game.game.isGameOver())

        if (training) {
            replayBuffer.addStep(step)
        }
        return step
    }

    class Step<T: Any, S>(
        val manager: NDManager, val handler: DJLHandler<T, S>,
        val pre: S, val post: S,
        val theAction: NDList, val theReward: Float, val done: Boolean
    ): RlEnv.Step {
        override fun close() = manager.close()

        override fun getPreObservation(): NDList = handler.observation(pre, manager)
        override fun getAction(): NDList = theAction
        override fun getPostObservation(): NDList = handler.observation(post, manager)
        override fun getPostActionSpace(): ActionSpace = handler.actionSpace(post, manager)
        override fun getReward(): NDArray = manager.create(theReward)
        override fun isDone(): Boolean = done
    }


}