package net.zomis.minesweeper.analyze.utils

import net.zomis.UtilZomisUtils

class OpenFieldApproxer {
    private var totalProb = 0.0
    private var analyze: AnalyzeProvider? = null
    fun floodFill(analyze: AnalyzeProvider, field: Flags.Field): Double {
        val ff: Collection<Flags.Field> = java.util.ArrayList<Flags.Field>()
        totalProb = 0.0
        this.analyze = analyze
        UtilZomisUtils.recursiveAdd(ff, field, Recursive())
        return totalProb - closestMineprobSum(analyze, field)
    }

    private fun closestMineprobSum(analyze: AnalyzeProvider, field: Flags.Field): Double {
        var total = 0.0
        val fields: MutableCollection<Flags.Field> = field.neighbors
        fields.add(field)
        for (ff in fields) {
            val know: ProbabilityKnowledge<Flags.Field> = analyze.getKnowledgeFor(ff)
            if (know != null) total += know.mineProbability
        }
        return total
    }

    private inner class Recursive : UtilZomisUtils.RecursiveInterface<Flags.Field?> {
        private val added: MutableSet<Flags.Field> = java.util.HashSet<Flags.Field>()
        fun performAdd(from: Flags.Field?, to: Flags.Field): Boolean {
            if (to.isClicked()) return false
            if (analyze.getKnowledgeFor(to) == null) return false
            if (analyze.getKnowledgeFor(to).getProbabilities().get(0) <= 0) return false
            if (added.contains(to)) return false
            added.add(to)
            //			Zomis.echo("Adding " + to);
            totalProb += analyze.getKnowledgeFor(to).getMineProbability()
            return true
        }

        fun performRecursive(from: Flags.Field?, to: Flags.Field): Boolean {
            return !to.isClicked() && analyze.getKnowledgeFor(to) != null && analyze.getKnowledgeFor(to)
                .getProbabilities().get(0) > 0
        }

        fun getRecursiveFields(field: Flags.Field): Collection<Flags.Field> {
            return field.neighbors
        }
    }

    fun expectedFrom(analyze: AnalyzeProvider, field: Flags.Field): Double {
        if (field.isClicked()) return 0
        var queue: java.util.Queue<Flags.Field?>
        var nextQ: java.util.Queue<Flags.Field?> = LinkedList<Flags.Field>()
        val completed: MutableSet<Flags.Field> = java.util.HashSet<Flags.Field>()
        val finalFields: MutableSet<Flags.Field> = java.util.HashSet<Flags.Field>()
        // Perform 3 iterations and count the total mine probability
//		double total = 0;
        var total: Double = analyze.getKnowledgeFor(field).getMineProbability()
        var iterations = 0
        nextQ.add(field)
        while (iterations < 3) {
            iterations++
            queue = nextQ
            nextQ = LinkedList<Flags.Field>()
            var foundProbability = false
            if (queue.isEmpty()) break
            while (!queue.isEmpty()) {
                val next: Flags.Field = queue.poll()
                completed.add(next)
                for (ff in next.getNeighbors()) {
                    val probData: ProbabilityKnowledge<Flags.Field> = analyze.getKnowledgeFor(ff)
                    if (!ff.isClicked() && !nextQ.contains(ff) && !queue.contains(ff) && !completed.contains(ff)) {
                        if (probData.probabilities.get(0) < 1.0) finalFields.add(ff)
                        if (probData.probabilities.get(0) <= 0) continue
                        nextQ.add(ff)
                        if (probData != null) {
                            total += probData.mineProbability
                            if (probData.mineProbability != 0.0) {
                                foundProbability = true
                            }
                        }
                    } else if (ff.isClicked()) finalFields.add(ff) // Added fix for 6f in this situation: 2a2_2ba2001a1000-_b2_2bb200122100-232_23310112b210-b_a_2b2001b44b21-_2__2b20123ba22b-_12221112a233322-13ba3222b333b3a2-1aaa3ba212bb34a2-13a322332334a211-_111_13bb3b32200-_____1bb4b33a100-_____12222b32100-_11________b2100-_1a2__xx_xa3b100-_112b22333222210-________a___1a10
                    // Don't need to check if it already is added since we're using a HashSet.
                }
            }
            if (!foundProbability) iterations--
        }


        // Added fix for revealing 50% mines and stuff.
        val extras: MutableSet<Flags.Field> = java.util.HashSet<Flags.Field>()
        total = analyze.getKnowledgeFor(field).getMineProbability()
        for (finalField in finalFields) {
            for (neighbor in finalField.getNeighbors()) {
                if (neighbor.isClicked()) continue
                if (extras.contains(neighbor)) continue
                extras.add(neighbor)
                total += analyze.getKnowledgeFor(neighbor).getMineProbability()
            }
        }


        // Fix for total < closestMineprobSum

//		return Math.abs(total - this.closestMineprobSum(analyze, field));
        return -analyze.getKnowledgeFor(field).getMineProbability() * 2 + total - closestMineprobSum(analyze, field)
    }
}