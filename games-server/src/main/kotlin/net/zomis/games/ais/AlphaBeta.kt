package net.zomis.games.ais

class Best<T>(val maximize: Boolean) {

    private var bestValue = if (maximize) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
    private var bestElements: MutableList<T> = mutableListOf()

    fun next(element: T, value: Double) {
        if (maximize) {
            if (value > bestValue) {
                bestValue = value
                bestElements = mutableListOf(element)
            } else if (value >= bestValue) {
                bestElements.add(element)
            }
        } else {
            if (value < bestValue) {
                bestValue = value
                bestElements = mutableListOf(element)
            } else if (value <= bestValue) {
                bestElements.add(element)
            }
        }
    }

    fun random(): T {
        return bestElements.random()
    }

    fun getBest(): List<T> {
        return bestElements.toList()
    }

    fun isBest(element: T): Boolean {
        return bestElements.contains(element)
    }

}

class AlphaBeta<S, A>(
    private val actions: (S) -> List<A>,
    val branching: (S, A) -> S,
    val terminalState: (S) -> Boolean,
    val heuristic: (S) -> Double
) {

    private suspend fun alphaBeta(state: S, depth: Int, alpha: Double, beta: Double, maximizingPlayer: Boolean): Pair<A?, Double> {
        var newAlpha = alpha
        var newBeta = beta
        if (depth == 0 || terminalState(state)) {
            return null to heuristic(state)
        }

        val availableActions = actions(state).toList()
        if (availableActions.isEmpty()) {
            throw IllegalStateException("No available actions but not terminal? $state")
        }
        if (maximizingPlayer) {
            val best = Best<A>(maximizingPlayer)
            var value = Double.NEGATIVE_INFINITY
            for (action in availableActions) {
                val child = branching(state, action)
                val childValue = alphaBeta(child, depth - 1, newAlpha, newBeta, !maximizingPlayer).second
                best.next(action, childValue)
                if (childValue > value) {
                    value = Math.max(value, childValue)
                }
                newAlpha = Math.max(newAlpha, value)
                if (newAlpha >= newBeta) {
                    break
                }
            }
            return best.random() to value
        } else {
            val best = Best<A>(maximizingPlayer)
            var value = Double.POSITIVE_INFINITY
            for (action in availableActions) {
                val child = branching(state, action)
                val childValue = alphaBeta(child, depth - 1, newAlpha, newBeta, !maximizingPlayer).second
                best.next(action, childValue)
                value = Math.min(value, childValue)
                newBeta = Math.min(newBeta, value)
                if (newAlpha >= newBeta) {
                    break
                }
            }
            return best.random() to value
        }
    }

    suspend fun score(node: S, depth: Int): Double {
        // This is scoring the next step, so currentPlayer is already opponent
        if (depth <= 1) {
            throw IllegalArgumentException("Depth ($depth) must be bigger than 1")
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