package net.zomis.games.impl.alchemists

import net.zomis.games.common.times
import net.zomis.games.common.toSingleList
import net.zomis.games.context.Context
import net.zomis.games.context.Entity

object Favors {

    enum class FavorType(val count: Int) {
        ASSISTANT(4),
        HERBALIST(4),
        ASSOCIATE(3),
        CUSTODIAN(3),
        SHOPKEEPER(2),
        BARMAID(2),
        MERCHANT(2),
        SAGE(2),
        ;
    }

    class FavorDeck(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx) {
        val deck by cards<FavorType>()
            .setup { value ->
                // Setup favors
                value.cards.addAll(FavorType.values().flatMap { it.toSingleList().times(it.count) })
                value
            }
            .publicView { it.size }
    }

}