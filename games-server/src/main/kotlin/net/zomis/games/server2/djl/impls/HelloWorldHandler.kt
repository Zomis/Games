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
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.impl.Game
import net.zomis.games.server2.djl.DJLFactory
import net.zomis.games.server2.djl.DJLHandler

object HelloWorldHandler: DJLFactory<HelloWorldGame.Model, HelloWorldHandler.HelloWorldSnapshot> {

    override fun shapes(config: Any, batchSize: Int) = arrayOf(Shape(batchSize.toLong(), 4L), Shape(batchSize.toLong()), Shape(batchSize.toLong()))

    override fun createBlock(): Block {
        return SequentialBlock().add { arrays: NDList ->
            val board = arrays[0] // Shape(N, 4)
            val points = arrays[1].reshape(-1, 1) // Shape(N, 1)
            val action = arrays[2].reshape(-1, 1) // Shape(N, 1)

            // Concatenate to a combined vector of Shape(N, 6)
            val combined = NDArrays.concat(NDList(board, points, action), 1)
            NDList(combined.toType(DataType.FLOAT32, true))
        }.add(Mlp(6, 1, intArrayOf(8, 4)))
    }

    override fun handler(config: Any) = Handler

    class HelloWorldSnapshot(val values: List<Boolean>, val points: Int)
    object Handler: DJLHandler<HelloWorldGame.Model, HelloWorldSnapshot> {
        override fun createGame(): GameReplayableImpl<HelloWorldGame.Model> {
            return GamesImpl.game(HelloWorldGame.game).replayable(1, null)
        }

        override fun moveToAction(game: Game<HelloWorldGame.Model>, move: Int): Actionable<HelloWorldGame.Model, out Any> {
            return game.actions.type(HelloWorldGame.playHelloWorldDJL)!!.createAction(0, move)
        }

//        override fun reward(pre: HelloWorldSnapshot, game: Game<HelloWorldGame.Model>, action: Actionable<HelloWorldGame.Model, out Any>, post: HelloWorldSnapshot): Float {
//            return post.points.toFloat() - pre.points
//        }

        override fun createSnapshot(t: Game<HelloWorldGame.Model>): HelloWorldSnapshot {
            return HelloWorldSnapshot(t.model.values.toList(), t.model.points)
        }

        override fun observation(snapshot: HelloWorldSnapshot, manager: NDManager): NDList {
            return NDList(manager.create(snapshot.values.map { it.toInt() }.toIntArray()), manager.create(snapshot.points))
        }

        override fun actionSpace(snapshot: HelloWorldSnapshot, manager: NDManager): ActionSpace {
            val actionSpace = ActionSpace()
            snapshot.values.indices.forEach { actionSpace.add(NDList(manager.create(it))) }
            return actionSpace
        }

    }
    private fun Boolean.toInt(): Int {
        return if (this) 1 else 0
    }

}