package net.zomis.games.server2.djl

import ai.djl.Model
import ai.djl.basicmodelzoo.basic.Mlp
import ai.djl.metric.Metrics
import ai.djl.ndarray.NDList
import ai.djl.ndarray.NDManager
import ai.djl.ndarray.types.DataType
import ai.djl.ndarray.types.Shape
import ai.djl.training.DefaultTrainingConfig
import ai.djl.training.Trainer
import ai.djl.training.dataset.ArrayDataset
import ai.djl.training.dataset.Dataset
import ai.djl.training.evaluator.Accuracy
import ai.djl.training.loss.Loss
import ai.djl.translate.Translator
import ai.djl.translate.TranslatorContext
import klog.KLoggers
import java.util.*

class DJL {

    private val logger = KLoggers.logger(this)

    fun fitModel(trainer: Trainer, trainingSet: Dataset, epochs: Int) {
        repeat(epochs) {
            logger.info { "Epoch $it" }
            trainer.iterateDataset(trainingSet).forEach {batch ->
                batch.use {
                    trainer.trainBatch(it)
                    trainer.step()
                }
            }
            trainer.endEpoch()
        }
    }

    class MyTranslator : Translator<FloatArray, FloatArray> {
        override fun processOutput(ctx: TranslatorContext, list: NDList): FloatArray {
            return list[0].toFloatArray()
        }

        override fun processInput(ctx: TranslatorContext, input: FloatArray): NDList {
            return NDList(ctx.ndManager.create(floatArrayOf(*input)))
        }
    }

    fun start() {
        val block = Mlp(2, 2, intArrayOf(2))

        Model.newInstance().use {model ->
            model.block = block

            val manager = NDManager.newBaseManager()
            val inputs = manager.create(arrayOf(
                floatArrayOf(0f, 0f),
                floatArrayOf(0f, 1f),
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 1f)
            )).toType(DataType.FLOAT32, false)
    //        val outputs = manager.create(floatArrayOf(0f, 0f, 0f, 1f)).toType(DataType.FLOAT32, false)
            val outputs = manager.create(arrayOf(
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 0f),
                floatArrayOf(1f, 0f),
                floatArrayOf(0f, 1f)
            )).toType(DataType.FLOAT32, false)

            val trainingSet = ArrayDataset.Builder().setData(inputs)
                    .setSampling(1, true)
                    .optLabels(outputs).build()
//            model.newPredictor(MyTranslator()).predict(floatArrayOf(0.5f, 0f)).let { println(it) }

            val trainingConfig = DefaultTrainingConfig(
//                    Loss.softmaxCrossEntropyLoss("SoftmaxCrossEntropyLoss", 1f, -1, false, false)
                Loss.l2Loss()
            ).addEvaluator(Accuracy()).setBatchSize(2)

            model.newTrainer(trainingConfig).use {trainer ->
                trainer.metrics = Metrics()

                val inputShape = Shape(2, 2)
                trainer.initialize(inputShape)
                fitModel(trainer, trainingSet, 100)

                val metrics = trainer.metrics
                println(metrics)
            }
            val sc = Scanner(System.`in`)
            val predictor = model.newPredictor(MyTranslator())
            while (true) {
                repeat(5) {
                    val a = Math.random().toFloat()
                    val b = Math.random().toFloat()
                    val result = predictor.predict(floatArrayOf(a, b))
                    println("$a, $b --> ${result!!.contentToString()}")
                }
                sc.nextLine()
            }
        }

    }

}

fun main() {
    DJL().start()
}
