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
            "hand" to if (viewer == index) hand.cards.map { it.name } else hand.size,
            "board" to if (viewer == index) played.cards.map { it.name } else played.size,
            "chosen" to chosen.cards.map { it.name },
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
    val selfEliminatedChooseNextPlayer: Boolean = true,
    val bettingPlayerAlwaysStart: Boolean = true
)

class SkullGameModel(val config: SkullGameConfig, playerCount: Int): Viewable {

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

    fun currentBet(): Int = players.maxOf { it.bet }
    override fun toView(viewer: PlayerIndex): Any {
        return mapOf(
            "currentPlayer" to currentPlayerIndex,
            "cardsTotal" to players.sumBy { it.played.size + it.chosen.size },
            "cardsChosenRemaining" to currentBet() - players.sumBy { it.chosen.size },
            "players" to players.map { it.toView(viewer) },
            "you" to viewer?.let { players[viewer].let { pl -> mapOf("hand" to pl.hand.cards.map { it.name }, "board" to pl.played.cards.map { it.name }) } }
        )
    }

}

object SkullGame {

    val factory = GameCreator(SkullGameModel::class)

    val bet = factory.action("bet", Int::class)
    val pass = factory.action("pass", Unit::class)
    val choose = factory.action("choose", SkullPlayer::class).serialization({ it.index }, { game.players[it] })
    val chooseNextPlayer = factory.action("chooseNextPlayer", SkullPlayer::class)
        .serialization({ it.index }, { game.players[it] })
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
        actionRules {
            allActions.precondition { playerIndex == game.currentPlayerIndex }
            fun nextTurn(game: SkullGameModel) { game.currentPlayerIndex = game.currentPlayerIndex.next(game.players.size) }

            action(play).options { game.currentPlayer.hand.cards }
            action(play).effect { game.currentPlayer.hand.card(action.parameter).moveTo(game.currentPlayer.played) }
            allActions.precondition {
                actionType == play.name
                    || game.currentPlayer.played.isNotEmpty()
                    || game.currentPlayer.chosen.isNotEmpty()
                    || game.choseOwnSkull
            }
            action(play).requires { game.players.all { it.bet == 0 } }
            action(bet).effect { game.currentPlayer.bet = action.parameter }
            action(bet).options { (game.players.maxOf { it.bet } + 1)..game.players.sumOf { it.played.size } }
            action(pass).requires { game.players.any { it.bet > 0 } }
            action(pass).requires { game.players.count { !it.pass } > 1 }
            action(pass).effect { game.currentPlayer.pass = true }
            action(pass).effect { log { "$player passes" } }
            allActions.after {
                if (!game.choseOwnSkull) {
                    while (game.currentPlayer.pass || game.currentPlayer.totalCards == 0) nextTurn(game)
                }
            }
            action(bet).requires {
                val maxBet = game.players.maxByOrNull { it.bet }!!.bet
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
            action(choose).precondition { game.currentPlayer.bet > 0 && !game.currentPlayer.pass && game.players.count { !it.pass } == 1 }
            action(choose).requires { action.parameter.played.cards.isNotEmpty() }
            action(choose).options { game.players.filter { it.played.cards.isNotEmpty() } }
            action(choose).after {
                val skullPlayer = game.players.find { it.chosen.cards.contains(SkullCard.SKULL) }
                if (skullPlayer != null) {
                    game.choseOwnSkull = skullPlayer == game.currentPlayer
                    if (game.choseOwnSkull) {
                        log { "$player chose their own skull and will have to choose a card to discard" }
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
                    if (!game.config.bettingPlayerAlwaysStart) {
                        game.currentPlayerIndex = skullPlayer.index
                    }
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
            action(discard).forceWhen { game.currentPlayer.totalCards > 0 && game.choseOwnSkull }
            action(discard).options { game.currentPlayer.hand.cards }
            action(discard).effect {
                logSecret(action.playerIndex) { "$player discarded $action" }
                game.currentPlayer.hand.card(action.parameter).remove()
                game.choseOwnSkull = (game.currentPlayer.totalCards == 0) // Allow chooseNextPlayer action if player is eliminated
                if (game.choseOwnSkull) {
                    log { "$player was eliminated by their own skull and has to choose the player to go next" }
                }
            }

            action(chooseNextPlayer).forceWhen { game.currentPlayer.totalCards == 0 && game.config.selfEliminatedChooseNextPlayer && game.choseOwnSkull }
            action(chooseNextPlayer).options { game.players.filter { it.totalCards > 0 } }
            action(chooseNextPlayer).effect {
                val nextPlayer = action.parameter.index
                log { "$player chose ${player(nextPlayer)} to be the next player" }
                game.choseOwnSkull = false
                game.currentPlayerIndex = nextPlayer
            }

            allActions.after {
                val emptyPlayer = game.players.find {
                    it.totalCards == 0 && eliminations.remainingPlayers().contains(it.index) && game.currentPlayer != it && !game.choseOwnSkull
                }
                if (emptyPlayer != null) {
                    log { "${player(emptyPlayer.index)} lost all their cards and is out of the game" }
                    emptyPlayer.bet = 0
                    emptyPlayer.pass = true
                    eliminations.result(emptyPlayer.index, WinResult.LOSS)
                    if (eliminations.remainingPlayers().size == 1) eliminations.eliminateRemaining(WinResult.WIN)
                }
            }
        }

        val playFlower = scorers.actionConditional(play) { action.parameter == SkullCard.FLOWER }
        val playSkull = scorers.actionConditional(play) { action.parameter == SkullCard.SKULL }
        val betHigh = scorers.action(bet) { 1.1 + (action.parameter).toDouble() / 100 }
        val betLow = scorers.action(bet) { 1.1 + -(action.parameter).toDouble() / 100 }
        val betMinimal = scorers.actionConditional(bet) { action.parameter == action.game.currentBet() + 1 }
        val play = scorers.isAction(play)
        val chooseAny = scorers.isAction(choose)
        val pass = scorers.isAction(pass)
        val discardFlower = scorers.actionConditional(discard) { action.parameter == SkullCard.FLOWER }
        val discardFirstTwoFlowers = scorers.actionConditional(discard) { action.parameter == SkullCard.FLOWER && action.game.currentPlayer.hand.size > 2 }
        val discardSkullLast = scorers.actionConditional(discard) { action.parameter == SkullCard.SKULL && action.game.currentPlayer.hand.size == 2 }
        val choose = scorers.action(choose) { action.parameter.index.toDouble() }

        scorers.ai("#AI_Reasonable",
            play, betMinimal, pass, discardFirstTwoFlowers, discardSkullLast, chooseAny
        )
        scorers.ai("#AI_Self_Destruct", playSkull, betHigh.weight(10), discardFlower)
        scorers.ai("#AI_Play_Random_Pass", play.weight(0.1), pass, betLow)
        scorers.ai("#AI_Flower_Pass", playFlower.weight(20), pass.weight(10), betLow)
        scorers.ai("#AI_Keep_Playing", play.weight(10), pass.weight(0.1), betLow)
    }

}
