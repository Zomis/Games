package net.zomis.games.impl.grids

import net.zomis.games.components.Point
import net.zomis.games.components.grids.*

object CryptidData {

    private fun parseClue(text: String): Cryptid.Clue {
        val scrapStart = text.substringAfter(' ')
        val negative = scrapStart.startsWith("not")
        val positiveText = if (negative) scrapStart.substringAfter("not").trim() else scrapStart.trim()
        return when {
            positiveText.startsWith("on") -> {
                val m = Regex("on (\\S+) or (\\S+)").matchEntire(positiveText) ?: throw IllegalArgumentException(text)
                val a = Cryptid.Terrain.of(m.groups[1]!!.value)
                val b = Cryptid.Terrain.of(m.groups[2]!!.value)
                a.or(b).negative(negative)
            }
            positiveText.startsWith("within one") -> {
                val m = positiveText.substringAfter("of ")
                val terrain = Cryptid.Terrain.ofOrNull(m)
                val clue = terrain?.withinOne() ?: Cryptid.Animal.any()
                clue.negative(negative)
            }
            positiveText.startsWith("within two") -> {
                val m = positiveText.substringAfter("of ")
                when (m) {
                    "a standing stone" -> Cryptid.StructureType.Stone.clue().negative(negative)
                    "an abandoned shack" -> Cryptid.StructureType.Shack.clue().negative(negative)
                    "cougar territory" -> Cryptid.Animal.Cougar.clue().negative(negative)
                    "bear territory" -> Cryptid.Animal.Bear.clue().negative(negative)
                    else -> throw IllegalArgumentException(text)
                }
            }
            positiveText.startsWith("within three") -> {
                val m = Regex("within three spaces of a (\\S+) structure")
                    .matchEntire(positiveText) ?: throw IllegalArgumentException(text)
                val colorText = m.groups[1]!!.value
                val color = Cryptid.StructureColor.values().firstOrNull { it.name.contentEquals(colorText, ignoreCase = true) } ?: throw IllegalArgumentException(colorText)
                color.clue().negative(negative)
            }
            else -> throw IllegalArgumentException(text)
        }
    }

    private fun setupStructures(structureString: String): Map<Point, Cryptid.StructureColor> {
        val colors = listOf(
            Cryptid.StructureColor.Blue,
            Cryptid.StructureColor.Green,
            Cryptid.StructureColor.White,
            Cryptid.StructureColor.Black,
        )
        return structureString.split(",").map {
            if (it.length <= 2) Point(it.first().digitToInt(), it.last().digitToInt())
            else Point(it.slice(0..1).toInt(), it.last().digitToInt())
        }.withIndex().associate {
            it.value to colors[it.index]
        }
    }

    class StructureView(val structure: Cryptid.Structure, val pos: Point)
    data class MapData(
        val boards: List<Int>,
        val flipped: List<Boolean>,
        val structures: List<StructureView>,
    ) {
        val advanced: Boolean get() = structures.any { it.structure.color == Cryptid.StructureColor.Black }

        fun createGrid(): Grid<Cryptid.CryptidField> {
            val end = Point(5, 2)
            return GridImpl(12, 9) { x, y ->
                val point = Point(x, y)
                val pointAtSection = Point(x % 6, y % 3)
                val section = Point(x / 6, y / 3)
                val boardStringIndex = section.x * 3 + section.y
                val boardId = boards[boardStringIndex] - 1
                val board = CryptidData.boards[boardId]
                val flipped = this.flipped[boardStringIndex]
                val p = if (flipped) end - pointAtSection else pointAtSection
                board.point(p).value.copy(
                    structure = structures.firstOrNull { it.pos == point }?.structure,
                    discs = mutableSetOf(),
                    cube = null
                )
            }
        }
        fun createHexGrid(): HexGrid<Cryptid.CryptidField> {
            val grid = createGrid()
            return HexGrid(12, 9, HexGridAdjust.OddQ) { x, y ->
                grid.get(x, y)
            }
        }
    }

    fun map(block: String, players: Int): Cryptid.Model {
        val lines = block.split("\n")
        val setupLine = lines[0].split(";").drop(7)
        val lineOffset = if (players == 2) 1 else players - 3
        val clueIds = lines[lineOffset].split(";").map { it.toIntOrNull() }
        val clues = listOf(alpha, beta, gamma, delta, epsilon).mapIndexed { index, book ->
            val clueId = clueIds[index] ?: return@mapIndexed null
            book.lines()[clueId - 1]
        }.filterNotNull().map(::parseClue)

        val hintId = clueIds[5]!!.toInt()
        val hint = hints.lines()[hintId - 1]

        val boards = setupLine[0]
        val numberInTopLeft = setupLine[1]
        val triangles = setupStructures(setupLine[2])
        val stones = setupStructures(setupLine[3])
        val mapData = MapData(
            boards.map { it.digitToInt() }, numberInTopLeft.map { it == '0' },
            triangles.map { StructureView(it.value.shack(), it.key) } +
                    stones.map { StructureView(it.value.stone(), it.key) },
        )
        val grid = mapData.createHexGrid()
        return Cryptid.Model(mapData, grid, clues, hint)
    }

    fun randomMap(advancedMode: Boolean, players: Int): Cryptid.Model {
        val multiblocks = if (advancedMode) advanced else maps
        val blocks = multiblocks.trim().split("\n\n")
        return map(blocks.random(), players)
    }

    private fun makeHexGrid(text: String, animal: Cryptid.Animal, point: Set<Point>): Grid<Cryptid.CryptidField> {
        return GridImpl(6, 3) { x, y ->
            val p = Point(x, y)
            val terrain = when (val ch = text[x * 4 + y]) {
                'w' -> Cryptid.Terrain.Water
                'd' -> Cryptid.Terrain.Desert
                'f' -> Cryptid.Terrain.Forest
                'm' -> Cryptid.Terrain.Mountain
                's' -> Cryptid.Terrain.Swamp
                else -> throw IllegalArgumentException(ch.toString())
            }
            Cryptid.CryptidField(terrain, animal.takeIf { point.contains(p) }, null)
        }
    }

    private val boards = listOf(
        // terrain top->bottom, left->right
        makeHexGrid("wss,wss,wwd,wdd,ffd,fff", Cryptid.Animal.Bear, setOf(Point(3, 2), Point(4, 2))),
        makeHexGrid("sss,fsm,ffm,fdm,fdm,fdd", Cryptid.Animal.Cougar, setOf(Point(0, 0), Point(1, 0), Point(2, 0))),
        makeHexGrid("ssm,ssm,ffm,fmm,fww,www", Cryptid.Animal.Cougar, setOf(Point(0, 1), Point(0, 2), Point(1, 1))),
        makeHexGrid("ddd,ddd,mmd,mwf,mwf,mwf", Cryptid.Animal.Cougar, setOf(Point(5, 1), Point(5, 2))),
        makeHexGrid("ssd,sdd,sdw,mww,mmw,mmw", Cryptid.Animal.Bear, setOf(Point(4, 2), Point(5, 1), Point(5, 2))),
        makeHexGrid("dmm,dmw,ssw,ssw,sfw,fff", Cryptid.Animal.Bear, setOf(Point(0, 0), Point(0, 1))),
    )

    private val advanced = """
13;65;65;;;8;;164253;111110;117,92,02,91;81,27,67,105
77;51;84;64;;79
24;91;68;72;41;11

37;34;39;;;78;;215346;110111;20,108,48,98;114,01,28,117
92;42;69;49;;75
35;56;69;49;31;26

18/85/13///62//234615/111100/63,17,107,68/117,82,35,75
41/76/2/51//23
35/57/14/89/81/61

13/83//92//47//236541/110011/94,83,06,81/30,85,107,118
85/2/78/13//29
44/73/28/52/81/18

23/85//67//32//253164/101010/92,21,118,10/77,33,28,78
78/81/68/53//50
45/59/24/26/13/48

17/16///73/52//314526/111100/90,13,108,66/05,86,38,94
87/29/88//85/35
7/44/38/31/7/11

37/74///13/25//365421/100100/111,25,71,98/58,62,107,13
52/46/92//83/44
30/61/18/89/53/17

81/78//29//32//364152/111100/87,28,113,67/71,00,37,106
31/16/7/69//11
38/83/78/42/35/43

58/53//45//74//213654/101000/53,82,34,80/58,116,21,08
70/47/28/82//19
5/44/19/46/65/34

54/67///35/58//342516/110000/02,52,67,17/97,07,38,82
39/39/36//25/66
21/75/14/95/30/45

20/85///27/12//312456/101010/102,52,47,24/57,54,50,72
90/60/24//76/3
29/80/44/20/41/31

67//25/95//32//415236/111110/06,72,94,01/107,20,114,63
86/33/50//73/22
16/62/11/53/61/71

36//62/92//77//415236/100010/74,111,17,07/94,115,107,54
71/35/2//75/27
5/49/53/91/57/72

89//62/67//75//436152/110000/47,30,61,76/52,71,78,12
39/5/89//82/8
51/92/68/90/13/78

54//46/68//20//461325/010111/60,68,25,91/118,75,92,46
86/13/88//11/65
19/94/56/75/89/38

42//49//86/26//541632/101100/26,36,37,80/23,62,54,86
6/59//50/19/31
8/49/81/89/70/61

78//1//14/64//543621/101111/17,68,83,95/92,40,13,56
18/81//58/93/62
24/1/44/72/70/24

90//11//72/50//632154/110101/58,62,118,101/83,102,47,31
23/95/21/63/49
3/89/68/77/17/41

79//11//30/49//543612/101000/55,23,60,24/105,15,88,77
62/78//87/83/7
65/89/53/45/57/40

94///83/1/66//632541/110111/22,16,60,05/46,87,93,40
43/81//3/79/70
54/68/74/62/74/34

/64/67/43//6//254631/0010000/16,92,04,06/77,54,61,47
16//45/28/16/3
80/60/35/16/49/68

/11/77/95//15//246531/011001/53,92,74,44/83,50,113,58
74//47/38/53/30
91/35/48/32/43/8

/5/94/63//59//254163/010010/75,110,114,54/101,56,14,63
12//35/54/33/12
33/25/75/23/69

/40/54/45//5//145623/001001/82,28,118,78/15,62,53,70/
84//86/54/18/9
22/26/73/48/11/13

/10/24/21//59//163245/011010/72,60,73,111/38,64,61,66
11//35/2/20/55
48/26/87/32/56/76

/1/95/36//17//154263/010010/80,47,32,73/96,45,06,24
17//42/27/70/45
84/26/50/50/28/10

/68//5/79/51//453216/000011/42,41,23,74/46,14,40,24
/62/1/78/65/48
91/87/88/63/49/68

/46//95/75/52//423165/000000/71,42,112,13/84,28,12,37
/48/39/18/70/37
87/30/64/14/68/63

/6//22/92/80//452163/010010/02,116,67,108/91,54,47,14
/72/4/26/2/44
1/26/66/32/83/35

/40/20//59/41//461325/010111/60,68,25,91/118,75,92,46
21//45/33/14/58
49/12/71/8/94/43

/22/87//64/64//456123/011100/27,51,24,55/84,42,57,71
26//42/17/60/59
42/87/36/58/40/53

/11/42//23/28//324615/010001/32,88,74,03/33,54,108,37
7//87/87/3/40
1/36/95/36/94/60

/22/72//84/72//361452/001010/57,74,12,70/92,71,112,87
56//52/6/29/49
32/50/35/87/56/69

/32//63/83/56//532416/011110/64,74,37,03/98,65,21,77
/94/58/59/6/39
94/646/74/6/79/39

/21//25/73/17//421536/011000/111,65,21,47/101,67,63,52
/75/78/80/9/45
73/15/70/63/96/54
""".trimIndent()

    // boards top->bottom x2, dots up-left, triangles blue green white black, structures blue green white black. then clues for alpha, beta, gamma, delta, epsilon.
    private val maps = """
69;;31;72;;74;;461352;110100;13,47,67;55,46,64
69;2;80;;57;15;;;;;
3;80;20;84;16;1;;;;;

;17;35;;76;1;;324165;001011;11-2,43,15;86,05,88
13;;54;27;45;24;;;;;
80;6;80;83;52;77;;;;;

17;45;84;;;7;;265314;111100;41,06,35;21,11-4,18
68;5;31;77;;72;;;;;
25;80;39;13;34;9;;;;;

13;73;73;;;67;;246513;101101;73,53,108;18,66,22
80;36;27;20;;65;;;;;
26;54;18;53;9;64;;;;;

42;;1;;66;53;;534261;111001;81,10-7,10-8;36,40,30
19;89;;30;89;29;;;;;
13;62;85;46;34;51;;;;;

91;;;91;9;33;;653412;100110;58,51,38;45,05,22
44;80;;58;42;58;;;;;
66;6;2;4;2;42;;;;;

28;;;85;8;25;;162534;010101;48,96,32;10,110,52
2;43;;52;42;74;;;;;
17;19;73;50;11;15;;;;;

;17;71;;23;29;;314256;011111;96,72,70;38,07,48
3;;20;30;17;38;;;;;
52;76;36;20;67;78;;;;;

;45;;43;51;47;;451326;010010;16,11-4,24;10,64,55
;42;23;91;41;42;;;;;
42;46;96;34;11;34;;;;;

17;49;;56;;7;;243156;100010;57,10-3,72;41,17,11-3
95;6;6;40;;53;;;;;
66;80;39;84;13;51;;;;;

1;62;;;21;55;;465123;110100;27,51,66;55,85,46
72;17;54;;28;27;;;;;
56;79;44;74;9;76;;;;;

91;;12;59;;2;;436521;111001;11-6,10-2,46;70,54,11
27;25;73;;92;9;;;;;
12;61;12;74;29;15;;;;;

17;;11;;66;16;;543216;101100;38,66,93;45,10-2,20
50;65;;50;50;69;;;;;
21;67;43;7;30;23;;;;;

2;79;;;28;37;;315426;100010;63,62,97;80,11-1,75
84;1;6;;67;14;;;;;
66;71;68;73;29;6;;;;;

17;86;6;;;21;;253146;101111;60,52,10-3;68,97,10-4
88;8;1;13;;24;;;;;
26;34;27;85;86;18;;;;;

55;;;52;45;28;;652341;101110;02,43,10-2;76,11-5,17
29;65;;2;38;44;;;;;
66;35;94;19;92;54;;;;;

42;;;13;2;30;;653142;100111;41,52,01;11-3,04,66
12;92;;26;28;48;;;;;
95;64;63;11;67;4;;;;;

61;;;49;21;20;;632154;110011;84,55,25;54,11-2,10-5
2;71;;45;51;43;;;;;
66;67;50;14;87;2;;;;;

30;45;;56;;42;;213645;110000;98,78,95;10-3,27,11-1
28;76;20;77;;38;;;;;
26;71;31;46;86;40;;;;;
""".trimIndent()

    private val hints = """
        1. no within two spaces clues
        2. no within three spaces clues
        3. no within two spaces clues
        4. no clues that mention any animal territory
        5. no within three spaces clues
        6. no within two spaces clues
        7. no terrain or terrain clues
        8. no within one space clues
        9. no within three spaces clues
        10. no clues that mention desert
        11. no clues that mention forest
        12. no within two spaces clues
        13. no within two spaces clues
        14. no within two spaces clues
        15. no within three spaces clues
        16. no within one space clues
        17. no within two spaces clues
        18. no clues that mention mountain
        19. no clues that mention swamp
        20. no within three spaces clues
        21. no within one space clues
        22. no clues that mention mountain
        23. no within three spaces clues
        24. no within one space clues
        25. no within two spaces clues
        26. no within one space clues
        27. no clues that mention forest
        28. no within one space clues
        29. no within three spaces clues
        30. no clues that mention water
        31. no clues that mention swamp
        32. no terrain or terrain clues
        33. no within one space clues
        34. no within two spaces clues
        35. no within one space clues
        36. no terrain or terrain clues
        37. no within one space clues
        38. no within three spaces clues
        39. no clues that mention water
        40. no within three spaces clues
        41. no within three spaces clues
        42. no within two spaces clues
        43. no within three spaces clues
        44. no within three spaces clues
        45. no within one space clues
        46. no clues that mention water
        47. no within two spaces clues
        48. no within two spaces clues
        49. no terrain or terrain clues
        50. no within two spaces clues
        51. no within two spaces clues
        52. no clues that mention any type of terrain
        53. no within two spaces clues
        54. no within three spaces clues
        55. no within one space clues
        56. no within three spaces clues
        57. no clues that mention desert
        58. no within three spaces clues
        59. no terrain or terrain clues
        60. no within two spaces clues
        61. no within two spaces clues
        62. no within two spaces clues
        63. no clues that mention forest
        64. no within two spaces clues
        65. no within two spaces clues
        66. no within three spaces clues
        67. no within two spaces clues
        68. no clues that mention swamp
        69. no clues that mention mountain
        70. no within three spaces clues
        71. no clues that mention desert
        72. no within two spaces clues
        73. no within three spaces clues
        74. no within three spaces clues
        75. no within three spaces clues
        76. no within one space clues
        77. no within two spaces clues
        78. no within two spaces clues
        79. no within three spaces clues
        80. no terrain or terrain clues
    """.trimIndent()

    private val alpha = """
        1 within two spaces of bear territory
        2 on water or mountains
        3 within one space of mountain
        4 not on water or mountains
        5 not on forest or water
        6 not within two spaces of bear territory
        7 not on desert or water
        8 not on forest or swamp
        9 on swamp or mountain
        10 on forest or water
        11 not within two spaces of a standing stone
        12 within one space of forest
        13 within three spaces of a white structure
        14 not within one space of water
        15 not on forest or mountain
        16 not within one space of either animal territory
        17 within two spaces of cougar territory
        18 within three spaces of a blue structure
        19 within one space of swamp
        20 within three spaces of a green structure
        21 within two spaces of an abandoned shack
        22 not on desert or water
        23 not within two spaces of cougar territory
        24 not within two spaces of an abandoned shack
        25 on desert or water
        26 within one space of water
        27 on desert or water
        28 within one space of swamp
        29 on forest or mountain
        30 within one space of forest
        31 within three spaces of a black structure
        32 not on forest or swamp
        33 within three spaces of a green structure
        34 on desert or swamp
        35 not on swamp or mountain
        36 within three spaces of a blue structure
        37 not within one space of mountain
        38 not on forest or desert
        39 not within two spaces of bear territory
        40 not on swamp or mountain
        41 not within two spaces of an abandoned shack
        42 within three spaces of a white structure
        43 not within one space of desert
        44 on water or swamp
        45 not on desert or mountain
        46 within two spaces of a standing stone
        47 not on water or swamp
        48 within two spaces of cougar territory
        49 not within one space of either animal territory
        50 within one space of either animal territory
        51 not on desert or swamp
        52 within one space of mountain
        53 not within three spaces of a white structure
        54 on forest or swamp
        55 within two spaces of a standing stone
        56 on desert or mountain
        57 on forest or desert
        58 not within one space of swamp
        59 not within three spaces of a black structure
        60 not on water or swamp
        61 on desert or swamp
        62 not within three spaces of a green structure
        63 not on desert or mountain
        64 not on forest or mountain
        65 within one space of desert
        66 on desert or mountain
        67 not within one space of desert
        68 on forest or swamp
        69 within one space of desert
        70 not within two spaces of a standing stone
        71 not on water or mountains
        72 on swamp or mountain
        73 not on forest or water
        74 not within one space of forest
        75 not on forest or desert
        76 not within three spaces of a white structure
        77 on forest or desert
        78 not within one space of forest
        79 not within one space of mountain
        80 on forest or mountain
        81 not within three spaces of a blue structure
        82 not on desert or swamp
        83 within two spaces of an abandoned shack
        84 within one space of either animal territory
        85 within one space of water
        86 not within one space of water
        87 not within three spaces of a blue structure
        88 on forest or water
        89 not within one space of swamp
        90 not within three spaces of a black structure
        91 within two spaces of bear territory
        92 on water or mountains
        93 within three spaces of a black structure
        94 not within two spaces of cougar territory
        95 on water or swamp
        96 not within three spaces of a green structure
    """.trimIndent()

    private val beta = """
        1 on desert or swamp
        2 within two spaces of cougar territory
        3 within three spaces of a blue structure
        4 not on forest or swamp
        5 within three spaces of a green structure
        6 within one space of desert
        7 not on desert or swamp
        8 on forest or swamp
        9 not on water or swamp
        10 not within three spaces of a black structure
        11 within two spaces of an abandoned shack
        12 not within one space of desert
        13 on forest or water
        14 not within three spaces of a white structure
        15 not within one space of either animal territory
        16 within two spaces of bear territory
        17 within one space of water
        18 not on swamp or mountain
        19 within one space of mountain
        20 not within three spaces of a black structure
        21 not within one space of swamp
        22 within three spaces of a black structure
        23 not within three spaces of a green structure
        24 not on forest or desert
        25 within one space of swamp
        26 not on forest or mountain
        27 not within two spaces of bear territory
        28 on forest or mountain
        29 on swamp or mountain
        30 not within two spaces of cougar territory
        31 not within two spaces of a standing stone
        32 on water or swamp
        33 on desert or water
        34 within three spaces of a blue structure
        35 within two spaces of a standing stone
        36 on water or mountains
        37 not on forest or swamp
        38 not on water or swamp
        39 not within two spaces of an abandoned shack
        40 not within one space of mountain
        41 not on desert or mountain
        42 within one space of forest
        43 within two spaces of bear territory
        44 not within one space of either animal territory
        45 within three spaces of a green structure
        46 within one space of either animal territory
        47 on desert or mountain
        48 not within two spaces of bear territory
        49 within one space of mountain
        50 not on desert or water
        51 not within one space of forest
        52 not within two spaces of an abandoned shack
        53 within two spaces of an abandoned shack
        54 within one space of swamp
        55 not within one space of desert
        56 not on desert or swamp
        57 not on desert or mountain
        58 within three spaces of a black structure
        59 not within one space of forest
        60 not within three spaces of a blue structure
        61 within one space of water
        62 on forest or water
        63 not on desert or water
        64 within three spaces of a white structure
        65 within two spaces of a standing stone
        66 within two spaces of cougar territory
        67 within one space of either animal territory
        68 not within one space of water
        69 not within three spaces of a blue structure
        70 not on forest or desert
        71 within one space of forest
        72 not on forest or mountain
        73 on desert or swamp
        74 on water or mountains
        75 not on swamp or mountain
        76 on forest or desert
        77 on desert or water
        78 not within three spaces of a white structure
        79 within three spaces of a white structure
        80 within one space of desert
        81 not within one space of mountain
        82 on forest or desert
        83 on forest or mountain
        84 not on water or mountains
        85 not within one space of water
        86 on water or swamp
        87 not on forest or water
        88 not within two spaces of cougar territory
        89 on desert or mountain
        90 on forest or swamp
        91 not on water or mountains
        92 on swamp or mountain
        93 not on forest or water
        94 not within two spaces of a standing stone
        95 not within three spaces of a green structure
        96 not within one space of swamp
    """.trimIndent()

    private val gamma = """
        1 within three spaces of a blue structure
        2 within one space of water
        3 not on water or mountains
        4 on forest or swamp
        5 not on desert or water
        6 within three spaces of a blue structure
        7 not within one space of desert
        8 not on forest or swamp
        9 not on forest or water
        10 not within two spaces of a standing stone
        11 within three spaces of a green structure
        12 within two spaces of a standing stone
        13 on forest or water
        14 not within three spaces of a black structure
        15 not on forest or swamp
        16 not within two spaces of a standing stone
        17 not within one space of mountain
        18 on desert or mountain
        19 not on desert or swamp
        20 on desert or swamp
        21 on forest or desert
        22 not on forest or desert
        23 within one space of mountain
        24 not within one space of swamp
        25 within three spaces of a black structure
        26 not within three spaces of a blue structure
        27 within one space of forest
        28 within three spaces of a white structure
        29 within one space of either animal territory
        30 not within three spaces of a white structure
        31 on water or swamp
        32 on swamp or mountain
        33 not on desert or swamp
        34 not within three spaces of a green structure
        35 within three spaces of a green structure
        36 within one space of forest
        37 not on desert or water
        38 not within two spaces of bear territory
        39 on water or mountains
        40 within three spaces of a white structure
        41 on water or mountains
        42 not within three spaces of a blue structure
        43 on forest or mountain
        44 within two spaces of bear territory
        45 not within one space of swamp
        46 not within one space of forest
        47 within three spaces of a black structure
        48 on desert or swamp
        49 within two spaces of cougar territory
        50 within two spaces of cougar territory
        51 not on swamp or mountain
        52 not within two spaces of bear territory
        53 within one space of either animal territory
        54 within two spaces of bear territory
        55 not on forest or mountain
        56 not on water or swamp
        57 not on water or mountains
        58 not within one space of either animal territory
        59 not on forest or water
        60 not on desert or mountain
        61 not on forest or desert
        62 not within one space of water
        63 within two spaces of a standing stone
        64 not on swamp or mountain
        65 within two spaces of an abandoned shack
        66 not on water or swamp
        67 not within one space of mountain
        68 within one space of desert
        69 not within two spaces of cougar territory
        70 on water or swamp
        71 within two spaces of an abandoned shack
        72 not within three spaces of a green structure
        73 within one space of water
        74 on forest or desert
        75 not within one space of either animal territory
        76 not within one space of forest
        77 on forest or mountain
        78 not within two spaces of an abandoned shack
        79 not on forest or mountain
        80 on desert or mountain
        81 within one space of desert
        82 not within one space of water
        83 not within two spaces of an abandoned shack
        84 within one space of swamp
        85 on desert or water
        86 not within two spaces of cougar territory
        87 on forest or water
        88 not within three spaces of a black structure
        89 on forest or swamp
        90 not on desert or mountain
        91 not within three spaces of a white structure
        92 on desert or water
        93 within one space of mountain
        94 within one space of swamp
        95 not within one space of desert
        96 on swamp or mountain
    """.trimIndent()

    private val delta = """
        1 not on swamp or mountain
        2 within two spaces of bear territory
        3 not within two spaces of cougar territory
        4 within one space of mountain
        5 not on desert or mountain
        6 not within three spaces of a black structure
        7 on forest or desert
        8 not on forest or water
        9 not within two spaces of bear territory
        10 not within one space of water
        11 on desert or water
        12 not on desert or swamp
        13 within two spaces of bear territory
        14 within one space of swamp
        15 within three spaces of a green structure
        16 not within two spaces of a standing stone
        17 within two spaces of an abandoned shack
        18 not within two spaces of a standing stone
        19 on forest or mountain
        20 within one space of water
        21 not within one space of desert
        22 within three spaces of a black structure
        23 not on water or mountains
        24 on water or mountains
        25 on swamp or mountain
        26 within one space of desert
        27 on desert or swamp
        28 not on forest or desert
        29 within three spaces of a black structure
        30 within one space of forest
        31 not on swamp or mountain
        32 not within three spaces of a blue structure
        33 not on desert or water
        34 on desert or mountain
        35 on water or swamp
        36 within three spaces of a white structure
        37 not on water or mountains
        38 not within two spaces of an abandoned shack
        39 on forest or swamp
        40 on swamp or mountain
        41 not on water or swamp
        42 within one space of swamp
        43 within three spaces of a blue structure
        44 not within one space of mountain
        45 within two spaces of a standing stone
        46 within one space of desert
        47 within one space of mountain
        48 not within three spaces of a white structure
        49 within two spaces of a standing stone
        50 on forest or water
        51 on desert or swamp
        52 within two spaces of an abandoned shack
        53 on water or mountains
        54 not on forest or mountain
        55 not within three spaces of a blue structure
        56 within three spaces of a white structure
        57 not on water or swamp
        58 within one space of either animal territory
        59 on forest or swamp
        60 not on forest or swamp
        61 not within one space of water
        62 not within one space of either animal territory
        63 within two spaces of cougar territory
        64 not within two spaces of bear territory
        65 not on forest or water
        66 not within three spaces of a black structure
        67 not within one space of desert
        68 not within two spaces of an abandoned shack
        69 on water or swamp
        70 not within three spaces of a green structure
        71 not on forest or swamp
        72 within two spaces of cougar territory
        73 within three spaces of a blue structure
        74 on forest or mountain
        75 not on forest or mountain
        76 not on desert or water
        77 within one space of forest
        78 on desert or water
        79 not within one space of forest
        80 not on forest or desert
        81 on desert or mountain
        82 not within one space of either animal territory
        83 within one space of either animal territory
        84 within one space of water
        85 within three spaces of a green structure
        86 on forest or water
        87 not within one space of swamp
        88 not within one space of mountain
        89 not within three spaces of a green structure
        90 not within three spaces of a white structure
        91 on forest or desert
        92 not within one space of forest
        93 not on desert or mountain
        94 not within one space of swamp
        95 not within two spaces of cougar territory
        96 not on desert or swamp
    """.trimIndent()

    private val epsilon = """
        1 on forest or swamp
        2 within one space of forest
        3 not within one space of either animal territory
        4 not on forest or mountain
        5 not on forest or swamp
        6 not within three spaces of a blue structure
        7 within three spaces of a black structure
        8 on desert or water
        9 within three spaces of a green structure
        10 not within two spaces of an abandoned shack
        11 within one space of desert
        12 not within two spaces of bear territory
        13 within one space of either animal territory
        14 not within one space of mountain
        15 not within one space of either animal territory
        16 within three spaces of a white structure
        17 within one space of water
        18 not within one space of desert
        19 within three spaces of a black structure
        20 not on forest or desert
        21 within two spaces of cougar territory
        22 within one space of water
        23 within two spaces of cougar territory
        24 not on desert or swamp
        25 not on forest or desert
        26 on forest or mountain
        27 on forest or water
        28 within three spaces of a green structure
        29 within one space of swamp
        30 within two spaces of a standing stone
        31 not on water or swamp
        32 not on desert or mountain
        33 on swamp or mountain
        34 within one space of mountain
        35 not within two spaces of cougar territory
        36 not on desert or water
        37 on forest or water
        38 on swamp or mountain
        39 not on water or swamp
        40 not on forest or mountain
        41 within three spaces of a blue structure
        42 within one space of forest
        43 not within three spaces of a white structure
        44 on water or mountains
        45 on desert or mountain
        46 not on desert or water
        47 not on desert or swamp
        48 on forest or swamp
        49 not within one space of mountain
        50 within three spaces of a blue structure
        51 on forest or mountain
        52 within three spaces of a white structure
        53 not within one space of swamp
        54 not on swamp or mountain
        55 on desert or swamp
        56 not within two spaces of bear territory
        57 within one space of swamp
        58 not within one space of water
        59 not within two spaces of an abandoned shack
        60 not within two spaces of a standing stone
        61 within two spaces of an abandoned shack
        62 on desert or mountain
        63 not within three spaces of a white structure
        64 not within one space of water
        65 not within three spaces of a green structure
        66 on water or swamp
        67 within one space of desert
        68 within one space of mountain
        69 not on swamp or mountain
        70 not within three spaces of a black structure
        71 not on forest or water
        72 on forest or desert
        73 not within three spaces of a black structure
        74 not within three spaces of a green structure
        75 not within three spaces of a blue structure
        76 on desert or swamp
        77 not on forest or swamp
        78 on desert or water
        79 not within one space of swamp
        80 not on water or mountains
        81 not within one space of forest
        82 within two spaces of an abandoned shack
        83 within two spaces of a standing stone
        84 on water or mountains
        85 not within two spaces of cougar territory
        86 within two spaces of bear territory
        87 on forest or desert
        88 not on water or mountains
        89 within two spaces of bear territory
        90 not on desert or mountain
        91 not within one space of forest
        92 within one space of either animal territory
        93 not on forest or water
        94 not within two spaces of a standing stone
        95 on water or swamp
        96 not within one space of desert
    """.trimIndent()

}