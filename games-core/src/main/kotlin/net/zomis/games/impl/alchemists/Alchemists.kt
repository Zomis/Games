package net.zomis.games.impl.alchemists

import net.zomis.games.cards.probabilities.Combinatorics
import net.zomis.games.common.toPercent
import kotlin.math.round

enum class AlchemistsColor(private val char: Char) {
    RED('R'), GREEN('G'), BLUE('B');
    fun with(sign: AlchemistsSign, size: AlchemistsSize): Pair<AlchemistsColor, AlchemistsProperty>
        = this to AlchemistsProperty(sign, size)
    override fun toString(): String = char.toString()
    val plus: AlchemistsPotion = AlchemistsPotion(this, AlchemistsSign.POSITIVE)
    val negative: AlchemistsPotion = AlchemistsPotion(this, AlchemistsSign.NEGATIVE)
    val minus = negative
}
enum class AlchemistsSign(private val char: Char) {
    POSITIVE('+'), NEGATIVE('-');
    override fun toString(): String = char.toString()
}
enum class AlchemistsSize { BIG, SMALL }
enum class AlchemistsSellResult(val price: Int) {
    CORRECT_COLOR_AND_SIGN(4),
    CORRECT_SIGN_WRONG_COLOR(3),
    BLOCKED(2),
    WRONG_SIGN(1)
}
data class AlchemistsProperty(val sign: AlchemistsSign, val size: AlchemistsSize) {
    fun mixWith(other: AlchemistsProperty): AlchemistsSign? {
        if (this.sign != other.sign) return null
        if (this.size == other.size) return null
        return this.sign
    }
    fun toString(c: Char): String = (if (big) c.toUpperCase() else c) + sign.toString()

    val small = size == AlchemistsSize.SMALL
    val big = size == AlchemistsSize.BIG
    val positive = sign == AlchemistsSign.POSITIVE
    val negative = sign == AlchemistsSign.NEGATIVE
}
data class AlchemistsPotion(val color: AlchemistsColor?, val sign: AlchemistsSign?) {
    init {
        if ((color == null) xor (sign == null)) {
            throw IllegalArgumentException("either color and sign should be neutral, or both should be set. Was given $color and $sign")
        }
    }
    val blocked = color == null && sign == null
    val textRepresentation = if (blocked) "NO" else color.toString() + sign.toString()
    override fun toString(): String = textRepresentation
}
class AlchemistsChemical(vararg properties: Pair<AlchemistsColor, AlchemistsProperty>) {

    val red: AlchemistsProperty
    val green: AlchemistsProperty
    val blue: AlchemistsProperty
    val properties: Map<AlchemistsColor, AlchemistsProperty>
    init {
        require(properties.distinctBy { it.first }.size == 3) { "Chemical has invalid properties $properties" }
        red = properties.first { it.first == AlchemistsColor.RED }.second
        green = properties.first { it.first == AlchemistsColor.GREEN }.second
        blue = properties.first { it.first == AlchemistsColor.BLUE }.second
        this.properties = properties.associate { it.first to it.second }
    }

    fun mixWith(other: AlchemistsChemical): AlchemistsPotion {
        val mixes = this.properties.entries.associate {entry ->
            entry.key to entry.value.mixWith(other.properties.getValue(entry.key))
        }
        val blockedMixes = mixes.filter { it.value != null }
        if (blockedMixes.isEmpty()) {
            return AlchemistsPotion(null, null)
        }
        if (blockedMixes.size != 1) {
            throw IllegalStateException()
        }
        val result = blockedMixes.entries.single()
        return AlchemistsPotion(result.key, result.value)
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AlchemistsChemical) return false
        return properties == other.properties
    }
    override fun hashCode(): Int = properties.hashCode()

    val representation = listOf(red.toString('r'), green.toString('g'), blue.toString('b')).joinToString("/")
    override fun toString(): String = representation
}
object Alchemists {

    val red = AlchemistsColor.RED
    val green = AlchemistsColor.GREEN
    val blue = AlchemistsColor.BLUE
    class Solutions(private val solutions: Set<AlchemistsSolution>) {


        fun filtered(function: (AlchemistsSolution) -> Boolean): Solutions {
            return Solutions(solutions.filter(function).toSet())
        }
        fun proabilityOf(function: (AlchemistsSolution) -> Boolean): Double {
            val count = solutions.count(function)
            return count.toDouble() / solutions.size
        }
        val count = solutions.size

        fun probabilityOfIngredient(ingredient: Ingredient): Map<AlchemistsChemical, Double> {
            return solutions.groupBy { it.valueOf(ingredient) }.mapValues { it.value.size.toDouble() / solutions.size }
        }

        fun bestPotionMixes(): List<Pair<Ingredient, Ingredient>> {
            val potions = possiblePotions()
            val best = mutableListOf<Pair<Ingredient, Ingredient>>()
            var bestScore: Double = solutions.size.toDouble()
            for ((a, b) in ingredientCombinations()) {
                val weightedSum = potions.sumByDouble { potion -> weightedRemains { it.matchesKnowledge(AlchemistsKnowledge(a, b, potion)) }.weightedRemains }
                if (weightedSum < bestScore) {
                    bestScore = weightedSum
                    best.clear()
                }
                if (weightedSum <= bestScore) {
                    best.add(a to b)
                }
            }
            return best
        }

        fun findBestPotionMix() {
            val potions = possiblePotions()
            val best = mutableListOf<Pair<Ingredient, Ingredient>>()
            var bestScore: Double = solutions.size.toDouble()
            // Check all 28 possible ingredient mixes
            for ((a, b) in ingredientCombinations()) {
                var weightedSum = 0.0
                var negativeSolutions = 0
                // Check probability distribution of results (7 different possible results: colored +/- or blocked)
                for (potion in potions) {
                    val remains = weightedRemains { it.matchesKnowledge(AlchemistsKnowledge(a, b, potion)) }
                    if (potion.sign == AlchemistsSign.NEGATIVE) negativeSolutions += remains.count

                    val existsString = "$a and $b with result $potion exists in ${remains.count}"
                    val percentageString = "(${remains.probability.toPercent(2)})"
//                    val weightedRemainsString = "with weighted remain ${remains.weightedRemains}"
                    val sellString = sellAnalyze(a, b, potion)
                    println("$existsString $percentageString $sellString")
                    weightedSum += remains.weightedRemains
                }
                println("$a and $b has weighted remain sum $weightedSum (${negativeSolutions.toDouble() / solutions.size * 100}% negative)")
                // Check which reduces the options the most on average
                if (weightedSum < bestScore) {
                    bestScore = weightedSum
                    best.clear()
                }
                if (weightedSum <= bestScore) {
                    best.add(a to b)
                }
            }
            println("Best is $bestScore")
            best.forEach { println(it) }
        }

        data class SellAnalyze(val probability: Double, val guarantee: AlchemistsSellResult, val expectedGold: Double) {
            override fun toString(): String = "($probability, $guarantee, $expectedGold gold)"
        }
        private fun sellAnalyze(a: Ingredient, b: Ingredient, request: AlchemistsPotion): String {
            if (request == blocked) return ""
            var weightedRemains = 0.0
            for (sellResult in AlchemistsSellResult.values()) {
                val remains = this.weightedRemains { it.sellPotion(a, b, request) == sellResult }
                weightedRemains += remains.probability * remains.count
            }

            val reward = AlchemistsSellResult.values().map { guarantee ->
                val betterOrEqual = AlchemistsSellResult.values().filter { it.price >= guarantee.price }
                val allRemains = betterOrEqual.map {result ->
                    weightedRemains { it.sellPotion(a, b, request) == result }
                }
                val probabilitySum = allRemains.sumByDouble { it.probability }
                SellAnalyze(probabilitySum, guarantee, probabilitySum * guarantee.price)
            }.maxBy { it.expectedGold }
            return "selling gives $weightedRemains weighted remains, best guarantee is $reward"
        }

        class WeightedRemains(val parent: Solutions, val solutions: Set<AlchemistsSolution>) {
            val count = solutions.size
            val solution = Solutions(solutions)
            val probability = count.toDouble() / parent.solutions.size
            val weightedRemains = probability * count
        }
        private fun weightedRemains(function: (AlchemistsSolution) -> Boolean): WeightedRemains {
            return WeightedRemains(this, solutions.filter(function).toSet())
        }

        fun testMix(ingredient: Ingredient, other: Ingredient): Sequence<Pair<AlchemistsKnowledge, Solutions>> = sequence {
            for (potion in possiblePotions()) {
                val knowledge = AlchemistsKnowledge(ingredient, other, potion)
                val solutions = filtered { it.matchesKnowledge(knowledge) }
                if (solutions.count > 0) {
                    yield(knowledge to solutions)
                }
            }
        }

        fun findBestPotionSell(requests: List<AlchemistsPotion>) {
            // Results are:
            // - Correct color and sign
            // - Correct sign, wrong color
            // - Blocked potion
            // - Wrong sign, color completely unknown

            // Loop through the 28 possible ingredient mixes
            // Loop through the 3 requests
            // Find out which one is more likely to give information
            // Find out which one is more likely to give money

            val ingredients = Ingredient.values().toList()
            val sellResults = AlchemistsSellResult.values().toList()
            var qualityBest = 0 to mutableListOf<Pair<Ingredient, Ingredient>>()
            var solutionsBest = solutions.size to mutableListOf<Pair<Ingredient, Ingredient>>()
            for ((a, b) in ingredientCombinations()) {
                var totalQuality = 0
                var solutionsRemaining = 0
                for (request in requests) {
                    for (sellResult in sellResults) {
                        val sold = solutions.count { it.sellPotion(a, b, request) == sellResult }
                        totalQuality += sold * sellResult.price
                        solutionsRemaining += sold
                    }
                }
                if (totalQuality > qualityBest.first) {
                    qualityBest = totalQuality to mutableListOf()
                }
                if (totalQuality >= qualityBest.first) qualityBest.second.add(a to b)

                if (solutionsRemaining < solutionsBest.first) {
                    solutionsBest = solutionsRemaining to mutableListOf()
                }
                if (solutionsRemaining <= solutionsBest.first) solutionsBest.second.add(a to b)
            }
            println("Best quality: ${qualityBest.first}")
            qualityBest.second.forEach { println(it) }

            println("Best solutions reduction: ${solutionsBest.first}")
            solutionsBest.second.forEach { println(it) }
        }

        fun showKnowledge() {
            // Loop through all ingredient combinations, show which possible potions they must be
            println("Solutions: ${solutions.size}")
            if (solutions.isEmpty()) return
            println("Known potions:")
            for (combination in ingredientCombinations()) {
                for (potion in possiblePotions()) {
                    val matches = solutions.count {
                        it.matchesKnowledge(AlchemistsKnowledge(combination.first, combination.second, potion))
                    }
                    if (matches == solutions.size) {
                        println("$combination has to make $potion")
                    }
                }
            }
        }

        fun probabilities() = Ingredient.values().associate { it to probabilityOfIngredient(it) }

        fun showProbabilities() {
            val probabilities = probabilities()
            println("\t\t\t" + Ingredient.values().map { it.toString().padStart(8) }.joinToString("\t"))
            for (chemical in alchemyValues) {
                print(chemical)
                print("\t")
                println(Ingredient.values().map { probabilities.getValue(it).getOrElse(chemical) { 0.0 } }
                    .map { round(it * 10000.0) / 10000.0 }
                    .map { if (it == 0.0) "---" else it.toString() }
                    .map { it.padStart(8) }
                    .joinToString("\t"))
            }
        }

        fun showBestImprovement() {
            val currentMaxSum = probabilities().map { it.value.values.max() ?: 0.0 }.sum()
            for (combination in ingredientCombinations()) {
                var newMaxTotal = 0.0
                for (potion in possiblePotions()) {
                    val test = weightedRemains { it.matchesKnowledge(AlchemistsKnowledge(combination.first, combination.second, potion)) }
                    val newMaxProbabilitySum = test.solution.probabilities().map { it.value.values.max() ?: 0.0 }.sum()
                    newMaxTotal += test.probability * newMaxProbabilitySum
                }
                if (newMaxTotal > currentMaxSum) {
                    println("$combination gives $newMaxTotal which is higher than $currentMaxSum")
                }
            }
        }

        fun showBestImprovementTo(ingredient: Ingredient) {
            val currentAvg = probabilities().getValue(ingredient).values.average()
            ingredientCombinations().forEach { mix ->
                // Test mix the ingredients and check the average best probability to the ingredient
                var sum = 0.0
                possiblePotions().forEach { potion ->
                    val sol2 = filtered { it.mixPotion(mix.first, mix.second) == potion }
                    if (sol2.count > 0) {
                        val probability = sol2.count.toDouble() / count.toDouble()
                        val probabilities = sol2.probabilityOfIngredient(ingredient)
                        val maxProbability = probabilities.values.max()!!
                        sum += maxProbability * probability
                        println("Mix ${mix.first} + ${mix.second} = $potion: $probabilities ($probability)")
                    }
                }
                println("Mix ${mix.first} + ${mix.second}: sum $sum")
//                if (newAvg > )
            }
        }

//        fun potionMix(ingredient: Ingredient, other: Ingredient): AlchemistsPotion {
//            this.
//        }
    }

    enum class Ingredient(private val char: Char) {
        PURPLE_MUSHROOM('A'), GREEN_PLANT('B'), BROWN_FROG('C'), YELLOW_CHICKEN_LEG('D'),
        BLUE_FLOWER('E'), GRAY_TREE('F'), RED_SCORPION('G'), BLACK_FEATHER('H'),
        ;
        override fun toString(): String = char.toString()
    }
    class AlchemistsSolution(private val values: Map<Ingredient, AlchemistsChemical>) {
        fun matchesKnowledge(knowledge: AlchemistsKnowledge): Boolean {
            val actual = values.getValue(knowledge.ingredient).mixWith(values.getValue(knowledge.other))
            val expected = knowledge.result
            return actual == expected
        }

        fun sellPotion(ingredient: Ingredient, other: Ingredient, request: AlchemistsPotion): AlchemistsSellResult {
            val actual = valueOf(ingredient).mixWith(valueOf(other))
            return when {
                actual.color == null && actual.sign == null -> AlchemistsSellResult.BLOCKED
                actual.color == request.color && actual.sign == request.sign -> AlchemistsSellResult.CORRECT_COLOR_AND_SIGN
                actual.sign == request.sign -> AlchemistsSellResult.CORRECT_SIGN_WRONG_COLOR
                else -> AlchemistsSellResult.WRONG_SIGN
            }
        }
        fun ingredientIs(ingredient: Ingredient, chemical: AlchemistsChemical): Boolean = valueOf(ingredient) == chemical

        fun valueOf(ingredient: Ingredient): AlchemistsChemical = values.getValue(ingredient)
        fun mixPotion(ingredient: Ingredient, other: Ingredient): AlchemistsPotion
            = valueOf(ingredient).mixWith(valueOf(other))
    }
    data class AlchemistsKnowledge(val ingredient: Ingredient, val other: Ingredient, val result: AlchemistsPotion) {
        override fun toString(): String = "$ingredient + $other => $result"
    }
    class AlchemistsPotionSell(val ingredient: Ingredient, val other: Ingredient, val promise: AlchemistsPotion, val result: AlchemistsSellResult)

    class State {
        private val knowledge = mutableListOf<AlchemistsKnowledge>()

        fun addKnowledge(knowledge: AlchemistsKnowledge) {
            this.knowledge.add(knowledge)
        }

        fun addKnowledge(ingredient: Ingredient, otherIngredient: Ingredient, potion: AlchemistsPotion): State {
            this.addKnowledge(AlchemistsKnowledge(ingredient, otherIngredient, potion))
            return this
        }

        fun addKnowledgeSellResult(potion: AlchemistsPotionSell) {
            TODO()
        }
        fun findSolutions(): Solutions {
            val ingredients = Ingredient.values().toList()
            val solutions = (0 until 40320).map { Combinatorics.specificPermutation(8, it) }.map { permutation ->
                AlchemistsSolution(permutation.withIndex().toList().associate { ingredients[it.index] to alchemyValues[it.value] })
            }
            val possibleSolutions = solutions.filter { solution -> knowledge.all { solution.matchesKnowledge(it) } }
            return Solutions(possibleSolutions.toSet())
        }
    }

    val alchemyValues = listOf(
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL)
            ),

            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.SMALL),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.SMALL)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.NEGATIVE, AlchemistsSize.BIG)
            ),
            AlchemistsChemical(
                    AlchemistsColor.RED to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG),
                    AlchemistsColor.GREEN to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG),
                    AlchemistsColor.BLUE to AlchemistsProperty(AlchemistsSign.POSITIVE, AlchemistsSize.BIG)
            )
    )

    fun possiblePotions(): List<AlchemistsPotion> {
        return sequence {
            yield(AlchemistsPotion(null, null))
            for (color in AlchemistsColor.values()) {
                for (sign in AlchemistsSign.values()) {
                    yield(AlchemistsPotion(color, sign))
                }
            }
        }.toList()
    }

    fun newGame(): State = State()

    fun ingredientCombinations() = sequence {
        for (a in Ingredient.values()) {
            for (b in Ingredient.values().filter { it.ordinal > a.ordinal }) {
                yield(a to b)
            }
        }
    }.toList()

    val blocked = AlchemistsPotion(null, null)

}

