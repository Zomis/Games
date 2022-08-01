package net.zomis.games.dsl

typealias GameSituationState = Map<String, Any>?
data class ActionReplay(val actionType: String, val playerIndex: Int, val serializedParameter: Any, val state: Map<String, Any>)
data class ReplayData(
    val gameType: String,
    val playerCount: Int,
    val config: GameConfigs,
    val initialState: GameSituationState,
    val actions: List<ActionReplay>
) {
    fun serialize(): Map<String, Any?> {
        return mapOf(
            "gameType" to gameType,
            "playerCount" to playerCount,
            "config" to config.toJSON(),
            "initialState" to initialState,
            "actions" to actions
        )
    }

    fun addAction(actionReplay: ActionReplay?): ReplayData {
        if (actionReplay == null) return this
        return this.copy(actions = actions + actionReplay)
    }
}
