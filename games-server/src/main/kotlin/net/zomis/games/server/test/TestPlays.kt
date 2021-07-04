package net.zomis.games.server.test

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import klog.KLoggers
import kotlinx.coroutines.runBlocking
import net.zomis.games.WinResult
import net.zomis.games.dsl.*
import net.zomis.games.dsl.flow.GameFlowContext
import net.zomis.games.dsl.flow.GameFlowImpl
import net.zomis.games.dsl.impl.GameSetupImpl
import net.zomis.games.server2.ServerGames
import java.io.File
import java.util.Scanner

private class TestPlayRoot(private val mapper: ObjectMapper, val file: File) {
    private val logger = KLoggers.logger(this)
    private val node: ObjectNode = mapper.readTree(file) as ObjectNode
    private var modified = false
    private var nextStep: Int = 0

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

    fun replayCallback(): GameplayCallbacks<Any> {
        return object : GameplayCallbacks<Any>() {
            override fun startState(setStateCallback: (GameSituationState) -> Unit) = setStateCallback(stateLoad(node["state"]))
            override fun startedState(playerCount: Int, config: Any, state: GameSituationState) = stateSave(node, "state", state)
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

    fun configOrDefault(setup: GameSetupImpl<Any>): Any {
        if (node.get("config") == null) {
            logger.info { "Config modified" }
            this.modified = true
            node.set<JsonNode>("config", mapper.convertValue(setup.getDefaultConfig(), JsonNode::class.java))
        }
        return mapper.convertValue(node.get("config"), setup.configClass().java)
    }

    fun save() {
        if (modified) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(File(file.parent, file.name), node) // + ".new"
        }
    }

    fun replayState(state: Map<String, Any>) {
        val steps = node.get("steps") as ArrayNode
        val node = steps[nextStep - 1] as ObjectNode
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

    fun nextStep(replayable: GameReplayableImpl<Any>): PlayTestStep? {
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

    private fun readStep(node: JsonNode?, replayable: GameReplayableImpl<Any>): PlayTestStep? {
        if (node == null) return null
        return when (val type = node["type"].asText()) {
            "perform" -> {
                val actionTypeName = node["actionType"].asText()
                val actionParameterAny = mapper.convertValue(node["action"], Any::class.java)
                val actionType = replayable.game.actions.type(actionTypeName)!!
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

    fun handleStep(step: PlayTestStep, replayable: GameReplayableImpl<Any>) {
        when (step) {
            is PlayTestStepPerform -> {
                if (step.state.isNotEmpty()) {
                    replayable.state.setState(step.state)
                }
                val actionType = replayable.game.actions.type(step.actionType) ?: throw IllegalStateException("Action ${step.type} does not exist")
                val actionable = actionType.createAction(step.playerIndex, step.action)
                runBlocking {
                    if (replayable.game is GameFlowImpl<*>) {
                        replayable.game.actionsInput.send(actionable)
                    } else {
                        replayable.perform(actionable)
                        println("LAST MOVE STATE: " + replayable.state.lastMoveState())
                    }
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
    val config: (GameEntryPoint<Any>) -> Any
)

object PlayTests {

    private val logger = KLoggers.logger(this)
    private val mapper = jacksonObjectMapper()

    fun createNew(file: File, gameName: String, playersCount: Int, config: Any?) {
        if (file.exists()) throw IllegalArgumentException("File already exists: $file")
        file.writeText("{}")
        fullJsonTest(file, TestPlayChoices(
            gameName = { gameName },
            playersCount = { playersCount },
            config = { config ?: it.setup().getDefaultConfig() }
        ))
    }

    fun fullJsonTest(file: File, choices: TestPlayChoices) {
        val tree = TestPlayRoot(mapper, file)
        val gameName = tree.getString("game", choices.gameName)
        val entry = ServerGames.entrypoint(gameName)!!
        val playersCount = tree.getInt("players") { choices.playersCount(entry) }

        logger.info { "Testing ${file.absolutePath}: $gameName with $playersCount players" }
        tree.save()

        val config = tree.configOrDefault(entry.setup())
        val replayable = entry.replayable(playersCount, config, tree.replayCallback())

        val s = Scanner(System.`in`)

        if (replayable.game is GameFlowImpl) {
            runBlocking {
                var nextViews = 0
                for (a in replayable.game.feedbackReceiver) {
                    logger.info { a }
                    if (a is GameFlowContext.Steps.NextView) {
                        if (nextViews++ > 10) throw IllegalStateException("Too many next views")
                    }
                    if (a is GameFlowContext.Steps.ActionPerformed<*>) {
                        tree.replayState(a.replayState)
                    }
                    if (a is GameFlowContext.Steps.AwaitInput) {
                        nextViews = 0
                        println("--- Await Input")
                        nextSteps(tree, replayable, s) || break
                    }
                    if (a is GameFlowContext.Steps.GameEnd) {
                        nextSteps(tree, replayable, s) || break
                        break
                    }
                }
            }
        } else {
            nextSteps(tree, replayable, s)
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

    private fun nextSteps(tree: TestPlayRoot, replayable: GameReplayableImpl<Any>, scanner: Scanner): Boolean {
        do {
            val nextStep = tree.nextStep(replayable)
            println("Performing saved step")
            if (nextStep is PlayTestStepPerform) {
                return true
            }
            if (nextStep is PlayTestStepAssertElimination && replayable.game.isGameOver()) {
                return true
            }
        } while (nextStep != null)

        val input = PlayTestInput(scanner, replayable)

        ConsoleView<Any>().showView(replayable.game)
        println("Choose:")
        println("R. Perform random action")
        println("P. Perform specific action")
        println("V. Assert view")
        println("A. Assert actions")
        println("E. Assert eliminations")
        println("X/Q. Exit/Quit")
        val choice: String
        try {
            choice = scanner.nextLine()
        } catch (e: NoSuchElementException) {
            println("No line from scanner, exiting")
            return false
        }
        val step = when (choice.toLowerCase()) {
            "x", "q" -> return false
            "r" -> {
                TODO()
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
                var input = ""
                do {
                    val options = when (current) {
                        is Map<*, *> -> current.keys.map { it.toString() }.sorted()
                        is List<*> -> current.indices
                        else -> break
                    }
                    println(options)
                    input = scanner.nextLine()
                    if (input.isEmpty()) break
                    paths.add(input)

                    current = viewNavigation(playerView, paths)
                } while (true)

                println("Value is $current")

                println("W. I expected something different")
                println("E. I expected this")
                println("X. I just expected it to be something")
                println("Q. I expected it to not exist")
                when (scanner.nextLine().toLowerCase()) {
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
            else -> throw IllegalArgumentException("Invalid choice")
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
