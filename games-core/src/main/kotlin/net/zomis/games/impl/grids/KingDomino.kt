package net.zomis.games.impl.grids

import net.zomis.games.api.Games
import net.zomis.games.cards.CardZone
import net.zomis.games.common.Direction4
import net.zomis.games.common.Point
import net.zomis.games.common.toSingleList

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
                Tile(TileType.valueOf(tile.toUpperCase().replace("*", "")), tile.count { it == '*' })
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
            val existing = grid.all().filter { it.value.type != null }.map { it.point }
            val check = existing + placement.placePoints()
            val uniqueX = check.map { it.x }.distinct()
            val uniqueY = check.map { it.y }.distinct()
            val diffX = uniqueX.maxOrNull()!! - uniqueX.minOrNull()!!
            val diffY = uniqueY.maxOrNull()!! - uniqueY.minOrNull()!!
            return diffX <= 4 && diffY <= 4
        }

        val biggestAreaSize: Int get() = this.connectedAreas()
            .filter { it.points.first().value.type != null }
            .map { it.points.size }.maxOrNull()!!
        val totalCrowns get() = grid.all().map { it.value.crowns }.sum()

        // For simplicity, as build grid is 5x5 but castle does not need to be in center, use 9x9
        val grid = Games.components.grid(9, 9) { _, _ -> Tile(null, 0) }

        fun connectedAreas() = grid.connected(Direction4.values().map(Direction4::delta)) { it.value.type?.name ?: "" }
        fun points(): Int {
            val areas = connectedAreas()
            return areas.sumBy { area ->
                val crowns = area.points.sumBy { it.value.crowns }
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
                    val centerX = it.grid.sizeX / 2
                    val centerY = it.grid.sizeY / 2
                    it.grid.set(centerX, centerY, Tile(TileType.CASTLE, 0))
                }

                val playerIndices = if (game.players.size == 2) game.players.indices + game.players.indices else game.players.indices
                game.playOrder = replayable.ints("playerOrder") { playerIndices.shuffled() }

                game.dominoesRemaining -= game.dominoesPerTurn
                game.dominoDeck.random(replayable, game.dominoesPerTurn, "dominoes") { it.toStateString() }.forEach {
                    it.moveTo(game.dominoChoices)
                }
                game.dominoChoices.cards.sortBy { it.value }
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
                        options { game.dominoChoices.cards.filter { it.owner == null } }
                        perform {
                            log { "${action.toStateString()} done by $player" }
                            action.parameter.owner = playerIndex

                            if (game.dominoChoices.cards.none { it.owner == null }) {
                                game.dominoesRemaining -= game.dominoesPerTurn
                                game.dominoDeck.random(replayable, game.dominoesPerTurn, "dominoes") { it.toStateString() }.forEach {
                                    it.moveTo(game.dominoNextChoices)
                                }
                                game.dominoNextChoices.cards.sortBy { it.value }
                                game.playOrder = game.dominoChoices.cards.map { it.owner!! }
                            }
                        }
                    }
                }
            }

            // Place dominoes, pick next tile
            loop {
                for (player in game.playOrder) {
                    game.currentPlayerIndex = player
//                    check(game.dominoChoices.cards.first().owner == player) {
//                        "Next domino ${game.dominoChoices.cards.first()} is not owned by correct player $player"
//                    }
                    val placementDone = step("place dominoes") {
                        println("Chosen: " + game.dominoChoices.cards + " NextChoices: " + game.dominoNextChoices.cards)
                        yieldAction(place) {
                            precondition { playerIndex == player }
                            choose {
                                optionsWithIds({
                                    if (game.dominoChoices.isEmpty()) return@optionsWithIds emptyList()
                                    val chosenDomino = game.dominoChoices.cards.first()
                                    chosenDomino.tiles.toList().distinctBy { it.type }.map { it.type!!.name to it }
                                }) { firstTile ->
                                    options({
                                        val grid = game.players[playerIndex].grid
                                        grid.all().filter { tile -> tile.value.type == null && Direction4.values().any {
                                            grid.getOrNull(tile.point + it.delta())?.connectableWith(firstTile) ?: false
                                        } }.map { it.point }
                                    }) { point ->
                                        options({ Direction4.values()
                                            .map { point + it.delta() }
                                            .filter {
                                                val grid = game.players[playerIndex].grid
                                                grid.isOnMap(it.x, it.y) && grid.get(it.x, it.y).type == null
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
                    println("Placement done: " + placementDone.action)
                    if (placementDone.action == null && !game.dominoChoices.isEmpty()) {
                        val discarded = game.dominoChoices.cards.removeFirst()
                        println("No moves possible, player $player has to discard tile $discarded")
                    }

                    println("BEFORE PICK NEXT TILE")
                    val pickNext = step("pick next tile") {
                        yieldAction(chooseDomino) {
                            precondition { playerIndex == player }
                            requires { action.parameter.owner == null }
                            options { game.dominoNextChoices.cards.filter { it.owner == null } }
                            perform {
                                log { "${action.toStateString()} done by $player" }
                                action.parameter.owner = playerIndex

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
                    }
                    println("AFTER PICK NEXT TILE")
                    if (pickNext.action == null) {
                        println("pickNext action null. No moves possible I guess")
                    }
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

                view("width") { game.players.first().grid.sizeX }
                view("height") { game.players.first().grid.sizeY }
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
                        mapOf(
                            "playerIndex" to player.playerIndex,
                            "points" to player.points(),
                            "biggestArea" to player.biggestAreaSize,
                            "crowns" to player.totalCrowns,
                            "grid" to player.grid.view { tileView(it) }
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

}