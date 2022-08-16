package net.zomis.games.ecs

import net.zomis.games.dsl.ViewScope

interface ECSViewScope: ViewScope<ECSEntity> {
    val entity: ECSEntity
}

class ECSViewContext(
    private val currentEntity: ECSEntity,
    val scope: ViewScope<ECSEntity>
): ECSViewScope, ViewScope<ECSEntity> by scope {
    override val entity: ECSEntity get() = currentEntity
}
