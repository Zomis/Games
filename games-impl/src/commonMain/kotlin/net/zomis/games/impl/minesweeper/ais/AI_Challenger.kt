package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.impl.GameAIDependency
import net.zomis.games.dsl.impl.GameAIScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.WeaponUse
import kotlin.math.max
import kotlin.random.Random

object AI_Challenger {
    private val random = Random.Default

    fun play(game: Flags.Model, player: Flags.Player): WeaponUse? {
        val notMine: MutableSet<Flags.Field> = HashSet<Flags.Field>()
        val possibleMine: MutableList<Flags.Field> = ArrayList<Flags.Field>()
        for (f in game.grid.all()) {
            val field = f.value
            if (field.clicked && !field.isMine() && !field.blocked) {
                if (getFoundAdjacentMines(field) == field.value) {
                    for (neighbor in field.neighbors) {
                        if (!neighbor.clicked) notMine.add(neighbor)
                    }
                }
            }
        }
        for (f in game.grid.all()) {
            val field = f.value
            if (!field.clicked || field.isMine() || field.blocked) continue
            var possibleMineCount = 0
            for (neighbor in field.neighbors) {
                if (!neighbor.clicked && !notMine.contains(neighbor)) possibleMineCount++
            }
            if (getFoundAdjacentMines(field) + possibleMineCount == field.value) {
                for (neighbor in field.neighbors) {
                    if (!neighbor.clicked && !notMine.contains(neighbor)) {
//						this.sendChatMessage("Neighbor :" + neighbor.getCoordinate());
//						this.sendChatMessage("Field :" + field.getCoordinate());
                        return player.clickWeaponUse(neighbor)
                    }
                }
            } else if (getFoundAdjacentMines(field) < field.value) {
                for (neighbor in field.neighbors) {
                    if (!neighbor.clicked && !notMine.contains(neighbor)) {
                        if (!possibleMine.contains(neighbor)) possibleMine.add(neighbor)
                    }
                }
            }
        }
        if (possibleMine.size < 10) {
            if (ereDags(game, player)) {
                return player.bombWeaponUse(getAIBombPos(game))
            }
        }
        if (possibleMine.size > 0) {
//			this.sendChatMessage("PossibleMine :" + possibleMine.size());
            val randInt: Int = random.nextInt(possibleMine.size)
            return player.clickWeaponUse(possibleMine[randInt])
        }
        return if (ereDags(game, player)) {
            player.bombWeaponUse(getAIBombPos(game))
        } else null
    }

    private fun getFoundAdjacentMines(field: Flags.Field): Int {
        var mineCount = 0
        for (neighbor in field.neighbors) {
            if (neighbor.clicked && neighbor.isMine()) {
                mineCount++
            }
        }
        return mineCount
    }

    private fun ereDags(game: Flags.Model, player: Flags.Player): Boolean {
        if (player.canUseBomb(game) && random.nextInt(100) < 42) {
            var avarage = 0
            for (i in 0 until game.players.size) {
                avarage += game.players[i].score
            }
            avarage /= game.players.size
            if (player.score < avarage - (random.nextInt(3) + 2)) {
                return true
            }
            if (game.players.maxOf { it.score } >= game.totalMines() * 0.4) {
                return true
            }
        }
        return false
    }

    fun block(gameAIScope: GameAIScope<Flags.Model>, backup: GameAIDependency<Flags.Model>) {
        // draw = false
        gameAIScope.action {
            play(game.model, game.model.players[playerIndex])?.toAction(game.model, playerIndex) ?: this.byAI(backup)
        }
    }
        
    fun getAIBombPos(game: Flags.Model): Flags.Field {
        val bombable: MutableList<Flags.Field> = ArrayList<Flags.Field>()
        var maxPossibleBombClick = 0
        for (g in game.grid.all()) {
            val gamePosition = g.value
            var unClickedPosCurrentBomb = 0
            for (y in -2..2) {
                for (x in -2..2) {
                    val f = game.grid.getOrNull(gamePosition.x + x, gamePosition.y + y)
                    if (f != null) {
                        if (!f.clicked) unClickedPosCurrentBomb++
                    }
                }
            }
            if (maxPossibleBombClick < unClickedPosCurrentBomb) {
                bombable.clear()
                bombable.add(gamePosition)
            } else if (maxPossibleBombClick == unClickedPosCurrentBomb) {
                bombable.add(gamePosition)
            }
            maxPossibleBombClick = max(maxPossibleBombClick, unClickedPosCurrentBomb)
        }
        return bombable[random.nextInt(bombable.size)]
    }

}
