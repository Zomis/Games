package net.zomis.games.ecs

import net.zomis.games.dsl.*
import net.zomis.games.dsl.impl.GameConfigImpl
import kotlin.reflect.KClass

interface ECSGameFactoryScope {
    fun copyFrom(gameSpec: ECSGameSpec)
    fun players(range: IntRange)
    fun playersFixed(count: Int) = players(count..count)
    fun defaultConfigs(configs: Iterable<Pair<GameConfig<*>, Any>>)
    fun root(builder: ECSRootEntityBuilder.() -> Unit)
}

class ECSGameSpec(val name: String, val function: ECSGameFactoryScope.() -> Unit) {
    fun toDsl() = GameSpec<ECSEntity>(name, ECSFactoryContext(function).toDsl())
}

class ECSFactoryContext(val function: ECSGameFactoryScope.() -> Unit) : ECSGameFactoryScope {
    private var configs: MutableList<Pair<GameConfig<*>, Any>> = mutableListOf()
    private var playerRange: IntRange = 0..0
    private var rootBuilder: ECSEntityBuilder.() -> Unit = {}

    fun toDsl(): GameDsl<ECSEntity>.() -> Unit {
        function.invoke(this)
        if (playerRange.any { it <= 0 }) throw IllegalArgumentException("All possible playerCounts must be positive, but was: $playerRange")
        return {
            setup {
                players(playerRange)
                init {
                    ECSSimpleEntity(null, null)
                        .also { it has ECSComponentBuilder("configs", ECSConfigs) { configs } }
                        .build(rootBuilder)
                }
            }
            configs.forEach {
                this.config(it.first.key) { it.second }
            }
            gameFlow {
                println("game flow")

                loop {
                    println("game loop")

                    this.step("game loop") {
                        // Loop through all child entities of root (including root itself) and yield all actions
                        // Create ECSActionTypes for all of them, with their path and action name as the name
                        // and inheriting the rest from the action spec.
                        println("game loop step")
                        game.allChildren(includingSelf = true)
                            .onEach { println(it.path()) }.filter { it.has(ECSActions) }
                            .forEach { entity ->
                                println("Found actions: $entity")
                                entity[ECSActions].createActions(entity).forEach { action ->
                                    yieldAction(action as ActionType<ECSEntity, Unit>) {
                                        precondition { true }
                                        requires { true }
                                        options { listOf(Unit) }
                                    }
                                }
                            }
                    }
                }
            }
            gameFlowRules {
                beforeReturnRule("view") {
                    view("") {
                        game.view(this)
                    }
                }
            }
        }
    }

    override fun copyFrom(gameSpec: ECSGameSpec) = gameSpec.function.invoke(this)

    override fun players(range: IntRange) {
        this.playerRange = range
    }

    override fun defaultConfigs(configs: Iterable<Pair<GameConfig<*>, Any>>) = this.configs.addAll(configs).let {}

    override fun root(builder: ECSRootEntityBuilder.() -> Unit) {
        val oldBuilder = this.rootBuilder
        this.rootBuilder = {
            oldBuilder.invoke(this)
            builder.invoke(this as ECSRootEntityBuilder)
        }
    }
}

class ECSFactory {
    val components = ECSComponentFactoryImpl(null)

    fun <C : Any> config(key: String, default: () -> C): GameConfig<C> = GameConfigImpl(key, default)
    fun <A : Any> action(name: String, parameterType: KClass<A>): GameActionCreator<ECSEntity, A> =
        GameActionCreator(name, parameterType, parameterType, { it }, { it as A })

    fun simpleAction(name: String) = this.action(name, Unit::class)
    fun game(name: String, dsl: ECSGameFactoryScope.() -> Unit): ECSGameSpec = ECSGameSpec(name, dsl)
}
