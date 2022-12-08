package net.zomis.games.listeners

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import klog.KLoggers
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.listeners.BlockingGameListener
import net.zomis.games.server2.ServerGames
import kotlin.reflect.KClass

/*
* - Game actions should be replayable
*   - Prevent situations where a deserialized object is not the same object as the one in game, causing updates on something else
*   - Replaying actions should generate the same view as when the original action was made.
* - Possibly in the future: No Kotlin-specific data sent to frontend
*/
class SanityCheckListener(val game: Game<out Any>): GameListener {
    private val logger = KLoggers.logger(this)
    private val replayListener = ReplayListener(game.gameType)
    var replay: Replay<Any>? = null
    private val liveReplayingListener = LiveReplayingListener()
    private val blockingListener = BlockingGameListener()
    private val enumsFound = mutableSetOf<KClass<*>>()
    private val mapper = jacksonObjectMapper()

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        replayListener.handle(coroutineScope, step)
        val replay = this.replay
        if (step is FlowStep.GameSetup<*>) {
            liveReplayingListener.setupState = step as FlowStep.GameSetup<Any>
            this.replay = ServerGames.entrypoint(game.gameType)!!
                .replay(coroutineScope, replayListener.data(), gameListeners = {
                    listOf(blockingListener, liveReplayingListener)
                })
            blockingListener.await()
        }

        if (step is FlowStep.ActionPerformed<*>) {
            // Perform the same action with the same state in the live replay game
            val replayGame = replay?.game ?: throw IllegalStateException("Replay game is not initialized")
            val actionType = replayGame.actions.type(step.action.actionType)
                ?: throw IllegalStateException("LiveReplay did not find actionType for: ${step.action}")
            liveReplayingListener.lastAction = step as FlowStep.ActionPerformed<Any>
            val replayAction = actionType.createActionFromSerialized(step.playerIndex, step.serializedParameter)
            blockingListener.awaitAndPerform(replayAction)
        }

        if (step is FlowStep.AwaitInput) {
            blockingListener.await()

            val replayGame = replay?.game ?: throw IllegalStateException("Replay game is not initialized")
            val game = this.game

            // make sure that view in this game is the same as in live replay game, for all players
            // also check view for all players for Kotlin-specific data
            for (playerIndex in game.playerIndices.toList() + null) {
                val originalView = game.view(playerIndex)
                val replayView = replayGame.view(playerIndex)
                // checkViewTypes(originalView) // Allow all view types for now. There's a lot of data classes being used.
                check(viewMatch("/${game.gameType}/${playerIndex}", originalView, replayView, playerIndex)) {
                    "Original view:\n$originalView\ndiffers from replayView:\n$replayView"
                }
            }
        }
    }

    private val ignorePaths = listOf(
        Regex("^/Dixit/[^/]+/board$")
    )

    private fun viewMatch(path: String, a: Any?, b: Any?, viewer: PlayerIndex): Boolean {
        if (ignorePaths.any { it.matches(path) }) {
            println("Ignoring $path: $a vs. $b")
            return true
        }
        if (a == null && b == null) return true
        if ((a == null) xor (b == null)) {
            println("Mismatch at $path: $a vs. $b")
            return false
        }
        if (a!!::class != b!!::class) {
            println("Mismatching class at $path: $a vs. $b")
            return false
        }
        return when (a) {
            is String, is Number, is Boolean -> {
                if (a != b) {
                    println("Mismatch at $path: $a vs. $b")
                }
                return a == b
            }
            is Viewable -> viewMatch(path, a.toView(viewer), (b as Viewable).toView(viewer), viewer)
            is List<*> -> {
                check(a.size == (b as List<*>).size)
                val result = a.indices.all { viewMatch("$path/$it", a[it], b[it], viewer) }
                if (!result) println("Mismatch at $path: $a vs. $b")
                result
            }
            is Map<*, *> -> {
                check(a.size == (b as Map<*, *>).size)
                val result = a.entries.all {
//                    viewMatch("$path/${it.key}_key", it.key, )
                    viewMatch("$path/${it.key}", it.value, b[it.key], viewer)
                }
                if (!result) println("Mismatch at $path: $a vs. $b")
                result
            }
            else -> {
                val (first, second) = a to b
                val jacksonSerialized = mapper.writeValueAsString(first) to mapper.writeValueAsString(second)
                val jacksonMatch = jacksonSerialized.first == jacksonSerialized.second
                val result = first == second
                if (jacksonMatch && !result) {
                    return true
                }
                if (!result) println("Mismatch at $path: $first vs. $second. Jackson values ${jacksonSerialized.first} vs. ${jacksonSerialized.second}")
                result
            }
        }
    }

    private fun checkViewTypes(data: Any?) {
        when (data) {
            null -> return
            is Int, is String, is Double, is Boolean -> return
            is Viewable -> return
            is List<*> -> data.forEach { checkViewTypes(it) }
            is Map<*, *> -> data.forEach { entry ->
                val keyClazz = entry.key?.let { it::class } ?: "null"
                when (entry.key) {
                    is String, is Number, is Boolean, is Enum<*> -> {}
                    else -> throw IllegalStateException("Keys in view data must be String/Number/Boolean/Enum. " +
                        "Key `${entry.key}` of type `$keyClazz` found in $data"
                    )
                }
                checkViewTypes(entry.value)
            }
            is Enum<*> -> {
                if (enumsFound.add(data::class)) {
                    logger.warn { "Enum detected: $data of type ${data::class}. Enums in view are considered okay for now, but things may change" }
                }
            }
            else -> throw IllegalStateException("Unsupported view data: $data of class ${data::class}")
        }
    }

    class LiveReplayingListener: GameListener {

        lateinit var setupState: FlowStep.GameSetup<Any>
        var lastAction: FlowStep.ActionPerformed<Any>? = null

        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            if (step is FlowStep.PreSetup<*>) {
                step.setState(setupState.state)
            }
            if (step is FlowStep.PreMove) {
                step.setState(lastAction!!.state)
            }
        }

    }

}