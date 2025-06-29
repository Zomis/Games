package net.zomis.games.impl.logic

import net.zomis.GreedyIterator
import net.zomis.games.api.GamesApi
import net.zomis.games.cards.probabilities.Combinatorics
import net.zomis.games.impl.logic.TuringMachine.Checker

object TuringMachineGame {

    val factory = GamesApi.gameCreator(Model::class)
    val compose = factory.action("compose", TuringNumber::class).serialization({ it.toInt() }, { TuringMachine.turingNumber(it) })
    val question = factory.action("question", Int::class).serialization({ it }, { it })

    class Player {
        var composedNumber: TuringNumber? = null
    }
    class Model(val playerCount: Int, val challenge: TuringMachine.Level) {
        val answer = challenge.solution
        val players = (0 until playerCount).map { Player() }

        val verifiers = challenge.verifiers()
    }

    val game = factory.game("Turing Machine") {
        setup {
            players(1..8)
            init {
                val game = Model(playerCount, TuringMachine.levels[2])
                val possibleSolutions = TuringMachine.turingNumbers.filter { num ->
                    game.verifiers.all { it.check(num) }
                }
                println("Possible solutions $possibleSolutions")

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
                        options { game.verifiers.indices }
                        perform {
                            val number = game.players[playerIndex].composedNumber
                            val verifier = game.verifiers[action.parameter]
                            val result = verifier.check(number!!)
                            println("$playerIndex got $result when checking $number against $verifier")
                        }
                    }
                }
            }
        }
        gameFlowRules {

        }
    }

    class AI(val criteriaCards: List<TuringMachine.Checker<Any>>) {

        private val possibleCriteria = criteriaCards.map { it.options.map { true }.toBooleanArray() }
        private val options = possibleSolutions().toMutableList()

        fun possibleSolutions(): List<TuringNumber> {
            val choices = criteriaCards.map { it.options.size }.toIntArray()
            val combinations = Combinatorics.combinations(choices)
            val potentialSolutions = mutableSetOf<TuringNumber>()
            for (i in (0 until combinations)) {
                val chosen = Combinatorics.specificPermutation(choices, i)
                if (criteriaCards.indices.any {
                    !possibleCriteria[it][chosen[it]]
                }) {
                    continue // One or more combinators can't have this criterion, continue.
                }
                val verifiers = TuringMachine.createVerifierCombination(criteriaCards, chosen)
                val workingNumbers = TuringMachine.turingNumbers.filter { num ->
                    verifiers.all { it.check(num) }
                }
                if (workingNumbers.size == 1) potentialSolutions.add(workingNumbers.single())
            }
            return potentialSolutions.sortedBy { it }
        }

        fun disqualifyImplies() {
            val disqualified = mutableListOf<Pair<Int, Int>>()
            // Loop through criteria cards
            for ((checkerIndex, checker) in criteriaCards.withIndex()) {
                // Loop through possible options on that card
                for ((optionIndex, option) in checker.options.withIndex()) {
                    val verifier = checker.creator.invoke(option)
                    // Pretend that it is true -- find all numbers where it is true
                    val trueForNumbers = TuringMachine.turingNumbers.filter { verifier.check(it) }
                    // Check if this forces some other card to have a specific option.
                    val impliedSolution = criteriaCards.minus(checker).any { otherChecker ->
                        val otherCheckerSolutions = solutionsForChecker(otherChecker, trueForNumbers)
                        otherCheckerSolutions.count { it != 0 } == 1
                    }
                    // "A implies B, but the verifier for B should be useful, therefore not A."
                    if (impliedSolution) {
                        disqualified.add(checkerIndex to optionIndex)
                    }
                }
            }
            disqualified.forEach {
                possibleCriteria[it.first][it.second] = false
            }
            updateOptions()
        }

        private fun updateOptions() {
            val solutions2 = possibleSolutions().toSet()
            options.retainAll(solutions2)
        }

        fun solutionsForChecker(checker: TuringMachine.Checker<Any>, possibleSolutions: List<TuringNumber> = options): List<Int> {
            // TODO: Use `possibleCriteria` values?
            return checker.options.mapIndexed { index, any ->
                if (!possibleCriteria[criteriaCards.indexOf(checker)][index]) return@mapIndexed 0
                val verifier = checker.creator.invoke(any)
                possibleSolutions.count { verifier.check(it) }
            }
        }

        private fun excludeCriteriaOptions() {
            // Look at solutionsForChecker, use that information to turn off possible criteria options
            for (cardIndex in criteriaCards.indices) {
                solutionsForChecker(criteriaCards[cardIndex]).forEachIndexed { index, i ->
                    if (i == 0) possibleCriteria[cardIndex][index] = false
                }
            }
        }

        fun pickBestProposal(): List<TuringNumber> {
            excludeCriteriaOptions()
            val currentSolutionsForCheckers = this.criteriaCards.map { solutionsForChecker(it, options) }

            // Check all possible numbers
            val best = GreedyIterator<TuringNumber>()
            for (turingNumber in TuringMachine.turingNumbers) {
                var sum = 0.0
                for ((index, checker) in criteriaCards.withIndex()) {
                    val currentDistribution = currentSolutionsForCheckers[index]
                    val currentScore = checkerDistributionScore(currentDistribution)
                    val nextScore = scoreAfterQuestion(checker, turingNumber) ?: 1000.0
                    sum += (nextScore / currentScore)
                }
                best.next(-sum) { turingNumber }
            }
            return best.getBest()
        }

        fun checkerDistributionScore(distribution: List<Int>): Double = distribution.sum().toDouble()

        private fun scoreAfterQuestion(checker: TuringMachine.Checker<Any>, number: TuringNumber): Double? {
            val checkerDistribution = solutionsForChecker(checker, options)
            if (checkerDistribution.count { it != 0 } == 1) return null // Only one option, no need to ask this.
            val checkerCorrect = checker.options.map { checker.creator.invoke(it).check(number) }
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

        private fun resultDistribution(currentDistribution: List<Int>, checker: TuringMachine.Checker<Any>, number: TuringNumber, result: Boolean): List<Int> {
            TODO()
        }

        fun pickBestQuestion(proposal: TuringNumber): Checker<Any>? {
            // Check "How many possible solutions can remain after I check this number against this verifier?"
            val best = GreedyIterator<Checker<Any>>()
            for (checker in criteriaCards) {
                val score = scoreAfterQuestion(checker, proposal)
                if (score != null) best.next(-score) { checker }
            }
            println("Best questions: ${best.getBest().map { 'A' + criteriaCards.indexOf(it) }} with score ${best.getBestValue()}")
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

        fun learn(testedNumber: TuringNumber, verifierIndex: Int, result: Boolean) {
            // need to keep track of possible Checker parameters,
            // as any result might not eliminate actual numbers, just possible checker parameters (for the advanced checkers)
            val criteria = criteriaCards[verifierIndex]
            val optionResults = criteria.options.map { criteria.creator.invoke(it) }.map { it.check(testedNumber) }

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
            println("Options: ${criteriaCards.map { it.options }}")
            for (i in criteriaCards.indices) {
                val ch = 'A' + i
                println("$ch: " + solutionsForChecker(criteriaCards[i]))
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

        fun playRound(verifiers: List<TuringMachine.Verifier>, number: TuringNumber, verifiersToQuestion: String): RoundResult {
            require(verifiersToQuestion.length <= 3)
            val before = createKnowledge()
            val results = mutableListOf<QuestionResult>()
            for (ch in verifiersToQuestion) {
                val indexAsk: Int = ch - 'A'
                require(indexAsk in verifiers.indices)
                val result = verifiers[indexAsk].check(number)
                learn(number, indexAsk, result)
                results.add(QuestionResult(ch, number, result, createKnowledge()))
            }
            return RoundResult(number, before, results)
        }

        fun createKnowledge() = Knowledge(possibleSolutions(), criteriaCards.map { solutionsForChecker(it) })

        fun printRound(verifiers: List<TuringMachine.Verifier>, int: TuringNumber, s: String) {
            println(playRound(verifiers, int, s).text())
        }

        fun playFullGame(verifiers: List<TuringMachine.Verifier>): List<RoundResult> {
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

                val checker = pickBestQuestion(number)
                if (checker == null) {
                    rounds.add(RoundResult(number, beforeRound, after.toList()))
                    after.clear()
                    number = null
                    continue
                }

                val indexAsk = criteriaCards.indexOf(checker)
                val result = verifiers[indexAsk].check(number)
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

    }

}