package net.zomis.games.cards

import net.zomis.games.components.SemiKnownCardZone
import net.zomis.games.dsl.impl.ReplayState
import net.zomis.games.dsl.impl.StateKeeper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SemiKnownCardZoneTest {

    @Nested
    @DisplayName("given cards in range 1..10")
    inner class GivenTenCards {
        val zone = SemiKnownCardZone((1..10).toList()) { "value-$it" }

        @Test
        fun `then all the cards should be there in order`() {
            Assertions.assertEquals((0 until 10).toList(), zone.knownCards().map { it.index })
            Assertions.assertEquals((1..10).toList(), zone.knownCards().map { it.value })
        }

        @Test
        fun `then size should be 10`() {
            Assertions.assertEquals(10, zone.size)
            Assertions.assertEquals(0..9, zone.indices)
        }

        @Test
        fun `then selecting top 11 should throw an exception`() {
            Assertions.assertThrows(IllegalArgumentException::class.java) {
                val replayable = ReplayState(StateKeeper())
                zone.top(replayable, "ignore", zone.size + 1)
            }
        }

        @Nested
        @DisplayName("when cards are shuffled")
        inner class WhenShuffled {
            @BeforeEach
            fun setup() {
                zone.shuffle()
            }

            @Test
            fun `nothing is known`() {
                Assertions.assertTrue(zone.knownCards().isEmpty())
            }

            @Nested
            @DisplayName("and when top card is chosen")
            inner class AndWhenTopCardChosen {
                @Test
                fun `then top card should be random and remembered`() {
                    val replayable = ReplayState(StateKeeper())
                    val topCard = zone.top(replayable, "top", 1)
                    replayable.stateKeeper.replayMode = true
                    Assertions.assertEquals(topCard.toList(), zone.top(replayable, "top", 1).toList())
                    Assertions.assertEquals(IndexedValue(0, topCard.single().card), zone.knownCards().single())
                    val remembered = replayable.stateKeeper.lastMoveState().getValue("top") as List<String>
                    Assertions.assertEquals("value-" + topCard.single().card, remembered.single())
                }
            }

            @Test
            fun `then inserting new cards should make them known`() {
                zone.insertAt(DeckDirection.TOP, 5, listOf(11, 12, 13))
                Assertions.assertEquals(
                    listOf(
                        IndexedValue(5, 11),
                        IndexedValue(6, 12),
                        IndexedValue(7, 13),
                    ),
                    zone.knownCards()
                )
            }

            @Test
            fun `then inserting new cards 2 steps from bottom should make them known`() {
                zone.insertAt(DeckDirection.BOTTOM, 2, listOf(11, 12, 13))
                Assertions.assertEquals(13, zone.size)
                Assertions.assertEquals(
                    listOf(
                        IndexedValue(8, 11),
                        IndexedValue(9, 12),
                        IndexedValue(10, 13),
                    ),
                    zone.knownCards()
                )
            }

            @Nested
            @DisplayName("and when top card is moved")
            inner class AndWhenTopCardMoved {
                private val replayable = ReplayState(StateKeeper())
                private val otherZone = SemiKnownCardZone<Int>(emptyList()) { "other-$it" }
                private val topCard: Int = zone.top(replayable, "top", 1).first().also { it.moveTo(otherZone) }.card

                @Test
                fun `then size should be 9`() {
                    Assertions.assertEquals(9, zone.size)
                }

                @Test
                fun `then card should no longer exist in original zone`() {
                    zone.top(replayable, "ignore", 9).none { it.card == topCard }
                }

                @Test
                fun `then card should exist and be known in new zone`() {
                    Assertions.assertEquals(topCard, otherZone.knownCards().single().value)
                }
            }

            @Nested
            @DisplayName("and when top card is chosen with replay data 'value-4'")
            inner class ReplayTop {
                private val replayable = ReplayState(StateKeeper())

                @BeforeEach
                fun setup() {
                    replayable.stateKeeper.setState(mapOf("top" to listOf("value-4")))
                    replayable.stateKeeper.replayMode = true
                }

                @Test
                fun `then top card should be 4`() {
                    val topCard = zone.top(replayable, "top", 1).toList()
                    Assertions.assertEquals(listOf(Card(zone, 0, 4)), topCard)
                    Assertions.assertEquals(IndexedValue(0, 4), zone.knownCards().single())
                }
            }
        }
    }

/*
moving top card to another card zone should remove it from this and add it to the other
shuffling and inserting a known value somewhere should make it known
*/
}
