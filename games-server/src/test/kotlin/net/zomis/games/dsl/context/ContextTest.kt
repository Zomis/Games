package net.zomis.games.dsl.context

import kotlinx.coroutines.runBlocking
import net.zomis.games.api.GamesApi
import net.zomis.games.context.Context
import net.zomis.games.context.ContextHolder
import net.zomis.games.context.Entity
import net.zomis.games.dsl.ConsoleView
import net.zomis.games.dsl.GameReplayableImpl
import net.zomis.games.dsl.GameSerializable
import net.zomis.games.dsl.GamesImpl
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
        var value by value { 0 }.privateView(playerIndex) { it }
        val boosterEvent by event(Booster::class) // TODO: Support generic types
        val boosters by cards<Booster>().setup { it.cards.addAll(Booster.values()); it }
            .privateView(playerIndex) { it.cards }.publicView { it.size }
        val used by cards<Booster>().on(boosterEvent) { value.cards.add(event) }.publicView { it.cards }
    }
    class Model(override val ctx: Context): Entity(ctx), ContextHolder {
        val eventRun by event(EventClass::class)
        val players by playerComponent { Player(ctx, it) }
        var value by value { 0 }
        val inner by component { Inner(this@Model, ctx) }
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
    fun startConfig() {
        val entry = GamesImpl.game(game)
        val replayable = entry.replayable(3, entry.setup().configs().set("startValue", 100))
        runBlocking { replayable.await() }
        Assertions.assertEquals(3, replayable.game.model.players.size)
        Assertions.assertTrue(replayable.game.model.players.all { it.value == 100 })
    }

    private fun init(): GameReplayableImpl<Model> {
        val entry = GamesImpl.game(game)
        val repl = entry.replayable(2, entry.setup().configs())
        runBlocking { repl.await() }
        return repl
    }

    private fun runAndView(): Pair<Map<String, Any?>, Model> {
        val replayable = init()
        replayable.doGlobal(0, 10)
        replayable.doBoost(0, Booster.TEN_TIMES)
        return replayable.game.view(0) to replayable.game.model
    }

    private fun runAndViewPlayer(viewer: Int): Pair<Model, Map<String, Any?>> {
        val replayable = init()
        replayable.doGlobal(0, 10)
        replayable.doBoost(0, Booster.TEN_TIMES)
        val view = replayable.game.view(viewer)
        ConsoleView<Model>().showView(replayable.game, viewer)
        val playerViews = view["players"].let { it as List<Map<String, Any?>> }
        return replayable.game.model to playerViews[0]
    }

    private fun GameReplayableImpl<Model>.doGlobal(playerIndex: Int, value: Int) {
        runBlocking {
            this@doGlobal.performSerialized(playerIndex, "global", value)
        }
    }

    private fun GameReplayableImpl<Model>.doBoost(playerIndex: Int, value: Booster) {
        runBlocking {
            this@doBoost.performSerialized(playerIndex, "boost", value.name)
        }
    }

    @Test
    fun changeOnEvent() {
        val replayable = init()
        replayable.doGlobal(0, 10)
        Assertions.assertEquals(10, replayable.game.model.inner.sum)
    }

    @Test
    fun onEvent() {
        val replayable = init()
        replayable.doBoost(0, Booster.RESET)
        Assertions.assertEquals(listOf(Booster.RESET), replayable.game.model.players[0].used.cards)
    }

    @Test
    fun dynamicSum() {
        val view = runAndView().first
        val v = view["inner"] as Map<String, Any?>
        Assertions.assertEquals(10, v["sum"])
    }

    @Test
    fun dynamic() {
        val view = runAndView().first
        val v = view["inner"] as Map<String, Any?>
        Assertions.assertEquals(20, v["dynamic"])
    }

    @Test
    fun playerValue() {
        val (_, view) = runAndViewPlayer(0)
        val (_, invisible) = runAndViewPlayer(1)
        Assertions.assertEquals(520, view["value"])
        Assertions.assertFalse(invisible.containsKey("value"))
    }

    @Test
    fun playerEvent() {
        val (game, view) = runAndViewPlayer(0)
        Assertions.assertFalse(view.containsKey(game.players[0]::boosterEvent.name))
    }

    @Test
    fun boosters() {
        val (_, view) = runAndViewPlayer(0)
        val (_, sizeOnly) = runAndViewPlayer(1)
        Assertions.assertEquals(Booster.values().toList().minus(Booster.TEN_TIMES), view["boosters"])
        Assertions.assertEquals(3, sizeOnly["boosters"])
    }

    @Test
    fun used() {
        val (_, view) = runAndViewPlayer(0)
        Assertions.assertEquals(listOf(Booster.TEN_TIMES), view["used"])
    }

    @Test
    fun players() {
        val view = runAndView().first
        Assertions.assertEquals(2, (view["players"] as List<*>).size)
    }

    @Test
    fun value() {
        val (view, _) = runAndView()
        Assertions.assertEquals(100, view["value"])
    }

    @Test
    fun globalAction() {
        val (view, game) = runAndView()
        Assertions.assertFalse(view.containsKey(game::globalAction.name))
    }

    @Test
    fun boostAction() {
        val (view, game) = runAndView()
        Assertions.assertFalse(view.containsKey(game::boostAction.name))
    }

    @Test
    fun event() {
        val (view, game) = runAndView()
        Assertions.assertFalse(view.containsKey(game::eventRun.name))
    }

}
