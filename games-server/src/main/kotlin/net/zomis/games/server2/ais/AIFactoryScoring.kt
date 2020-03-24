package net.zomis.games.server2.ais

import net.zomis.aiscores.*
import net.zomis.core.events.EventSystem
import net.zomis.games.dsl.Actionable
import net.zomis.games.dsl.impl.GameImpl
import net.zomis.games.server2.games.PlayerGameMoveRequest

class AIFactoryScoring {

    fun <T: Any> scorer(config: ScoreConfig<GameImpl<T>, ActScorable<T>>, playerIndex: Int)
            : FieldScoreProducer<GameImpl<T>, ActScorable<T>> {
        val strategy = object: ScoreStrategy<GameImpl<T>, ActScorable<T>> {
            override fun canScoreField(parameters: ScoreParameters<GameImpl<T>>?, field: ActScorable<T>?): Boolean {
                return parameters!!.parameters.actions.type(field!!.actionType)!!.isAllowed(field)
            }

            override fun getFieldsToScore(params: GameImpl<T>?): MutableCollection<Actionable<T, Any>> {
                return params!!.actions.types().flatMap { it.availableActions(playerIndex) }.toMutableList()
            }
        }
        return FieldScoreProducer(config, strategy)
    }

    fun <T: Any> createAI(events: EventSystem, gameType: String, name: String, ai: ScoreConfigFactory<GameImpl<T>, ActScorable<T>>) {
        val config = ai.build()
        ServerAI(gameType, name) { game, index ->
            val obj = game.obj as GameImpl<T>
            if (noAvailableActions(obj, index)) {
                return@ServerAI listOf()
            }
            val scorer = scorer(config, index)

            val scores = scorer.analyzeAndScore(obj)
            val move = scores.getRank(1).random()
            val action = move.field
            listOf(PlayerGameMoveRequest(game, index, action.actionType, action.parameter))
        }.register(events)
    }

}