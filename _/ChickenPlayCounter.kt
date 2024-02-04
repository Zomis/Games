package net.zomis.minesweeper.ais.post

import net.zomis.UtilZomisList

class ChickenPlayCounter : PostScorer() {
    fun handle(scores: FieldScores) {
        if (!this.weaponIsClick(scores.getWeapon())) return
        val theField: ProbabilityKnowledge<Flags.Field> =
            scores.getAnalyze().getKnowledgeFor(scores.getRankings().get(0).get(0))
        if (theField.mineProbability == 0.0) { // The fact that we are playing a field without mine probability says enough.
            performChickenAnalyze(scores)
            return
        }
    }

    private fun performChickenAnalyze(scores: FieldScores) {
        val chicken = ChickenAnalyze(scores.getPlayer().getMap(), scores.getAnalyze())
        chicken.analyze()
        val unevenConsideration = if (unevenIsDangerous(chicken)) 0 else 1

//		ai.sendInfo("Chicken! " + chicken.toString());
        val opens: List<OpenField> = chicken.getOpenFields()
        if (opens.size > 1) {
//			ai.sendInfo("Multiple open fields detected! It's time to check if they are odd or even.");
            // Opponent will not be able to change number of even open fields.
            if (chicken.getSafeCount() % 2 + opens.size === 1 - unevenConsideration) {
                if (playOpenField(scores, opens, false)) return
            } else {
                if (playOpenField(scores, opens, true)) return
            }
            if (playOpenField(scores, opens, false)) return
            if (playOpenField(scores, opens, true)) return
        } else if (opens.size == 1) {
//			ai.sendInfo("One open field detected. Now let's see what we should do about that.");
            val of: OpenField = opens[0]
            //			int evenOF = of.neighbors.size() % 2; // The size of the open field is more than one.
            if (chicken.getSafeCount() % 2 === unevenConsideration) {
                // Play the open field itself.
                this.force(scores, of.getOpenFieldSource(), 4.2)
            } else {
                // Play one of the neighbors of the open field
                if (of.neighbors.isEmpty()) this.force(
                    scores,
                    of.getOpenFieldSource(),
                    4.2
                ) else this.force(scores, UtilZomisList.getRandom(of.neighbors), 2.1)
            }
        } else {
            if (chicken.getSafeCount() === 0) {
                // Playing unsafe.
                analyzeUnsafe(scores, chicken) // forceSet(fields, chicken.getBestUnsafe(ai.getAnalyze()), -0.0001);
                // 001a102b3a113aab-0011113b3222bb63-121101b211b34aa1-a3a43322133a2343-13baab11a2a211aa-1223321112___122-b1_________x____-11___xx___x____x-___x__________x_-_______x__xx____-______xx________-____xx_____xxx_x-__x__x____xx____-__x__________x__-______________1_-_x______x_______
            }
        }
    }

    private fun analyzeUnsafe(scores: FieldScores, chicken: ChickenAnalyze) {
        for (field in chicken.getUnsafe()) {
            val revealed: Int =
                chicken.getSafeRevealedBy(scores.getAnalyze(), scores.getAnalyze().getKnowledgeFor(field))
            if (revealed % 2 == 0) {
                // if field would reveal an even amount of safe clicks, then it's a good move.
                forceSet(scores, field, 0.01)
                //				Logger.getLogger(getClass()).info(field + " would reveal " + revealed + " and is good.");
            } else {
                forceSet(scores, field, -9)
                //				Logger.getLogger(getClass()).info(field + " would reveal " + revealed + " and is bad.");
                // if field would reveal an odd  amount of safe clicks, then it's a bad move
            }
        }
    }

    private fun playOpenField(scores: FieldScores, openFields: List<OpenField>, playOdd: Boolean): Boolean {
        for (of in openFields) {
            if (of.isEven() === !playOdd) {
                force(scores, of.getOpenFieldSource(), 42)
                return true
            }
        }
        return false
    }

    private fun unevenIsDangerous(chicken: ChickenAnalyze): Boolean {
        return true
    }
}