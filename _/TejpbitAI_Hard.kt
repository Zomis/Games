package net.tejpbit.ais

import net.zomis.UtilZomisList

// @AI(rating = 1500)
class TejpbitAI_Hard : MinesweeperAI("#TejpbitAI_Hard") {
    fun agreeDraw(pp: MinesweeperPlayingPlayer): Boolean {
        return MineprobHelper.isDraw(pp.getMap())
    }

    fun play(pp: MinesweeperPlayingPlayer): MinesweeperMove {
        val gameAnalyze: AnalyzeResult<MinesweeperField> = AnalyzeFactory.analyze(pp.getMap(), false).getAnalyze()
        if (pp.canUseWeapon(MinesweeperMove.STANDARD_BOMB)) {
            val bombField: MinesweeperField? = eredags(pp, gameAnalyze)
            if (bombField != null) return pp.createMove(MinesweeperMove.STANDARD_BOMB, bombField)
        }
        val groups: List<FieldGroup<MinesweeperField>> = gameAnalyze.groups
        var highProbabilityFields: MutableList<MinesweeperField?> = java.util.ArrayList<MinesweeperField>()
        var highestProbability = -1.0
        for (group in groups) {
            if (group.probability > highestProbability) {
                highestProbability = group.probability
                highProbabilityFields.clear()
                highProbabilityFields.addAll(group)
            } else if (group.probability == highestProbability) {
                highProbabilityFields.addAll(group)
            }
        }
        ////////////// Om det �r "open-field" klick, klicka intill en mina.
        if (highProbabilityFields.size > 8) {
            val highProbabilityCleaner: MutableList<MinesweeperField?> =
                java.util.ArrayList<MinesweeperField>(highProbabilityFields)
            for (checkAdjacents in highProbabilityFields) {
                var hasMineAdjacent = false
                for (invertedAdjacents in checkAdjacents.getInvertedNeighbors()) {
                    if (invertedAdjacents.isMine() && invertedAdjacents.isClicked()) {
                        hasMineAdjacent = true
                    }
                }
                if (hasMineAdjacent == false) {
                    highProbabilityCleaner.remove(checkAdjacents)
                }
            }
            if (highProbabilityCleaner.size > 0) {
                highProbabilityFields = highProbabilityCleaner // f�lt som �r intill en mina och har h�g sannolikhet
            }
            ////////////
            val highProbabilityFieldsClone: MutableList<MinesweeperField?> =
                java.util.ArrayList<MinesweeperField>(highProbabilityFields)
            cleanOpenFields(highProbabilityFieldsClone)
            if (highProbabilityFieldsClone.isEmpty()) {
                // Adds the fields that has a clicked adjacent mine to highProbabilityFieldsClone
                for (field in pp.getMap().getIteration()) {
                    if (hasAdjacentClickedMine(field) && !field.isClicked()) {
                        highProbabilityFieldsClone.add(field)
                    }
                }
                val safeDive: MutableList<MinesweeperField> = java.util.ArrayList<MinesweeperField>()
                for (field in highProbabilityFieldsClone) {
                    //if the field has a mineProbability higher than 0 then it's a safe dive
                    if (gameAnalyze.getGroupFor(field).probability > 0) {
                        safeDive.add(field)
                    }
                }
                //if no safeDive was found then use the remaining safeClics
                if (safeDive.isEmpty()) {
                    if (!highProbabilityFieldsClone.isEmpty()) {
                        highProbabilityFields = highProbabilityFieldsClone
                    }
                }
            }
        }
        /////////////////
        val random: java.util.Random = java.util.Random()
        val clickIndex: Int = random.nextInt(highProbabilityFields.size)
        return pp.createMove(MinesweeperMove.STANDARD_CLICK, highProbabilityFields[clickIndex])
    }

    private fun hasAdjacentClickedMine(field: MinesweeperField): Boolean {
        for (adjacent in field.getNeighbors()) {
            if (adjacent.isDiscoveredMine()) return true
        }
        return false
    }

    private fun eredags(pp: MinesweeperPlayingPlayer, gameAnalyze: AnalyzeResult<MinesweeperField>): MinesweeperField? {
        val bombPossibilitys: MutableList<MinesweeperField> = java.util.ArrayList<MinesweeperField>()
        val bombFields: MutableMap<MinesweeperField, List<MinesweeperField>> =
            java.util.HashMap<MinesweeperField, List<MinesweeperField>>()
        for (bombCheck in pp.getMap().getIteration()) {
            val bombField: List<MinesweeperField> = java.util.ArrayList<MinesweeperField>(getBombAdjacents(bombCheck))
            if (bombField.size > 0) {
                bombFields[bombCheck] = getBombAdjacents(bombCheck)
            }
        }
        var mineProbability = 0.0
        for ((key, value) in bombFields) {

//			Map<MinesweeperField,Double> tempMineProbability = new HashMap<MinesweeperField, Double>();
            var tempMineProbability: Double = pp.getScore()
            for (field in value) {
                if (gameAnalyze.getGroupFor(field) != null) tempMineProbability += gameAnalyze.getGroupFor(field).probability
            }

//			Minesweeper.getServer().log("tempMineProbability :" + tempMineProbability);
//			Minesweeper.getServer().log("mineProbability :" + mineProbability);
            if (tempMineProbability == mineProbability) {
                bombPossibilitys.add(key)
            } else if (tempMineProbability > mineProbability) {
                bombPossibilitys.clear()
                bombPossibilitys.add(key)
                mineProbability = tempMineProbability
            }
        }

//		Minesweeper.getServer().broadcastLobbyChat("mineProbability " + mineProbability);
//		for (MinesweeperField bombableField : bombPossibilitys) {
//			Minesweeper.getServer().broadcastLobbyChat("field is :" + String.format("(%d,  %d)", bombableField.getX(), bombableField.getY()));
//		}
        return if (mineProbability >= pp.getMap().getMinesCount() as Double / pp.getMap().getPlayingPlayers().size()) {
            UtilZomisList.getRandom(bombPossibilitys)
        } else null
    }

    /**
     * kolla alla f�lt
     * om minsannolikheten �r st�rre till havs �n vad den �r vid andra nuffror.
     * klicka d� vid en s�ker dykning (adjacent till en redan hittad mina som inte �r i n�rheten av en nuffra)
     *
     */
    private fun cleanOpenFields(fields: MutableList<MinesweeperField?>) {
        for (field in java.util.ArrayList<MinesweeperField>(fields)) {
            val adjacents: Collection<MinesweeperField> = field.getNeighbors()
            var hasMine = false
            for (adjacent in adjacents) {
                if (adjacent.isDiscoveredMine()) {
                    hasMine = true
                    break
                }
            }
            if (!hasMine) {
                fields.remove(field)
            }
        }
    }

}