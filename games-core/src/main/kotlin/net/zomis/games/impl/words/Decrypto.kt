package net.zomis.games.impl.words

import net.zomis.games.WinResult
import net.zomis.games.api.GamesApi
import net.zomis.games.common.PlayerIndex
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.Viewable
import net.zomis.games.impl.words.wordlists.DecryptoWords
import kotlin.random.Random

object Decrypto {

    class Team(val teamNumber: Int, val teamMembers: List<Int>) {
        var chat: String = ""
        var clueGiverIndex: Int = 0
        val clueGiverPlayer: Int get() = teamMembers[clueGiverIndex % teamMembers.size]
        val words = mutableListOf<String>()
        var interceptions: Int = 0
        var miscommunications: Int = 0
        val communicationHistory = mutableListOf<CluesAndGuess>()
        val interceptionHistory = mutableListOf<CluesAndGuess>()
    }
    fun playersOnTeam(teamIndex: Int, playerCount: Int): List<Int> {
        return (0 until playerCount).filter { it % 2 == teamIndex }
    }
    class Model(val playerCount: Int) {
        val teamA = Team(0, playersOnTeam(0, playerCount))
        val teamB = Team(1, playersOnTeam(1, playerCount))

        var currentTeamIndex: Int = 0
        var currentCode: List<Int> = listOf()
        val currentTeam get() = teams[currentTeamIndex]
        val opponentTeam: Team get() = teams[1 - currentTeamIndex]
        val teams = listOf(teamA, teamB)

        fun roundNumber(): Int = teams.map { it.interceptionHistory.size }.min()!!
        fun teamFor(viewer: PlayerIndex): Team? = teams.find { it.teamMembers.contains(viewer) }
    }
    data class CluesAndGuess(private val correctCode: List<Int>, val clues: Clues, var guess: Guess?): Viewable {
        var correctCodeRevealed: Boolean = false
        fun publicCorrectCode(): List<Int> = if (correctCodeRevealed) correctCode else listOf()
        override fun toView(viewer: PlayerIndex): Any?
            = mapOf("clues" to clues.clues, "guess" to guess?.guess, "correct" to publicCorrectCode())
        fun correctGuess(): Boolean = guess?.guess == correctCode
    }
    class Clues(val clues: List<String>)
    data class Guess(val guess: List<Int>)

    val factory = GamesApi.gameCreator(Model::class)
    val chat = factory.action("chat", String::class)
    val giveClue = factory.action("giveClue", Clues::class).serialization({ it.clues.joinToString("\n") }) {
        Clues(it.split("\n"))
    }
    val guessCode = factory.action("guessCode", Guess::class).serialization({ it.guess.joinToString("") }) {
        Guess(it.map { c -> c.toString().toInt() })
    }
    val game = factory.game("Decrypto") {
        setup {
            players(4..16)
            init { Model(playerCount) }
            onStart {
                val words = DecryptoWords.words.chooseXY(replayable, "words", 4, 4)
                game.teamA.words.addAll(words.first)
                game.teamB.words.addAll(words.second)

                // each team's encryptor should get a code to give some thinking time
                game.currentCode = replayable.ints("code") { randomCode() }
            }
        }
        gameFlowRules {
            rule("win the game") {
                appliesWhen { game.teams.any { it.interceptions >= 2 } && !eliminations.isGameOver() }
                applyForEach { game.teams.filter { it.interceptions >= 2 } }.effect {winningTeam ->
                    eliminations.eliminateMany(winningTeam.teamMembers, WinResult.WIN)
                    eliminations.eliminateRemaining(WinResult.LOSS)
                }
            }
            rule("lose the game") {
                appliesWhen { game.teams.any { it.miscommunications >= 2 } && !eliminations.isGameOver() }
                applyForEach { game.teams.filter { it.miscommunications >= 2 } }.effect {losingTeam ->
                    eliminations.eliminateMany(losingTeam.teamMembers, WinResult.LOSS)
                    eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
            beforeReturnRule("chat action") {
                action(chat) {
                    precondition { playerIndex != game.currentTeam.clueGiverPlayer }
                    options { listOf("") }
                    perform {
                        game.teamFor(playerIndex)!!.chat += "$playerIndex: ${action.parameter}\n"
                    }
                }
            }
            beforeReturnRule("view") {
                view("currentTeam") { game.currentTeamIndex }
                view("words") {
                    game.teamFor(viewer)?.words ?: emptyList<String>()
                }
                view("yourTeam") { game.teamFor(viewer)?.teamNumber }
                view("teams") {
                    // Clues and guesses are public information for both teams
                    game.teams.map { team ->
                        mapOf(
                            "members" to team.teamMembers,
                            "chat" to team.chat.takeIf { viewer in team.teamMembers },
                            "clueGiver" to team.clueGiverPlayer,
                            "intercepted" to team.interceptions,
                            "miscommunications" to team.miscommunications,
                            "communications" to team.communicationHistory.map { it.toView(viewer) },
                            "interceptions" to team.interceptionHistory.map { it.toView(viewer) }
                        )
                    }
                }
                view("code") {
                    if (this.viewer == game.currentTeam.clueGiverPlayer) game.currentCode else listOf()
                }
            }
        }
        gameFlow {
            loop {
                for (team in game.teams) {
                    // TODO: Refactor this code and extract phases into methods
                    game.currentTeamIndex = team.teamNumber
                    step("team ${team.teamNumber} - giveClue") {
                        yieldAction(giveClue) {
                            precondition { playerIndex == team.clueGiverPlayer }
                            options { listOf(Clues(listOf())) }
                            perform {
                                game.currentTeam.communicationHistory.add(CluesAndGuess(game.currentCode, action.parameter, null))
                                game.opponentTeam.interceptionHistory.add(CluesAndGuess(game.currentCode, action.parameter, null))
                            }
                        }
                    }.loopUntil { action?.actionType == giveClue.name }
                    var intercepted: Boolean = false
                    if (game.roundNumber() > 0) {
                        step("team ${team.teamNumber} - opponents guess code") {
                            yieldAction(guessCode) {
                                options { listOf(Guess(listOf())) }
                                precondition { playerIndex in game.opponentTeam.teamMembers }
                                perform {
                                    val item = game.opponentTeam.interceptionHistory.last()
                                    item.guess = action.parameter
                                    intercepted = item.correctGuess()
                                    if (intercepted) {
                                        game.currentTeam.communicationHistory.last().correctCodeRevealed = true
                                        item.correctCodeRevealed = true
                                        game.opponentTeam.interceptions++
                                        game.currentCode = replayable.ints("code") { randomCode() }
                                    }
                                }
                            }
                        }.loopUntil { action?.actionType == guessCode.name }
                    }
                    if (!intercepted) {
                        step("team ${team.teamNumber} - guess own code") {
                            yieldAction(guessCode) {
                                options { listOf(Guess(listOf())) }
                                precondition { playerIndex in game.currentTeam.teamMembers }
                                precondition { playerIndex != game.currentTeam.clueGiverPlayer }
                                perform {
                                    val item = game.currentTeam.communicationHistory.last()
                                    item.correctCodeRevealed = true
                                    game.opponentTeam.interceptionHistory.last().correctCodeRevealed = true
                                    item.guess = action.parameter
                                    if (!item.correctGuess()) game.currentTeam.miscommunications++
                                    game.currentCode = replayable.ints("code") { randomCode() }
                                }
                            }
                        }.loopUntil { action?.actionType == guessCode.name }
                    }
                    game.currentTeam.clueGiverIndex++
                }
            }
        }
    }

    private fun randomCode(): List<Int> = mutableListOf(1, 2, 3, 4).shuffled().take(3)

}
