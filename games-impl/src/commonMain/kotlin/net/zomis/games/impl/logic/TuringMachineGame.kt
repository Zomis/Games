package net.zomis.games.impl.logic

import net.zomis.GreedyIterator
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.probabilities.Combinatorics
import net.zomis.games.common.asIndexRange

object TuringMachineGame {

    val factory = GamesApi.gameCreator(Model::class)
    val compose = factory.action("compose", TuringNumber::class).serialization({ it.asInt() }, { TuringMachine.turingNumber(it) })
    val question = factory.action("question", Int::class).serialization({ it }, { it })

    class Player {
        var composedNumber: TuringNumber? = null
    }
    class Model(val playerCount: Int, val level: TuringMachine.Level) {
        val answer = level.solution
        val players = (0 until playerCount).map { Player() }
    }

    val game = factory.game("Turing Machine") {
        setup {
            players(1..8)
            init {
                val game = Model(playerCount, TuringMachine.levels[2])
                game
            }
        }
        gameFlow {
            loop {
                step("compose") {
                    yieldAction(compose) {
                        precondition { true }
                        options {
                            TuringMachine.turingNumbers
                        }
                        perform {
                            game.players[playerIndex].composedNumber = action.parameter
                        }
                    }
                }
                step("question") {
                    yieldAction(question) {
                        precondition { true }
                        options { game.level.verifiers.indices }
                        perform {
                            val number = game.players[playerIndex].composedNumber
                            val criteria = game.level.criteria[action.parameter]
                            val result = criteria.check(number!!)
                            println("$playerIndex got $result when checking $number against $criteria")
                        }
                    }
                }
            }
        }
        gameFlowRules {

        }
    }

    class NightmareAI(private val verifiers: List<TuringMachine.Verifier>) : TuringDeducer {
        private val totalOptions = verifiers.sumOf { it.options }
        private val optionIndexStartsAt = verifiers.runningFold(0) { acc, next -> acc + next.options }
        private val possibleCriteria = verifiers.map { totalOptions.asIndexRange().map { true }.toMutableList() }

        private fun realAI(): AI {
            val bigVerifiers: List<TuringMachine.IVerifier> = verifiers.indices.map { realVerifierIndex ->
                object : TuringMachine.IVerifier {
                    override val options: Int = totalOptions
                    override fun option(optionIndex: Int): TuringMachine.Criterion {
                        val realVerifierStartIndex = optionIndexStartsAt.withIndex().last { it.value <= optionIndex }
                        if (!possibleCriteria[realVerifierIndex][optionIndex]) return TuringMachine.Criterias.FALSE // TODO: Test
                        return verifiers[realVerifierStartIndex.index].option(optionIndex - realVerifierStartIndex.value)
                    }
                }
            }
            val bigSolutions = possibleSolutions()
            return AI(bigVerifiers).apply {
                retainOptions(bigSolutions)
                excludeCriteriaOptions()
            }
        }

        inner class PermutationAI(val permutation: IntArray, val ai: AI)

        fun bigKnowledge(): AI.Knowledge {
            val result = verifiers.indices.map {
                totalOptions.asIndexRange().map { 0 }.toMutableList()
            }.toList()
            permutations.forEach { permutationAI ->
                val aiKnowledge = permutationAI.ai.createKnowledge()
                verifiers.indices.forEach { originalIndex ->
                    val thisIndex = permutationAI.permutation[originalIndex]
                    val startIndex = optionIndexStartsAt[thisIndex]
                    val aiVerifierSolutions = aiKnowledge.criteriaSolutions[originalIndex]
                    aiVerifierSolutions.indices.forEach {
                        result[originalIndex][startIndex + it] += aiVerifierSolutions[it]
                    }
                }
            }
            return AI.Knowledge(possibleSolutions(), result)
        }

        val factorial = Combinatorics.factorialLong(verifiers.size)
        private val permutations = (0 until factorial).map {
            val v = Combinatorics.specificPermutationLong(verifiers.size, it) // e.g. 2 1 3 0 --> C B D A. this A = original C, B=B, C=D, D=A
            val verifiers = List(verifiers.size) { i -> verifiers[v[i]] }
            PermutationAI(v, AI(verifiers))
        }.toMutableSet()

        override fun playFullGame(criteria: List<TuringMachine.Criterion>): List<AI.RoundResult> {
            TODO("Not yet implemented")
        }

        override fun possibleSolutions(): List<TuringNumber> {
            val aiSolutions = mutableSetOf<TuringNumber>()
            permutations.forEach { aiSolutions.addAll(it.ai.possibleSolutions()) }
            return aiSolutions.toList()
        }

        override fun pickBestProposal(): List<TuringNumber> {
            return realAI().pickBestProposal()
        }

        override fun pickBestQuestion(proposal: TuringNumber): TuringMachine.IVerifier? {
            return realAI().pickBestQuestion(proposal)
        }

        override fun learn(testedNumber: TuringNumber, verifierIndex: Int, result: Boolean) {
            permutations.forEach { it.ai.learn(testedNumber, verifierIndex, result) }
            val bigKnowledge = bigKnowledge()
            totalOptions.asIndexRange().forEach { optionIndex ->
                if (bigKnowledge.criteriaSolutions[verifierIndex][optionIndex] == 0) {
                    possibleCriteria[verifierIndex][optionIndex] = false
                }
            }
        }

        fun disqualifyImplies() {
            permutations.forEach { it.ai.disqualifyImplies() }
            val bigKnowledge = bigKnowledge()
            verifiers.indices.forEach { verifierIndex ->
                totalOptions.asIndexRange().forEach { optionIndex ->
                    if (bigKnowledge.criteriaSolutions[verifierIndex][optionIndex] == 0) {
                        possibleCriteria[verifierIndex][optionIndex] = false
                    }
                }
            }
        }
    }

    interface TuringDeducer {
        fun playFullGame(criteria: List<TuringMachine.Criterion>): List<AI.RoundResult>
        fun possibleSolutions(): List<TuringNumber>
        fun pickBestProposal(): List<TuringNumber>
        fun pickBestQuestion(proposal: TuringNumber): TuringMachine.IVerifier?
        fun learn(testedNumber: TuringNumber, verifierIndex: Int, result: Boolean)
    }

    class AI(val verifiers: List<TuringMachine.IVerifier>) : TuringDeducer {
        private fun <T> List<T>.iterateIndex(index: Int, countPerItem: (T) -> Int): Pair<Int, Int> {
            var i = index
            var itemIndex = 0
            val size = this.size
            while (true) {
                if (itemIndex >= size) throw IllegalArgumentException("index exceeds total countPerItem. Size is $size. Trying to find item $itemIndex")
                val countInItem = countPerItem.invoke(this[itemIndex])
                if (i < countInItem) return itemIndex to i
                itemIndex++
                i -= countInItem
            }
        }

        private val possibleCriteria = verifiers.map { it.options.asIndexRange().map { true }.toBooleanArray() }
        private val options = possibleSolutions().toMutableList()

        fun retainOptions(solutions: List<TuringNumber>) {
            options.retainAll(solutions)
            excludeCriteriaOptions()
        }

        override fun possibleSolutions(): List<TuringNumber> {
            val choices = verifiers.map { it.options }.toIntArray() // TODO: Problem with big verifiers
            val combinations = Combinatorics.combinations(choices)
            val potentialSolutions = mutableSetOf<TuringNumber>()
            for (i in (0 until combinations)) {
                val chosen = Combinatorics.specificPermutation(choices, i)
                if (verifiers.indices.any {
                    !possibleCriteria[it][chosen[it]]
                }) {
                    continue // One or more combinators can't have this criterion, continue.
                }
                val verifiers = TuringMachine.createVerifierCombination(verifiers, chosen)
                val workingNumbers = TuringMachine.turingNumbers.filter { num ->
                    verifiers.all { it.check(num) }
                }
                if (workingNumbers.size == 1) potentialSolutions.add(workingNumbers.single())
            }
            return potentialSolutions.sortedBy { it }
        }

        data class VerifierOption(val verifierIndex: Int, val optionIndex: Int)
        fun disqualifyImplies(): List<VerifierOption> {
            // TODO: Add support for multiple verifier implies, e.g. blue odd + blue equals purple --> purple odd
            val disqualified = mutableListOf<VerifierOption>()
            // Loop through criteria cards
            for ((checkerIndex, checker) in verifiers.withIndex()) {
                // Loop through possible options on that card
                for (optionIndex in checker.options.asIndexRange()) { // TODO: Problem with big verifiers, although this is not used with big verifiers
                    val verifier = checker.option(optionIndex)
                    // Pretend that it is true -- find all numbers where it is true
                    val trueForNumbers = TuringMachine.turingNumbers.filter { verifier.check(it) }
                    // Check if this forces some other card to have a specific option.
                    val impliedSolution = verifiers.minus(checker).any { otherChecker ->
                        val otherCheckerSolutions = solutionsForChecker(otherChecker, trueForNumbers)
                        otherCheckerSolutions.count { it != 0 } == 1
                    }
                    // "A implies B, but the verifier for B should be useful, therefore not A."
                    if (impliedSolution) {
                        disqualified.add(VerifierOption(checkerIndex, optionIndex))
                    }
                }
            }
            disqualified.forEach { // Cannot do this earlier as that would change the results
                possibleCriteria[it.verifierIndex][it.optionIndex] = false
            }
            updateOptions()
            return disqualified
        }

        private fun updateOptions() {
            val solutions2 = possibleSolutions().toSet()
            options.retainAll(solutions2)
        }

        fun solutionsForChecker(verifier: TuringMachine.IVerifier, possibleSolutions: List<TuringNumber> = options): List<Int> {
            return verifier.options.asIndexRange().map { index ->
                if (!possibleCriteria[verifiers.indexOf(verifier)][index]) return@map 0
                val criterion = verifier.option(index)
                possibleSolutions.count { criterion.check(it) } // TODO: Problem with big verifiers?
            }
        }

        fun excludeCriteriaOptions() {
            // Look at solutionsForChecker, use that information to turn off possible criteria options
            for (cardIndex in verifiers.indices) {
                solutionsForChecker(verifiers[cardIndex]).forEachIndexed { index, i ->
                    if (i == 0) possibleCriteria[cardIndex][index] = false
                }
            }
        }

        fun solutionsForCheckers(): List<List<Int>> {
            excludeCriteriaOptions()
            return this.verifiers.map { solutionsForChecker(it, options) }
        }

        override fun pickBestProposal(): List<TuringNumber> {
            excludeCriteriaOptions()
            updateOptions()
            val currentSolutionsForCheckers = solutionsForCheckers()

            // Check all possible numbers
            val best = GreedyIterator<TuringNumber>()
            for (turingNumber in TuringMachine.turingNumbers) {
                val score = proposalScore(turingNumber, currentSolutionsForCheckers)
                best.next(score) { turingNumber }
            }
            return best.getBest()
        }

        fun proposalScore(proposal: TuringNumber, currentSolutionsForCheckers: List<List<Int>>): Double {
            var sum = 0.0
            for ((index, verifier) in verifiers.withIndex()) {
                val currentDistribution = currentSolutionsForCheckers[index]
                val currentScore = checkerDistributionScore(currentDistribution)
                val nextScore = scoreAfterQuestion(verifier, proposal) ?: 1000.0
                sum += (nextScore / currentScore)
            }
            return -sum
        }

        fun scoreAfterQuestion(verifier: TuringMachine.IVerifier, number: TuringNumber): Double? {
            val checkerDistribution = solutionsForChecker(verifier, options)
            if (checkerDistribution.count { it != 0 } == 1) return null // Only one option, no need to ask this.
            val checkerCorrect = verifier.options.asIndexRange().map { verifier.option(it).check(number) }
            // Calculate probability of right vs. wrong, and how the distribution will look if that's the result
            val rightDistribution = checkerDistribution.zip(checkerCorrect).map { (results, correct) ->
                if (correct) results else 0
            }
            val wrongDistribution = checkerDistribution.zip(checkerCorrect).map { (results, correct) ->
                if (!correct) results else 0
            }
            if (rightDistribution.sum() == 0 || wrongDistribution.sum() == 0) {
                // No need to ask this, we know the answer
                return null
            }

            val rightProbability = rightDistribution.sum() / (rightDistribution.sum() + wrongDistribution.sum()).toDouble()
            val wrongProbability = (1 - rightProbability)
            val score = rightProbability * checkerDistributionScore(rightDistribution) + wrongProbability * checkerDistributionScore(wrongDistribution)
            // Score = a weight of green result and red results based on the probabilities and the individual distributions
            return score
            /*
            * TODO: choose the number that minimizes the sum of possibleCriteria throughout all the cards
            * TODO: when asking a question, choose the question that minimizes either the sum or the percent of possibleCriteria compared to before
            */
        }

        private fun resultDistribution(currentDistribution: List<Int>, criteriaCard: TuringMachine.CriteriaCard<Any>, number: TuringNumber, result: Boolean): List<Int> {
            TODO()
        }

        override fun pickBestQuestion(proposal: TuringNumber): TuringMachine.IVerifier? {
            // Check "How many possible solutions can remain after I check this number against this verifier?"
            val best = GreedyIterator<TuringMachine.IVerifier>()
            for (verifier in verifiers) {
                val score = scoreAfterQuestion(verifier, proposal)
                println("$verifier scored $score for $proposal")
                if (score != null) best.next(-score) { verifier }
            }
            println("Best questions: ${best.getBest().map { 'A' + verifiers.indexOf(it) }} with score ${best.getBestValue()}")
            if (best.getBest().isEmpty()) return null
            return best.getBest().random()
            // TODO: Advanced strategy:
            // Loop through all options of all checkers
            // Pick as low as possible
            // Build a tree to check all 2^(verifiers) combinations -- or (verifiers nPr 3) as 3 is max per round
            /*
            * for each turing number
            *   for each (verifier nPr 3)
            *       for each (2^3)
            *
            */
            // 5, 1, 2, 3 --> 5/11 * 5 + 1/11 * 1 + 2/11 * 2 + 3/11 * 3 --> 3.54
        }

        override fun learn(testedNumber: TuringNumber, verifierIndex: Int, result: Boolean) {
            // need to keep track of possible Checker parameters,
            // as any result might not eliminate actual numbers, just possible checker parameters (for the advanced checkers)
            val criteria = verifiers[verifierIndex]
            val optionResults = criteria.options.asIndexRange().map { criteria.option(it).check(testedNumber) }

            for ((criteriaIndex, opt) in optionResults.withIndex()) {
                if (result != opt) {
                    possibleCriteria[verifierIndex][criteriaIndex] = false
                }
            }
            options.retainAll(possibleSolutions().toSet())
        }

        fun printInformation() {
            val potentialSolutions = possibleSolutions()
            println("${potentialSolutions.size} possible numbers: $potentialSolutions")
            println("Options: ${verifiers.map { it.options }}")
            for (i in verifiers.indices) {
                val ch = 'A' + i
                println("$ch: " + solutionsForChecker(verifiers[i]))
            }
        }

        fun needsMoreInformation(): Boolean = possibleSolutions().size > 1

        data class Knowledge(
            val possibleSolutions: List<TuringNumber>,
            val criteriaSolutions: List<List<Int>>,
        ) {
            fun text(): String {
                val str = StringBuilder()
                str.appendLine("${possibleSolutions.size} possible numbers: $possibleSolutions")
                for (i in criteriaSolutions.indices) {
                    val ch = 'A' + i
                    str.appendLine("$ch: " + criteriaSolutions[i])
                }
                return str.toString()
            }
        }
        data class QuestionResult(
            val question: Char,
            val number: TuringNumber,
            val result: Boolean,
            val knowledge: Knowledge
        ) {
            fun text(): String = "Asking $question with $number returned $result"
        }
        data class RoundResult(
            val number: TuringNumber,
            val before: Knowledge,
            val after: List<QuestionResult>,
        ) {
            fun text(): String {
                val str = StringBuilder()
                str.appendLine("ROUND START! Number $number")
                str.appendLine(before.text())
                str.appendLine()
                after.forEach {
                    str.appendLine(it.text())
                    str.appendLine("Resulting in:")
                    str.appendLine(it.knowledge.text())
                    str.appendLine()
                }
                return str.toString()
            }
        }

        fun playRound(criteria: List<TuringMachine.Criterion>, number: TuringNumber, verifiersToQuestion: String): RoundResult {
            require(verifiersToQuestion.length <= 3)
            val before = createKnowledge()
            val results = mutableListOf<QuestionResult>()
            for (ch in verifiersToQuestion) {
                val indexAsk: Int = ch - 'A'
                require(indexAsk in criteria.indices)
                val result = criteria[indexAsk].check(number)
                learn(number, indexAsk, result)
                results.add(QuestionResult(ch, number, result, createKnowledge()))
            }
            return RoundResult(number, before, results)
        }

        fun createKnowledge() = Knowledge(possibleSolutions(), verifiers.map { solutionsForChecker(it) })

        fun printRound(criteria: List<TuringMachine.Criterion>, int: TuringNumber, verifiersToQuestion: String) {
            println(playRound(criteria, int, verifiersToQuestion).text())
        }

        override fun playFullGame(criteria: List<TuringMachine.Criterion>): List<RoundResult> {
            var questionsAsked = 0
            var number: TuringNumber? = null
            val rounds = mutableListOf<RoundResult>()
            var beforeRound: Knowledge = createKnowledge()
            val after = mutableListOf<QuestionResult>()
            while (needsMoreInformation()) {
                if (number == null) {
                    questionsAsked = 0
                    val proposals = pickBestProposal()
                    number = proposals.random()
                    beforeRound = createKnowledge()
                }

                val verifierToAsk = pickBestQuestion(number)
                if (verifierToAsk == null) {
                    rounds.add(RoundResult(number, beforeRound, after.toList()))
                    after.clear()
                    number = null
                    continue
                }

                val indexAsk = verifiers.indexOf(verifierToAsk)
                val result = criteria[indexAsk].check(number)
                val checkerCharacter = 'A' + indexAsk
                learn(number, indexAsk, result)
                after.add(QuestionResult(checkerCharacter, number, result, createKnowledge()))
                questionsAsked++
                if (questionsAsked >= 3) {
                    rounds.add(RoundResult(number, beforeRound, after.toList()))
                    after.clear()
                    number = null
                }
            }
            if (after.isNotEmpty()) rounds.add(RoundResult(number!!, beforeRound, after))
            return rounds
        }

        fun resultsText(rounds: List<RoundResult>): String {
            val str = StringBuilder()
            str.appendLine("RESULTS")
            str.appendLine(createKnowledge().text())
            str.appendLine("Found after ${rounds.size} rounds with ${rounds.map { it.after.size }} questions in each round")
            return str.toString()
        }

        companion object {
            fun checkerDistributionScore(distribution: List<Int>): Double = distribution.sum().toDouble()
        }

    }

}