package net.zomis.games.ecsmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class AdvancedGameTest {

    @Nested
    inner class ViewState {
        @Test
        fun `viewState properties should be observable`() = runTest {
            // receive updates every time view changes, don't send entire view unless explicitly requested
            val game = EcsGameImpl.withSpec(GameExamples.game).createGame(2) {
                listOf()
            }

            game.stateFor(game.root::value, 0)!!.test {
                assertThat(awaitItem()).isEqualTo(0)
                game.root.value = 42
                assertThat(awaitItem()).isEqualTo(42)
            }
        }

        @Test
        fun `viewState properties may be hidden from specific players`() {
            // e.g. card zones being private, things being face-down anywhere (including cards played), or players not knowing about color/value (Hanabi)
            val game = EcsGameImpl.withSpec(GameExamples.game).createGame(2) {
                listOf()
            }
            assertThat(game.stateFor(game.root::value, 1)).isNull()
        }

        @Test
        fun `if a card goes into a hidden zone, you won't know if you got the same card back later or not`() {
            TODO("Coup ambassador action + replacement card when you get challenged")
        }
    }

    @Nested
    inner class Randomness {
        @Test
        fun `with the right tool, randomness can be controlled`() {
            TODO("e.g. add a debugging thing to intercept and control all randomness, or regular replayability")
        }

        @Test
        fun `some ais may cheat and determine randomness`() {
            TODO("e.g. UR and any game that involves distributing cards and stuff (Memory, Coup...)")
        }

    }

    @Nested
    inner class AIs {
        @Test
        fun `normal ais may only see what players see`() {
            TODO("e.g. Minesweeper Flags")
        }

        @Test
        fun `some ais may cheat and know about hidden game state`() {
            TODO("e.g. Minesweeper Flags, Memory, Coup, Hanabi, Avalon, Skull...")
        }
    }

    @Nested
    inner class Copyable {
        @Test
        fun `game should be copyable`() {
            val a = CopyExample()
            val b = a.copy()
            a.value = 4
            assertThat(b.test.invoke()).isNotEqualTo(a.test.invoke())

            TODO("should this copy the exact gamestate, or just copy the *known* state from a person's perspective?")
            /*
            * reasons for copying:
            * 1. AI analyzing steps ahead (can use probabilities to replace cards in opponent hands)
            * 2. rules requiring that you can do something if the future allows it
            * 3. quicker support for undo
            */
        }

        @Test
        fun `undo should restore previously copied game`() {
            TODO("how should it work in multiplayer, like growth in Spirit Island when one player wants to undo?")
        }
    }

    @Test
    fun `games may support coming and going dynamically`() {
        TODO("e.g. Fluxx, Codenames")
    }

    @Test
    fun `for some actions, choices can be made in any order`() {
        TODO("e.g. Avalon: choose team first, mission later. Or Spirit Island push from X to Y piece Z")
    }

}
