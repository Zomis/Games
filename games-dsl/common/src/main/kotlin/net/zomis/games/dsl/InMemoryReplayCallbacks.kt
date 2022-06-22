package net.zomis.games.dsl

@Deprecated("Replaced by GameListeners")
class InMemoryReplayCallbacks<T : Any>(val gameType: String): GameplayCallbacks<T>() {

    private var gameStartedState: GameSituationState = null
    private val actions = mutableListOf<ActionReplay>()
    private var playerCount: Int? = null
    private var config: GameConfigs? = null

    override fun onMove(actionIndex: Int, action: Actionable<T, Any>, actionReplay: ActionReplay) {
        val expectedActionIndex = actions.size
        if (actionIndex != expectedActionIndex) {
            throw IllegalStateException("ActionIndex $actionIndex was received but expected $expectedActionIndex")
        }
        this.actions.add(actionReplay)
    }

    override fun startedState(playerCount: Int, config: GameConfigs, state: GameSituationState) {
        this.playerCount = playerCount
        this.config = config
        this.gameStartedState = state
    }

    fun data(): ReplayData = ReplayData(gameType, playerCount!!, config!!, gameStartedState, actions)

}
