package net.zomis.games.search

interface SearchStrategy<State, UniqueState, Step> {
    fun possibleMoves(state: State): Sequence<Step>
    fun nextState(state: State, step: Step): State
    fun isGoal(state: State): Boolean
    fun uniqueState(state: State): UniqueState
    fun onVisit(state: State, previousMoves: List<Step>, lastMove: Step) {}
}

/**
 * Breadth-First-Search
 */
class BFS<State, UniqueState, Step>(
    private val searchStrategy: SearchStrategy<State, UniqueState, Step>
) {
    sealed interface SearchStep<Step> {
        data class Path<Step>(val path: List<Step>) : SearchStep<Step>
        data class Depth<Step>(val depth: Int) : SearchStep<Step>
    }

    fun findFirst(from: State): List<Step> = search(from).filterIsInstance<SearchStep.Path<Step>>().first().path
    fun findAllShortest(from: State): Sequence<List<Step>> {
        var length: Int? = null
        return search(from).onEach {
            if (it is SearchStep.Path && length == null) length = it.path.size
        }.takeWhile {
            length == null || it is SearchStep.Path
        }.filterIsInstance<SearchStep.Path<Step>>().map { it.path }
    }
    fun findAll(from: State): Sequence<List<Step>> = search(from)
        .filterIsInstance<SearchStep.Path<Step>>()
        .map { it.path }

    fun search(from: State): Sequence<SearchStep<Step>> = sequence {
        if (searchStrategy.isGoal(from)) {
            yield(SearchStep.Path(emptyList()))
            return@sequence
        }

        val open = mutableMapOf<State, List<Step>>()
        val seen = mutableSetOf<UniqueState>()

        open[from] = emptyList()
        val openNext = mutableMapOf<State, List<Step>>()

        // Breadth-first-search
        var step = 0
        while (open.isNotEmpty()) {
            step++
            yield(SearchStep.Depth(step))
            openNext.clear()
            for (e in open) {
                val pieces = e.key
                val moves = searchStrategy.possibleMoves(pieces)
                for (move in moves) {
                    val next = searchStrategy.nextState(pieces, move)
                    searchStrategy.onVisit(next, e.value, move)
                    if (searchStrategy.isGoal(next)) {
                        yield(SearchStep.Path(e.value + move))
                    }
                    if (seen.add(searchStrategy.uniqueState(next))) {
                        openNext[next] = e.value + move
                    }
                }
            }
            open.clear()
            open.putAll(openNext)
        }
    }

}