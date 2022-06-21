@file:OptIn(ExperimentalCoroutinesApi::class)
package net.zomis.games.dsl.context

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import net.zomis.games.api.GamesApi
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.context.HiddenValue
import net.zomis.games.dsl.ConsoleView
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.GamesImpl
import net.zomis.games.dsl.flow.GameFlowImpl
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ContextTest {

    data class EventClass(var value: Int)
    class Inner(model: Model, ctx: Context): Entity(ctx) {
        val sum by value { 0 }
            .changeOn(model.eventRun) { value + event.value }
        val dynamic by dynamicValue { sum * 2 }
    }
    enum class Booster(val func: (Int) -> Int): GameSerializable {
        RESET({ 0 }), HALF({ it / 2 }), DOUBLE({ it * 2 }), TEN_TIMES({ it * 10 });
        override fun serialize(): String = name
    }
    class Player(ctx: Context, val playerIndex: Int): Entity(ctx) {
        var value by value { 0 }.privateView(playerIndex) { it }.publicView { HiddenValue }
        val boosterEvent = event<Booster>()
        val boosters by cards<Booster>().setup { it.cards.addAll(Booster.values()); it }
            .privateView(playerIndex) { it.cards }.publicView { it.size }
        val used by cards<Booster>().on(boosterEvent) { value.cards.add(event) }.publicView { it.cards }
    }
    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        val eventRun = event<EventClass>()
        val players by playerComponent { Player(this.ctx, it) }
        var value by value { 0 }
        val inner by component { Inner(this@Model, this.ctx) }
        val globalAction = action<Model, Int>("global", Int::class) {
            precondition { true }
            options { 1..10 }
            perform {
                val event = EventClass(action.parameter)
                game.eventRun.invoke(this, event)
                game.value += event.value
                game.players[playerIndex].value += event.value
            }
        }
        val boostAction = actionSerializable<Model, Booster>("boost", Booster::class) {
            precondition { true }
            options { game.players[playerIndex].boosters.cards }
            requires { game.players[playerIndex].boosters.cards.contains(action.parameter) }
            perform {
                val player = game.players[playerIndex]
                player.boosterEvent.invoke(this, action.parameter)
                player.boosters.cards.remove(action.parameter)
                player.value = action.parameter.func.invoke(player.value)
                game.value = action.parameter.func.invoke(game.value)
            }
        }
    }
    val game = GamesApi.gameContext("Test", Model::class) {
        val startValue = config("startValue") { 42 }
        players(2..4)
        init { Model(ctx) }
        gameFlow {
            game.players.forEach { it.value = config(startValue) }
            loop {
                step("step") {
                    enableAction(game.globalAction)
                    enableAction(game.boostAction)
                }
            }
        }
    }

    @Test
    fun startConfig() = runTest {
        val entry = GamesImpl.game(game)
        val replayable = entry.replayable(3, entry.setup().configs().set("startValue", 100))
        replayable.game.start(this)
        replayable.await()
        Assertions.assertEquals(3, replayable.game.model.players.size)
        Assertions.assertTrue(replayable.game.model.players.all { it.value == 100 })
        replayable.game.stop()
    }

    private suspend fun init(coroutineScope: CoroutineScope): GameReplayableImpl<Model> {
        val entry = GamesImpl.game(game)
        val repl = entry.replayable(2, entry.setup().configs())
        repl.game.start(coroutineScope)
        check(repl.game is GameFlowImpl)
        repl.await()
        return repl
    }

    private suspend fun runAndView(coroutineScope: CoroutineScope): Pair<Map<String, Any?>, Model> {
        val replayable = init(coroutineScope)
        replayable.doGlobal(0, 10)
        replayable.doBoost(0, Booster.TEN_TIMES)

        val view = replayable.game.view(0) to replayable.game.model
        replayable.game.stop()
        return view
    }

    private suspend fun runAndViewPlayer(coroutineScope: CoroutineScope, viewer: Int): Pair<Model, Map<String, Any?>> {
        val replayable = init(coroutineScope)
        replayable.doGlobal(0, 10)
        replayable.doBoost(0, Booster.TEN_TIMES)
        val view = replayable.game.view(viewer)
        ConsoleView<Model>().showView(replayable.game, viewer)
        val playerViews = view["players"].let { it as List<Map<String, Any?>> }
        val result = replayable.game.model to playerViews[0]
        replayable.game.stop()
        return result
    }

    private suspend fun GameReplayableImpl<Model>.doGlobal(playerIndex: Int, value: Int) {
        this.performSerialized(playerIndex, "global", value)
    }

    private suspend fun GameReplayableImpl<Model>.doBoost(playerIndex: Int, value: Booster) {
        this.performSerialized(playerIndex, "boost", value.name)
    }

    @Test
    fun changeOnEvent() = runTest {
        val replayable = init(this)
        replayable.doGlobal(0, 10)
        Assertions.assertEquals(10, replayable.game.model.inner.sum)
        replayable.game.stop()
    }

    @Test
    fun onEvent() = runTest {
        val replayable = init(this)
        replayable.doBoost(0, Booster.RESET)
        Assertions.assertEquals(listOf(Booster.RESET), replayable.game.model.players[0].used.cards)
        replayable.game.stop()
    }

    @Test
    fun dynamicSum() = runTest {
        val view = runAndView(this).first
        val v = view["inner"] as Map<String, Any?>
        Assertions.assertEquals(10, v["sum"])
    }

    @Test
    fun dynamic() = runTest {
        val view = runAndView(this).first
        val v = view["inner"] as Map<String, Any?>
        Assertions.assertEquals(20, v["dynamic"])
    }

    @Test
    fun playerValue() = runTest {
        val (_, view) = runAndViewPlayer(this, 0)
        val (_, invisible) = runAndViewPlayer(this, 1)
        Assertions.assertEquals(520, view["value"])
        Assertions.assertFalse(invisible.containsKey("value"))
    }

    @Test
    fun playerEvent() = runTest {
        val (game, view) = runAndViewPlayer(this, 0)
        Assertions.assertFalse(view.containsKey(game.players[0]::boosterEvent.name))
    }

    @Test
    fun boosters() = runTest {
        val (_, view) = runAndViewPlayer(this, 0)
        val (_, sizeOnly) = runAndViewPlayer(this, 1)
        Assertions.assertEquals(Booster.values().toList().minus(Booster.TEN_TIMES), view["boosters"])
        Assertions.assertEquals(3, sizeOnly["boosters"])
    }

    @Test
    fun used() = runTest {
        val (_, view) = runAndViewPlayer(this, 0)
        Assertions.assertEquals(listOf(Booster.TEN_TIMES), view["used"])
    }

    @Test
    fun players() = runTest {
        val view = runAndView(this).first
        Assertions.assertEquals(2, (view["players"] as List<*>).size)
    }

    @Test
    fun value() = runTest {
        val (view, _) = runAndView(this)
        Assertions.assertEquals(100, view["value"])
    }

    @Test
    fun globalAction() = runTest {
        val (view, game) = runAndView(this)
        Assertions.assertFalse(view.containsKey(game::globalAction.name))
    }

    @Test
    fun boostAction() = runTest {
        val (view, game) = runAndView(this)
        Assertions.assertFalse(view.containsKey(game::boostAction.name))
    }

    @Test
    fun event() = runTest {
        val (view, game) = runAndView(this)
        Assertions.assertFalse(view.containsKey(game::eventRun.name))
    }

    @Test
    fun noStackOverflowView() = runTest {
        val view = runAndView(this).first
        jacksonObjectMapper().writeValueAsString(view)
    }

}
