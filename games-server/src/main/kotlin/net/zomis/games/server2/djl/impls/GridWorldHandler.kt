package net.zomis.games.server2.djl.impls

import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.modality.rl.ActionSpace
import ai.djl.ndarray.NDArrays
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import ai.djl.nn.Block
import ai.djl.nn.SequentialBlock
import net.zomis.games.common.Direction4
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.djl.DJLFactory
import net.zomis.games.server2.djl.DJLHandler

object GridWorldHandler: DJLFactory<GridWorldGame.GridWorldModel, GridWorldHandler.SavedState> {

    class SavedState(val map: List<Int>, val possibleActions: List<Int>)

    override fun shapes(config: Any, batchSize: Int): Array<Shape> = arrayOf(Shape(batchSize.toLong(), 6L), Shape(batchSize.toLong()), Shape(batchSize.toLong()))

    override fun handler(config: Any): DJLHandler<GridWorldGame.GridWorldModel, SavedState> {
        return object : DJLHandler<GridWorldGame.GridWorldModel, SavedState> {
            override fun createGame(): GameReplayableImpl<GridWorldGame.GridWorldModel> {
                return GamesImpl.game(GridWorldGame.game).replayable(1, null)
            }

            override fun moveToAction(game: Game<GridWorldGame.GridWorldModel>, move: Int): Actionable<GridWorldGame.GridWorldModel, out Any> {
                return game.actions.type(GridWorldGame.gridWorldMove)!!.createAction(0, Direction4.values()[move])
            }

            override fun reward(pre: SavedState, game: Game<GridWorldGame.GridWorldModel>, action: Actionable<GridWorldGame.GridWorldModel, out Any>, post: SavedState): Float {
                return super.reward(pre, game, action, post) - 0.1f
            }

            override fun createSnapshot(t: Game<GridWorldGame.GridWorldModel>): SavedState {
                return SavedState(t.model.map.all().map { it.value.ordinal }, t.model.allowedMoves().map { it.ordinal })
            }

            override fun observation(snapshot: SavedState, manager: NDManager): NDList {
                return NDList(manager.create(snapshot.map.map { if (it == GridWorldGame.GridWorldTile.PLAYER.ordinal) 1 else 0 }.toIntArray()))
            }

            override fun actionSpace(snapshot: SavedState, manager: NDManager): ActionSpace {
                val actions = ActionSpace()
                snapshot.possibleActions.forEach { actions.add(NDList(manager.create(it))) }
                return actions
            }

        }
    }

    override fun createBlock(): Block {
        return SequentialBlock().add { arrays: NDList ->
            val board = arrays[0] // Shape(N, 6)
            val action = arrays[1].reshape(-1, 1) // Shape(N, 1)

            // Concatenate to a combined vector of Shape(N, 7)
            val combined = NDArrays.concat(NDList(board, action), 1)
            NDList(combined.toType(DataType.FLOAT32, true))
        }.add(Mlp(7, 1, intArrayOf(7)))
    }

}