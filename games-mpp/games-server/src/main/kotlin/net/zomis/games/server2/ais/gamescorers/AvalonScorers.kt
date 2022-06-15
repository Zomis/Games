package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.ResistanceAvalonGame

object AvalonScorers {

    val scorers = GamesImpl.game(ResistanceAvalonGame.game).scorers()
    val voteTrue = scorers.actionConditional(ResistanceAvalonGame.vote) { action.parameter }
    val performTrue = scorers.actionConditional(ResistanceAvalonGame.performMission) { action.parameter }
    val voteGoodCheat = scorers.actionConditional(ResistanceAvalonGame.vote) { action.parameter == model.voteTeam!!.team.all { it.character!!.good } }
    val voteAccordinglyCheat = scorers.actionConditional(ResistanceAvalonGame.vote) {
        val iAmGood = model.players[playerIndex].character!!.good
        val chosenAction = model.voteTeam!!.team.all { it.character!!.good } == iAmGood
        action.parameter == chosenAction
    }
    val performAccordingly = scorers.actionConditional(ResistanceAvalonGame.performMission) { action.parameter == model.players[playerIndex].character!!.good }

    val aiGood = scorers.ai("#AI_Good", voteTrue, performTrue)
    val aiBad = scorers.ai("#AI_Negative", voteTrue.weight(-1), performTrue.weight(-1))
    val aiGoodCheat = scorers.ai("#AI_Good_Cheat", voteGoodCheat, performTrue)
    val aiAccordinglyCheat = scorers.ai("#AI_Accordingly_Cheat", voteAccordinglyCheat, performAccordingly)
    val aiAccordinglyCheatTraitor = scorers.ai("#AI_Accordingly_Cheat_Traitor", voteAccordinglyCheat.weight(-1), performAccordingly.weight(-1))

    fun ais() = listOf(aiGood, aiGoodCheat, aiAccordinglyCheat, aiBad, aiAccordinglyCheatTraitor)

}
