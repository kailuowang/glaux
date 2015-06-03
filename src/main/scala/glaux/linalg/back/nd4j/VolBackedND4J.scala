package glaux.linalg.back.nd4j

import glaux.linalg.Dimension.{TwoD, ThreeD, Row, DimensionFactory}
import glaux.linalg._
import org.nd4j.linalg.api.ndarray.INDArray


trait VolBackedND4J extends Vol with VolOperationsImpl with WithIndArray

trait WithIndArray {
  val indArray: INDArray
}


trait VolOperationsImpl extends VolOperations {
  this: WithIndArray =>

  import Implicits.volBuilder
  import Implicits.toWithIndArray
  def +(that: Vol): Vol = this.indArray.add(that.indArray)

  def -(that: Vol): Vol = indArray.sub(that.indArray) 

  /** element-by-element multiplication */
  def *(that: Vol): Vol = indArray.mul(that.indArray)

  /** matrix multiplication */
  def **(that: Vol): Vol = indArray.mmul(that.indArray)

  /** matrix multiplication using Numpy syntax for arrays */
  def dot(that: Vol): Vol = indArray.dot(that.indArray)

  def /(that: Vol): Vol = indArray.div(that.indArray)

  /** right division ... is this the correct symbol? */
  def \(that: Vol): Vol = indArray.rdiv(that.indArray)

  def +(that: Number): Vol = indArray.add(that)
  def -(that: Number): Vol = indArray.sub(that)
  def *(that: Number): Vol = indArray.mul(that)
  def /(that: Number): Vol = indArray.div(that)
  def \(that: Number): Vol = indArray.rdiv(that)

  def T: Vol = indArray.transpose

}


protected sealed abstract class ND4JBackedVol(val indArray: INDArray) extends VolBackedND4J {
  val dimension: Dimensionality = dimensionFactory.create(indArray.shape())
  val dimensionFactory: DimensionFactory[Dimensionality]
  def iterable: Iterable[Double] = {
    val lv = indArray.linearView()
    val myLength = lv.size(Row.dimIndexOfData)

    new Iterable[Double] {
      def iterator = new Iterator[Double] {
        var index: Int = 0
        override def hasNext: Boolean = index < myLength
        override def next(): Double = {
          index += 1
          lv.getDouble(index - 1)
        }
      }
    }
  }

}

case class Vol3DImp(override val indArray: INDArray)      extends ND4JBackedVol(indArray) with Vol3D {
  val dimensionFactory = ThreeD
}
case class MatrixImp(override val indArray: INDArray)     extends ND4JBackedVol(indArray) with Matrix {
  val dimensionFactory = TwoD
}
case class RowVectorImp(override val indArray: INDArray)  extends ND4JBackedVol(indArray) with RowVector {
  val dimensionFactory = Row
  def apply(index: Int): Double = indArray.getDouble(index)
}


