package net.zomis.games.impl.grids

import net.zomis.games.components.Direction4
import net.zomis.games.components.Point
import net.zomis.games.components.grids.*
import net.zomis.games.search.BFS
import net.zomis.games.search.SearchStrategy

private val Direction4.char: Char get() {
    return when (this) {
        Direction4.LEFT -> '<'
        Direction4.RIGHT -> '>'
        Direction4.UP -> '^'
        Direction4.DOWN -> 'v'
    }
}

object Ricochet {
    class Tile(val walls: Set<Direction4>)
    data class PiecePositions(val relevant: Set<Point>, val other: Set<Point>)
    private fun isCenter(point: Point): Boolean {
        val x = point.x == 7 || point.x == 8
        val y = point.y == 7 || point.y == 8
        return x && y
    }
    class GameMap {
        inner class Pieces(val colors: Map<Color, Point>) {
            fun positions(targetColor: Color?): PiecePositions {
                val relevant = if (targetColor != null) setOf(colors.getValue(targetColor)) else colors.values.toSet()
                val other = if (targetColor != null) colors.values.toSet().minus(colors.getValue(targetColor)) else emptySet()
                return PiecePositions(relevant = relevant, other = other)
            }

            fun possibleMoves(): List<Move> {
                return allMoves.filter { canMove(it.color, it.direction) }
            }

            fun canMove(color: Color, direction: Direction4): Boolean {
                val pos = colors.getValue(color)
                val next = wallHit(color, direction)
                return pos != next
            }

            fun wallHit(color: Color, direction: Direction4): Point {
                var pos = this.colors.getValue(color)
                var next = pos + direction.delta()
                val otherColors = colors.values.toSet()
                while (
                    bigMap.isOnMap(next.x, next.y) &&
                    !otherColors.contains(next) &&
                    !isCenter(next) &&
                    !bigMap.get(pos.x, pos.y).walls.contains(direction) &&
                    !bigMap.get(next.x, next.y).walls.contains(direction.opposite())
                ) {
                    pos = next
                    next = pos + direction.delta()
                }
                return pos
            }

            fun afterMove(move: Move): Pieces {
                val next = this.colors.toMutableMap()
                next[move.color] = wallHit(move.color, move.direction)
                require(next != this.colors)
                return Pieces(next)
            }

            fun colorMatch(targetColor: Color?, targetPoint: Point): Boolean {
                return if (targetColor != null) this.colors.getValue(targetColor) == targetPoint
                else this.colors.values.contains(targetPoint)
            }

        }
        // Board offsets and rotations
        // Pieces placements (each color + black)

//        private val boardHoles = mutableMapOf<Board, Point>() // Only store the point for where the hole is Point(7..8, 7..8)
        private val pieces: MutableMap<Color, Point> = mutableMapOf()
        private val bigMap: Grid<Tile> = GridImpl(16, 16) { _, _ -> Tile(emptySet()) }
        private val targets = mutableMapOf<Target, Point>()

        fun place(target: Target, point: Point) {
            val board = boards.first { it.tiles.values.any { o -> o.matches(target) } }
            val hole = Point(
                x = if (point.x <= 7) 7 else 0,
                y = if (point.y <= 7) 7 else 0,
            )
            val offset = Point(if (point.x <= 7) 0 else 8, if (point.y <= 7) 0 else 8)

            val transformation = board.holeTransformation(hole)
            val transformed = board.grid().transformed(transformation)

            (0..7).forEach { y ->
                (0..7).forEach { x ->
                    bigMap.set(x + offset.x, y + offset.y, Tile(transformed.get(x, y).walls.map { it.transform(transformation) }.toSet()))
                }
            }
            board.tiles.forEach {
                val pos = Position(it.key.x, it.key.y, 8, 8)
                val newPos = pos.transform(transformation).point() + offset
                targets[it.value] = newPos
                if (it.value.matches(target)) {
                    check(newPos == point)
                }
                if (it.value.symbol == Symbol.Wildcard) {
                    // TODO: Place wall on opposite side. Currently handled by extra check in move logic.
                }
            }
        }

        fun piece(color: Color, point: Point) {
            pieces[color] = point
        }

        fun findPath(target: Target): Sequence<List<Move>> {
            val targetColor = target.color.takeIf { it != Color.Wildcard }
            val targetPoint = targets.entries.single { it.key.matches(target) }.value

            return BFS(object : SearchStrategy<Pieces, PiecePositions, Move> {
                override fun possibleMoves(state: Pieces): Sequence<Move> = state.possibleMoves().asSequence()
                override fun nextState(state: Pieces, step: Move): Pieces = state.afterMove(step)
                override fun isGoal(state: Pieces): Boolean = state.colorMatch(targetColor, targetPoint)
                override fun uniqueState(state: Pieces): PiecePositions = state.positions(targetColor)
            }).findAllShortest(Pieces(pieces.toMap()))
        }

        private val allMoves = Color.entries.flatMap { it.moves() }
        override fun toString(): String {
            val targetsInverse = targets.entries.associate { it.value to it.key }
            return (0 until 16).joinToString("\n") { y ->
                (0 until 16).joinToString("", postfix = "|") inner@{ x ->
                    if (isCenter(Point(x, y))) return@inner "|xxxxxx"
                    val tile = bigMap.get(x, y)
                    val target = targetsInverse[Point(x, y)]
                    val s = StringBuilder("|")
                    Direction4.entries.forEach {
                        if (tile.walls.contains(it)) s.append(it.char)
                        else s.append(' ')
                    }
                    if (target != null) {
                        s.append(target.color.name.first())
                        s.append(target.symbol.name.first())
                    } else {
                        s.append("  ")
                    }
                    s.toString()
                }
            } + " @ " + pieces
        }

        fun start(): Pieces = Pieces(pieces.toMap())
        fun shuffleBoards() {
            val unplaced = boards.toMutableList()
            val quadrants = (0 until 4).toMutableList()
            val holes = arrayOf(Point(7, 7), Point(0, 7), Point(7, 0), Point(0, 0))
            repeat(4) {
                val next = unplaced.removeLast()
                val quadrant = quadrants.random()
                quadrants.remove(quadrant)

                val (point, target) = next.tiles.entries.first()
                val transformation = next.holeTransformation(holes[quadrant])
                val position = Position(point.x + next.offset.x, point.y + next.offset.y, 16, 16).transform(transformation)
                println("$quadrant $transformation $target $position")
                place(target, position.point())
            }
        }

        fun shufflePieces() {
            val allPoints = bigMap.points().toList()
            for (color in Color.entries) {
                var point = allPoints.random()
                while (targets.values.contains(point) || pieces.values.contains(point) || isCenter(point)) {
                    point = allPoints.random()
                }
                piece(color, point)
            }
        }
    }
    data class Move(val color: Color, val direction: Direction4) {
        operator fun plus(other: Move) = listOf(this, other)
    }

    class Board(
        val hole: Point,
        val tiles: Map<Point, Target>,
        val walls: List<Pair<Point, Point>>
    ) {
        val offset: Point get() = when (hole) {
            Point(7, 7) -> Point(0, 0)
            Point(0, 7) -> Point(8, 0)
            Point(7, 0) -> Point(0, 8)
            Point(0, 0) -> Point(8, 8)
            else -> throw IllegalStateException(hole.toString())
        }

        fun grid(): Grid<Tile> {
            return GridImpl(8, 8) { x, y ->
                val p = Point(x, y)
                if (tiles.containsKey(p)) return@GridImpl Tile(tiles.getValue(p).walls)
                var tileWalls = emptySet<Direction4>()

                walls.filter { it.first == p || it.second == p }.forEach {
                    tileWalls = tileWalls + wallDirection(it, p)
                }

                tiles.entries.filter { it.key.manhattanDistance(p) == 1 }.forEach {
                    val wallFromTarget = it.value.walls.find { dir -> it.key + dir.delta() == p }
                    if (wallFromTarget != null) tileWalls = tileWalls + wallFromTarget.opposite()
                }

                Tile(tileWalls)
            }
        }

        private fun wallDirection(pair: Pair<Point, Point>, tile: Point): Direction4 {
            val other = if (tile == pair.first) pair.second else pair.first
            return Direction4.entries.first { tile + it.delta() == other }
        }

        fun holeTransformation(hole: Point): Transformation {
            val original = Position(this.hole.x, this.hole.y, 8, 8)
            var position = original
            var result = Transformation.NO_CHANGE
            var i = 0
            while (position.point() != hole) {
                result = result.apply(Transformation.ROTATE_90_CLOCKWISE)
                position = original.transform(result)
                if (i++ >= 10) throw IllegalArgumentException("No solution to rotation")
            }
            return result
        }
    }
    val boards = arrayOf(
        Board(
            hole = Point(7, 7),
            tiles = mapOf(
                Point(5, 1) to Color.Blue.halfMoon.wall(Direction4.LEFT, Direction4.DOWN),
                Point(7, 2) to Color.Wildcard.wildcard.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(3, 4) to Color.Red.starSign.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(6, 5) to Color.Green.planet.wall(Direction4.UP, Direction4.LEFT),
                Point(1, 6) to Color.Yellow.cog.wall(Direction4.UP, Direction4.RIGHT),
            ),
            walls = listOf(
                Point(2, 0) to Point(3, 0),
                Point(0, 3) to Point(0, 4),
            )
        ),
        Board(
            hole = Point(0, 7),
            tiles = mapOf(
                Point(3, 2) to Color.Red.planet.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(2, 4) to Color.Green.starSign.wall(Direction4.LEFT, Direction4.DOWN),
                Point(5, 3) to Color.Yellow.halfMoon.wall(Direction4.UP, Direction4.RIGHT),
                Point(4, 5) to Color.Blue.cog.wall(Direction4.UP, Direction4.LEFT),
            ),
            walls = listOf(
                Point(3, 0) to Point(4, 0),
                Point(7, 5) to Point(7, 6),
            )
        ),
        Board(
            hole = Point(7, 0),
            tiles = mapOf(
                Point(3, 1) to Color.Yellow.starSign.wall(Direction4.UP, Direction4.RIGHT),
                Point(6, 3) to Color.Blue.planet.wall(Direction4.UP, Direction4.LEFT),
                Point(1, 4) to Color.Green.cog.wall(Direction4.LEFT, Direction4.DOWN),
                Point(4, 6) to Color.Red.halfMoon.wall(Direction4.RIGHT, Direction4.DOWN),
            ),
            walls = listOf(
                Point(0, 5) to Point(0, 6),
                Point(6, 7) to Point(7, 7),
            )
        ),
        Board(
            hole = Point(0, 0),
            tiles = mapOf(
                Point(4, 1) to Color.Blue.starSign.wall(Direction4.UP, Direction4.LEFT),
                Point(2, 2) to Color.Yellow.planet.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(6, 4) to Color.Red.cog.wall(Direction4.UP, Direction4.RIGHT),
                Point(3, 6) to Color.Green.halfMoon.wall(Direction4.LEFT, Direction4.DOWN),
            ),
            walls = listOf(
                Point(7, 1) to Point(7, 2),
                Point(5, 7) to Point(6, 7),
            )
        ),

        Board(
            hole = Point(0, 7),
            tiles = mapOf(
                Point(2, 2) to Color.Red.starSign.wall(Direction4.LEFT, Direction4.DOWN),
                Point(4, 1) to Color.Yellow.cog.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(2, 7) to Color.Wildcard.wildcard.wall(Direction4.DOWN, Direction4.LEFT),
                Point(3, 5) to Color.Green.planet.wall(Direction4.UP, Direction4.RIGHT),
                Point(6, 6) to Color.Blue.halfMoon.wall(Direction4.UP, Direction4.LEFT),
            ),
            walls = listOf(
                Point(0, 0) to Point(1, 0),
                Point(7, 3) to Point(7, 4),
            )
        ),
        Board(
            hole = Point(0, 0),
            tiles = mapOf(
                Point(4, 1) to Color.Blue.planet.wall(Direction4.UP, Direction4.RIGHT),
                Point(1, 4) to Color.Yellow.starSign.wall(Direction4.UP, Direction4.LEFT),
                Point(6, 5) to Color.Green.cog.wall(Direction4.LEFT, Direction4.DOWN),
                Point(3, 6) to Color.Red.halfMoon.wall(Direction4.RIGHT, Direction4.DOWN),
            ),
            walls = listOf(
                Point(5, 7) to Point(6, 7),
                Point(7, 1) to Point(7, 2),
            )
        ),

        Board(
            hole = Point(7, 0),
            tiles = mapOf(
                Point(4, 1) to Color.Yellow.halfMoon.wall(Direction4.LEFT, Direction4.DOWN),
                Point(1, 2) to Color.Green.starSign.wall(Direction4.UP, Direction4.RIGHT),
                Point(6, 5) to Color.Blue.cog.wall(Direction4.UP, Direction4.LEFT),
                Point(2, 6) to Color.Red.planet.wall(Direction4.RIGHT, Direction4.DOWN),
            ),
            walls = listOf(
                Point(0, 3) to Point(0, 4),
                Point(3, 7) to Point(4, 7),
            )
        ),
        Board(
            hole = Point(7, 7),
            tiles = mapOf(
                Point(5, 2) to Color.Blue.starSign.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(2, 4) to Color.Green.halfMoon.wall(Direction4.UP, Direction4.RIGHT),
                Point(7, 5) to Color.Red.cog.wall(Direction4.LEFT, Direction4.DOWN),
                Point(1, 6) to Color.Yellow.planet.wall(Direction4.UP, Direction4.LEFT),
            ),
            walls = listOf(
                Point(3, 0) to Point(4, 0),
                Point(0, 4) to Point(0, 5),
            )
        ),

        Board(
            hole = Point(0, 7),
            tiles = mapOf(
                Point(4, 1) to Color.Green.planet.wall(Direction4.LEFT, Direction4.UP),
                Point(6, 2) to Color.Red.starSign.wall(Direction4.LEFT, Direction4.DOWN),
                Point(0, 3) to Color.Wildcard.wildcard.wall(Direction4.LEFT, Direction4.DOWN),
                Point(1, 6) to Color.Blue.halfMoon.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(3, 7) to Color.Yellow.cog.wall(Direction4.UP, Direction4.RIGHT),
            ),
            walls = listOf(
                Point(2, 0) to Point(3, 0),
                Point(7, 4) to Point(7, 5),
            )
        ),
        Board(
            hole = Point(0, 0),
            tiles = mapOf(
                Point(4, 1) to Color.Red.halfMoon.wall(Direction4.UP, Direction4.RIGHT),
                Point(1, 2) to Color.Blue.planet.wall(Direction4.LEFT, Direction4.DOWN),
                Point(6, 5) to Color.Green.cog.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(1, 6) to Color.Yellow.starSign.wall(Direction4.UP, Direction4.LEFT),
            ),
            walls = listOf(
                Point(2, 7) to Point(3, 7),
                Point(7, 1) to Point(7, 2),
            )
        ),
        Board(
            hole = Point(7, 0),
            tiles = mapOf(
                Point(3, 1) to Color.Blue.cog.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(5, 3) to Color.Red.planet.wall(Direction4.LEFT, Direction4.UP),
                Point(1, 5) to Color.Yellow.halfMoon.wall(Direction4.LEFT, Direction4.DOWN),
                Point(6, 6) to Color.Green.starSign.wall(Direction4.UP, Direction4.RIGHT),
            ),
            walls = listOf(
                Point(0, 2) to Point(0, 3),
                Point(4, 7) to Point(5, 7),
            )
        ),
        Board(
            hole = Point(7, 7),
            tiles = mapOf(
                Point(1, 1) to Color.Red.cog.wall(Direction4.LEFT, Direction4.DOWN),
                Point(6, 2) to Color.Green.halfMoon.wall(Direction4.UP, Direction4.RIGHT),
                Point(2, 4) to Color.Blue.starSign.wall(Direction4.RIGHT, Direction4.DOWN),
                Point(7, 5) to Color.Yellow.planet.wall(Direction4.LEFT, Direction4.UP),
            ),
            walls = listOf(
                Point(3, 0) to Point(4, 0),
                Point(0, 5) to Point(0, 6),
            )
        ),


    )

    fun createMap(): GameMap = GameMap()
    fun targets(): List<Target> = Color.entries.flatMap { it.targets() }

    data class Target(val color: Color, val symbol: Symbol, val walls: Set<Direction4>) {
        init {
            require((color == Color.Wildcard) == (symbol == Symbol.Wildcard))
        }
        fun matches(other: Target): Boolean = other.color == color && other.symbol == symbol
        fun wall(vararg walls: Direction4): Target = Target(color, symbol, walls.toSet())
        fun isWildcard(): Boolean {
            return this.color == Color.Wildcard
        }
    }
    enum class Symbol {
        Planet, CogWheel, HalfMoon, StarSign, Wildcard;
    }
    enum class Color {
        Red,
        Green,
        Blue,
        Yellow,
        Wildcard,
        ;

        fun moves(): Iterable<Move> = listOf(up, right, down, left)
        fun targets(): List<Target> = if (this == Wildcard) listOf(wildcard) else listOf(planet, cog, halfMoon, starSign)

        val planet get() = Target(this, Symbol.Planet, emptySet())
        val cog get() = Target(this, Symbol.CogWheel, emptySet())
        val halfMoon get() = Target(this, Symbol.HalfMoon, emptySet())
        val starSign get() = Target(this, Symbol.StarSign, emptySet())
        val wildcard get() = Target(this, Symbol.Wildcard, emptySet())

        val up get() = Move(this, Direction4.UP)
        val right get() = Move(this, Direction4.RIGHT)
        val down get() = Move(this, Direction4.DOWN)
        val left get() = Move(this, Direction4.LEFT)
    }


    // wallLeft, wallRight, maybe like Pentacolor

}

