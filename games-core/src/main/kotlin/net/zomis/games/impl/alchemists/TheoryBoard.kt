package net.zomis.games.impl.alchemists

object TheoryBoard {
    val theories = listOf(Ingredient.values().map { Theory(mutableListOf(), it, null) })
}

class Theory(
        val seals: MutableList<Seal>,
        val ingredient: Ingredient,
        var alchemical: AlchemistsChemical?
) {

    fun getOwnersOfNotProperlyHedgedSeals(solution: AlchemistsChemical): List<AlchemistsModel.Player> {
        val diffs = diff(solution, this.alchemical!!)
        return seals.filter { !it.properlyHedged(diffs) }.map { it.owner }
    }

    private fun diff(x: AlchemistsChemical, y: AlchemistsChemical): List<AlchemistsColor> {
        return AlchemistsColor.values().filter { x.properties.getValue(it).sign != y.properties.getValue(it).sign }
    }

}

data class Seal(val hedge: AlchemistsColor?, val victoryPoints: Int, val owner: AlchemistsModel.Player) {

    fun properlyHedged(differences: List<AlchemistsColor>): Boolean {
        return when (differences.count()) {
            0 -> true
            1 -> when (hedge) {
                null -> false
                else -> differences.first() == hedge
            }
            else -> false
        }
    }
}

fun getPlayersSeals(player: AlchemistsModel.Player): List<Seal> {
    return listOf(
            Seal(null, 5, player),
            Seal(null, 5, player),
            Seal(null, 3, player),
            Seal(null, 3, player),
            Seal(null, 3, player),
            Seal(AlchemistsColor.BLUE, 0, player),
            Seal(AlchemistsColor.BLUE, 0, player),
            Seal(AlchemistsColor.GREEN, 0, player),
            Seal(AlchemistsColor.GREEN, 0, player),
            Seal(AlchemistsColor.RED, 0, player),
            Seal(AlchemistsColor.RED, 0, player)
    )
}