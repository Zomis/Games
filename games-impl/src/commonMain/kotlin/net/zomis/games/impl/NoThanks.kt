package net.zomis.games.impl

import net.zomis.games.WinResult
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

    class ViewModel(
        val viewer: Int,
        val cardsRemaining: Int,
        val currentCard: Int?,
        val currentPlayer: Int,
        val currentTokens: Int,
        val players: List<ViewPlayer>
    ) {
        constructor(model: Model, viewer: Int) : this(
            viewer,
            cardsRemaining = model.deck.size,
            currentCard = model.currentCard,
            currentPlayer = model.currentPlayer,
            currentTokens = model.currentTokens,
            players = model.players.map(::ViewPlayer)
        )
    }

    class ViewPlayer(val tokens: Int, val cards: List<Int>) {
        constructor(player: Player) : this(player.tokens, player.cards.toList())
    }

    val factory = GamesApi.gameCreator(Model::class)
    val action = factory.action("take", Boolean::class)
    val viewModel = factory.viewModel(::ViewModel)
    val game = factory.game("NoThanks") {
        setup {
            players(2..7)
            init {
                Model(playerCount)
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                viewModel(viewModel)
            }
        }
        gameFlow {
            loop {
                if (game.currentCard == null) {
                    if (game.deck.isEmpty()) {
                        eliminations.eliminateRemaining(WinResult.DRAW)
//                        eliminations.eliminateBy()
                        return@loop
                    }
                    game.currentCard = game.deck.random(replayable, 1, "card", Int::toString).first().card
                    game.deck.cards.remove(game.currentCard)
                }
                step("turn") {
                    yieldAction(action) {
                        precondition { playerIndex == game.currentPlayer }
                        options { listOf(true, false) }
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