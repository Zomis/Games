package net.zomis.games.dsl

typealias GameSituationState = Map<String, Any>?
data class ActionReplay(val actionType: String, val playerIndex: Int, val serializedParameter: Any, val state: Map<String, Any>)
data class ReplayData(
    val gameType: String,
    val playerCount: Int,
    val config: GameConfigs,
    val initialState: GameSituationState,
    val actions: List<ActionReplay>
)
