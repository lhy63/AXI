import spinal.core.Component.push
import spinal.lib._
import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba4.axi.Axi4

import scala.collection.mutable.ListBuffer
import scala.math.random
import scala.util.Random._

package object lhy {
  case class IPConfig(threshold: Int, srcH: Int = 540, srcW: Int = 960, tagH: Int = 2160, tagW: Int = 3840, dataW: Int = 8)

  implicit class InitUtils(port: Bundle) {
    def setInvalid(): Unit = {
      port.flatten.foreach {
        case bool: Bool =>
          if (bool.isOutput) bool := False
        case bitVector: BitVector =>
          if (bitVector.isOutput) bitVector match {
            case uint: UInt => uint := U(0).resized
            case bits: Bits => bits := B(0).resized
            case _          =>
          }
        case bundle: Bundle =>
          bundle.setInvalid()
        case _ =>
      }
    }
  }

  def L_incr(address : UInt,burst : Bits,len : UInt,size : UInt,bytePerWord : Int) : UInt = {
    val area = new Area {
      val maxSize = log2Up(bytePerWord)
      val validSizeWidth = log2Up(maxSize + 1)
      val validSize = size(0, validSizeWidth bits)
      val result = UInt(address.getWidth bits)
      val highCat = if (address.getWidth > 12) address(address.high downto 12) else U""
      val sizeValue = (0 to maxSize).map(idx => idx === validSize).asBits.asUInt
      val alignMask = (0 to maxSize - 1).map(idx => idx < validSize).asBits.asUInt.resize(12)
      val base = address(Math.min(12, address.getWidth) - 1 downto 0).resize(12) & ~alignMask
      val baseIncr = base + sizeValue
      val wrapCaseMax = maxSize + 3 // 3 is the maximum result of the len.mux() below
      val wrapCaseWidth = log2Up(wrapCaseMax + 1)
      val wrapCase = validSize.resize(wrapCaseWidth) + len.mux(
        M"----1---" -> U"11",
        M"----01--" -> U"10",
        M"----001-" -> U"01",
        default -> U"00"
      )
      switch(burst) {
        is(Axi4.burst.FIXED) {
          result := address
        }
        is(Axi4.burst.WRAP) {
          val cases = Vec((0 to wrapCaseMax).map(i => base(11 downto i + 1) @@ baseIncr(i downto 0)))
          result := (highCat @@ cases(wrapCase)).resized
        }
        default {
          result := (highCat @@ baseIncr).resized
        }
      }
    }.setWeakName("Axi4Incr")
    area.result
  }


}
