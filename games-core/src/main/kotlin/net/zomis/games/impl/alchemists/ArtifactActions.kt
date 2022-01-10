package net.zomis.games.impl.alchemists

import net.zomis.games.context.Context
import net.zomis.games.context.Entity
import net.zomis.games.impl.alchemists.artifacts.Artifact

object ArtifactActions {

    class BuyArtifact(model: AlchemistsDelegationGame.Model, ctx: Context): Entity(ctx), AlchemistsDelegationGame.HasAction {
        override val actionSpace by component { model.ActionSpace(this.ctx, "BuyArtifacts") }
            .setup { it.initialize(listOf(1, 2), playerCount) }
        override val action by actionSerializable<AlchemistsDelegationGame.Model, Artifact>("buyArtifact", Artifact::class) {

        }
    }

}