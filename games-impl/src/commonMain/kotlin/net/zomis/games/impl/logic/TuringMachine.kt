package net.zomis.games.impl.logic

import net.zomis.games.cards.probabilities.Combinatorics
import kotlin.math.absoluteValue
import kotlin.math.sign

class TuringNumber(private val _values: IntArray) : Comparable<TuringNumber> {
    init {
        require(_values.size == 3)
        require(_values.all { it in 1..5 })
    }
    constructor() : this(intArrayOf())
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
    override fun hashCode(): Int = asInt().hashCode()
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
fun TuringNumber.toInt() = this[0] * 100 + this[1] * 10 + this[2]
object TuringMachine {
    fun ai() {
        val levels = TuringMachine.levels.filter { it.allPossibleVerifiers().size == 1 }
            .filter { it.solution.asInt() == 111 }
//    val level = levels.first()
//    val level = TuringMachine.levels.last { it.allPossibleVerifiers().size == 1 } // My machine: 5 questions (two rounds)
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(4, 5, 2)), intArrayOf(3, 6, 12, 16)) // My machine: 2 questions
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(1, 2, 2)), intArrayOf(2, 5, 10, 16)) // Only two solutions: 122, 522. My machine: 1 question.

        // #C654GZ7
//    val level = TuringMachine.Level(TuringNumber(intArrayOf(1, 1, 4)), intArrayOf(33, 36, 40, 44, 47, 48))
//        val level = TuringMachine.Level(TuringNumber(intArrayOf(4, 4, 3)), intArrayOf(3, 9, 12, 17, 20))
        val level = TuringMachine.Level(TuringNumber(intArrayOf(1, 3, 3)), intArrayOf(1, 22, 31, 38, 41))
        val verifierOptions = arrayOf(0, 1, 1, 0, 1)

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
        val checkers = level.checkers()
        val verifiers = if (verifierOptions.isEmpty()) level.verifiers() else verifierOptions.mapIndexed { index, i ->
            checkers[index].option(i)
        }
        val ai = TuringMachineGame.AI(checkers)
        ai.disqualifyImplies()
        var questionsAsked = 0
        var number: TuringNumber? = null
        while (ai.needsMoreInformation()) {
            println("AI Needs more information.")
            ai.printInformation()
            println("Possible proposals: " + ai.pickBestProposal())
            if (number == null || questionsAsked >= 3) {
                questionsAsked = 0
                val proposals = ai.pickBestProposal()
                number = proposals.random()
            }

            val checker = ai.pickBestQuestion(number)
            if (checker == null) {
                number = null
                println("No more questions to ask right now.")
                println()
                continue
            }

            val indexAsk = checkers.indexOf(checker)
            val checkerCharacter = 'A' + indexAsk
            val result = verifiers[indexAsk].check(number)
            questionsAsked++

            println("result was $result when checking $number criteria $checkerCharacter")
            ai.learn(number, indexAsk, result)
            println()
        }
        ai.printInformation()
    }

    private val digitCounts = (0..3).toList()
    private val digitCountsExceptMax = digitCounts.dropLast(1)
    fun turingNumber(i: Int): TuringNumber {
        if (i == 0) return TuringNumber()
        check(i >= 111)
        check(i <= 555)
        val result = IntArray(3)
        result[0] = i / 100
        result[1] = (i / 10) % 10
        result[2] = i % 10
        check(result.all { it in 1..5 })
        return TuringNumber(result)
    }

    val colors = Color.entries.toList()
    class Level(val solution: TuringNumber, val cards: IntArray) {
        fun checkers(): List<Checker<Any>> = Criterias.forCards(cards)

        fun allPossibleVerifiers(): List<List<Verifier>> {
            val checkers = checkers()
            val choices = checkers.map { it.options.size }.toIntArray()
            val combinations = Combinatorics.combinations(choices)
            return (0 until combinations).map {
                createVerifierCombination(checkers, Combinatorics.specificPermutation(choices, it))
            }.filter { verifiers ->
                verifiers.all { it.check(solution) }
            }
        }

        fun verifiers(): List<Verifier> {
            val possibleVerifiers = allPossibleVerifiers()
            return possibleVerifiers.singleOrNull() ?: throw IllegalStateException("${possibleVerifiers.size} verifier combinations valid for $solution with cards ${cards.contentToString()}")
        }

        override fun toString(): String = "Level($solution, ${cards.contentToString()})"
    }
    fun level(solution: Int, vararg cards: Int) = Level(turingNumber(solution), cards)

    fun createVerifierCombination(checkers: List<Checker<out Any>>, chosen: IntArray): List<Verifier> {
        return checkers.mapIndexed { index, it ->
            val checker = it as Checker<Any>
            checker.creator.invoke(checker.options[chosen[index]])
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
        level(133, 21, 31, 37, 39),
        level(331, 23, 28, 41, 48),
        level(224, 19, 24, 30, 31, 38),
        level(411, 11, 22, 30, 33, 34, 40),
    )

    class Checker<T>(val options: List<T>, val creator: (T) -> Verifier) {
        fun option(i: Int) = creator.invoke(options[i])

        constructor(options: Iterable<T>, creator: (T) -> Verifier) : this(options.toList(), creator)
    }
    class Verifier(val name: String? = null, val check: (TuringNumber) -> Boolean) {
        override fun toString(): String = name ?: "???"
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
        fun forCard(card: Int): Checker<Any> {
            require(card >= 1)
            require(card <= criterias.size) { "Criteria size is ${criterias.size}, cannot find card $card" }
            return criterias[card - 1] as Checker<Any>
        }
        fun forCards(cards: IntArray): List<Checker<Any>> = cards.map { forCard(it) }

        val comparisons = Comparison.entries.toList()
        fun comparisonsFor(value: Int) = when (value) {
            1 -> listOf(Comparison.Equal, Comparison.More)
            2, 3, 4 -> comparisons
            5 -> listOf(Comparison.Less, Comparison.Equal)
            else -> throw IllegalArgumentException("$value must be within range 1..5")
        }
        fun compare(a: (TuringNumber) -> Int, b: (TuringNumber) -> Int): Checker<Comparison> = Checker(comparisons) { comp ->
            Verifier(comp.toString()) { comp.check(a.invoke(it), b.invoke(it)) }
        }
        fun compare(color: Color, value: Int): Checker<Comparison> = Checker(comparisonsFor(value)) { comp ->
            Verifier("$color $comp $value") { comp.check(it.color(color), value) }
        }
        fun compare(a: Color, b: Color) = Checker(comparisons) { comp ->
            Verifier("$a $comp $b") { comp.check(it.color(a), it.color(b)) }
        }
        fun specificColor(condition: (Int) -> Boolean) = Checker(colors) {
            Verifier { ints -> condition.invoke(ints.color(it)) }
        }
        fun color(condition: (Color, TuringNumber) -> Boolean) = Checker(colors) {
            Verifier { ints -> condition.invoke(it, ints) }
        }
        fun evenOdd(color: Color) = Checker(EvenOdd.entries.toList()) {
            Verifier { ints -> it.matches(ints[color]) }
        }
        fun mod(color: Color, value: Int) = Checker((0 until value).toList()) { mod ->
            Verifier("$color modulo $value == $mod") { it.color(color) % value == mod }
        }
        fun numberOf(condition: (Int) -> Boolean) = Checker((0..3).toList()) { count ->
            Verifier { num -> num.count(condition) == count }
        }
        fun smallest() = Checker(colors) { color ->
            Verifier("$color is smallest") { it.color(color) < it.color(color.other1) && it.color(color) < it.color(color.other2) }
        }
        fun largest() = Checker(colors) { color ->
            Verifier("$color is largest") { it.color(color) > it.color(color.other1) && it.color(color) > it.color(color.other2) }
        }
        fun countCompare() = Checker(EvenOdd.entries.toList()) { evenOdd ->
            Verifier("More $evenOdd than ${evenOdd.other}") { ints -> ints.count { evenOdd.matches(it) } > ints.count { evenOdd.other.matches(it) } }
        }
        fun <A, B> combined(a: List<A>, b: List<B>, creator: (A, B) -> Verifier) = Checker(
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
            Checker(EvenOdd.entries.toList()) { evenOdd ->
                Verifier("Sum of numbers is $evenOdd") { ints -> evenOdd.matches(ints.sum()) }
            },
            Checker(comparisons) { comp ->
                Verifier("Blue + Yellow $comp 6") { ints -> comp.check(ints.blue + ints.yellow, 6) }
            },
            Checker((3 downTo 1).toList()) { repeats -> // 20: triple number, double number, no repetition
                Verifier("$repeats equal numbers") { ints -> ints.values.groupingBy { it }.eachCount().values.max() == repeats }
            },
            Checker(listOf(false, true)) { exactlyTwice ->
                val name = if (exactlyTwice) "exactly twice" else "not exactly twice"
                Verifier(name) { ints -> ints.values.groupingBy { it }.eachCount().values.any { it == 2 } == exactlyTwice }
            },
            Checker(listOf(-1 to "descending", 0 to "no", 1 to "ascending")) { param ->
                val order = param.first
                Verifier("Numbers are in ${param.second} order") { ints ->
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
            Checker(1..3) { ascending -> // Sequence of ascending numbers
                Verifier("$ascending consecutive ascending numbers") { ints ->
                    val diff1 = ints.purple - ints.yellow
                    val diff2 = ints.yellow - ints.blue
                    listOf(1, diff1, diff2).count { it == 1 } == ascending
                }
            },
            Checker(1..3) { consecutiveSequence -> // 25: Sequence of ascending or descending numbers
                Verifier("$consecutiveSequence consecutive numbers (ascending or descending)") { ints ->
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
                Verifier("$color is $evenOdd") { ints -> evenOdd.matches(ints.color(color)) }
            },
            color { color, ints -> ints.color(color) <= ints.color(color.other1) && ints.color(color) <= ints.color(color.other2) },
            color { color, ints -> ints.color(color) >= ints.color(color.other1) && ints.color(color) >= ints.color(color.other2) },// 35
            Checker(3..5) { divisor ->
                Verifier("Sum of numbers is divisible by $divisor") { ints -> ints.sum() % divisor == 0 }
            },
            color { color, ints -> ints.sum() - ints.color(color) == 4 },
            color { color, ints -> ints.sum() - ints.color(color) == 6 },
            combined(colors, listOf(Comparison.Equal, Comparison.More)) { color, comp ->
                Verifier("$color $comp 1") { ints -> comp.check(ints.color(color), 1) }
            },
            combined(colors, comparisons) { color, comp ->// 40
                Verifier("$color $comp 3") { ints -> comp.check(ints.color(color), 3) }
            },
            combined(colors, comparisons) { color, comp ->
                Verifier("$color $comp 4") { ints -> comp.check(ints.color(color), 4) }
            },
            combined(colors, listOf(Comparison.Less, Comparison.More)) { color, comp -> // 42: color is the smallest or largest
                Verifier("$color is the most $comp") { ints -> comp.check(ints[color], ints[color.other1]) && comp.check(ints[color], ints[color.other2]) }
            },
            combined(colors.minus(Color.Blue), comparisons) { color, comp ->
                Verifier("Blue $comp $color") { ints -> comp.check(ints.blue, ints[color]) }
            },
            combined(colors.minus(Color.Yellow), comparisons) { color, comp ->
                Verifier("Yellow $comp $color") { ints -> comp.check(ints.yellow, ints[color]) }
            },
            combined(digitCountsExceptMax, listOf(1, 3)) { count, value ->
                Verifier("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(digitCountsExceptMax, listOf(3, 4)) { count, value ->
                Verifier("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(digitCountsExceptMax, listOf(1, 4)) { count, value ->
                Verifier("There are $count $value's") { ints -> ints.count { it == value } == count }
            },
            combined(colors.reversed(), comparisons) { excludeColor, comparison ->
                val colors = Color.entries.filter { it != excludeColor }
                check(colors.size == 2)
                val first = colors.first()
                val second = colors.last()
                Verifier("$first $comparison $second") { ints ->
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
