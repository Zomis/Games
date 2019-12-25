package net.zomis.games.dsl.impl

import net.zomis.games.dsl.*
import kotlin.reflect.KClass

class GameModelContext<T, C> : GameModel<T, C> {
    lateinit var factory: (C) -> T
    lateinit var creator: () -> C

    override fun defaultConfig(creator: () -> C) {
        this.creator = creator
    }

    override fun init(factory: (C) -> T) {
        this.factory = factory
    }
}
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

class GameLogicContext<T>(val model: T) : GameLogic<T> {
    val actions = mutableMapOf<String, ActionLogic2D<T, Any?>>()

    override fun <P> action2D(name: String, logic: ActionLogic2D<T, P>) {
        actions[name] = logic as ActionLogic2D<T, Any?>
    }

}
class GameViewContext2D<T, P>(override val model: T) : GameView2D<T, P> {

    lateinit var size: Pair<Int, Int>
    lateinit var getter: (x: Int, y: Int) -> P
    var ownerFunction: ((tile: P) -> Int?)? = null

    override fun size(sizeX: Int, sizeY: Int) {
        this.size = sizeX to sizeY
    }

    override fun getter(getter: (x: Int, y: Int) -> P) {
        this.getter = getter
    }

    override fun owner(function: (tile: P) -> Int?) {
        this.ownerFunction = function
    }

}
class GameViewContext<T>(val model: T) : GameView<T> {
    private val viewResult: MutableMap<String, Any?> = mutableMapOf()

    override fun result(): Map<String, Any?> {
        return viewResult.toMap()
    }

    override fun currentPlayer(function: (T) -> Int) {
        viewResult["currentPlayer"] = function(model)
    }

    override fun <P> grid(name: String, view: ViewDsl2D<T, P>) {
        val context = GameViewContext2D<T, P>(model)
        view(context)
        viewResult[name] = (0 until context.size.second).flatMap {y ->
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

    override fun winner(function: (T) -> Int?) {
        viewResult["winner"] = function(model)
    }
}

class GameDslContext<T : Any> : GameDsl<T> {
    lateinit var configClass: KClass<*>
    lateinit var modelDsl: GameModelDsl<T, Any>
    lateinit var logicDsl: GameLogicDsl<T>
    lateinit var viewDsl: GameViewDsl<T>

    override fun <C : Any> setup(configClass: KClass<C>, modelDsl: GameModelDsl<T, C>) {
        this.configClass = configClass
        this.modelDsl = modelDsl as GameModelDsl<T, Any>
    }

    override fun logic(logicDsl: GameLogicDsl<T>) {
        this.logicDsl = logicDsl
    }
    override fun view(viewDsl: GameViewDsl<T>) {
        this.viewDsl = viewDsl
    }
}
