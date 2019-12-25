package net.zomis.games.mahjong

enum class MahjongSuit(private val count: Int) {
    DRAGON(3), WIND(4), FLOWER(8),
    SIGN(9), CIRCLE(9), BEAN(9),
    ;

    companion object {
        fun numberSuits(): Set<MahjongSuit> {
            return setOf(BEAN, SIGN, CIRCLE)
        }
    }

    fun createTiles(): List<MahjongTile> {
        val countPerTile = if (this == FLOWER) 1..1 else 1..4
        return countPerTile
            .flatMap { 1..count }
            .map { createTile(it) }
    }

    fun createTile(value: Int): MahjongTile {
        if (value <= 0 || value > this.count) {
            throw IllegalArgumentException("Value for $this must be inside 1..$value")
        }
        return MahjongTile(this, value)
    }

    fun createTiles(vararg value: Int): List<MahjongTile> {
        return value.map { createTile(it) }.toList()
    }

}

data class MahjongTile(val suit: MahjongSuit, val value: Int)
data class Player(val tiles: MutableList<MahjongTile>)

class Mahjong {

    private var currentPlayer: Int = 0

    val players = (1..4).map { Player(mutableListOf()) }.toList()
    private val wall = mutableListOf<MahjongTile>()

    private fun createWall(): List<MahjongTile> {
        return MahjongSuit.values().flatMap { it.createTiles() }.shuffled()
    }

    fun currentPlayer(): Player {
        return this.players[currentPlayer]
    }

    fun startGame() {
        wall.clear()
        wall.addAll(createWall())
        (1..3).forEach { _ ->
            players.forEach {player -> (1..4).forEach { dealTile(player) } }
        }
        players.forEach {
            dealTile(it)
        }
        startTurn(0)
    }

    private fun startTurn(index: Int) {
        this.currentPlayer = index
        val comparator: Comparator<MahjongTile> = Comparator { a, b -> b.suit.ordinal - a.suit.ordinal }
        dealTile(players[index])
        players[index].tiles.sortWith(comparator.thenBy { it.value })
    }

    fun dealTile(to: Player) {
        val tile = wall.removeAt(0)
        to.tiles.add(tile)
    }

    fun callChow(player: Player) {

    }

    fun callPong(player: Player) {

    }

    fun callMahjong(player: Player) {

    }

    fun discard(tile: MahjongTile) {

    }

}