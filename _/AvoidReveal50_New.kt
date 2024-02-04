package net.zomis.minesweeper.aiscore

import net.zomis.minesweeper.analyze.utils.OpenFieldApproxer

class AvoidReveal50_New : AbstractScorer() {
    var openFieldScan: OpenFieldApproxer = OpenFieldApproxer()
    fun getScoreFor(
        field: Flags.Field?,
        data: ProbabilityKnowledge<Flags.Field?>, scores: ScoreParameters?
    ): Double {
        if (hasProbability(data.mineProbability, 1.0)) return 0

        // Correction for c5-c7 in this situation: 01222a10000012a1-12ba321001112a31-2a43a10123b12b2_-2b212211bb21222_-22101b12331__a2_-b1001233b1___x3_-111122ab21_1__3x-001a3a321___xx3_-0012b221211_a_a_-1222222b2b12a421-2bb23b5331112b10-b33b3abb32101221-11224343ba1001b2-002b3b112321023b-003b521002a201a2-002ba10002b20111
        if (ZomisTools.isAIChallengerField(data)) return 0 // basically, if this is an AI Challenger field.
        var groupFound: Boolean? = null
        var groupsFound = 0
        for ((key, value) in data.neighbors.entrySet()) {
            // Check if field is neighbor with a 1/2 group.
            if (value == 1) { // has one neighbor to the fieldgroup
                if (key.size() <= 3) { // fieldgroup size is 3 or less
                    if (hasProbability(
                            key.probability,
                            1.0 * (key.size() - 1) / key.size()
                        )
                    ) { // fieldgroup probability is 1/2 or 2/3...
                        if (data.fieldGroup.size() === 2 && hasProbability(data.mineProbability, 0.5)) {
                            // Check if field IS a 1/2 group
                            if (key === data.fieldGroup) return 0 // Field group matches! The group neighbor is the same as this group, no punishment for that!
                            groupFound = true
                        }
                        groupsFound++
                        if (groupFound == null) groupFound = false
                    }
                }
            }
        }
        if (groupFound == null) return 0
        if (groupsFound >= 2) return (-8 + groupsFound).toDouble() // New code for AI_TestmareB.
        return if (groupFound) -5 else -10
    }

    fun workWithWeapon(scores: ScoreParameters): Boolean {
        return this.weaponIsClick(scores.getWeapon())
    }
}