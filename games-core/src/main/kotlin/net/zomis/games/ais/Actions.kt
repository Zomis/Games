package net.zomis.games.ais

import net.zomis.games.dsl.impl.GameImpl

fun <T: Any> noAvailableActions(model: GameImpl<T>, index: Int): Boolean {
    return model.actions.types().all { it.availableActions(index, null).none() }
}

