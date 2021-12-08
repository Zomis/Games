package net.zomis.games.impl.grids

import net.zomis.games.api.Games
import net.zomis.games.cards.CardZone
import net.zomis.games.common.Direction4
import net.zomis.games.common.Point
import net.zomis.games.common.toSingleList
import net.zomis.games.components.connected
import net.zomis.games.dsl.ReplayableScope

object KingDomino {

    val bricks = """
        sand-sand
        sand-sand
        forest-forest
        forest-forest
        forest-forest
        forest-forest
        water-water
        water-water
        
        water-water
        field-field
        field-field
        dirt-dirt
        sand-forest
        sand-water
        sand-field
        sand-dirt
        
        forest-water
        forest-field
        *sand-forest
        *sand-water
        *sand-field
        *sand-dirt
        *sand-mine
        *forest-sand
        
        *forest-sand
        *forest-sand
        *forest-sand
        *forest-water
        *forest-field
        *water-sand
        *water-sand
        *water-forest
        
        *water-forest
        *water-forest
        *water-forest
        sand-*field
        water-*field
        sand-*dirt
        field-*dirt
        *mine-sand
        
        sand-**field
        water-**field
        sand-**dirt
        field-**dirt
        **mine-sand
        dirt-**mine
        dirt-**mine
        sand-***mine
        """.trimIndent().split("\n").filter { it.isNotEmpty() }.withIndex().map { domino ->
            domino.value.split("-").map { tile ->
                Tile(TileType.valueOf(tile.uppercase().replace("*", "")), tile.count { it == '*' })
            }.let { DominoTile(domino.index + 1, it[0] to it[1]) }
    }

    enum class TileType {
        CASTLE,
        SAND,
        FOREST,
        WATER,
        FIELD,
        DIRT,
        MINE
    }
    class Tile(var type: TileType?, var crowns: Int) {
        fun toStateString(): String = "$crowns $type"
        override fun toString() = toStateString()
        fun connectableWith(tile: Tile): Boolean
            = type == TileType.CASTLE || tile.type == type
    }
    class Player(val playerIndex: Int) {
        fun placeableConnection(domino: DominoTile, placement: DominoPlacement): Boolean {
            val placeTypes = domino.tiles.toList().map { it.type }.distinct().filterNotNull()
            val checkPoints = placement.placePoints().flatMap { p -> Direction4.values().map { it.delta() + p } }
            return checkPoints.mapNotNull { grid.getOrNull(it) }
                .any { it.type == TileType.CASTLE || placeTypes.contains(it.type) }
        }

        fun placeableBox(placement: DominoPlacement): Boolean {
            val border = placement.placePoints().fold(grid.border()) { rect, point -> rect.include(point.x, point.y) }
            return border.width <= 5 && border.height <= 5
        }

        val biggestAreaSize: Int get() = this.connectedAreas()
            .filter { it.points.first().value.type != null }
            .maxOf { it.points.size }
        val totalCrowns get() = grid.all().sumOf { it.value.crowns }

        val grid = Games.components.expandableGrid<Tile>()

        fun connectedAreas() = grid.connected(Direction4.values().map(Direction4::delta)) { it.valueOrNull(grid)?.type?.name ?: "" }
        fun points(): Int {
            val areas = connectedAreas()
            return areas.sumOf { area ->
                val crowns = area.points.sumOf { it.valueOrNull(grid)?.crowns ?: 0 }
                crowns * area.points.size
            }
        }

    }
    class Model(playerCount: Int) {
        val dominoesPerTurn = if (playerCount == 3) playerCount else 4
        val dominoDeck = CardZone(bricks.toMutableList())
        val dominoChoices = CardZone<DominoTile>()
        val dominoNextChoices = CardZone<DominoTile>()
        var dominoesRemaining = 12 * playerCount

        var playOrder: List<Int> = emptyList()
        val players = (0 until playerCount).map { Player(it) }

        var currentPlayerIndex = 0
    }
    class DominoTile(val value: Int, val tiles: Pair<Tile, Tile>, var owner: Int? = null) {
        override fun toString(): String = toStateString() + " (owned by $owner)"
        fun toStateString(): String = "$value: ${tiles.first.toStateString()}/${tiles.second.toStateString()}"
    }
    class DominoPlacement(val firstTile: Tile, val point: Point, val point2: Point) {
        fun secondTile(domino: DominoTile): Tile = domino.tiles.toList().minus(firstTile).single()

        fun toStateString(): String = "${firstTile.toStateString()} ${point.toStateString()} ${point2.toStateString()}"
        override fun toString(): String = toStateString()
        fun placePoints() = point.toSingleList() + point2
    }

    val factory = Games.api.gameCreator(Model::class)
    val chooseDomino = factory.action("choose", DominoTile::class).serializer { it.toStateString() }
    val place = factory.action("place", DominoPlacement::class).serializer { it.toStateString() }
    val game = factory.game("King Domino") {
        setup {
            players(2..4)
            init { Model(playerCount) }
            onStart {
                game.players.forEach {
                    it.grid.set(0, 0, Tile(TileType.CASTLE, 0))
                }

                val playerIndices = if (game.players.size == 2) game.players.indices + game.players.indices else game.players.indices
                game.playOrder = replayable.ints("playerOrder") { playerIndices.shuffled() }

                game.dominoesRemaining -= game.dominoesPerTurn
                game.dominoDeck.random(replayable, game.dominoesPerTurn, "dominoes") { it.toStateString() }.forEach {
                    it.moveTo(game.dominoNextChoices)
                }
                game.dominoNextChoices.cards.sortBy { it.value }
            }
        }
        gameFlow {
            // Place kings on dominoes
            for (player in game.playOrder) {
                game.currentPlayerIndex = player
                step("kings on dominoes") {
                    yieldAction(chooseDomino) {
                        precondition { playerIndex == player }
                        requires { action.parameter.owner == null }
                        options { game.dominoNextChoices.cards.filter { it.owner == null } }
                        perform {
                            log { "${action.toStateString()} done by $player" }
                            action.parameter.owner = playerIndex
                            newDominoesCheck(game, replayable)
                        }
                    }
                }
            }

            // Place dominoes, pick next tile
            loop {
                for (player in game.playOrder) {
                    game.currentPlayerIndex = player

                    check(game.dominoChoices.cards.first().owner == player) {
                        "Next domino ${game.dominoChoices.cards.first()} is not owned by correct player $player"
                    }
                    val placementDone = step("place dominoes") {
                        yieldAction(place) {
                            precondition { playerIndex == player }
                            choose {
                                optionsWithIds({
                                    if (game.dominoChoices.isEmpty()) return@optionsWithIds emptyList()
                                    val chosenDomino = game.dominoChoices.cards.first()
                                    chosenDomino.tiles.toList().distinctBy { it.type }.map { it.type!!.name to it }
                                }) { firstTile ->
                                    options({
                                        val grid = game.players[playerIndex].grid.cropped(1)
                                        grid.all().filter { tile -> tile.valueOrNull(grid)?.type == null && Direction4.values().any {
                                            grid.getOrNull(tile.point + it.delta())?.connectableWith(firstTile) ?: false
                                        } }.map { it.point }
                                    }) { point ->
                                        options({ Direction4.values()
                                            .map { point + it.delta() }
                                            .filter {
                                                val grid = game.players[playerIndex].grid.cropped(2)
                                                grid.isOnMap(it.x, it.y) && grid.getOrNull(it.x, it.y)?.type == null
                                            }
                                        }) { point2 ->
                                            parameter(DominoPlacement(firstTile, point, point2))
                                        }
                                    }
                                }
                            }
                            requires {
                                val domino = game.dominoChoices.cards.first()
                                val kingPlayer = game.players[playerIndex]
                                kingPlayer.placeableConnection(domino, action.parameter)
                                    && kingPlayer.placeableBox(action.parameter)
                            }
                            perform {
                                log { "${action.toStateString()} done by $player" }
                                val placement = action.parameter
                                val point = placement.point
                                val secondPoint = placement.point2

                                val kingPlayer = game.players[playerIndex]
                                val domino = game.dominoChoices.cards.first()
                                game.dominoChoices.card(domino).remove()

                                kingPlayer.grid.set(point.x, point.y, placement.firstTile)
                                kingPlayer.grid.set(secondPoint.x, secondPoint.y, placement.secondTile(domino))
                            }
                        }
                    }
                    check(placementDone.action?.actionType == place.name || placementDone.action == null)
                    if (placementDone.action == null && !game.dominoChoices.isEmpty()) {
                        game.dominoChoices.cards.removeFirst()
                        log { "${player(player)} had to discard a tile" }
                    }

                    val pickNext = step("pick next tile") {
                        yieldAction(chooseDomino) {
                            precondition { playerIndex == player }
                            requires { action.parameter.owner == null }
                            options { game.dominoNextChoices.cards.filter { it.owner == null } }
                            perform {
                                log { "${action.toStateString()} done by $player" }
                                action.parameter.owner = playerIndex
                                newDominoesCheck(game, replayable)
                            }
                        }
                    }
                    check(pickNext.action?.actionType != place.name) { "Last action was ${pickNext.action}" }
                }

                step("end check") {
                    if (game.dominoChoices.isEmpty()) {
                        eliminations.eliminateBy(game.players.mapIndexed { index, splendorPlayer -> index to splendorPlayer },
                            // points, most extended property, most crowns
                            compareBy(
                                { it.points() },
                                { player -> player.connectedAreas().map { it.points.size }.maxOrNull()!! },
                                { player -> player.grid.all().map { it.value.crowns }.sum() }
                            )
                        )
                    }
                }
            }
        }
        gameFlowRules {
            beforeReturnRule("view") {
                fun tileView(tile: Tile): Map<String, Any?> = mapOf("tile" to tile.type?.name, "crowns" to tile.crowns)
                fun dominoView(domino: DominoTile): Map<String, Any?> {
                    return mapOf(
                        "owner" to domino.owner, "value" to domino.value,
                        "first" to tileView(domino.tiles.first),
                        "second" to tileView(domino.tiles.second)
                    )
                }

                view("viewer") { viewer ?: 0 }
                view("currentPlayer") { game.currentPlayerIndex }
                view("actions") {
                    mapOf(
                        "chosen" to actionsChosen().chosen(),
                        "dominoOptions" to action(chooseDomino).options().associate { it.value to chooseDomino.serialize(it) },
                        "place" to mapOf(
                            "tile" to action(place).nextSteps(Tile::class).map { it.type?.name },
                            "point" to action(place).nextSteps(Point::class).associateBy { it.toStateString() }
                        )
                    )
                }
                view("players") {
                    game.players.map { player ->
                        val extraRadius = if (player.playerIndex == viewer) 2 else 0
                        mapOf(
                            "playerIndex" to player.playerIndex,
                            "points" to player.points(),
                            "biggestArea" to player.biggestAreaSize,
                            "crowns" to player.totalCrowns,
                            "grid" to player.grid.cropped(extraRadius).view { tileView(it) }
                        )
                    }
                }
                view("playOrder") { game.playOrder }
                view("dominoChoices") {
                    game.dominoChoices.cards.map(::dominoView)
                }
                view("dominoNextChoices") { game.dominoNextChoices.cards.map(::dominoView) }
            }
        }
    }

    private fun newDominoesCheck(game: Model, replayable: ReplayableScope) {
        if (game.dominoNextChoices.cards.all { it.owner != null }) {
            check(game.dominoChoices.isEmpty())
            game.dominoNextChoices.moveAllTo(game.dominoChoices)

            if (game.dominoesRemaining >= game.dominoesPerTurn) {
                game.dominoesRemaining -= game.dominoesPerTurn
                game.dominoDeck.random(replayable, game.dominoesPerTurn, "dominoes") { it.toStateString() }.forEach {
                    it.moveTo(game.dominoNextChoices)
                }
            }
            game.dominoNextChoices.cards.sortBy { it.value }
            game.playOrder = game.dominoChoices.cards.map { it.owner!! }
        }
    }

}