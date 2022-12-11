package net.zomis.games.impl

import net.zomis.games.components.resources.ResourceMap

val splendorCards = listOf("""
        W,0,UGRB
        W,0,UGGRB
        W,0,WWWUB
        W,0,UUGGB
        W,0,UUBB
        W,0,RRB
        W,1,GGGG
        W,0,UUU
        U,0,WGRB
        U,0,WGRRB
        U,0,UGGGR
        U,0,WGGRR
        U,0,GGBB
        U,0,WBB
        U,1,RRRR
        U,0,BBB
        B,0,WUUGR
        B,0,WUGR
        B,0,GRRRB
        B,0,WWUUR
        B,0,WWGG
        B,0,GGR
        B,0,GGG
        B,1,UUUU
        R,0,WWUGB
        R,0,WUGB
        R,0,WRBBB
        R,0,WWRR
        R,0,UUG
        R,1,WWWW
        R,0,WWW
        R,0,WWGBB
        G,1,BBBB
        G,0,WURB
        G,0,WURBB
        G,0,WUUUG
        G,0,URRBB
        G,0,UURR
        G,0,WWU
        G,0,RRR
    """.trimIndent(),"""
        W,1,GGGRRBB
        W,1,WWUUURRR
        W,2,GRRRRBB
        W,2,RRRRRBBB
        W,2,RRRRR
        W,3,WWWWWW
        U,1,UUGGRRR
        U,1,UUGGGBBB
        U,2,WWWWWUUU
        U,2,WWRBBBB
        U,3,UUUUUU
        U,2,UUUUU
        B,1,WWWGGGBB
        B,2,UGGGGRR
        B,1,WWWUUGG
        B,2,GGGGGRRR
        B,2,WWWWW
        B,3,BBBBBB
        R,1,WWRRBBB
        R,1,UUURRBBB
        R,2,WUUUUGG
        R,2,WWWBBBBB
        R,2,BBBBB
        R,3,RRRRRR
        G,2,GGGGG
        G,2,UUUUUGGG
        G,3,GGGGGG
        G,1,WWUUUBB
        G,1,WWWGGRR
        G,2,WWWWUUB
    """.trimIndent(),
    """
        W,4,WWWRRRBBBBBB
        W,4,BBBBBBB
        W,5,WWWBBBBBBB
        W,3,UUUGGGRRRRRBBB
        U,5,WWWWWWWUUU
        U,4,WWWWWWUUUBBB
        U,3,WWWGGGRRRBBBBB
        U,4,WWWWWWW
        B,4,RRRRRRR
        B,4,GGGRRRRRRBBB
        B,5,RRRRRRRBBB
        B,3,WWWUUUGGGGGRRR
        R,4,GGGGGGG
        R,3,WWWUUUUUGGGBBB
        R,5,GGGGGGGRRR
        R,4,UUUGGGGGGRRR
        G,4,WWWUUUUUUGGG
        G,4,UUUUUUU
        G,5,UUUUUUUGGG
        G,3,WWWWWUUURRRBBB
    """.trimIndent()
)

fun splendorCardsFromMultilineCSV(level: Int, multilineString: String): List<SplendorCard> {
    return multilineString.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.map {
        splendorCardFromCSV(level, it)
    }
}

fun splendorCardFromCSV(level: Int, csv: String): SplendorCard {
    val split = csv.split(",").map { it.trim() }
    fun String.toMoney(): ResourceMap {
        return this.fold(ResourceMap.empty()) { acc, c -> acc + MoneyType.values().first { it.char == c }.toMoney(1) }
    }

    return SplendorCard(level, split[0].toMoney(), split[2].toMoney(), split[1].toInt())
}
