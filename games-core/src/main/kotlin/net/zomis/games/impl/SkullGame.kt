package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.CardZone
import net.zomis.games.dsl.ActionSerialization
import net.zomis.games.dsl.createActionType
import net.zomis.games.dsl.createGame
import net.zomis.games.dsl.sourcedest.next

enum class SkullCard {
    SKULL,
    FLOWER,
    ;
}
data class SkullPlayer(val index: Int, val hand: CardZone<SkullCard>) {
    val played = CardZone<SkullCard>()
    val chosen = CardZone<SkullCard>()
    var bet: Int = 0
    var pass: Boolean = false
    var points: Int = 0

    val totalCards: Int get() = hand.size + played.size + chosen.size
}
data class SkullGameConfig(val skulls: Int = 1, val flowers: Int = 3)

class SkullGameModel(config: SkullGameConfig, playerCount: Int) {

    var choseOwnSkull: Boolean = false
    val players = (0 until playerCount).map {
        SkullPlayer(it, CardZone(List(config.skulls){ SkullCard.SKULL }
                       .plus(List(config.flowers){ SkullCard.FLOWER }).toMutableList()))
    }
    var currentPlayerIndex: Int = 0
    val currentPlayer get() = players[currentPlayerIndex]

    fun nextTurn() {
        this.currentPlayerIndex = this.currentPlayerIndex.next(players.size)
    }

    fun newRound() {
        this.players.filter { it.totalCards > 0 }.forEach {
            it.played.moveAllTo(it.hand)
            it.chosen.moveAllTo(it.hand)
            it.pass = false
            it.bet = 0
        }
    }

}

object SkullGame {

    val bet = createActionType("bet", Int::class)
    val pass = createActionType("pass", Unit::class)
    val choose = createActionType("choose", SkullPlayer::class, ActionSerialization<SkullPlayer, SkullGameModel>({ it.index }, { game.players[it as Int] }))
    val discard = createActionType("discard", SkullCard::class)
    val play = createActionType("play", SkullCard::class)
    val game = createGame<SkullGameModel>("Skull") {
        setup(SkullGameConfig::class) {
            players(3..16)
            defaultConfig { SkullGameConfig() }
            init {
                SkullGameModel(config, playerCount)
            }
        }
        rules {
            allActions.precondition { playerIndex == game.currentPlayerIndex }
            fun nextTurn(game: SkullGameModel) { game.currentPlayerIndex = game.currentPlayerIndex.next(game.players.size) }

            view("currentPlayer") { game.currentPlayerIndex }

            action(play).options { game.currentPlayer.hand.cards }
            action(play).effect { game.currentPlayer.hand.card(action.parameter).moveTo(game.currentPlayer.played) }
            action(play).forceUntil { game.currentPlayer.played.size + game.currentPlayer.chosen.size > 0 || game.choseOwnSkull }
            view("players") {
                game.players.mapIndexed {index, it -> mapOf(
                    "hand" to if (viewer == index) it.hand.cards else it.hand.size,
                    "board" to if (viewer == index) it.played.cards else it.played.size,
                    "chosen" to it.chosen.cards,
                    "points" to it.points,
                    "bet" to it.bet,
                    "pass" to it.pass
                )}
            }

            view("you") { if (viewer != null) game.players[viewer!!].let { mapOf("hand" to it.hand.cards, "board" to it.played.cards) } else null }
            action(play).requires { game.players.all { it.bet == 0 } }
            action(bet).effect { game.currentPlayer.bet = action.parameter }
            action(bet).options { (game.players.map { it.bet }.max()!! + 1)..game.players.map { it.played.size }.sum() }
            action(pass).requires { game.players.any { it.bet > 0 } }
            action(pass).requires { game.players.count { !it.pass } > 1 }
            action(pass).effect { game.currentPlayer.pass = true }
            allActions.after { while (game.currentPlayer.pass || game.currentPlayer.totalCards == 0) nextTurn(game) }
            action(bet).requires {
                val maxBet = game.players.maxBy { it.bet }!!.bet
                game.currentPlayer.bet < maxBet || maxBet == 0
            }
            action(bet).after {
                if (game.players.sumBy { it.played.size } == action.parameter) {
                    // Auto pass
                    game.players.minus(game.players[action.playerIndex]).forEach { it.pass = true }
                }
            }

            action(play).after { nextTurn(game) }
            action(pass).after { nextTurn(game) }
            action(bet).after { nextTurn(game) }

            action(choose).effect { action.parameter.let { it.played.card(it.played.cards.last()).moveTo(it.chosen) } }
            action(choose).requires { action.parameter == game.currentPlayer || game.currentPlayer.played.cards.isEmpty() }
            action(choose).requires { game.currentPlayer.bet > 0 && !game.currentPlayer.pass && game.players.count { !it.pass } == 1 }
            action(choose).requires { action.parameter.played.cards.isNotEmpty() }
            action(choose).options { game.players.filter { it.played.cards.isNotEmpty() } }
            action(choose).after {
                val skullPlayer = game.players.find { it.chosen.cards.contains(SkullCard.SKULL) }
                if (skullPlayer != null) {
                    game.choseOwnSkull = skullPlayer == game.currentPlayer
                    game.currentPlayer.chosen.moveAllTo(game.currentPlayer.hand)
                    game.currentPlayer.played.moveAllTo(game.currentPlayer.hand)
                    if (!game.choseOwnSkull) {
                        game.currentPlayer.hand.random(replayable, 1, "lost") { it.name }.forEach { it.remove() }
                    }
                    game.newRound() // reset boards, bets and pass values
                    game.currentPlayerIndex = skullPlayer.index
                }
            }
            action(choose).after {
                if (game.players.flatMap { it.chosen.cards }.count { it == SkullCard.FLOWER } == game.currentPlayer.bet && game.currentPlayer.bet > 0) {
                    game.currentPlayer.points++
                    game.newRound()
                    game.currentPlayerIndex = action.playerIndex
                }
            }
            action(choose).after {
                if (game.currentPlayer.points == 2) {
                    eliminations.result(game.currentPlayerIndex, WinResult.WIN)
                    eliminations.eliminateRemaining(WinResult.LOSS)
                }
            }

            // If you lost to your own skull... choose a card to get rid of
            action(discard).requires { game.choseOwnSkull }
            action(discard).forceUntil { !game.choseOwnSkull }
            action(discard).options { game.currentPlayer.hand.cards }
            action(discard).effect {
                game.currentPlayer.hand.card(action.parameter).remove()
                game.choseOwnSkull = false
            }

            allActions.after {
                val emptyPlayer = game.players.find { it.totalCards == 0 && eliminations.remainingPlayers().contains(it.index) }
                if (emptyPlayer != null) {
                    emptyPlayer.bet = 0
                    emptyPlayer.pass = true
                    eliminations.result(emptyPlayer.index, WinResult.LOSS)
                    if (eliminations.remainingPlayers().size == 1) eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }
    }

}
