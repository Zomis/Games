package net.zomis.games.ecsmodel

import net.zomis.games.cards.CardZone
import kotlin.reflect.KProperty

/*
 * Inspired from https://github.com/JetBrains/Exposed
 */

open class GameModelEntityPrototype<T>(val factory: () -> T) {
    fun create(block: T.() -> Unit) {}
}

context(Game)
open class GameModelEntity {
    internal var publicKnown: Boolean = true
    internal val known: KnownMap = KnownMapList() // playerIndices that knows about this entity
    internal var parent: GameModelEntity? = null
    internal val children = mutableListOf<GameModelEntity>()
    internal val properties = mutableMapOf<String, Any>()
    internal var id: Int = 0
    internal var key: String = ""
    internal val path: String get() {
        return parent.let { p ->
            if (p == null) "/" else p.path + key + "/"
        }
    }
    /*
    * Examples:
    * /players/0/hand/contessa
    * /players/0/hand/contessa_0
    * /players/0/hand/contessa_1
    * /players/0/hand/green-6
    * /players/0/hand/id-42
    */

    inline fun <reified T : GameModelEntity> find() : T {
        TODO()
    }

    fun <ModelType> initialize(property: KProperty<ModelType>, value: () -> ModelType) {

    }

    fun initialize(vararg properties: InitializedProperty<out Any>) {

    }
    fun <ModelType> initialize(property: KProperty<ModelType>, value: ModelType) {

    }
    fun rule(ruleScope: EcsRuleScope.() -> Unit): PropertyDelegate<Rule> {
        return GamePropertyDelegate(this) { Rule() }
    }
    fun actionChoice(choices: () -> List<out Any>) {
        TODO()
    }

    fun <ModelType> readOnly(): PropertyProvider<ModelType> {
        TODO()
    }

    fun <ModelType> property(): PropertyProvider<ModelType> {
        TODO()
    }

    fun <ModelType> property(defaultValue: () -> ModelType): PropertyDelegate<ModelType> {
        return GamePropertyDelegate(this, defaultValue)
    }

    fun <CardType> cardZone(): PropertyDelegate<CardZone<CardType>> {
        return EcsModelCardZoneDelegate(this)
    }

    fun action(block: Any.() -> Unit): PropertyDelegate<Any> {
        return GamePropertyDelegate(this) { Unit }
        /*
        * ACTIONS:
        * when is it allowed
        *   - precondition
        *   - requirements / costs
        * what can be targeted + other options to choose when performing
        * what happens
        *
        * Requirements:
        * - Replayable
        * - viewable
        *   - options
        *   - effects / influences
        * - alterable ("when paying cost, you can use 2 X to pay for 1 Y. This can only be used twice per turn")
        * - Needs to use some kind of events
        */
    }

}

interface GameModelStep {
    fun activeWhile() = true
    suspend fun perform()
}
