package net.zomis.games.impl.logic

import net.zomis.games.cards.probabilities.Combinatorics
import net.zomis.games.dsl.GameSerializable
import kotlin.math.absoluteValue

class TuringNumber(private val _values: IntArray) : Comparable<TuringNumber>, GameSerializable {
    init {
        require(_values.size == 3)
        require(_values.all { it in 1..5 })
    }
    override fun toString(): String = _values.joinToString("")
    operator fun get(i: Int) = _values[i]
    operator fun get(color: TuringMachine.Color) = color(color)
    fun count(condition: (Int) -> Boolean) = _values.count(condition)
    fun sum() = _values.sum()
    fun color(color: TuringMachine.Color): Int = this[color.ordinal]
    val blue get() = color(TuringMachine.Color.Blue)
    val yellow get() = color(TuringMachine.Color.Yellow)
    val purple get() = color(TuringMachine.Color.Purple)
    val values get() = _values.toList()
    fun asInt() = blue * 100 + yellow * 10 + purple
    override fun compareTo(other: TuringNumber): Int = this.asInt().compareTo(other.asInt())
    override fun hashCode(): Int = asInt()
    override fun serialize(): Int = asInt()

    override fun equals(other: Any?): Boolean {
        return (other is TuringNumber) && other.asInt() == asInt()
    }

    companion object {
        fun int(i: Int): TuringNumber {
            require(i >= 111)
            require(i <= 555)
            return TuringNumber(intArrayOf(i / 100, i / 10 % 10, i % 10))
        }
    }
}
object TuringMachine {
    fun ai() {
//        val levels = TuringMachine.levels.filter { it.allPossibleVerifiers().size == 1 }
//            .filter { it.solution.asInt() == 111 }
//    val level = levels.first()
//    val level = TuringMachine.levels.last { it.allPossibleVerifiers().size == 1 } // My machine: 5 questions (two rounds)
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(4, 5, 2)), intArrayOf(3, 6, 12, 16)) // My machine: 2 questions
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(1, 2, 2)), intArrayOf(2, 5, 10, 16)) // Only two solutions: 122, 522. My machine: 1 question.

        // #C654GZ7
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(1, 1, 4)), intArrayOf(33, 36, 40, 44, 47, 48))
//        val level = TuringMachine.Level(TuringNumber(intArrayOf(4, 4, 3)), intArrayOf(3, 9, 12, 17, 20))
        val level = Level.extreme(
            TuringNumber(intArrayOf(4, 5, 5)),
            arrayOf(
                intArrayOf(12, 22), intArrayOf(23, 8), intArrayOf(48,2),
                intArrayOf(5, 24), intArrayOf(19, 9), intArrayOf(3, 43),
            ),
            mapOf(22 to 2, 23 to 2, 48 to 0, 24 to 1, 9 to 0, 43 to 1)
        )
//        val verifierOptions = arrayOf(0, 1, 2, 1, 1)

        /*
        * interesting level:
        * 2: blue vs 3
        * 5: blue even or odd
        * 10: number of 4s
        * 16: more even or odd numbers
        *
        * Blue = 3 implies blue odd
        * 3 4s implies blue > 4, blue even, and more even than odd
        * 2 4s implies more even than odd
        * Numbers that should not be allowed: 322, 244, 144, 344, 544, 444
        */
        println(level)
        val ai = TuringMachineGame.AI(level.verifiers)
        ai.disqualifyImplies()
        val rounds = ai.playFullGame(level.criteria)
        rounds.forEach {
            println(it.text())
        }
        println(ai.resultsText(rounds))

//        val level = Level.classic(TuringNumber(intArrayOf(1, 5, 4)), intArrayOf(24, 31, 36, 47, 48))
//        val verifierOptions = arrayOf(0, 1, 2, 1, 1)
        // Algorithm
//        ai.printRound(verifiers, TuringNumber.int(514), "ACD")
//        ai.printRound(verifiers, TuringNumber.int(411), "DBE")
//        ai.printRound(verifiers, TuringNumber.int(134), "BE")
        // Algorithm preferred
//        ai.printRound(verifiers, TuringNumber.int(514), "EDC")
//        ai.printRound(verifiers, TuringNumber.int(411), "BED")
//        ai.printRound(verifiers, TuringNumber.int(134), "E")


        // M
//        ai.printRound(verifiers, TuringNumber.int(134), "EDA")
//        ai.printRound(verifiers, TuringNumber.int(152), "ABD")
//        ai.printRound(verifiers, TuringNumber.int(132), "EC")

        // M improved
//        ai.printRound(verifiers, TuringNumber.int(134), "EDA")
//        ai.printRound(verifiers, TuringNumber.int(152), "ABE")
//        ai.printRound(verifiers, TuringNumber.int(132), "EC")

        ai.printInformation()
        return
    }

    private val digitCounts = (0..3).toList()
    private val digitCountsExceptMax = digitCounts.dropLast(1)
    fun turingNumber(i: Int): TuringNumber = TuringNumber.int(i)

    val colors = Color.entries.toList()

    class Level(
        val solution: TuringNumber,
        val verifiers: List<Verifier>,
        cardOptions: IntArray?, // null to auto-select
        val mixedCardsAnswers: Boolean, // nightmare mode, don't know which solution card is connected to which checker card
    ) {
        fun allPossibleCriteria() = Companion.allPossibleCriteria(verifiers, solution)

        val criteria = if (cardOptions != null) cardOptions.withIndex().map {
            verifiers[it.index].option(it.value)
        } else singleCriteria()

        init {
            require(mixedCardsAnswers.not()) { "Nightmare mode not supported yet" }
            val criteriaAnswers = criteria.map { it.check(solution) }
            require(criteriaAnswers.all { it }) { "All criteria does not match solution: $criteriaAnswers" }
            val possibleAnswers = turingNumbers.filter { num -> criteria.all { it.check(num) } }
            require(possibleAnswers.size == 1) { "Criteria does not give a unique answer. Possible answers are $possibleAnswers" }
        }

        fun singleCriteria(): List<Criterion> {
            val possibleVerifiers = allPossibleCriteria()
            return possibleVerifiers.singleOrNull() ?:
                throw IllegalStateException("${possibleVerifiers.size} verifier combinations valid for $solution with verifiers $verifiers")
        }

        override fun toString(): String = "Level($solution, $verifiers)"

        companion object {
            fun classic(solution: TuringNumber, checkers: IntArray): Level {
                return Level(solution, checkers.map { Verifier(listOf(Criterias.forCard(it))) }, null, mixedCardsAnswers = false)
            }

            fun allPossibleCriteria(verifiers: List<Verifier>, solution: TuringNumber): List<List<Criterion>> {
                val choices = verifiers.map { it.options }.toIntArray()
                val combinations = Combinatorics.combinations(choices)
                return (0 until combinations).map {
                    createVerifierCombination(verifiers, Combinatorics.specificPermutation(choices, it))
                }.filter { criteria ->
                    criteria.all { it.check(solution) }
                }
            }

            fun extreme(
                answer: TuringNumber,
                verifierCards: Array<IntArray>,
                verifierOptionIndices: Map<Int, Int>
            ): Level {
                val verifiers = verifierCards.map { cards ->
                    Verifier(cards.map { Criterias.forCard(it) })
                }
                val cardOptions = verifierCards.mapIndexed { index, cards ->
                    val optionEntry = verifierOptionIndices.entries.single { it.key in cards }
                    val cardIndex = cards.indexOf(optionEntry.key)
                    verifiers[index].cardIndexOption(cardIndex, optionEntry.value)
                }.toIntArray()
                return Level(answer, verifiers, cardOptions, mixedCardsAnswers = false)
            }
        }
    }
    fun level(solution: Int, vararg cards: Int) = Level.classic(turingNumber(solution), cards)

    fun createVerifierCombination(criteriaCards: List<Verifier>, chosen: IntArray): List<Criterion> {
        return criteriaCards.mapIndexed { index, it ->
            it.option(chosen[index])
        }
    }

    val turingNumbers = sequence<TuringNumber> {
        for (a in 1..5) {
            for (b in 1..5) {
                for (c in 1..5) {
                    yield(TuringNumber(intArrayOf(a, b, c)))
                }
            }
        }
    }.toList()

    enum class Color {
        Blue, Yellow, Purple;

        val other1 get() = when (this) {
            Blue -> Yellow
            Yellow -> Purple
            Purple -> Blue
        }
        val other2 get() = this.other1.other1
    }
    enum class EvenOdd(private val mod2: Int) {
        Even(0), Odd(1);
        val other get() = if (this == Even) Odd else Even
        fun matches(i: Int) = i % 2 == mod2
    }

    val levels = listOf(
        level(241, 4, 9, 11, 14),
        level(435, 3, 7, 10, 14),
        level(331, 4, 9, 13, 17),
        level(345, 3, 8, 15, 16),
        level(354, 2, 6, 14, 17),
        level(512, 2, 7, 10, 13),
        level(241, 8, 12, 15, 17),
        level(423, 3, 5, 9, 15, 16),
        level(344, 1, 7, 10, 12, 17), // My machine: 3 questions
        level(242, 2, 6, 8, 12, 15),
        level(325, 5, 10, 11, 15, 17),
        level(111, 4, 9, 18, 20),
        level(111, 11, 16, 19, 21),
        level(422, 2, 13, 17, 20), // My machine: 4 questions
        level(253, 5, 14, 18, 19, 20),
        level(243, 2, 7, 12, 16, 19, 22),
        // More than one possible combination of checker options below:
//        level(133, 21, 31, 37, 39),
//        level(331, 23, 28, 41, 48),
//        level(224, 19, 24, 30, 31, 38),
//        level(411, 11, 22, 30, 33, 34, 40),
    )

    class Verifier(private val cards: List<CriteriaCard<*>>) {
        val options = cards.sumOf { it.criteriaOptions }
        fun option(optionIndex: Int): Criterion {
            require(optionIndex < options)
            var cardIndex = 0
            var i = optionIndex
            while (i >= cards[cardIndex].criteriaOptions) {
                i -= cards[cardIndex].criteriaOptions
                cardIndex++
            }
            return cards[cardIndex].option(i)
        }

        fun cardIndexOption(cardIndex: Int, optionIndex: Int): Int {
            return cards.take(cardIndex).sumOf { it.criteriaOptions } + optionIndex
        }
    }
    class CriteriaCard<T>(private val options: List<T>, private val creator: (T) -> Criterion) {
        val criteriaOptions: Int = options.size

        fun option(i: Int) = creator.invoke(options[i])

        /*
        fun or(other: CriteriaCard<*>): CriteriaCard<Any> {
            val newOptions = criteriaOptions + other.criteriaOptions
            return CriteriaCard<Int>((0 until newOptions).toList()) {
                if (it < criteriaOptions) option(it) else other.option(it - criteriaOptions)
            } as CriteriaCard<Any>
        }
        */

        constructor(options: Iterable<T>, creator: (T) -> Criterion) : this(options.toList(), creator)
    }
    class Criterion(val name: String? = null, val check: (TuringNumber) -> Boolean) {
        override fun toString(): String = name ?: "???"
        // TODO: fun withName(name: String): Verifier = Verifier(name, check)
    }

    enum class Comparison {
        Less, Equal, More;

        fun check(a: Int, b: Int): Boolean = when (this) {
            Less -> a < b
            Equal -> a == b
            More -> a > b
        }
    }

    object Criterias {
        fun forCard(card: Int): CriteriaCard<Any> {
            require(card >= 1)
            require(card <= criterias.size) { "Criteria size is ${criterias.size}, cannot find card $card" }
            return criterias[card - 1] as CriteriaCard<Any>
        }
        fun forCards(cards: IntArray): List<CriteriaCard<Any>> = cards.map { forCard(it) }

        val comparisons = Comparison.entries.toList()
        fun comparisonsFor(value: Int) = when (value) {
            1 -> listOf(Comparison.Equal, Comparison.More)
            2, 3, 4 -> comparisons
            5 -> listOf(Comparison.Less, Comparison.Equal)
            else -> throw IllegalArgumentException("$value must be within range 1..5")
        }
        fun compare(a: (TuringNumber) -> Int, b: (TuringNumber) -> Int): CriteriaCard<Comparison> = CriteriaCard(comparisons) { comp ->
            Criterion(comp.toString()) { comp.check(a.invoke(it), b.invoke(it)) }
        }
        fun compare(color: Color, value: Int): CriteriaCard<Comparison> = CriteriaCard(comparisonsFor(value)) { comp ->
            Criterion("$color $comp $value") { comp.check(it.color(color), value) }
        }
        fun compare(a: Color, b: Color) = CriteriaCard(comparisons) { comp ->
            Criterion("$a $comp $b") { comp.check(it.color(a), it.color(b)) }
        }
        fun specificColor(condition: (Int) -> Boolean) = CriteriaCard(colors) {
            Criterion { ints -> condition.invoke(ints.color(it)) }
        }
        fun color(condition: (Color, TuringNumber) -> Boolean) = CriteriaCard(colors) {
            Criterion { ints -> condition.invoke(it, ints) }
        }
        fun evenOdd(color: Color) = CriteriaCard(EvenOdd.entries.toList()) {
            Criterion { ints -> it.matches(ints[color]) }
        }
        fun mod(color: Color, value: Int) = CriteriaCard((0 until value).toList()) { mod ->
            Criterion("$color modulo $value == $mod") { it.color(color) % value == mod }
        }
        fun numberOf(condition: (Int) -> Boolean) = CriteriaCard((0..3).toList()) { count ->
            Criterion { num -> num.count(condition) == count }
        }
        fun smallest() = CriteriaCard(colors) { color ->
            Criterion("$color is smallest") { it.color(color) < it.color(color.other1) && it.color(color) < it.color(color.other2) }
        }
        fun largest() = CriteriaCard(colors) { color ->
            Criterion("$color is largest") { it.color(color) > it.color(color.other1) && it.color(color) > it.color(color.other2) }
        }
        fun countCompare() = CriteriaCard(EvenOdd.entries.toList()) { evenOdd ->
            Criterion("More $evenOdd than ${evenOdd.other}") { ints -> ints.count { evenOdd.matches(it) } > ints.count { evenOdd.other.matches(it) } }
        }
        fun <A, B> combined(a: List<A>, b: List<B>, creator: (A, B) -> Criterion) = CriteriaCard(
            (0 until a.size * b.size).map {
                val chosenIndices = Combinatorics.specificPermutation(intArrayOf(a.size, b.size), it.toLong())
                val chosenA = a[chosenIndices[0]]
                val chosenB = b[chosenIndices[1]]
                chosenA to chosenB
            }
        ) { i ->
            creator.invoke(i.first, i.second)
        }

        private val criterias = listOf(
            compare(Color.Blue, 1),
            compare(Color.Blue, 3),
            compare(Color.Yellow, 3),
            compare(Color.Yellow, 4),
            evenOdd(Color.Blue), // 5
            evenOdd(Color.Yellow),
            evenOdd(Color.Purple),
            numberOf { it == 1 },
            numberOf { it == 3 },
            numberOf { it == 4 },// 10
            compare(Color.Blue, Color.Yellow),
            compare(Color.Blue, Color.Purple),// 12
            compare(Color.Yellow, Color.Purple),
            smallest(), // smallest / largest, explicitly. not equal to.
            largest(),// 15
            countCompare(),
            numberOf { EvenOdd.Even.matches(it) },// 17
            CriteriaCard(EvenOdd.entries.toList()) { evenOdd ->
                Criterion("Sum of numbers is $evenOdd") { ints -> evenOdd.matches(ints.sum()) }
            },
            CriteriaCard(comparisons) { comp ->
                Criterion("Blue + Yellow $comp 6") { ints -> comp.check(ints.blue + ints.yellow, 6) }
            },
            CriteriaCard((3 downTo 1).toList()) { repeats -> // 20: triple number, double number, no repetition
                Criterion("$repeats equal numbers") { ints -> ints.values.groupingBy { it }.eachCount().values.max() == repeats }
            },
            CriteriaCard(listOf(false, true)) { exactlyTwice ->
                val name = if (exactlyTwice) "exactly twice" else "not exactly twice"
                Criterion(name) { ints -> ints.values.groupingBy { it }.eachCount().values.any { it == 2 } == exactlyTwice }
            },
            CriteriaCard(listOf(1 to "ascending", -1 to "descending", 0 to "no")) { param ->
                val order = param.first
                Criterion("Numbers are in ${param.second} order") { ints ->
                    // 134 (ascending) / 345 (ascending) / 321 (descending) / 243 (no order)
                    // 133 is also "no order"
                    val a = ints.blue.compareTo(ints.yellow) // returns -1 if blue is less than yellow (ascending)
                    val b = ints.yellow.compareTo(ints.purple)
                    when {
                        a == -1 && b == -1 -> 1 == order
                        a == 1 && b == 1 -> -1 == order
                        else -> 0 == order
                    }
                }
            },
            compare({ it.sum() }, { 6 }),
            CriteriaCard(1..3) { ascending -> // Sequence of ascending numbers
                Criterion("$ascending consecutive ascending numbers") { ints ->
                    val diff1 = ints.purple - ints.yellow
                    val diff2 = ints.yellow - ints.blue
                    listOf(1, diff1, diff2).count { it == 1 } == ascending
                }
            },
            CriteriaCard(1..3) { consecutiveSequence -> // 25: Sequence of ascending or descending numbers
                Criterion("$consecutiveSequence consecutive numbers (ascending or descending)") { ints ->
                    // 543, 123, 234 --> 3
                    // 431, 532, 124 --> 2
                    // 531 --> 1
                    val diff1 = ints.purple - ints.yellow
                    val diff2 = ints.yellow - ints.blue
                    val actual = when {
                        diff1 == diff2 && diff1.absoluteValue == 1 -> 3
                        diff1.absoluteValue == 1 || diff2.absoluteValue == 1 -> 2
                        else -> 1
                    }
                    actual == consecutiveSequence
                }
            },
            specificColor { it < 3 },
            specificColor { it < 4 },
            specificColor { it == 1 },
            specificColor { it == 3 },
            specificColor { it == 4 },// 30
            specificColor { it > 1 },
            specificColor { it > 3 },
            combined(colors, EvenOdd.entries.toList()) { color, evenOdd ->
                Criterion("$color is $evenOdd") { ints -> evenOdd.matches(ints.color(color)) }
            },
            color { color, ints -> ints.color(color) <= ints.color(color.other1) && ints.color(color) <= ints.color(color.other2) },
            color { color, ints -> ints.color(color) >= ints.color(color.other1) && ints.color(color) >= ints.color(color.other2) },// 35
            CriteriaCard(3..5) { divisor ->
                Criterion("Sum of numbers is divisible by $divisor") { ints -> ints.sum() % divisor == 0 }
            },
            color { color, ints -> ints.sum() - ints.color(color) == 4 },
            color { color, ints -> ints.sum() - ints.color(color) == 6 },
            combined(colors, listOf(Comparison.Equal, Comparison.More)) { color, comp ->
                Criterion("$color $comp 1") { ints -> comp.check(ints.color(color), 1) }
            },
            combined(colors, comparisons) { color, comp ->// 40
                Criterion("$color $comp 3") { ints -> comp.check(ints.color(color), 3) }
            },
            combined(colors, comparisons) { color, comp ->
                Criterion("$color $comp 4") { ints -> comp.check(ints.color(color), 4) }
            },
            combined(colors, listOf(Comparison.Less, Comparison.More)) { color, comp -> // 42: color is the smallest or largest
                Criterion("$color is the most $comp") { ints -> comp.check(ints[color], ints[color.other1]) && comp.check(ints[color], ints[color.other2]) }
            },
            combined(colors.minus(Color.Blue), comparisons) { color, comp ->
                Criterion("Blue $comp $color") { ints -> comp.check(ints.blue, ints[color]) }
            },
            combined(colors.minus(Color.Yellow), comparisons) { color, comp ->
                Criterion("Yellow $comp $color") { ints -> comp.check(ints.yellow, ints[color]) }
            },
            combined(digitCountsExceptMax, listOf(1, 3)) { count, value ->
                Criterion("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(digitCountsExceptMax, listOf(3, 4)) { count, value ->
                Criterion("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(digitCountsExceptMax, listOf(1, 4)) { count, value ->
                Criterion("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(colors.reversed(), comparisons) { excludeColor, comparison ->
                val colors = Color.entries.filter { it != excludeColor }
                check(colors.size == 2)
                val first = colors.first()
                val second = colors.last()
                Criterion("$first $comparison $second") { ints ->
                    comparison.check(ints[first], ints[second])
                }
            },
        )
    }
}
/*
each answer card has 133 results, but there's only 125 combinations of puzzles?

1 blue compared to 1
blue ~ 3
3 yellow ~ 3
yellow ~ 4
5 blue evenodd
yellow evenodd
purple evenodd
8 number of 1s
number of 3s
10 number of 4s
blue ~ yellow
blue ~ purple
yellow ~ purple
14 which color is smallest
15 which color is largest
no of even vs no of odd
no of even numbers
18 sum of numbers even or odd
sum of blue and yellow ~ 6
20 triple number, double number, no repetition
any number exactly twice
ascending, descending, or no order
sum of numbers ~ 6
sequence of ascending numbers (3 numbers, 2 numbers, no numbers)
25 sequence of ascending or descending (3 numbers, 2 numbers, none)
specific color less than 3
specific color less than 4
specific color = 1 (3 options)
spec color = 3
30 spec color = 4
spec color > 1
spec color > 3
spec color even or odd
smallest or tied for smallest number
35 largest or tied for largest number
sum of numbers multiple of 3/4/5 (3/4/5)
sum of 2 specific Color = 4
sum of 2 specific Color = 6
number of one specific color ~ 1 (6 options)
40 number of one specific color ~ 3 (9 options)
number of one specific color ~ 4 (9 options)
which color is the smallest or largest (6 options)
blue number compared to another specific color (6 options)
yellow number compared to another specific color (6 options)
45 how many 1s or 3s in the code (6 options -- 0-2 + 1/3)
how many 3s or 4s in the code (6 options -- 0-2 + 1/3)
how many 1s or 4s in the code (6 options -- 0-2 + 1/3)
48 one color compared to another color (9 options)
*/
