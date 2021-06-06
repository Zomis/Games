package net.zomis.games.server.test

import com.fasterxml.jackson.annotation.JsonCreator
import net.zomis.games.PlayerElimination
import net.zomis.games.WinResult
import net.zomis.games.dsl.ConsoleView
import net.zomis.games.dsl.DslConsoleView
import net.zomis.games.dsl.GameReplayableImpl
import kotlin.math.exp

enum class PlayTestViewAssertionType {
    NOT_EQUALS,
    EQUALS,
    EXISTS,
    DOES_NOT_EXIST
}
enum class PlayTestActionAssertionType {
    ALLOWED,
    NOT_ALLOWED,
    COUNT
}
enum class PlayTestEliminationAssertionType {
    REMAINING_PLAYERS,
    ELIMINATION,
    PLAYER_ALIVE,
    PLAYER_ELIMINATED
}

class PlayTestException(message: String): Exception(message)

sealed class PlayTestStep(val type: String)

class PlayTestStepPerform(val playerIndex: Int, val actionType: String, val action: Any) : PlayTestStep("perform")
data class PlayTestStepAssertView @JsonCreator constructor(
    val playerIndex: Int,
    val assertionType: PlayTestViewAssertionType,
    val path: List<Any>,
    val parameter: Any
) : PlayTestStep("assertView") {

    fun assert(replayable: GameReplayableImpl<Any>) {
        println("Asserting $this")
        val actual = replayable.game.view(playerIndex)
        val value = PlayTests.viewNavigation(actual, path)

        when (assertionType) {
            PlayTestViewAssertionType.EQUALS -> {
                if (value != parameter) {
                    ConsoleView<Any>().showView(replayable.game, playerIndex)
                    throw PlayTestException("Mismatching view for $playerIndex : Path $path was $value but excepted $parameter")
                }
            }
            PlayTestViewAssertionType.NOT_EQUALS -> {
                if (value == parameter) {
                    ConsoleView<Any>().showView(replayable.game, playerIndex)
                    throw PlayTestException("Mismatching view for $playerIndex : Path $path was $value but excepted something different than $parameter")
                }
            }
            PlayTestViewAssertionType.EXISTS -> {
                if (value == null) {
                    ConsoleView<Any>().showView(replayable.game, playerIndex)
                    throw PlayTestException("Mismatching view for $playerIndex : Path $path was $value but excepted it to exist")
                }
            }
            PlayTestViewAssertionType.DOES_NOT_EXIST -> {
                if (value != null) {
                    ConsoleView<Any>().showView(replayable.game, playerIndex)
                    throw PlayTestException("Mismatching view for $playerIndex : Path $path was $value but excepted it to not exist")
                }
            }
            else -> TODO("Unknown assertion type: $assertionType")
        }
    }

}

class PlayTestStepAssertActions @JsonCreator constructor(
    val playerIndex: Int,
    val assertionType: PlayTestActionAssertionType,
    val chosen: List<Any>,
    val actionType: String?,
    val parameter: Any?
) : PlayTestStep("assertActions") {
    fun assert(replayable: GameReplayableImpl<Any>) {
        TODO("Not yet implemented")
    }
}

class PlayTestStepAssertElimination @JsonCreator constructor(
    val assertionType: PlayTestEliminationAssertionType,
    val playerIndex: Int? = null,
    val remainingPlayers: Int? = null,
    val winResult: WinResult? = null,
    val position: Int? = null
) : PlayTestStep("assertEliminations") {
    fun assert(replayable: GameReplayableImpl<Any>) {
        println("Asserting $this")
        val eliminations = replayable.game.eliminations
        when (this.assertionType) {
            PlayTestEliminationAssertionType.REMAINING_PLAYERS -> {
                val actual = eliminations.remainingPlayers().size
                if (actual != remainingPlayers) {
                    throw PlayTestException("Mismatching eliminations. Expected $remainingPlayers remaining players but was $actual")
                }
            }
            PlayTestEliminationAssertionType.ELIMINATION -> {
                val actual = eliminations.eliminationFor(playerIndex!!)
                val expected = PlayerElimination(playerIndex, winResult!!, position!!)
                if (actual != expected) {
                    throw PlayTestException("Mismatching eliminations. Expected $expected but was $actual")
                }
            }
            PlayTestEliminationAssertionType.PLAYER_ALIVE -> {
                val actual = eliminations.eliminationFor(playerIndex!!)
                if (eliminations.isEliminated(playerIndex)) {
                    throw PlayTestException("Mismatching eliminations. Expected alive but was $actual")
                }
            }
            PlayTestEliminationAssertionType.PLAYER_ELIMINATED -> {
                if (eliminations.isAlive(playerIndex!!)) {
                    throw PlayTestException("Mismatching eliminations. Expected eliminated but was remaining")
                }
            }
            else -> TODO()
        }
    }
}
