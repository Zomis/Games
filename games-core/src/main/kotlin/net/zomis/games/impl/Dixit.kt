import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.isObserver
import net.zomis.games.common.toSingleList
import net.zomis.games.common.withLeadingZeros
import net.zomis.games.dsl.Replayable
import kotlin.math.min

object Dixit {

    val cardSets = mapOf("dixit-cards-01" to 100)
    fun createCards(cardSet: String): List<String> = cardSets.get(cardSet)?.let {count ->
        (1..count).map { it.withLeadingZeros(3) }
    } ?: throw IllegalArgumentException("No cardSet with name: $cardSet")
    class Config(val cardSet: String)
    class Story(val card: String, val clue: String): Replayable {
        override fun toStateString(): String = "$card:$clue"
    }
    class PlaceCard(val card: String): Replayable {
        override fun toStateString(): String = "$card:null"
    }
    class Vote(val first: String, val second: String?): Replayable {
        override fun toStateString(): String = "$first:$second"
        fun asList(): List<String> = listOfNotNull(first, second)
    }
    class Player(val playerIndex: Int) {
        var points: Int = 0
        val cards = CardZone<String>()
        var placedCard: String? = null
        var vote: Vote? = null
    }
    class Model(val playerCount: Int, val config: Config) {
        fun startingCards(): Int = 6
        var phase: String = "setup"
        val players = (0 until playerCount).map { Player(it) }
        var story: Story? = null
        var storyteller: Player = players[0]
        val everyoneButStoryteller get() = players.minus(storyteller)
        val board = CardZone<String>()
        val deck = CardZone<String>()
        val trash = CardZone<String>()
    }
    val factory = GamesApi.gameCreator(Model::class)
    val story = factory.action("story", Story::class).serialization({ it.toStateString() }) {text ->
        Story(text.substringBefore(':'), text.substringAfter(':'))
    }
    val place = factory.action("place", PlaceCard::class).serializer { it.toStateString() }
    val vote = factory.action("vote", Vote::class).serializer { it.toStateString() }

    val game = factory.game("Dixit") {
        setup(Config::class) {
            players(3..12)
            defaultConfig { Config("dixit-cards-01") }
            init {
                Model(playerCount, config)
            }
            onStart {
                val game = it
                game.deck.cards.addAll(createCards(it.config.cardSet))
                val cards = game.deck.random(this, game.startingCards() * game.playerCount, "cards") { c -> c }
                game.deck.deal(cards.map { c -> c.card }.toList(), game.players.map { player -> player.cards })
            }
        }
        gameFlow {
            loop {
                for (player in game.players) {
                    game.story = null
                    game.storyteller = game.players[player.playerIndex]
                    game.phase = "tell story"
                    step("tell story") {
                        yieldAction(story) {
                            precondition { playerIndex == game.storyteller.playerIndex }
                            options {
                                check(game.storyteller.cards.size > 0)
                                game.storyteller.cards.cards.map { Story(it, "random") }
                            }
                            requires {
                                game.storyteller.cards.cards.contains(action.parameter.card)
                            }
                            requires { action.parameter.clue.isNotBlank() }
                            perform {
                                game.story = action.parameter
                                game.storyteller.cards.card(action.parameter.card).moveTo(game.board)
                                game.storyteller.placedCard = action.parameter.card
                            }
                        }
                    }
                    game.phase = "place cards"
                    step("place cards") {
                        yieldAction(place) {
                            precondition { game.players[playerIndex].placedCard == null }
                            options { game.players[playerIndex].cards.cards.map { PlaceCard(it) } }
                            requires { game.players[playerIndex].cards.cards.contains(action.parameter.card) }
                            perform {
                                game.players[playerIndex].cards.card(action.parameter.card).moveTo(game.board)
                                game.players[playerIndex].placedCard = action.parameter.card
                            }
                        }
                    }.loopUntil { game.players.all { it.placedCard != null } }

                    game.board.cards.shuffle()

                    game.phase = "vote for cards"
                    step("vote for cards") {
                        yieldAction(vote) {
                            precondition { game.players[playerIndex].vote == null }
                            options {
                                game.board.cards.minus(game.players[playerIndex].placedCard!!).map { Vote(it, null) }
                            }
                            requires {
                                // May not vote for your own card
                                game.players[playerIndex].placedCard !in action.parameter.asList()
                            }
                            perform { game.players[playerIndex].vote = action.parameter }
                        }
                    }.loopUntil { game.everyoneButStoryteller.all { it.vote != null } }

                    game.phase = "scoring 1"
                    step("correct answers") {
                        val correct = game.everyoneButStoryteller.filter {
                            it.vote!!.asList().contains(game.storyteller.placedCard!!)
                        }
                        if (correct.isEmpty() || correct.size == game.playerCount) {
                            // Everyone except storyteller gets two points
                            game.everyoneButStoryteller.forEach { it.points += 2 }
                        } else {
                            // Everyone who guessed correctly gets three points
                            correct.forEach { it.points += 3 }
                        }
                    }

                    game.phase = "scoring 2"
                    step("bonus points") {
                        game.everyoneButStoryteller.associateWith {scoringPlayer ->
                            val votesForPlayer = game.everyoneButStoryteller.count {
                                scoringPlayer.placedCard in it.vote!!.asList()
                            }
                            votesForPlayer.coerceAtMost(3)
                        }.forEach {
                            it.key.points += it.value
                        }
                    }

                    game.phase = "prepare next round"
                    step("prepare next round") {
                        game.board.asSequence().forEach { it.moveTo(game.trash) }
                        game.players.forEach {
                            it.vote = null
                            it.placedCard = null
                        }
                        if (game.deck.size < game.playerCount) {
                            game.trash.asSequence().forEach { it.moveTo(game.deck) }
                        }
                        val replacementCards = game.deck.random(replayable, game.playerCount, "replacement-cards") { it }
                        game.deck.deal(replacementCards.map { it.card }.toList(), game.players.map { it.cards })
                    }
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                view("phase") { game.phase }
                view("config") {
                    mapOf("cardSet" to game.config.cardSet)
                }
                view("story") { game.story?.clue }
                view("storyteller") { game.storyteller.playerIndex }
                view("hand") {
                    if (viewer.isObserver()) return@view emptyList<String>()
                    game.players[viewer!!].cards.cards
                }
                view("board") {
                    game.board.cards.takeIf { it.size == game.playerCount } ?: emptyList<String>()
                }
                view("players") {
                    game.players.map {
                        mapOf(
                            "points" to it.points,
                            "voted" to (it.vote != null),
                            "placed" to (it.placedCard != null)
                        )
                    }
                }
            }
            rule("game end") {
                appliesWhen { !eliminations.isGameOver() && game.players.any { it.points >= 30 } }
                effect {
                    eliminations.eliminateBy(game.players.map { it.playerIndex to it.points }, compareBy { it })
                }
            }
        }
    }

}