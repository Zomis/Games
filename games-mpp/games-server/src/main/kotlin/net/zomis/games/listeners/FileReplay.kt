package net.zomis.games.listeners

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineScope
import net.zomis.games.dsl.GameListener
import net.zomis.games.dsl.impl.FlowStep
import java.nio.file.Path
import kotlin.io.path.outputStream

class FileReplay(private val file: Path?, private val replayListener: ReplayListener): GameListener {

    private val mapper = jacksonObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)

    override suspend fun handle(coroutineScope: CoroutineScope, step: FlowStep) {
        if (file == null) return
        if (step is FlowStep.ProceedStep) {
            save()
        }
    }

    fun save() {
        if (file == null) return
        mapper.writeValue(file.outputStream(), replayListener.data().serialize())
    }

}
