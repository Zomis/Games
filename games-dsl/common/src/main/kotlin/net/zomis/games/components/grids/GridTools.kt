package net.zomis.games.components.grids

import net.zomis.games.components.Point

fun <T> Grid<T>.mnkLines(includeDiagonals: Boolean): Sequence<GridLine<T>> {
    fun loopAdd(board: Grid<T>, xxStart: Int, yyStart: Int, dx: Int, dy: Int): List<T> {
        var xx = xxStart
        var yy = yyStart
        val items = mutableListOf<T>()

        var tile: T?
        do {
            tile = board.getOrNull(xx, yy)
            xx += dx
            yy += dy
            if (tile != null) {
                items.add(tile)
            }
        } while (tile != null)

        return items
    }
    val board = this
    return sequence {
        // columns
        for (xx in 0 until sizeX) {
            yield(GridLine(loopAdd(board, xx, 0, 0, 1)))
        }

        // Scan rows for a winner
        for (yy in 0 until sizeY) {
            yield(GridLine(loopAdd(board, 0, yy, 1, 0)))
        }

        if (!includeDiagonals) {
            return@sequence
        }

        // Scan diagonals for a winner: Bottom-right
        for (yy in 0 until sizeY) {
            yield(GridLine(loopAdd(board, 0, yy, 1, 1)))
        }
        for (xx in 1 until sizeX) {
            yield(GridLine(loopAdd(board, xx, 0, 1, 1)))
        }

        // Scan diagonals for a winner: Bottom-left
        for (xx in 0 until sizeX) {
            yield(GridLine(loopAdd(board, xx, 0, -1, 1)))
        }
        for (yy in 1 until sizeY) {
            yield(GridLine(loopAdd(board, board.sizeX - 1, yy, -1, 1)))
        }
    }
}

class ConnectedArea<T, R>(val group: R, val points: List<GridPoint<T>>)
fun <T, R: Any> Grid<T>.connected(neighbors: List<Point>, function: (GridPoint<T>) -> R): List<ConnectedArea<T, R>> {
    return connectedAreas(neighbors = { origin ->
        neighbors.map { this.point(origin.point + it) }
    }, groupFunction = function)
}
fun <T, R: Any> Grid<T>.connectedAreas(
    neighbors: (GridPoint<T>) -> List<GridPoint<T>>,
    groupFunction: (GridPoint<T>) -> R,
    originFilter: (GridPoint<T>) -> Boolean = { true },
): List<ConnectedArea<T, R>> {
    val matched = mutableSetOf<Point>()
    val waiting = mutableSetOf<Point>()
    val inGroup = mutableSetOf<GridPoint<T>>()
    val groups = mutableListOf<ConnectedArea<T, R>>()

    for (pos in all()) {
        if (matched.contains(pos.point)) {
            continue
        }
        if (!originFilter.invoke(pos)) {
            continue
        }
        check(waiting.isEmpty())
        waiting.add(pos.point)
        val group = groupFunction.invoke(pos)

        while (waiting.isNotEmpty()) {
            // Pick a spot from awaiting, mark it as checked and add it to group
            val current = waiting.first()
            val currentPoint = point(current)
            waiting.remove(current)
            matched.add(current)
            inGroup.add(currentPoint)

            for (neighbor in neighbors.invoke(currentPoint)) {
                // check neighbors of spot and compare grouping
                val target = this.point(current.x + neighbor.x, current.y + neighbor.y).rangeCheck(this) ?: continue
                if (matched.contains(target.point) || waiting.contains(target.point)) continue
                if (groupFunction.invoke(target) == group) {
                    // if match, add to awaiting for later processing when it will be marked as checked and added to group
                    waiting.add(target.point)
                }
            }
        }
        // When awaiting is empty, finish group and move on
        val area = ConnectedArea(group, inGroup.toList())
        groups.add(area)
        inGroup.clear()
    }
    return groups.toList()

}
