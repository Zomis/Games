package net.zomis.games.server2.djl.impls

import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.modality.rl.ActionSpace
import ai.djl.ndarray.NDArrays
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.DataType
import ai.djl.nn.Block
import ai.djl.nn.SequentialBlock
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.impl.ttt.DslTTT
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.impl.ttt.ultimate.TTPlayer
import net.zomis.games.server2.djl.DJL
import net.zomis.games.server2.djl.DJLHandler
import net.zomis.games.server2.games.impl.playerIndex

object TTTHandler {
    fun createBlock(): Block {
        return SequentialBlock().add { arrays: NDList ->
            val board = arrays[0] // Shape(N, 9)
            val turn = arrays[1].reshape(-1, 1) // Shape(N, 1)
            val action = arrays[2].reshape(-1, 1) // Shape(N, 1)

            // Concatenate to a combined vector of Shape(N, 11)
            val combined = NDArrays.concat(NDList(board, turn, action), 1)
            NDList(combined.toType(DataType.FLOAT32, true))
        }.add(Mlp(11, 1, intArrayOf(20, 10)))
    }

    object handler: DJLHandler<TTController, DJL.MySavedState> {
        private fun playerIndex(player: TTPlayer): Int {
            return when (player) {
                TTPlayer.NONE -> 0
                TTPlayer.X -> 1
                TTPlayer.O -> -1
                else -> throw IllegalArgumentException(player.toString())
            }
        }

        override fun observation(snapshot: DJL.MySavedState, manager: NDManager): NDList {
            return NDList(manager.create(snapshot.tiles), manager.create(snapshot.turn))
        }

        override fun actionSpace(snapshot: DJL.MySavedState, manager: NDManager): ActionSpace {
            val actions = ActionSpace()
            snapshot.tiles.withIndex().filter { it.value == 0 }.forEach { actions.add(NDList(manager.create(it.index))) }
            return actions
        }

        override fun createGame(): GameReplayableImpl<TTController> {
            return GamesImpl.game(DslTTT.game).replayable(2, null)
        }

        override fun moveToAction(game: Game<TTController>, move: Int): Actionable<TTController, Any> {
            val target = game.model.game.subs()[move]
            return game.actions.type(DslTTT.playAction.name)!!.createAction(game.model.currentPlayer.playerIndex(), target)
        }

        override fun createSnapshot(t: Game<TTController>): DJL.MySavedState {
            return DJL.MySavedState(t.model.game.subs().map { playerIndex(it.wonBy) }.toIntArray(), t.model.currentPlayer.playerIndex())
        }
    }
}
