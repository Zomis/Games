package net.zomis.games.server2.ais

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.CoroutineScope
import net.zomis.core.events.EventSystem
import net.zomis.games.common.PlayerIndex
import net.zomis.games.components.Point
import net.zomis.games.components.grids.GridImpl
import net.zomis.games.components.grids.Position
import net.zomis.games.components.grids.Transformation
import net.zomis.games.components.grids.standardizedTransformation
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAI
import net.zomis.games.impl.ttt.ultimate.TTBase
import net.zomis.games.impl.ttt.ultimate.TTController
import net.zomis.games.impl.ttt.ultimate.TTPlayer
import net.zomis.games.server2.games.*
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.io.path.outputStream

class TTTQLearn(val file: Path) {
    val gameType = "DSL-TTT"

    val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()

    private val qStore = QStoreMap<String>().also { qStore ->
        if (!file.isRegularFile()) return@also
        val tree = mapper.readTree(file.inputStream()) as ObjectNode
        qStore.map.putAll(tree.fieldNames().asSequence().map { it to tree[it].asDouble() })
    }

    val actionPossible: ActionPossible<TTController> = { tt, action ->
        val pos = actionToPosition(tt, action)
        tt.isAllowedPlay(tt.game.getSub(pos.x, pos.y)!!)
    }

    private val learn = this.newLearner(qStore)
    fun newLearner(qStore: QStore<String>): MyQLearning<TTController, String> {
        return newLearner(9, qStore)
    }

    fun newLearner(controller: TTController, qStore: QStore<String>): MyQLearning<TTController, String> {
        return newLearner(controller.game.sizeX * controller.game.sizeY, qStore)
    }

    private fun normalizeTransformation(controller: TTController): Transformation {
        return GridImpl(controller.game.sizeX, controller.game.sizeY) { x, y ->
            controller.game.getSub(x, y)!!.wonBy
        }.standardizedTransformation {
            it.ordinal
        }
    }

    private fun newLearner(maxActions: Int, qStore: QStore<String>): MyQLearning<TTController, String> {
        val stateToString: (TTController) -> String = { g ->
            val transformation = normalizeTransformation(g)
            val sizeX = g.game.sizeX
            val sizeY = g.game.sizeY
            val str = StringBuilder()
            for (y in 0 until sizeY) {
                for (x in 0 until sizeX) {
                    val p = Position(x, y, sizeX, sizeY).transform(transformation)
                    val sub = g.game.getSub(p.x, p.y)!!
                    str.append(if (sub.wonBy.isExactlyOnePlayer) sub.wonBy.name else "_")
                }
                str.append('-')
            }
            str.toString()
        }
        val learn = MyQLearning(maxActions, stateToString, actionPossible, this::stateActionString, qStore)
        // learn.setLearningRate(-0.01); // This leads to bad player moves. Like XOX-OXO-_X_ instead of XOX-OXO-X__
        learn.discountFactor = -0.9
        learn.learningRate = 1.0
        learn.randomMoveProbability = 0.05
        return learn
    }

    private fun stateActionString(environment: TTController, state: String, action: Int): String {
        val transformation = normalizeTransformation(environment)
        // Transform action
        val point = actionToPosition(environment, action)
        val resultingActionPoint = point.transform(transformation)
        val resultingActionInt = positionToAction(environment, resultingActionPoint)
        return state + resultingActionInt
    }

    private fun positionToAction(environment: TTController, position: Position): Int {
        val columns = environment.game.sizeX
        return position.y * columns + position.x
    }

    private fun actionToPosition(environment: TTController, action: Int): Position {
        val columns = environment.game.sizeX
        val x = action % columns
        val y = action / columns
        return Position(x, y, environment.game.sizeX, environment.game.sizeY)
    }

    fun setup(events: EventSystem) {
        events.listen("register ServerAIs for DSL Game", GameTypeRegisterEvent::class, { it.gameType == gameType }, {
            registerAI(events)
        })
    }

    class QLearnListener(val game: Game<TTController>, val myPlayerIndex: Int, val learn: MyQLearning<TTController, String>, val save: () -> Unit): GameListener {
        private var awaitingResult: QAwaitingReward<String>? = null

        fun isDraw(tt: TTController): Boolean = tt.game.all().all { it.value.isWon }
        fun observeReward(game: Game<TTController>, action: Int, myIndex: PlayerIndex): MyQLearning.Rewarded<TTController> {
            val tt = game.model
            val player = if (myIndex == 0) TTPlayer.X else TTPlayer.O

            // Observe reward
            var reward = if (tt.isGameOver && tt.wonBy.isExactlyOnePlayer) {
                (if (tt.wonBy.`is`(player)) 1 else -1).toDouble()
            } else {
                -0.01
            }
            if (isDraw(tt)) {
                reward = 0.0
            }
            return MyQLearning.Rewarded(tt, reward)
        }

        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            if (step is FlowStep.PreMove) {
                val point = step.action.parameter as TTBase
                val action = point.y * game.model.game.sizeX + point.x
                val awaitingReward = learn.prepareReward(game.model, action)
                this.awaitingResult = awaitingReward
            }
            if (step is FlowStep.ActionPerformed<*>) {
                val entry = awaitingResult!!
                this.awaitingResult = null
                val reward = observeReward(game, entry.action, myPlayerIndex)
                learn.performReward(entry, reward)
            }
            if (step is FlowStep.GameEnd) {
                save.invoke()
            }
        }
    }

    fun registerAI(events: EventSystem) {
        learn.randomMoveProbability = 0.0

        val gameAI = GameAI<TTController>("#AI_QLearn_$gameType") {
            // Always do actions based on the standardized state
            // Find possible symmetry transformations
            // Make move
            // TODO: Learn the same value for all possible symmetries of action
            val learnListener = listener {
                QLearnListener(this.game, this.playerIndex, learn) {
                    mapper.writeValue(file.outputStream(), qStore.map)
                }
            }
            queryable {
                learnListener.learn.getActionScores(game.model)
            }
            action {
                val action = learnListener.learn.pickWeightedBestAction(model)
                val x = action % model.game.sizeX
                val y = action / model.game.sizeX
                val point = Point(x, y)
                game.actions.type("play")!!.createActionFromSerialized(playerIndex, point)
            }
        }
        val serverAI = ServerAI(listOf(gameType), gameAI.name, gameAI.listenerFactory())
        serverAI.register(events)
    }

}