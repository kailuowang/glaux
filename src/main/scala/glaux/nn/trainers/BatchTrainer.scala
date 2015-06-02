package glaux.nn.trainers

import glaux.nn._
import glaux.nn.trainers.BatchTrainer.RegularizationContext
import org.nd4j.api.linalg.DSL._

trait BatchTrainer[Trainee <: Net] {
  type Input = Trainee#Input
  type Output = Trainee#Output

  type CalculationContext <: RegularizationContext
  
  case class BatchResult(lossInfo: LossInfo, net: Trainee, batchSize: Int, calcContext: CalculationContext)

  val build: Net.CanBuildFrom[Trainee]

  val initialCalculationContext : CalculationContext

  val initialLossInfo = LossInfo(0,0,0)
  def init(net: Trainee) = BatchResult(initialLossInfo, net, 0, initialCalculationContext)


  def trainBatch(batch: Iterable[(Input, Output)], lastResult: BatchResult): BatchResult = {
    val net: Trainee = lastResult.net
    val pairs = batch.map {
      case (input, target) =>
        val dataFlow = net.forward(input.asInstanceOf[net.Input]) //unforutnately this is the cleanest way to encode Type
        net.backward(target, dataFlow)
    }
    val batchLoss = pairs.last._1 //todo: confirm on this
    val batchSize = pairs.size
    val paramsGrads = accumulate(pairs.map(_._2))

    val (newParams, newContext, lossInfo) = calculate(paramsGrads, lastResult, batchLoss, batchSize)

    val newLayers = newParams.map {
      case (l, ps) => l.updateParams(ps)
    }
    BatchResult(lossInfo, build(net, newLayers), batchSize, newContext)
  }

  def accumulate(netParamGradients: Iterable[NetParamGradients]): NetParamGradients =
    netParamGradients.reduce { (npg1, npg2) =>
      (npg1, npg2).zipped.map {
        case ((layer1, paramGradients1),(layer2, paramGradients2)) =>
          assert(layer1 == layer2)//assume sequence of the two NetParamGradients are the same, but assert the match here
          val newParamGradient = (paramGradients1, paramGradients2).zipped.map { (paramGradient1, paramGradient2) =>
            assert(paramGradient1.param == paramGradient2.param) //assume the sequence remain the same, but assert the match here
            paramGradient1.copy(value = paramGradient1.value.add(paramGradient2.value))
          }
          (layer1, newParamGradient)
      }
    }


  def calculate(paramGrads: NetParamGradients, lastIterationResult: BatchResult, loss: Loss, batchSize: Int ): (NetParams, CalculationContext, LossInfo)
}


case class LossInfo(l1Decay: Loss, l2Decay: Loss, cost: Loss) {
  val total: Loss = l1Decay + l2Decay + cost
}

object BatchTrainer {
  trait RegularizationContext {
    def l1Decay : Decay
    def l2Decay : Decay
  }

  abstract class SGDBase[NT <: Net: Net.CanBuildFrom](learningRate: Double) extends BatchTrainer[NT] {
    type Trainee = NT
    val build: Net.CanBuildFrom[Trainee] = implicitly[Net.CanBuildFrom[Trainee]]

    def calculate(netParamGradients: NetParamGradients, lastIterationResult: BatchResult, loss: Loss, batchSize: Int): (NetParams, CalculationContext, LossInfo) = {
      val lastContext = lastIterationResult.calcContext

      case class NewParamResult(newParam: LayerParam, l1DecayLoss: Loss, l2DecayLoss: Loss)

      def calcNewParam(paramGrad: ParamGradient, layer: Layer): NewParamResult = {
        val l1Decay =  lastContext.l1Decay * paramGrad.param.regularizationSetting.l1DM
        val l2Decay =  lastContext.l2Decay * paramGrad.param.regularizationSetting.l2DM
        val cb = implicitly[ Vol.CanBuildFrom[Vol]]
        val p2DL: Vol = (paramGrad.param.value * paramGrad.param.value) * l2Decay / 2
        val p1DL: Vol = paramGrad.param.value.map(Math.abs(_)) * l1Decay
        val l2DecayLoss: Loss = p2DL.sumAll
        val l1DecayLoss: Loss = p1DL.sumAll
        val pValue = paramGrad.param.value
        val l1grad: Vol = pValue.map(v => if(v > 0) 1 else -1) * l1Decay
        val l2grad: Vol = pValue * l2Decay
        val rawBatchGradient: Vol = (l1grad.add(l2grad).add(paramGrad.value)) / batchSize

        val newParamValue = pValue - (rawBatchGradient * learningRate) //todo: need to implement with momentum
        val newParam = paramGrad.param.copy(value = newParamValue)

        NewParamResult(
          newParam,
          l1DecayLoss,
          l2DecayLoss
        )
      }

      val results = (for {
        (layer, paramGrads) <- netParamGradients.toSeq
        paramGrad <- paramGrads
        newParamResult = calcNewParam(paramGrad, layer)
      } yield (layer, newParamResult)).toSeq
      val newNetParams: NetParams = results.groupBy(_._1).mapValues(_.map(_._2.newParam))
      val newContext = updateContext(lastContext)
      val l1DecayLoss = results.map(_._2.l1DecayLoss).sum
      val l2DecayLoss = results.map(_._2.l2DecayLoss).sum
      val lossInfo = LossInfo(l1DecayLoss, l2DecayLoss, loss)
      (newNetParams, newContext, lossInfo)
    }

    def updateContext(lastContext: CalculationContext): CalculationContext
  }

  case class VanillaSGD[NT <: Net: Net.CanBuildFrom](learningRate: Double) extends SGDBase[NT](learningRate) {
    case class VanillaSGDIterationContext(l1Decay: Decay, l2Decay: Decay) extends RegularizationContext

    type CalculationContext = VanillaSGDIterationContext

    val initialCalculationContext: VanillaSGDIterationContext = VanillaSGDIterationContext(0, 0)

    def updateContext(lastContext: VanillaSGDIterationContext) = lastContext

  }

  case class MomentumSGD[ NT <: Net: Net.CanBuildFrom](learningRate: Double) extends SGDBase[NT](learningRate) {
    case class ParamGSum(layer: Layer, param: LayerParam, value: Vol)

    case class MomentumSGDIterationContext(l1Decay: Decay, l2Decay: Decay, gsum: Seq[ParamGSum]) extends  RegularizationContext

    type CalculationContext = MomentumSGDIterationContext

    val initialCalculationContext: MomentumSGDIterationContext = MomentumSGDIterationContext(0, 0, Nil)

    def updateContext(lastContext: MomentumSGDIterationContext): MomentumSGDIterationContext = ???
  }

}