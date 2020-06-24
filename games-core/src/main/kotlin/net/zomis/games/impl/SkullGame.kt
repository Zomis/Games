package net.zomis.games.impl

import net.zomis.games.WinResult
import net.zomis.games.cards.CardZone
import net.zomis.games.common.PlayerIndex
import net.zomis.games.common.next
import net.zomis.games.dsl.*

enum class SkullCard {
    SKULL,
    FLOWER,
    ;
}

data class SkullPlayer(val index: Int, val hand: CardZone<SkullCard>): Viewable {
    val played = CardZone<SkullCard>()
    val chosen = CardZone<SkullCard>()
    var bet: Int = 0
    var pass: Boolean = false
    var points: Int = 0

    val totalCards: Int get() = hand.size + played.size + chosen.size

    override fun toView(viewer: PlayerIndex): Any? {
        return mapOf(
            "hand" to if (viewer == index) hand.cards else hand.size,
            "board" to if (viewer == index) played.cards else played.size,
            "chosen" to chosen.cards,
            "points" to points,
            "bet" to bet,
            "pass" to pass
        )
    }
}

data class SkullGameConfig(
    val skulls: Int = 1,
    val flowers: Int = 3,
    // TODO: Technically same player should start. If player *eliminate* themselves, they should choose the next player.
    val selfEliminatedChooseNextPlayer: Boolean = false,
    val skullPlayerStarts: Boolean = false
)

class SkullGameModel(config: SkullGameConfig, playerCount: Int): Viewable {

    var choseOwnSkull: Boolean = false
    val players = (0 until playerCount).map {
        SkullPlayer(it, CardZone(List(config.skulls){ SkullCard.SKULL }
                       .plus(List(config.flowers){ SkullCard.FLOWER }).toMutableList()))
    }
    var currentPlayerIndex: Int = 0
    val currentPlayer get() = players[currentPlayerIndex]

    fun newRound() {
        this.players.filter { it.totalCards > 0 }.forEach {
            it.played.moveAllTo(it.hand)
            it.chosen.moveAllTo(it.hand)
            it.pass = false
            it.bet = 0
        }
    }

    fun currentBet(): Int = players.map { it.bet }.max()!!
    override fun toView(viewer: PlayerIndex): Any? {
        return mapOf(
            "currentPlayer" to currentPlayerIndex,
            "players" to players.map { it.toView(viewer) },
            "you" to viewer?.let { players[viewer].let { mapOf("hand" to it.hand.cards, "board" to it.played.cards) } }
        )
    }

}

object SkullGame {

    val factory = GameCreator(SkullGameModel::class)

    val bet = factory.action("bet", Int::class)
    val pass = factory.action("pass", Unit::class)
    val choose = factory.action("choose", SkullPlayer::class).serialization(Int::class, { it.index }, { game.players[it] })
    val discard = factory.action("discard", SkullCard::class)
    val play = factory.action("play", SkullCard::class)
    val game = factory.game("Skull") {
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

            action(play).options { game.currentPlayer.hand.cards }
            action(play).effect { game.currentPlayer.hand.card(action.parameter).moveTo(game.currentPlayer.played) }
            action(play).forceUntil { game.currentPlayer.played.size + game.currentPlayer.chosen.size > 0 || game.choseOwnSkull }
            action(play).requires { game.players.all { it.bet == 0 } }
            action(bet).effect { game.currentPlayer.bet = action.parameter }
            action(bet).options { (game.players.map { it.bet }.max()!! + 1)..game.players.map { it.played.size }.sum() }
            action(pass).requires { game.players.any { it.bet > 0 } }
            action(pass).requires { game.players.count { !it.pass } > 1 }
            action(pass).effect { game.currentPlayer.pass = true }
            action(pass).effect { log { "$player passes" } }
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

            action(play).effect {
                logSecret(action.playerIndex) { "$player played $action" }.publicLog { "$player played a card" }
            }
            action(play).after { nextTurn(game) }
            action(pass).after { nextTurn(game) }
            action(bet).effect { log { "$player bets $action" } }
            action(bet).after { nextTurn(game) }

            action(choose).effect {
                val player = action.parameter
                val chosenCard = player.played.cards.last()
                player.played.card(chosenCard).moveTo(player.chosen)
                log { "${this.player} choose ${player(action.index)} and revealed $chosenCard" }
            }
            action(choose).requires { action.parameter == game.currentPlayer || game.currentPlayer.played.cards.isEmpty() }
            action(choose).requires { game.currentPlayer.bet > 0 && !game.currentPlayer.pass && game.players.count { !it.pass } == 1 }
            action(choose).requires { action.parameter.played.cards.isNotEmpty() }
            action(choose).options { game.players.filter { it.played.cards.isNotEmpty() } }
            action(choose).after {
                val skullPlayer = game.players.find { it.chosen.cards.contains(SkullCard.SKULL) }
                if (skullPlayer != null) {
                    game.choseOwnSkull = skullPlayer == game.currentPlayer
                    if (game.choseOwnSkull) {
                        log { "$player chose their own and will have to choose a card to discard" }
                    }
                    game.currentPlayer.chosen.moveAllTo(game.currentPlayer.hand)
                    game.currentPlayer.played.moveAllTo(game.currentPlayer.hand)
                    if (!game.choseOwnSkull) {
                        game.currentPlayer.hand.random(replayable, 1, "lost") { it.name }.forEach {
                            logSecret(action.playerIndex) { "$player lost a ${it.card}" }.publicLog { "$player lost a card" }
                            it.remove()
                        }
                    }
                    game.newRound() // reset boards, bets and pass values
                    game.currentPlayerIndex = skullPlayer.index
                }
            }
            action(choose).after {
                if (game.players.flatMap { it.chosen.cards }.count { it == SkullCard.FLOWER } == game.currentPlayer.bet && game.currentPlayer.bet > 0) {
                    game.currentPlayer.points++
                    log { "$player completed the bet of ${game.currentPlayer.bet} and got a point!" }
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
                logSecret(action.playerIndex) { "$player discarded $action" }
                game.currentPlayer.hand.card(action.parameter).remove()
                game.choseOwnSkull = false
            }

            allActions.after {
                val emptyPlayer = game.players.find { it.totalCards == 0 && eliminations.remainingPlayers().contains(it.index) }
                if (emptyPlayer != null) {
                    log { "${player(emptyPlayer.index)} lost all their cards and is out of the game" }
                    emptyPlayer.bet = 0
                    emptyPlayer.pass = true
                    eliminations.result(emptyPlayer.index, WinResult.LOSS)
                    if (eliminations.remainingPlayers().size == 1) eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }
    }

}
