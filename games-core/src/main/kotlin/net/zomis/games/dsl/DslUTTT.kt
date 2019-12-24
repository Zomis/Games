package net.zomis.games.dsl

import net.zomis.games.core.ActionAllowedCheck
import net.zomis.tttultimate.TTBase
import net.zomis.tttultimate.TTFactories
import net.zomis.tttultimate.TTPlayer
import net.zomis.tttultimate.games.TTClassicController
import net.zomis.tttultimate.games.TTController
import kotlin.reflect.KClass

class GameModelContext<T, C> {
    lateinit var factory: (C) -> T
    lateinit var creator: () -> C

    fun defaultConfig(creator: () -> C) {
        this.creator = creator
    }

    fun init(factory: (C) -> T) {
        this.factory = factory
    }
}

data class Action2D<T, P>(val game: T, val playerIndex: Int, val x: Int, val y: Int, val target: P)
class GameLogicContext2D<T, P>(val model: T) {
    lateinit var size: Pair<Int, Int>
    lateinit var getter: (x: Int, y: Int) -> P?
    lateinit var allowedCheck: (Action2D<T, P>) -> Boolean
    lateinit var effect: (Action2D<T, P>) -> Unit

    fun size(sizeX: Int, sizeY: Int) {
        this.size = sizeX to sizeY
    }
    fun getter(getter: (x: Int, y: Int) -> P?) {
        this.getter = getter
    }
    fun allowed(condition: (Action2D<T, P>) -> Boolean) {
        this.allowedCheck = condition
    }
    fun effect(effect: (Action2D<T, P>) -> Unit) {
        this.effect = effect
    }
}

class GameViewContext2D<T, P> {

    lateinit var size: Pair<Int, Int>
    lateinit var getter: (x: Int, y: Int) -> P
    var ownerFunction: ((tile: P) -> Int?)? = null

    fun size(sizeX: Int, sizeY: Int) {
        this.size = sizeX to sizeY
    }

    fun getter(getter: (x: Int, y: Int) -> P) {
        this.getter = getter
    }

    fun owner(function: (tile: P) -> Int?) {
        this.ownerFunction = function
    }

}

typealias ActionLogic2D<T, P> = GameLogicContext2D<T, P>.() -> Unit

typealias ViewDsl2D<T, P> = GameViewContext2D<T, P>.() -> Unit

class GameLogicContext<T>(val model: T) {
    val actions = mutableMapOf<String, ActionLogic2D<T, Any?>>()

    fun <P> action2D(name: String, logic: ActionLogic2D<T, P>) {
        actions[name] = logic as ActionLogic2D<T, Any?>
    }

}
class GameViewContext<T>(val model: T) {
    private val viewResult: MutableMap<String, Any?> = mutableMapOf()

    fun result(): Map<String, Any?> {
        return viewResult.toMap()
    }

    fun currentPlayer(function: (T) -> Int) {
        viewResult["currentPlayer"] = function(model)
    }

    fun <P> grid(name: String, view: ViewDsl2D<T, P>) {
        val context = GameViewContext2D<T, P>()
        view(context)
        viewResult["name"] = (0 until context.size.second).flatMap {y ->
            (0 until context.size.first).map {x ->
                val p = context.getter(x, y)
                val tileMap = mutableMapOf<String, Any?>()
                if (context.ownerFunction != null) {
                    tileMap["owner"] = context.ownerFunction!!(p)
                }
                tileMap
            }
        }
    }

    fun winner(function: (T) -> Int?) {
        viewResult["winner"] = function(model)
    }
}

class GameDslContext<T : Any> {
    lateinit var configClass: KClass<*>
    lateinit var modelDsl: GameModelDsl<T, Any>
    lateinit var logicDsl: GameLogicDsl<T>
    lateinit var viewDsl: GameViewDsl<T>

    fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>) {
        this.configClass = configClass
        this.modelDsl = modelDsl as GameModelDsl<T, Any>
    }

    fun logic(logicDsl: GameLogicDsl<T>) {
        this.logicDsl = logicDsl
    }
    fun view(viewDsl: GameViewDsl<T>) {
        this.viewDsl = viewDsl
    }
}

typealias GameDsl<T> = GameDslContext<T>.() -> Unit
typealias GameModelDsl<T, C> = GameModelContext<T, C>.() -> Unit
typealias GameLogicDsl<T> = GameLogicContext<T>.() -> Unit
typealias GameViewDsl<T> = GameViewContext<T>.() -> Unit

data class TTOptions(val m: Int, val n: Int, val k: Int)
fun TTPlayer.index(): Int {
    return when (this) {
        TTPlayer.X -> 0
        TTPlayer.O -> 1
        TTPlayer.NONE -> -1
        TTPlayer.XO -> -1
    }
}

class DslTTT {

    val game: GameDsl<TTController> = {
        setup(TTOptions::class) {
            defaultConfig {
                TTOptions(3, 3, 3)
            }
            init {conf ->
                TTClassicController(TTFactories().classicMNK(conf.m, conf.n, conf.k))
            }
        }
        logic {
            action2D<TTBase>("play") {
                size(model.game.sizeX, model.game.sizeY)
                getter { x, y -> model.game.getSub(x, y) }
                allowed { it.playerIndex == it.game.currentPlayer.index() && it.game.isAllowedPlay(it.target) }
                effect {
                    it.game.play(it.target)
                }
            }
        }
        view {
            currentPlayer { model.currentPlayer.index() }
            winner { if (model.isGameOver) model.wonBy.index() else null }
            grid<TTBase>("board") {
                size(model.game.sizeX, model.game.sizeY)
                getter { x, y -> model.game.getSub(x, y)!! }
                owner { it.wonBy.index().takeIf {n -> n >= 0 } }
            }
        }
    }

}

fun main(args: Array<String>) {
    println("Hello")
}