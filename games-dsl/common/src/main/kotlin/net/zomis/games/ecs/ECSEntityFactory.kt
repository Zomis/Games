package net.zomis.games.ecs

import net.zomis.games.dsl.GameConfig

interface ECSEntityCreating {
    infix fun <T: Any> has(component: ECSComponentBuilder<T>)
    infix fun mayHave(component: ECSComponentBuilder<out Any>)
}
interface ECSEntityBuilder {
    val entity: ECSEntityCreating
}
interface ECSRootEntityBuilder: ECSComponentFactory {
    val game: ECSEntityCreating
}
interface ECSTileFactoryScope: ECSComponentFactory {
    val tile: ECSEntityCreating
}

class ECSEntityFactory(override val entity: ECSEntityCreating): ECSRootEntityBuilder, ECSTileFactoryScope, ECSEntityBuilder {
    override val game: ECSEntityCreating get() = entity
    override val tile: ECSEntityCreating get() = entity
    override fun <E : Any> config(config: GameConfig<E>): E = (entity as ECSSimpleEntity).root[ECSConfigs].get(config)
}
