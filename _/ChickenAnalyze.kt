package net.zomis.minesweeper.ais.utils

import net.zomis.UtilZomisUtils

class ChickenAnalyze(map: Flags.Model?, analyze: AnalyzeProvider) {
    private val analyze: AnalyzeProvider
    private val openFields: MutableList<OpenField>
    var safeCount = 0
        private set
    private val unsafeReveal: MutableSet<Flags.Field>
    private val unsafe: MutableSet<Flags.Field>
    val unsafeCount: Int
        get() = unsafe.size

    fun getUnsafe(): Set<Flags.Field> {
        return HashSet<Flags.Field>(unsafe)
    }

    fun getOpenFields(): List<OpenField> {
        return ArrayList<OpenField>(openFields)
    }

    fun getUnsafeReveal(): Set<Flags.Field> {
        return HashSet<Flags.Field>(unsafeReveal)
    }

    init {
//		this.map = map;
        this.analyze = analyze
        openFields = ArrayList<OpenField>()
        unsafeReveal = HashSet<Flags.Field>()
        unsafe = HashSet<Flags.Field>()
    }

    fun analyze(): ChickenAnalyze {
        val approxer = OpenFieldApproxer()
        val scorer: AbstractScorer = AvoidReveal50()
        val fieldOfInterest: MutableSet<ProbabilityKnowledge<Flags.Field>> =
            HashSet<ProbabilityKnowledge<Flags.Field>>()
        for (know in analyze.getAllKnowledge()) {
            val ofProb: Double = know.probabilities.get(0)
            if (ofProb == 1.0) {
                if (approxer.expectedFrom(analyze, know.field) === 0) addOpenField(know)
            } else if (ofProb == 0.0) {
                if (scorer.getScoreFor(know.field, know, null) === 0) fieldOfInterest.add(know)
            }
        }
        for (data in fieldOfInterest) {
            if (MineprobHelper.getCertainValue(data) != null) {
                if (!existsInOpenField(data.field)) {
                    safeCount++
                }
            } else if (data.mineProbability == 0.0) {
                addUnsafe(data)
                // Unsafe click, check the neighbors of the knowledge.
            }
        }
        // TODO: Add analyze of untaken safe 50%-mines??

        /* Count odd-sized open fields
		 * Count even-sized open fields
		 * Count non-revealing fields --- do not count the ones close to one of the above
		 * Count small revealing fields (with risk of revealing 100%)
		 * 
		 * Check for common neighbors in small revealing fields
		 **/

        // _____112a2bb21a1-_____1b2234a__2_-11___1233b2__12b-_b2a212bb2___b2_-22323b332211__2_-b23b44b202a21b_1-__bb4bb324a223a1-__224b42aa211a32-____2b2122223_3b-___________aa4a_-__x__x__123a_aa2-xx_____2a3b22221-_______3a3_321__-x______a211aa1__-121212a2_____1__-__x_x__1________
        // x____a3b11111bb1-_1___2b222b11221-______122b211221-_xx___12b2223ba2-_x____2a421bb55b-__x___3ab3333bb2-x_x___b33bb11221-_x__122123421___-____1a1_1a3a311_-____1_____3a3b1_-________________-x___xx__________-__x______xx___xx-x_________x_____-_1_x_x_______x_x-___x_____x_x____
        // 23211111222b2222-baa11b__bb43b3ba-233221_a5b6a4a32-112a1__14baa4210-2a2221__2a54b100-b212b21___b21100-11_3b42aa_221000-___2ba22211b1000-___122___1233210-__________a2aa31-_x____x__1245b3a-__x_x_____1aa221-______x_x_1221__-x___x___________-_xx____x________-_________xx___x_
        return this
    }

    private fun addUnsafe(data: ProbabilityKnowledge<Flags.Field>) {
        unsafe.add(data.field)
        unsafeReveal.addAll(getUnsafeRevealFor(data))
    }

    private fun addOpenField(know: ProbabilityKnowledge<Flags.Field>) {
        if (existsInOpenField(know.field)) return  // Each field will only be connected to one OpenField, because of the neighborset.
        val of: OpenField = OpenField.construct(analyze, know)
        //		logger.info("OF ADDED: " + of);
        openFields.add(of)
    }

    private fun existsInOpenField(field: Flags.Field): Boolean {
        for (of in openFields) if (of.hasField(field)) return true
        return false
    }

    override fun toString(): String {
        return (UtilZomisUtils.implode(", ", openFields) + " - extraSafe: " + safeCount + " unsafe: " + unsafe
                + " unsafeReveal: " + UtilZomisUtils.implode(", ", unsafeReveal))
    }

    fun getSafeRevealedBy(analyze: AnalyzeProvider, fieldData: ProbabilityKnowledge<Flags.Field>): Int {
        require(unsafe.contains(fieldData.field))
        //		Logger.getLogger(getClass()).info(fieldData.getField() + " would reveal what?");
        var i = 0
        val myRevealOriginal: Set<Flags.Field> = getUnsafeRevealFor(fieldData)
        //		Logger.getLogger(getClass()).info(fieldData.getField() + " unsafe reveal is " + myRevealOriginal);
        for (ff in unsafe) {
            if (ff === fieldData.field) continue
            val ffReveal: MutableSet<Flags.Field> = getUnsafeRevealFor(analyze.getKnowledgeFor(ff))
            //			Logger.getLogger(getClass()).info("ffReveal is " + ff + ", " + ffReveal);
            val myReveal: Set<Flags.Field> = HashSet<Flags.Field>(myRevealOriginal)
            ffReveal.removeAll(myReveal)
            //			Logger.getLogger(getClass()).info("myReveal " + myReveal + ". ffReveal " + ffReveal);
            if (ffReveal.size == 0) {
                i++
            }
        }
        return i
    }

    companion object {
        fun getUnsafeRevealFor(data: ProbabilityKnowledge<Flags.Field>): MutableSet<Flags.Field> {
            val result: MutableSet<Flags.Field> = HashSet<Flags.Field>()
            for ((key, value) in data.neighbors.entrySet()) {
                if (key.size() !== value) { // Not neighbor with all of them
                    val neighbors: MutableCollection<Flags.Field> = data.field.neighbors
                    neighbors.retainAll(key)
                    result.addAll(neighbors)
                }
            }
            return result
        }
    }
}