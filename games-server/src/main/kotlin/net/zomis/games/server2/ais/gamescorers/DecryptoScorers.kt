package net.zomis.games.server2.ais.gamescorers

import net.zomis.games.dsl.GamesImpl
import net.zomis.games.impl.words.Decrypto
import net.zomis.games.scorers.ScorerController

object DecryptoScorers {

    val scorers = GamesImpl.game(Decrypto.game).scorers()
    fun ais(): List<ScorerController<out Any>> = listOf(noChat)

    val noChatScorer = scorers.actionConditional(Decrypto.chat) {
        true
    }
    val giveClue = scorers.isAction(Decrypto.giveClue)
    val guess = scorers.isAction(Decrypto.guessCode)

    val noChat = scorers.ai("#AI_NoChat", noChatScorer.weight(-1), giveClue, guess)

}