import net.zomis.games.api.GamesApi
import net.zomis.games.cards.CardZone
import net.zomis.games.common.isObserver
import net.zomis.games.common.withLeadingZeros
import net.zomis.games.dsl.Replayable
import net.zomis.games.dsl.flow.GameFlowScope

object Dixit {

    val cardSets = mapOf("dixit-cards-01" to 100)
    fun createCards(cardSet: String): List<String> = cardSets.get(cardSet)?.let {count ->
        (1..count).map { it.withLeadingZeros(3) }
    } ?: throw IllegalArgumentException("No cardSet with name: $cardSet")
    val factory = GamesApi.gameCreator(Model::class)
    val story = factory.action("story", ActionStory::class).serialization({ it.toStateString() }) {text ->
        ActionStory(text.substringBefore(':'), text.substringAfter(':'))
    }
    val place = factory.action("place", ActionPlaceCard::class).serializer { it.toStateString() }
    val vote = factory.action("vote", ActionVote::class).serializer { it.toStateString() }

    class Config(val cardSet: String)
    class ActionStory(val card: String, val clue: String): Replayable {
        override fun toStateString(): String = "$card:$clue"
    }
    class ActionPlaceCard(val card: String): Replayable {
        override fun toStateString(): String = "$card:null"
    }
    class ActionVote(val first: String, val second: String?): Replayable {
        override fun toStateString(): String = "$first:$second"
        fun asList(): List<String> = listOfNotNull(first, second)
    }
    class Player(val playerIndex: Int) {
        var points: Int = 0
        val cards = CardZone<String>()
        var placedCard: String? = null
        var vote: ActionVote? = null
        override fun toString(): String = "($playerIndex: $points points, $cards, placed $placedCard, voted for $vote)"
    }
    class Model(val playerCount: Int, val config: Config) {
        fun startingCards(): Int = 6
        var phase: String = "setup"
        val players = (0 until playerCount).map { Player(it) }
        var story: ActionStory? = null
        var storyteller: Player = players[0]
        val everyoneButStoryteller get() = players.minus(storyteller)
        val board = CardZone<String>()
        val deck = CardZone<String>()
        val trash = CardZone<String>()
    }

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
                    // TODO: Check eliminations instead, or just stop executing the coroutine in common flow-code?
                    if (game.players.any { it.points >= 30 }) return@loop
                    game.story = null
                    game.storyteller = game.players[player.playerIndex]
                    storytellPhase(this)
                    placecardPhase(this)
                    game.board.cards.shuffle()
                    votePhase(this)
                    scoringPhase(this)
                    cleanupPhase(this)
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

    private suspend fun storytellPhase(gameFlow: GameFlowScope<Model>) {
        gameFlow.apply {
            game.phase = "tell story"
            step("tell story") {
                yieldAction(story) {
                    precondition { playerIndex == game.storyteller.playerIndex }
                    options {
                        check(game.storyteller.cards.size > 0)
                        game.storyteller.cards.cards.map { ActionStory(it, "random") }
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
        }
    }

    private suspend fun placecardPhase(gameFlow: GameFlowScope<Model>) {
        gameFlow.apply {
            game.phase = "place cards"
            step("place cards") {
                yieldAction(place) {
                    precondition { game.players[playerIndex].placedCard == null }
                    options { game.players[playerIndex].cards.cards.map { ActionPlaceCard(it) } }
                    requires { game.players[playerIndex].cards.cards.contains(action.parameter.card) }
                    perform {
                        game.players[playerIndex].cards.card(action.parameter.card).moveTo(game.board)
                        game.players[playerIndex].placedCard = action.parameter.card
                    }
                }
            }.loopUntil { game.players.all { it.placedCard != null } }
        }
    }

    private suspend fun votePhase(gameFlow: GameFlowScope<Model>) {
        gameFlow.apply {
            game.phase = "vote for cards"
            step("vote for cards") {
                yieldAction(vote) {
                    precondition { game.players[playerIndex].vote == null }
                    options {
                        game.board.cards.minus(game.players[playerIndex].placedCard!!).map { ActionVote(it, null) }
                    }
                    requires {
                        // May not vote for your own card
                        game.players[playerIndex].placedCard !in action.parameter.asList()
                    }
                    perform { game.players[playerIndex].vote = action.parameter }
                }
            }.loopUntil { game.everyoneButStoryteller.all { it.vote != null } }
        }
    }

    private suspend fun scoringPhase(gameFlow: GameFlowScope<Model>) {
        gameFlow.apply {
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
        }
    }

    private suspend fun cleanupPhase(gameFlow: GameFlowScope<Model>) {
        gameFlow.apply {
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