package net.zomis.games.impl

import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.next

object NoThanks {

    class Player(var tokens: Int = 0) {
        val cards: CardZone<Int> = CardZone()
    }
    class Model(val playerCount: Int) {
        val deck = CardZone((3..36).toMutableList())
        val startTokens = when (playerCount) {
            in 2..5 -> 11
            6 -> 9
            7 -> 7
            else -> throw IllegalArgumentException("Unsupported player count: $playerCount")
        }
        val players = (1..playerCount).map {
            Player(tokens = startTokens)
        }
        var currentCard: Int? = null
        var currentTokens: Int = 0
        var currentPlayer = 0
    }

    class ViewModel(model: Model) {
        val cardsRemaining = model.deck.size
        val currentCard = model.currentCard
        val currentPlayer = model.currentPlayer
        val currentTokens = model.currentTokens
        val players = model.players.map(::ViewPlayer)
    }
    class ViewPlayer(player: Player) {
        val tokens = player.tokens
        val cards = player.cards.toList()
    }

    val factory = GamesApi.gameCreator(Model::class)
    val action = factory.action("take", Boolean::class)
    // val viewModel = factory.viewModel(::ViewModel)
    val game = factory.game("NoThanks") {
        setup {
            players(2..7)
            init {
                Model(playerCount)
            }
        }
        gameFlow {
            while (game.deck.isNotEmpty()) {
                if (game.currentCard == null) {
                    game.currentCard = game.deck.random(replayable, 1, "card", Int::toString).first().card
                    game.deck.cards.remove(game.currentCard)
                }
                step("turn") {
                    yieldAction(action) {
                        precondition { playerIndex == game.playerCount }
                        requires { action.parameter || game.players[playerIndex].tokens > 0 }
                        perform {
                            val player = game.players[playerIndex]
                            if (action.parameter) {
                                player.cards.add(game.currentCard!!)
                                player.tokens += game.currentTokens
                                game.currentTokens = 0
                                game.currentCard = null
                            } else {
                                player.tokens--
                                game.currentTokens++
                                game.currentPlayer = game.currentPlayer.next(eliminations)
                            }
                        }
                    }
                }
            }
        }
    }

}