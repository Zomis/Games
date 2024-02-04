package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.analyze.utils.OpenFieldApproxer

class OpenFieldsPenalty : AbstractScorer() {
    private val openField: OpenFieldApproxer = OpenFieldApproxer()
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters
    ): Double {
        return if (isInterestingField(field, data, scores.getWeapon())) {

            // TODO: AI Modify penalty for this kind of situation?    012a2123a2___1b1-01b33a2aa41_1221-012b3234aa1_1a1_-1122a11a321_111_-1b1111111_______-332_____________-aa1_____________-221__xx______x__-_______________x-__111____111____-x_1a1_1122a32_x_-x_11213a3a5aa4b3-___13a4a43abb__x-1234aa313b432___-2aaaa3214a422___-a4a4211a3a3aa1_x
            // x Adjust penalty for ff here: 012a2123a2___1b1-01b33a2aa41_1221-012b3234aa1_1a1_-1122a11a321_111_-1b1111111_______-332_____________-aa1_____________-221__xx______x__-_______________x-__111____111____-x_1a1_1122a323a2-x_11213a3a5aa4b3-___13a4a43abb32b-1234aa313b432111-2aaaa3214a4221__-a4a4211a3a3aa1_x
            // x quite obvious penalty incorrect: 012a2123a2___1b1-01b33a2aa41_1221-012b3234aa1_1a1_-1122a11a321_111_-1b1111111_______-332_____________-aa1_________111_-221__xx_____1a21-____________112b-__111____111_122-x_1a1_1122a323a2-x_11213a3a5aa4b3-___13a4a43abb32b-1234aa313b432111-2aaaa3214a4221__-a4a4211a3a3aa1_x
            val expected: Double = openField.expectedFrom(scores.getAnalyze(), field)
            val probability: Double = data.probabilities.get(0) * 7
            val factor = 1.0

//			logger.info(String.format("%s: Expected %f prob %f factor %f", field, expected, probability, factor));
            -(expected * probability * factor)
        } else 0
    }

    fun isInterestingField(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>,
        weapon: MinesweeperWeapon?
    ): Boolean {
        return data.probabilities.get(0) > 0
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return if (!this.weaponIsClick(scores.getWeapon())) false else true
        // this.getMario().getBoard().MaxExpectedValue() <= 0;


//		int realGroups = 0;
//		for (FieldGroup<Flags.Field> group : this.analyze.getAnalyze().getGroups()) {
//			if (group.getProbability() > 0) realGroups++;
//		}
//		
//		return this.weaponIsClick(weapon) && realGroups < 2;
    }
}