package net.zomis.minesweeper.ais.post

import net.zomis.games.impl.minesweeper.ais.BombTools

class BombStifle : PostScorer() {
    fun handle(scores: FieldScores) {
        if (!this.weaponIsClick(scores.getWeapon())) return
        val player: MinesweeperPlayingPlayer = scores.getPlayer()
        val bomb: String = MinesweeperMove.STANDARD_BOMB
        val opponent: MinesweeperPlayingPlayer = MineprobHelper.getOpponents(scores.getPlayer()).get(0)
        if (opponent.getScore() > player.getScore()) {
//			ai.sendInfo("Opponent has more points than I");
            return
        }
        val bombWeapon: BombWeapon = opponent.getWeapon(bomb) as BombWeapon
            ?: // Bomb weapon does not exist.
            return
        if (bombWeapon.getUseCount() > 0) {
//			ai.sendInfo("Opponent has already used bomb.");
            return
        }
        if (player.getScore() < 15) return
        if (opponent.getScore() < 15) return
        if (player.getScore() === opponent.getScore() && player.getMap().getMinesLeft() === 1) {
            // New code for TestmareB.
            return
        }
        val bestBombField: Flags.Field = BombTools.getBestBomb(player.getMap(), scores.getAnalyze().getAnalyze())
        //		double bombScore = BombTools.getBombProbability(bestBombField, scores.getAnalyze());
        val bestAffected: List<Flags.Field> = BombTools.getBombAdjacents(bestBombField)
        val bombWinChance: Double =
            BombTools.getBombWinPercent(scores.getAnalyze().getAnalyze(), bestBombField, opponent.getScore())
        val bestField: Flags.Field = scores.getRankings().get(0).get(0)
        val bestData: ProbabilityKnowledge<Flags.Field> = scores.getAnalyze().getKnowledgeFor(bestField)
        if (bestData.probabilities.get(0) > 0 && bestData.probabilities.get(0) < 0.15 && bombWinChance >= 0.3) {
            // Opponent can bomb, best field has open field chance, but it's not big.
//			ai.sendInfo("I have to play open field and it's starting to be bomb time. Best bomb is " + bombScore + " @ " + bestBombField + " and my field is " + bestField);
            performAddBombScore(scores, bestAffected)
        }


//		if (ai leder och det b�rjar bli dags: perform! Add the actual bomb probability on each field);
        if (bombWinChance >= 0.42) {

//			if (MineprobHelper.find100(ai.getAnalyze().getAnalyze())  ) {
//				
//			}
            if (opponent.getScore() === player.getScore()) {
                val myWeapon: BombWeapon = player.getWeapon(bomb) as BombWeapon
                if (myWeapon.getUseCount() === 0) // Check if AI has used the bomb
                    performAvoidGiveBomb(scores)
            } else {
//				ai.sendInfo("I have a bad feeling about this. I'd better sabotage your bomb. " + bombScore + " @ " + bestBombField);
                performAddBombScore(scores, bestAffected)
            }
        }
    }

    private fun performAddBombScore(scores: FieldScores, bestBombEffect: List<Flags.Field>) {
//		ai.sendInfo("Considering bombscore, mine probability, and open field probability.");
        for (ee in scores.getScores().values()) {
            val stifle = stifleEffect(ee.getField(), scores.getAnalyze(), bestBombEffect)
            if (stifle != 0.0) {
//				ai.sendInfo(ee.getKey() + " changes " + stifle + " to " + newValue);
            }
            this.force(ee, stifle)
        }
    }

    private fun performAvoidGiveBomb(scores: FieldScores) {
//		ai.sendInfo("If I get a mine you will be able to bomb. I'd better avoid them. But I need to start take them at some point, don't I?");
        for (ee in scores.getScores().values()) {
            val data: ProbabilityKnowledge<Flags.Field> = scores.getAnalyze().getKnowledgeFor(ee.getField())
            //			ai.sendInfo("Decreasing score for " + ee.getKey() + " by " + data.getMineProbability() + " previous was " + ee.value);
            this.force(ee, -data.mineProbability * CertainMines.POWER)
        }
        run {

            // New code for AI_TestmareB
            val me: MinesweeperPlayingPlayer = scores.getPlayer()
            val opponent: MinesweeperPlayingPlayer = MineprobHelper.getOpponents(scores.getPlayer()).get(0)
            val mines100: Int = MineprobHelper.find100(scores.getAnalyze().getAnalyze())
            if (me.getScore() === opponent.getScore() && me.getScore() + mines100 + 1 >= WinChanceTools.NEEDED_SCORE) {
                val best: List<FieldScore> = scores.getBestFields()
                if (scores.getAnalyze().getKnowledgeFor(best[0].getField()).getMineProbability() > 0) {
//					ai.sendInfo("I have no other choice but to take a chance!");
                    for (ee in scores.getScores().values()) {
                        val data: ProbabilityKnowledge<Flags.Field> =
                            scores.getAnalyze().getKnowledgeFor(ee.getField())
                        if (data.mineProbability < 1.0) {
//							ai.sendInfo("Adding score for " + ee.getKey() + " by " + data.getMineProbability() + " previous was " + ee.value);
                            this.force(ee, data.mineProbability * 20)
                        }
                    }
                }
            }
        }
    }

    companion object {
        fun stifleEffect(
            field: Flags.Field,
            analyze: AnalyzeProvider,
            bestBombEffect: List<Flags.Field>?
        ): Double {
            if (bestBombEffect != null && !bestBombEffect.contains(field)) return 0 // Field not affected, ignore it.
            val bombScore: Double = BombTools.getBombProbability(field, analyze.getAnalyze())
            if (analyze.getKnowledgeFor(field) == null) return (-42).toDouble()
            val bonusMineProbability: Double = analyze.getKnowledgeFor(field).getMineProbability()
            val bonusAvoidOpenField: Double = 1 - analyze.getKnowledgeFor(field).getProbabilities().get(0)
            return bombScore * 2 + bonusMineProbability * 10 + bonusAvoidOpenField * 3
        }
    }
}