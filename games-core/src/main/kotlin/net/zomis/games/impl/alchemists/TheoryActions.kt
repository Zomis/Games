package net.zomis.games.impl.alchemists

import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.dsl.GameSerializable

object TheoryActions {

    class TheoryAction: GameSerializable {
        override fun serialize(): String = ""
    }

    class DebunkAction: GameSerializable {
        override fun serialize(): String = ""
    }

    class DebunkTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun actionAvailable(): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, TheoryAction>("publishTheory", TheoryAction::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            choose {
            }
            perform {
                actionSpace.resolveNext()
            }
        }
    }

    class PublishTheory(val model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override fun actionAvailable(): Boolean = model.round >= 2
        override val actionSpace by component { model.ActionSpace(this.ctx, "PublishTheory") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, TheoryAction>("publishTheory", TheoryAction::class) {
            precondition { playerIndex == actionSpace.nextPlayerIndex() }
            choose {
            }
            perform {
                actionSpace.resolveNext()
            }
        }
    }


}