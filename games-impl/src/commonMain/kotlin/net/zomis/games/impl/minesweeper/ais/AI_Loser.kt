package net.zomis.games.impl.minesweeper.ais

import net.zomis.games.dsl.impl.GameAIScope
import net.zomis.games.impl.minesweeper.Flags
import net.zomis.games.impl.minesweeper.MfeAnalyze
import net.zomis.games.impl.minesweeper.WeaponUse
import net.zomis.minesweeper.analyze.FieldGroup
import kotlin.random.Random

object AI_Loser {
    private val random = Random.Default

    fun play(game: Flags.Model, player: Flags.Player): WeaponUse {
        val gameAnalyze = MfeAnalyze.analyze(game)
        if (player.canUseBomb(game) && game.grid.all().count { !it.value.clicked } == game.remainingMines()) {
            val bombField: Flags.Field? = eredags(game, player)
            if (bombField != null) {
                return player.bombWeaponUse(bombField)
            }
        }

        val groups: List<FieldGroup<Flags.Field>> = gameAnalyze.groups
        var lowProbabilityFields: MutableList<Flags.Field> = ArrayList<Flags.Field>()
        var lowestProbability = 42.42
        for (group in groups) {
            if (group.probability < lowestProbability) {
                lowestProbability = group.probability
                lowProbabilityFields.clear()
                lowProbabilityFields.addAll(group)
            } else if (group.probability == lowestProbability) {
                lowProbabilityFields.addAll(group)
            }
        }
        //////////////
        val lowProbabilityCleaner: MutableList<Flags.Field> =
            ArrayList<Flags.Field>(lowProbabilityFields)
        for (checkAdjacents in lowProbabilityFields) {
            var hasMineAdjacent = false
            for (invertedAdjacents in checkAdjacents.inverseNeighbors) {
                if (invertedAdjacents.isMine() && invertedAdjacents.clicked) {
                    hasMineAdjacent = true
                }
            }
            if (!hasMineAdjacent) {
                lowProbabilityCleaner.remove(checkAdjacents)
            }
        }
        if (lowProbabilityCleaner.size > 0) {
            lowProbabilityFields = lowProbabilityCleaner
        }
        ////////////
        val clickIndex: Int = random.nextInt(lowProbabilityFields.size)
        return player.clickWeaponUse(lowProbabilityFields[clickIndex])
    }

    private fun eredags(game: Flags.Model, player: Flags.Player): Flags.Field {
        val bombableFields: MutableMap<Flags.Field, Int> = HashMap<Flags.Field, Int>()
        val fields: List<Flags.Field> = ArrayList(game.grid.all().map { it.value })
        for (field in fields) {
            if (field.x >= 2 && field.y <= game.grid.sizeX - 3 && field.y >= 2 && field.y <= game.grid.sizeY - 3) {
                var mines = 0
                for (bombAdjacent in getBombAdjacents(game, field)) {
                    if (!bombAdjacent.clicked) {
                        mines++
                    }
                }
                bombableFields[field] = mines
            }
        }
        val finalBombFields: MutableList<Flags.Field> = ArrayList<Flags.Field>()
        var lowestMineCount = 42
        for ((key, value) in bombableFields) {
            if (value < lowestMineCount && value != 0) {
                finalBombFields.clear()
                lowestMineCount = value
                finalBombFields.add(key)
            } else if (value == lowestMineCount) {
                finalBombFields.add(key)
            }
        }
        return finalBombFields[random.nextInt(finalBombFields.size)]
    }

    fun getBombAdjacents(game: Flags.Model, field: Flags.Field): List<Flags.Field> {
        val bombAdjacents: MutableList<Flags.Field> = ArrayList()
        val x: Int = field.x
        val y: Int = field.y
        for (yy in -2..2) {
            for (xx in -2..2) {
                if (x + xx >= 0 && x + xx < game.grid.sizeX && y + yy >= 0 && y + yy < game.grid.sizeY)
                    bombAdjacents.add(game.getRelativePosition(field, xx, yy))
            }
        }
        return bombAdjacents
    }

    fun block(gameAIScope: GameAIScope<Flags.Model>) {
        gameAIScope.action {
            play(this.game.model, this.game.model.players[playerIndex]).toAction(game.model, playerIndex)
        }
    }

}