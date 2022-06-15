package net.zomis.games.server2.djl

data class AgentParameters(
    val learningRate: Float, val discountFactor: Float, val randomMoveProbability: Double, val batchSize: Int,
    val printConfig: PrintConfigs
)
fun String.printIf(condition: Boolean) {
    if (condition) println(this)
}

data class PrintConfigs(
    val state: Boolean,
    val output: Boolean,
    val experience: Boolean,
    val training: Boolean,
    val playing: Boolean
) {
    fun state(s: String) = s.printIf(state)
    fun output(s: String) = s.printIf(output)
    fun experience(s: String) = s.printIf(experience)
    fun training(s: String) = s.printIf(training)
    fun playing(s: String) = s.printIf(playing)
}
