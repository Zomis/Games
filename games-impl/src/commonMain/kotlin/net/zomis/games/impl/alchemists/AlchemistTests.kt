package net.zomis.games.impl.alchemists

import net.zomis.games.context.GameCreatorContextScope

object AlchemistTests {

    fun GameCreatorContextScope<AlchemistsDelegationGame.Model>.tests() {
        testCase(2, name = "Resolve Herbalist") {
            state("favors-0", listOf(Favors.FavorType.HERBALIST.name, Favors.FavorType.SAGE.name))
            state("favors-1", listOf(Favors.FavorType.ASSOCIATE.name, Favors.FavorType.SAGE.name))
            state("startingPlayer", 1)
            state("startingIngredients-0", listOf("C", "B", "A"))
            initialize()

            action(0, Favors.discardFavor, Favors.FavorType.SAGE)
//            actionAllowed(0, game.favors.herbalistDiscard, listOf("A", "B"))

            state("herbalist", listOf("E", "F", "G"))
            action(1, Favors.discardFavor, Favors.FavorType.ASSOCIATE)

            actionNotAllowed(0, game.turnPicker.action.actionType, game.turnPicker.options.first { it.ingredients == 2 })
            action(0, Favors.herbalistDiscard, PotionActions.IngredientsMix(0, Ingredient.GRAY_TREE to Ingredient.RED_SCORPION))
            expectEquals(listOf(Ingredient.PURPLE_MUSHROOM, Ingredient.GREEN_PLANT, Ingredient.BROWN_FROG, Ingredient.BLUE_FLOWER), game.players[0].ingredients.cards)
        }
        testCase(2, name = "Play first round") {
            // TODO: Can I just disable some rules, such as discarding favors in beginning and not getting any starting favors at all?
            // TODO: Can I just customize some rules and play with 2-3 rounds instead of 6 ?
            // TODO: Can I add some plugin to the game which just disables certain artifacts? (don't make them be returnable from randomness)
            state("solution", listOf("A", "B", "C", "D", "E", "F", "G", "H"))
            state("favors-0", listOf(Favors.FavorType.SHOPKEEPER.name, Favors.FavorType.HERBALIST.name))
            state("favors-1", listOf(Favors.FavorType.SAGE.name, Favors.FavorType.ASSOCIATE.name))
            state("startingPlayer", 0)
            state("heroes", listOf("B-R-G-", "B+R+G+", "G-B-R+", "R-G-B+", "G+B+R-"))
            state("artifacts-1", listOf(ArtifactActions.printingPress.name, ArtifactActions.magicMortar.name, ArtifactActions.bootsOfSpeed.name))
            state("artifacts-2", listOf(ArtifactActions.witchTrunk.name, ArtifactActions.amuletOfRhetoric.name, ArtifactActions.sealOfAuthority.name))
            state("artifacts-3", listOf(ArtifactActions.wisdomIdol.name, ArtifactActions.magicMirror.name, ArtifactActions.crystalCabinet.name))
            state("startingIngredients-0", listOf("A", "B", "C"))
            state("startingIngredients-1", listOf("D", "E", "F"))
            initialize()

            action(0, Favors.discardFavor, Favors.FavorType.HERBALIST)
            state("ingredients-slots", listOf("A", "B", "C", "D", "E"))
            action(1, Favors.discardFavor, Favors.FavorType.SAGE)

            state("ingredients", listOf("C", "A"))
            action(0, game.turnPicker.action.actionType, game.turnPicker.options.first { it.ingredients == 2 })
            state("favors", listOf(Favors.FavorType.BARMAID.name, Favors.FavorType.SAGE.name))
            action(1, game.turnPicker.action.actionType, game.turnPicker.options.first { it.favors == 2 })

            action(1, game.actionPlacement.actionType, AlchemistsDelegationGame.Model.ActionPlacement(
                listOf(
                    AlchemistsDelegationGame.Model.ActionChoice(game.ingredients, 1, false),
                    AlchemistsDelegationGame.Model.ActionChoice(game.testSelf, 1, false),
                    AlchemistsDelegationGame.Model.ActionChoice(game.transmute, 1, false),
                )
            ))
            action(0, game.actionPlacement.actionType, AlchemistsDelegationGame.Model.ActionPlacement(
                listOf(
                    AlchemistsDelegationGame.Model.ActionChoice(game.transmute, 1, false),
                    AlchemistsDelegationGame.Model.ActionChoice(game.buyArtifact, 1, false),
                    AlchemistsDelegationGame.Model.ActionChoice(game.testSelf, 1, false),
                )
            ))

            action(1, game.ingredients.action.actionType, Ingredient.PURPLE_MUSHROOM.serialize())
            action(0, game.transmute.action.actionType, Ingredient.PURPLE_MUSHROOM)
            action(1, game.transmute.action.actionType, Ingredient.YELLOW_CHICKEN_LEG)
            action(0, game.buyArtifact.action.actionType, ArtifactActions.magicMortar)

            state("discardIndex", 1)
            action(0, game.testSelf.action.actionType, PotionActions.IngredientsMix(0, Ingredient.GREEN_PLANT to Ingredient.BROWN_FROG))
            state("ingredients-slots", listOf("B", "H", "G", "F", "C"))
            action(1, game.testSelf.action.actionType, PotionActions.IngredientsMix(1, Ingredient.BLUE_FLOWER to Ingredient.GRAY_TREE))

            expectEquals(2, game.round)
        }
    }

}