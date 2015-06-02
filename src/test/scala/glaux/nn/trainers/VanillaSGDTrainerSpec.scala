package glaux.nn.trainers

import glaux.nn.Dimension.{TwoD, Row}
import glaux.nn.Net.SimpleNet
import glaux.nn.layers.{Regression, FullyConnected}
import glaux.nn.trainers.BatchTrainer.{SGDOptions, VanillaSGD}
import glaux.nn.{Matrix, InputLayer, RowVector, Net}
import org.specs2.mutable.Specification

class VanillaSGDTrainerSpec extends Specification {

  val inputLayer = InputLayer[RowVector](Row(3))
  val hiddenLayer = FullyConnected(Matrix.uniform(TwoD(3, 1), 0.5), RowVector(1))
  val lossLayer = Regression(1)
  val initNet: SimpleNet[RowVector] = SimpleNet(inputLayer, Seq(hiddenLayer), lossLayer)

  val trainer = VanillaSGD[SimpleNet[RowVector]](SGDOptions(learningRate = 0.05))
  val initResult = trainer.init(initNet)

  "train summation" >> {
    val batches = 0.until(20).map(_ => Seq(
      (RowVector(1,1,1), RowVector(4)),
      (RowVector(3,4,6), RowVector(14)),
      (RowVector(2,1,5), RowVector(9))
    ))
    val finalResult = batches.foldLeft(initResult){ (lastResult, batch) =>
      trainer.trainBatch(batch, lastResult)
    }

    val result = finalResult.net.predict(RowVector(2,3,4))
    result.dimension.size === 1
    result(0) must  beCloseTo(10.0 within 3.significantFigures)
  }

}