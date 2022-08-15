package net.zomis.games.ecs

import net.zomis.games.components.grids.GridImpl
import net.zomis.games.dsl.ActionType
import net.zomis.games.dsl.GameConfig
import net.zomis.games.dsl.GameConfigs

class ECSComponentFactoryImpl(private val configs: GameConfigs?): ECSComponentFactory {
    override fun <E : Any> config(config: GameConfig<E>): E = configs!!.get(config)
}

interface ECSComponentFactory {
    fun <E: Any> config(config: GameConfig<E>): E
    fun playerIndex() = ECSComponentBuilder("playerIndex", PlayerIndex)
    fun grid(width: Int, height: Int, tileFactory: ECSTileFactoryScope.() -> Unit): ECSComponentBuilder<ECSGrid> {
        return ECSComponentBuilder("grid", Grid) { parent ->
            ECSGrid(GridImpl(width, height) { x, y ->
                ECSSimpleEntity(parent.owner, parent).also {
                    it has Point.withValue(net.zomis.games.components.Point(x, y)).hiddenView()
                }.also { tileFactory.invoke(ECSEntityFactory(it)) }
            })
        }.view {
            it.grid.view { e -> e.view(this) }
        }
    }
    fun action(action: ActionType<ECSEntity, out Any>): ECSComponentBuilder<ECSActions>
        = ECSComponentBuilder("actions", ECSActions) {
            ECSActions(listOf(action))
        }.view {
//            this.actions()
        }.hiddenView()
    fun <A: Any> actionRule(action: ActionType<ECSEntity, A>, rule: ECSActionScope<A>.() -> Unit): ECSComponentBuilder<ECSRules> {
        return ECSComponentBuilder("rules", ECSRules) {
            ECSRules(listOf(ECSActionRule(action, rule)))
        }.hiddenView()
    }
}
