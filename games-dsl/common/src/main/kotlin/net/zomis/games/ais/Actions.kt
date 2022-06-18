package net.zomis.games.ais

import net.zomis.games.dsl.impl.Game

fun <T: Any> noAvailableActions(model: Game<T>, index: Int): Boolean {
    return model.actions.types().all { it.availableActions(index, null).none() }
}

