package net.zomis.games.ais

import net.zomis.BestSuspending

class AlphaBeta<S, A>(
    private val actions: (S) -> List<A>,
    val branching: suspend (S, A) -> S,
    val terminalState: (S) -> Boolean,
    val heuristic: (S) -> Double,
    private val depthRemainingBonus: Double = 0.0
) {

    private suspend fun alphaBeta(state: S, depth: Int, alpha: Double, beta: Double, maximizingPlayer: Boolean): Pair<A?, Double> {
        var newAlpha = alpha
        var newBeta = beta
        if (depth == 0 || terminalState(state)) {
            return null to heuristic(state) + depth * depthRemainingBonus
        }

        val availableActions = actions(state).toList()
        if (availableActions.isEmpty()) {
            throw IllegalStateException("No available actions but not terminal? $state")
        }
        if (maximizingPlayer) {
            val best = BestSuspending<A> {action ->
                val child = branching(state, action)
                alphaBeta(child, depth - 1, newAlpha, newBeta, !maximizingPlayer).second
            }
            for (action in availableActions) {
                best.next(action)
                newAlpha = maxOf(newAlpha, best.getBestValue())
                if (newAlpha >= newBeta) {
                    break
                }
            }
            return best.getBest().random() to best.getBestValue()
        } else {
            val best = BestSuspending<A> {action ->
                val child = branching(state, action)
                -alphaBeta(child, depth - 1, newAlpha, newBeta, !maximizingPlayer).second
            }
            for (action in availableActions) {
                best.next(action)
                newBeta = minOf(newBeta, -best.getBestValue())
                if (newAlpha >= newBeta) {
                    break
                }
            }
            return best.getBest().random() to -best.getBestValue()
        }
    }

    suspend fun score(node: S, depth: Int): Double {
        // This is scoring the next step, so currentPlayer is already opponent
        if (depth < 0) {
            throw IllegalArgumentException("Depth ($depth) may not be negative.")
        }
        val result = alphaBeta(node, depth, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, false)
        return result.second
    }

/*
function alphabeta(node, depth, α, β, maximizingPlayer) is
    if depth = 0 or node is a terminal node then
        return the heuristic value of node
    if maximizingPlayer then
        value := −∞
        for each child of node do
            value := max(value, alphabeta(child, depth − 1, α, β, FALSE))
            α := max(α, value)
            if α ≥ β then
                break (* β cut-off *)
        return value
    else
        value := +∞
        for each child of node do
            value := min(value, alphabeta(child, depth − 1, α, β, TRUE))
            β := min(β, value)
            if α ≥ β then
                break (* α cut-off *)
        return value
*/
}