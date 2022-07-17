package net.zomis.games.server.test

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import klog.KLoggers
import kotlinx.coroutines.*
import net.zomis.games.WinResult
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowContext
import net.zomis.games.dsl.flow.GameFlowImpl
import net.zomis.games.dsl.impl.FlowStep
import net.zomis.games.dsl.impl.Game
import net.zomis.games.dsl.impl.GameAIs
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.JacksonTools
import net.zomis.games.server2.ServerGames
import net.zomis.games.server2.ais.AIRepository
import net.zomis.games.server2.ais.ServerAIs
import java.io.File
import java.util.Scanner
import kotlin.time.Duration.Companion.seconds

private class TestPlayRoot(private val mapper: ObjectMapper, val file: File) {
    var running: Boolean = true
    private val logger = KLoggers.logger(this)
    private val node: ObjectNode = mapper.readTree(file) as ObjectNode
    private var modified = false
    private var nextStep: Int = 0
    private var nextLoadState: Map<String, Any> = emptyMap()

    fun isModified() = modified
    private fun stateLoad(node: JsonNode?): GameSituationState {
        if (node == null) return null
        return mapper.convertValue(node, jacksonTypeRef<Map<String, Any>?>())
    }

    private fun stateSave(node: JsonNode, fieldName: String, newState: GameSituationState) {
        val oldNode = node.get(fieldName)
        if (oldNode == null || newState != mapper.convertValue(oldNode, jacksonTypeRef<Map<String, Any>?>())) {
            logger.info { "OldNode $oldNode modified to $newState" }
            modified = true
            (node as ObjectNode).set<JsonNode>(fieldName, mapper.convertValue(newState, JsonNode::class.java))
        }
    }

    fun replayCallback(): GameListener {
        return object : GameListener {
            override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
                when (step) {
                    is FlowStep.PreMove -> {
                        step.setState(nextLoadState)
                    }
                    is FlowStep.ActionPerformed<*> -> {
                        val actionReplay = step.toActionReplay()
                        if (actionReplay.state.isNotEmpty()) {
                            replayState(actionReplay.state.toMap(), -1)
                        }
                        nextLoadState = emptyMap()
                    }
                    is FlowStep.PreSetup<*> -> {
                        step.state.clear()
                        step.state.putAll(stateLoad(node["state"])?.toMap() ?: emptyMap())
                    }
                    is FlowStep.GameSetup<*> -> {
                        stateSave(node, "state", step.state)
                    }
                }
            }
        }
    }

    fun getString(fieldName: String, default: () -> String): String {
        if (node.get(fieldName) == null) {
            logger.info { "$fieldName modified" }
            this.modified = true
            node.put(fieldName, default())
        }
        return node.get(fieldName).asText()
    }

    fun getInt(fieldName: String, default: () -> Int): Int {
        if (node.get(fieldName) == null) {
            logger.info { "Int $fieldName modified" }
            this.modified = true
            node.put(fieldName, default())
        }
        return node.get(fieldName).asInt()
    }

    fun configOrDefault(setup: GameSetupImpl<Any>): GameConfigs {
        if (node.get("config") == null) {
            logger.info { "Config modified" }
            this.modified = true
            node.set<JsonNode>("config", mapper.convertValue(setup.configs().toJSON(), JsonNode::class.java))
        }
        return JacksonTools.config(setup.configs(), node.get("config"))
    }

    fun save() {
        if (modified) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(file.parent, file.name), node) // + ".new"
        }
    }

    fun replayState(state: Map<String, Any>, nextStepOffset: Int) {
        val steps = node.get("steps") as ArrayNode
        val node = steps[nextStep + nextStepOffset] as ObjectNode
        val oldState = if (node.has("state")) node.get("state") as ObjectNode else null
        if (oldState != null && oldState.size() > 0) {
            val expected = mapper.convertValue(node.get("state"), object : TypeReference<Map<String, Any>>() {})
            if (expected != state) {
                throw PlayTestException("Mismatching state: Already saved $expected but new state was $state")
            }
            return
        }
        node.set<ObjectNode>("state", mapper.convertValue(state, JsonNode::class.java))
        modified = true
        save()
    }

    fun nextStep(replayable: PlayTests.GameWrapper): PlayTestStep? {
        if (node.get("steps") == null) {
            node.set<ObjectNode>("steps", ArrayNode(mapper.nodeFactory))
            modified = true
        }
        val steps = node.get("steps") as ArrayNode
        val step = readStep(steps[nextStep], replayable) ?: return null
        handleStep(step, replayable)
        nextStep++
        return step
    }

    private fun readStep(node: JsonNode?, replayable: PlayTests.GameWrapper): PlayTestStep? {
        if (node == null) return null
        return when (val type = node["type"].asText()) {
            "perform" -> {
                val actionTypeName = node["actionType"].asText()
                val actionParameterAny = mapper.convertValue(node["action"], Any::class.java)
                val actionType = replayable.game.actions.type(actionTypeName) ?: throw IllegalStateException("No such actionType found: '$actionTypeName'")
                val actionParameterSerialized = mapper.convertValue(actionParameterAny, actionType.actionType.serializedType.java)
                val actionable = actionType.createActionFromSerialized(node["playerIndex"].asInt(), actionParameterSerialized)
                val state = if (node.has("state")) mapper.convertValue(node["state"], object : TypeReference<Map<String, Any>>() {}) else emptyMap()
                PlayTestStepPerform(actionable.playerIndex, actionable.actionType, actionable.parameter, state)
            }
            "assertView" -> mapper.convertValue(node, PlayTestStepAssertView::class.java)
            "assertEliminations" -> mapper.convertValue(node, PlayTestStepAssertElimination::class.java)
            "assertActions" -> mapper.convertValue(node, PlayTestStepAssertActions::class.java)
            else -> TODO("$type Not yet implemented")
        }
    }

    fun handleStep(step: PlayTestStep, replayable: PlayTests.GameWrapper) {
        when (step) {
            is PlayTestStepPerform -> {
                if (step.state.isNotEmpty()) {
                    // replayable.game.stateKeeper.setState(step.state)
                    this.nextLoadState = step.state
                }
                val actionType = replayable.game.actions.type(step.actionType) ?: throw IllegalStateException("Action ${step.type} does not exist")
                val actionable = actionType.createAction(step.playerIndex, step.action)
                if (!actionType.isAllowed(actionable)) {
                    throw IllegalStateException("Action is not allowed: $actionable")
                }
                runBlocking {
                    replayable.game.actionsInput.send(actionable)
                    println("LAST MOVE STATE: " + replayable.game.stateKeeper.lastMoveState())
                }
            }
            is PlayTestStepAssertActions -> step.assert(replayable)
            is PlayTestStepAssertView -> step.assert(replayable)
            is PlayTestStepAssertElimination -> step.assert(replayable)
            else -> TODO("Unknown step: $step")
        }
    }

    fun addStep(step: PlayTestStep) {
        val steps = node.get("steps") as ArrayNode
        val nodeStep = mapper.convertValue(step, JsonNode::class.java)
        steps.add(nodeStep)
        modified = true
    }

}

class TestPlayChoices(
    val gameName: () -> String,
    val playersCount: (GameEntryPoint<Any>) -> Int,
    val config: (GameEntryPoint<Any>) -> GameConfigs
)

object PlayTests {

    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()
    private val coroutineScope = CoroutineScope(Job())

    fun createNew(file: File, gameName: String, playersCount: Int, config: GameConfigs?) {
        if (file.exists()) throw IllegalArgumentException("File already exists: $file")
        file.writeText("{}")
        fullJsonTest(file, TestPlayChoices(
            gameName = { gameName },
            playersCount = { playersCount },
            config = { config ?: it.setup().configs() }
        ), interactive = true)
    }

    data class GameWrapper(val game: Game<Any>)
    private class FeedbackHandler(val scanner: Scanner?, val tree: TestPlayRoot, val replayable: GameWrapper): GameListener {
        private var nextViews = 0

        override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
            val a = step
            println("feedback $a")
            logger.info { a }
            if (a is FlowStep.NextView) {
                if (nextViews++ > 10) throw IllegalStateException("Too many next views")
            }
            if (a is FlowStep.AwaitInput) {
                nextViews = 0
                println("--- Await Input")
                nextSteps(tree, replayable, scanner)
            }
            if (a is FlowStep.GameEnd) {
                nextSteps(tree, replayable, scanner)
            }
        }
    }

    fun fullJsonTest(file: File, choices: TestPlayChoices, interactive: Boolean) {
        val tree = TestPlayRoot(mapper, file)
        val gameName = tree.getString("game", choices.gameName)
        val entry = ServerGames.entrypoint(gameName)!!
        val playersCount = tree.getInt("players") { choices.playersCount(entry) }

        logger.info { "Testing ${file.absolutePath}: $gameName with $playersCount players" }
        tree.save()

        val config = tree.configOrDefault(entry.setup())
        val game: Game<Any>
        val s = Scanner(System.`in`).takeIf { interactive }
        runBlocking {
            game = entry.setup().startGameWithConfig(coroutineScope, playersCount, config) {
                listOf(tree.replayCallback(), FeedbackHandler(s, tree, GameWrapper(it)))
            }
        }
        val replayable = GameWrapper(game)
        println(replayable.game)
        runBlocking {
            withTimeout(60.seconds) {
                while (game.isRunning() && tree.running) {
                    println("Delay")
                    delay(100)
                }
            }
        }

        tree.save()
    }

    fun viewNavigation(view: Any, path: List<Any>): Any? {
        var current: Any? = view
        for (step in path) {
            when (current) {
                is Map<*, *> -> current = current[step]
                is List<*> -> current = current[step.toString().toInt()]
            }
        }
        return current
    }

    private fun nextSteps(tree: TestPlayRoot, replayable: GameWrapper, scanner: Scanner?): Boolean {
        do {
            val nextStep = tree.nextStep(replayable)
            println("Performing saved step: $nextStep")
            if (nextStep is PlayTestStepPerform) {
                return true
            }
            if (nextStep is PlayTestStepAssertElimination && replayable.game.isGameOver()) {
                return true
            }
        } while (nextStep != null)
        tree.running = false
        if (scanner == null) return false

        val input = PlayTestInput(scanner)

        ConsoleView<Any>().showView(replayable.game)
        println("Choose:")
        println("R. Perform random action")
        println("P. Perform specific action")
        println("V. Assert view")
        println("A. Assert actions")
        println("E. Assert eliminations")
        println("X/Q. Exit/Quit")
        println("Available actions is: ${replayable.game.actions.actionTypes}")
        val choice: String
        try {
            choice = scanner.nextLine()
        } catch (e: NoSuchElementException) {
            println("No line from scanner, exiting")
            return false
        }
        val step = when (choice.lowercase()) {
            "x", "q" -> return false
            "r" -> {
                if (replayable.game.isGameOver()) {
                    return false
                }
                val players = (0 until replayable.game.playerCount).shuffled()
                val action = players.firstNotNullOf {
                    GameAIs.randomActionable(replayable.game, it)
                }
                val serialized = replayable.game.actions.type(action.actionType)!!.actionType.serialize(action.parameter)
                PlayTestStepPerform(action.playerIndex, action.actionType, serialized, emptyMap()) // state is filled in later
            }
            "p" -> {
                val action = ConsoleController<Any>().let {
                    it.inputRepeat { it.queryInput(replayable.game, scanner) }
                }
                val serialized = replayable.game.actions.type(action.actionType)!!.actionType.serialize(action.parameter)
                PlayTestStepPerform(action.playerIndex, action.actionType, serialized, emptyMap()) // state is filled in later
            }
            "v" -> {
                println("Enter player index")
                val playerIndex = scanner.nextLine().toInt()
                val playerView = replayable.game.view(playerIndex)
                ConsoleView<Any>().showView(replayable.game, playerIndex)

                val paths = mutableListOf<Any>()
                var current: Any? = playerView
                var inputPaths: String
                do {
                    val options = when (current) {
                        is Map<*, *> -> current.keys.map { it.toString() }.sorted()
                        is List<*> -> current.indices
                        else -> break
                    }
                    println(options)
                    inputPaths = scanner.nextLine()
                    if (inputPaths.isEmpty()) break
                    paths.add(inputPaths)

                    current = viewNavigation(playerView, paths)
                } while (true)

                println("Value is $current")

                println("W. I expected something different")
                println("E. I expected this")
                println("X. I just expected it to be something")
                println("Q. I expected it to not exist")
                when (scanner.nextLine().lowercase()) {
                    "w" -> PlayTestStepAssertView(playerIndex, PlayTestViewAssertionType.NOT_EQUALS, paths, current!!)
                    "e" -> PlayTestStepAssertView(playerIndex, PlayTestViewAssertionType.EQUALS, paths, current!!)
                    "x" -> PlayTestStepAssertView(playerIndex, PlayTestViewAssertionType.EXISTS, paths, -1)
                    "q" -> PlayTestStepAssertView(playerIndex, PlayTestViewAssertionType.DOES_NOT_EXIST, paths, -1)
                    else -> throw IllegalArgumentException()
                }
            }
            "a" -> {
                val playerIndex = input.playerIndex()
                val type = input.fromList(PlayTestActionAssertionType.values().toList())

                when (type) {
                    PlayTestActionAssertionType.ALLOWED -> TODO()
                    PlayTestActionAssertionType.NOT_ALLOWED -> TODO()
                    PlayTestActionAssertionType.COUNT -> {
                        val actionType = input.fromList(replayable.game.actions.types().toList().map { it.name }, "Choose action type")
                        val count = input.number("Expected count:")
                        PlayTestStepAssertActions(playerIndex, type, actionType = actionType, parameter = count)
                    }
                }
            }
            "e" -> {
                println("Current eliminations are:")
                replayable.game.eliminations.eliminations().forEach { println(it) }
                val remaining = replayable.game.eliminations.remainingPlayers()
                println("Players remaining: $remaining (total of ${remaining.size} players)")

                println("Choose assertion type:")
                val types = PlayTestEliminationAssertionType.values()
                types.forEachIndexed { index, value ->
                    println("$index: $value")
                }
                val type = types[scanner.nextLine().toInt()]
                when (type) {
                    PlayTestEliminationAssertionType.REMAINING_PLAYERS -> {
                        println("Enter expected players:")
                        PlayTestStepAssertElimination(type, null, scanner.nextLine().toInt(), null, null)
                    }
                    PlayTestEliminationAssertionType.ELIMINATION -> {
                        println("Enter player index:")
                        val playerIndex = scanner.nextLine().toInt()

                        println("Enter win result:")
                        WinResult.values().forEach { println(it) }
                        val winner = WinResult.valueOf(scanner.nextLine())

                        println("Enter position:")
                        val position = scanner.nextLine().toInt()
                        PlayTestStepAssertElimination(type, playerIndex, null, winner, position)
                    }
                    PlayTestEliminationAssertionType.PLAYER_ALIVE -> {
                        println("Enter player index:")
                        val playerIndex = scanner.nextLine().toInt()
                        PlayTestStepAssertElimination(type, playerIndex, null, null, null)
                    }
                    PlayTestEliminationAssertionType.PLAYER_ELIMINATED -> {
                        println("Enter player index:")
                        val playerIndex = scanner.nextLine().toInt()
                        PlayTestStepAssertElimination(type, playerIndex, null, null, null)
                    }
                }
            }
            else -> return nextSteps(tree, replayable, scanner)
        }
        tree.addStep(step)

        if (tree.isModified()) {
            println("TREE MODIFIED")
            tree.save()
        }
        tree.nextStep(replayable)

        if (step !is PlayTestStepPerform) {
            return nextSteps(tree, replayable, scanner)
        }
        return true
    }

}
