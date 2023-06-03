package net.zomis.games.compose.common.mfe.challenges

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import kotlinx.coroutines.Dispatchers
import net.zomis.games.compose.common.CoroutineScope
import net.zomis.games.compose.common.Navigator
import net.zomis.games.compose.common.game.LocalGameComponent
import net.zomis.games.compose.common.mfe.MfeGameTypes
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameConfigs
import net.zomis.games.impl.minesweeper.specials.OpenFieldChallengeDifficulty
import net.zomis.games.listeners.LimitedNextViews

object Challenges {
    fun createComponentForChallenge(
        componentContext: ComponentContext,
        challenge: Challenge,
        gameTypeStore: MfeGameTypes,
        navigator: Navigator<*>,
    ): ChallengeComponent {
        val coroutineScope = CoroutineScope(Dispatchers.Default, componentContext.lifecycle)

        return when (challenge) {
            is Challenge.OpenFieldChallenge -> {
                DefaultOpenFieldChallengeComponent(
                    LocalGameComponent(
                        coroutineScope,
                        gameTypeStore.ofcGameType,
                        playerCount = 1,
                        MutableValue(0),
                        config = { it.set("difficulty", challenge.difficulty) },
                    ) {
                        listOf(LimitedNextViews(10))
                    },
                    navigator = navigator
                )
            }
        }
    }
}

sealed interface Challenge {

    data class OpenFieldChallenge(val difficulty: OpenFieldChallengeDifficulty) : Challenge

}