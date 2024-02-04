package net.zomis.minesweeper.analyze.utils

import net.zomis.minesweeper.analyze.impl.MineprobabilityAnalyze

class OpenFieldAnalyzer(map: MinesweeperMap) {
    private val map: MinesweeperMap

    init {
        this.map = map
    }

    fun analyzePossibleOpenFields(totalTime: Int): Map<Flags.Field, Double> {
        val result: MutableMap<Flags.Field, Double> = java.util.HashMap<Flags.Field, Double>()
        val unclicked: List<Flags.Field> = map.getAllUnclickedFields()
        //		logger.info("Unclicked: " + unclicked.size());
        if (unclicked.isEmpty()) return result
        val tests = totalTime / unclicked.size
        //		logger.info("Tests: " + tests);
        val str: String = map.saveMap()
        for (ff in unclicked) {
            if (!ff.isClicked()) {
//				logger.info("Testing: " + ff);
                val dbl = getExpected100fromOpenField(str, ff, tests, false)
                if (dbl > 0) result[ff] = dbl
            }
        }
        return result
    }

    companion object {
        private val random: java.util.Random = java.util.Random()

        //	static MineprobabilityAnalyze prepareOpenFieldAnalyze(MinesweeperMap map, ) {
        //		Create a method so that no new map needs to be created every iteration in analyzePossibleOpenFields.
        //	}
        fun getExpected100fromOpenField(
            map: String?,
            field: Flags.Field,
            tests: Int,
            testsAsTime: Boolean
        ): Double {
//		logger.info("Map: " + map);
            var field: Flags.Field = field
            val tempMap: MinesweeperMap = field.getMap().getMapFactory().withTwoPlayers().loadFrom(map).map()
            field = tempMap.getPosition(field.getX(), field.getY())
            for (ff in field.getNeighbors()) {
                if (ff.isClicked() && ff.isMine()) return Int.MIN_VALUE.toDouble()
            }
            val analyze = MineprobabilityAnalyze(tempMap)
            analyze.addRule(MineprobabilityAnalyze.ruleFromField(field, 0))
            analyze.addRule(MineprobabilityAnalyze.ruleForField(field, false))
            val result: AnalyzeResult<Flags.Field> = analyze.solve()
            clearHiddenMines(tempMap)
            if (result.total == 0.0) {
                return Int.MIN_VALUE.toDouble()
            }
            val start100: Int = MineprobHelper.find100(result)
            var total = 0
            field = tempMap.getPosition(field.getX(), field.getY())
            val weapon = ClickWeapon()
            var totalTests = 0
            if (!testsAsTime) {
                for (i in 0 until tests) {
//				logger.info(field + " Loop " + i);
                    totalTests++
                    tempMap.loadMap(map)
                    total += getExpected100fromOpenFieldIteration(tempMap, result, random, field, weapon, start100)
                }
            } else {
                val startTime: Long = java.lang.System.currentTimeMillis()
                while (java.lang.System.currentTimeMillis() - startTime < tests) {
//				logger.info(field + " Loop " + totalTests);
                    totalTests++
                    tempMap.loadMap(map)
                    total += getExpected100fromOpenFieldIteration(tempMap, result, random, field, weapon, start100)
                }
            }
            return 1.0 * total / totalTests
        }

        private fun clearHiddenMines(tempMap: MinesweeperMap) {
            for (ff in tempMap.getIteration()) if (!ff.isClicked() && ff.isMine()) ff.setMine(false)
            tempMap.saveMap()
        }

        private fun getExpected100fromOpenFieldIteration(
            tempMap: MinesweeperMap,
            analyze: AnalyzeResult<Flags.Field>,
            random: java.util.Random,
            field: Flags.Field,
            weapon: MinesweeperWeapon,
            start100: Int
        ): Int {
//		List<Flags.Field> randomFields = fastRegenerate(analyze, random);
            MineprobHelper.regenerate(tempMap, analyze, random)
            //		if (randomFields == null) throw new AssertionError("Regenerate returned null");
            if (field.isClicked()) throw AssertionError(
                field.toString() + " is already clicked. " + field.getMap().saveMap()
            )
            if (field.isMine()) throw AssertionError(field.toString() + " is a mine. " + field.getMap().saveMap())
            if (field.getValue() !== 0) throw AssertionError(
                (field.toString() + " is a " + field.getValue()).toString() + ". " + field.getMap().saveMap()
            )
            weapon.useAt(field.getMap().createMove(null, weapon, field))
            val analyze2 = MineprobabilityAnalyze(tempMap)
            val result2: AnalyzeResult<Flags.Field> = analyze2.solve()
            //		clearMines(randomFields);
//		logger.info(tempMap.saveMap());
            return MineprobHelper.find100(result2) - start100
        } /*	private static void clearMines(List<Flags.Field> fields) {
		for (Flags.Field ff : fields) ff.setMine(false);
	}
	private static List<Flags.Field> fastRegenerate(MineprobabilityAnalyze analyze, Random random) {
		List<Flags.Field> recalc = new ArrayList<Flags.Field>();
		List<Flags.Field> chosen = analyze.randomSolution(random);
		for (Flags.Field ff : chosen) {
			recalc.addAll(ff.getNeighbors());
			ff.setMine(true);
		}
		
//		for (Flags.Field ff : recalc)
//		if (!ff.isClicked())
//			ff.init();
		recalc.get(0).getMap().initFields();
		
		return chosen;
	}*/
    }
}