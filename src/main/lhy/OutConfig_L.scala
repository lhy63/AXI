package lhy

import spinal.core._
import spinal.core.sim._
import spinal.lib.bus.amba4.axilite.{AxiLite4, AxiLite4Config}
import spinal.lib._
import spinal.sim._

case class OutConfig_L (Dw: Int, Aw: Int) extends Component{
  val axiLiteConfig = AxiLite4Config(addressWidth = Aw, dataWidth = Dw)
  val io = new Bundle{
    val axiLiteSignal = slave (AxiLite4(axiLiteConfig))
    val ap_done = in Bool()
    val src_Width = out Bits(Dw bits)
    val src_Height = out Bits(Dw bits)
    val threshold = out Bits(Dw bits)
    val ap_start = out Bits(Dw bits)
  }
  noIoPrefix()
  val regSrcW = Reg(Bits(Dw bits))init(1023)
  val regSrcH = Reg(Bits(Dw bits))init(1023)
  val regThreshold = Reg(Bits(Dw bits))init(1023)
  val regApStart = Reg(Bits(Dw bits))init(1023)

  val regWrAddr = Reg(UInt(8 bits))init(255)
  val regRAddr = Reg(UInt(8 bits)) init(255)
  val updataWrAdrr = Reg(Bool())init(true)
  val updataRAdrr = Reg(Bool())init(true)
  val writeSuccess = Reg(Bool())init(false)

  //--------------------------AXI-LITE write logic, address bus -------------------------
  io.axiLiteSignal.aw.ready := updataWrAdrr
  when(io.axiLiteSignal.aw.fire){
    regWrAddr := io.axiLiteSignal.aw.payload.addr.asBits(7 downto(0)).asUInt
  }

  when(io.axiLiteSignal.aw.fire){
    updataWrAdrr := False
  }elsewhen(io.axiLiteSignal.w.fire){
    updataWrAdrr := True
  }

  //--------------------------AXI-LITE write logic, data bus -------------------------
  io.axiLiteSignal.w.ready := !updataWrAdrr

  when(io.axiLiteSignal.w.fire){
    switch(regWrAddr){
      is(0 * Dw/8){
        regSrcW := io.axiLiteSignal.w.payload.data
      }
      is(1 * Dw/8){
        regSrcH := io.axiLiteSignal.w.payload.data
      }
      is(2 * Dw/8){
        regThreshold := io.axiLiteSignal.w.payload.data
      }
      is(3 * Dw/8){
        regApStart := io.axiLiteSignal.w.payload.data
      }
    }
  }

  when(io.axiLiteSignal.w.fire){
    writeSuccess := True
  }elsewhen(io.axiLiteSignal.b.fire){
    writeSuccess := False
  }

  //--------------------------AXI-LITE write logic, b bus -------------------------
  io.axiLiteSignal.b.valid := writeSuccess
  io.axiLiteSignal.b.payload.resp := 0

  //--------------------------AXI-LITE read logic, address bus -------------------------
  io.axiLiteSignal.ar.ready := updataRAdrr
  when(io.axiLiteSignal.ar.fire){
    regRAddr := io.axiLiteSignal.ar.payload.addr.asBits(7 downto(0)).asUInt

  }
  when(io.axiLiteSignal.ar.fire){
    updataRAdrr := False
  }elsewhen (io.axiLiteSignal.r.fire){
    updataRAdrr := True
  }
  //--------------------------AXI-LITE read logic, data bus -------------------------
  io.axiLiteSignal.r.payload.resp := 0
  when(!updataRAdrr){
    io.axiLiteSignal.r.valid := True
    switch(regRAddr){
      is(0 * Dw/8){
        io.axiLiteSignal.r.payload.data := regSrcW
      }
      is(1 * Dw/8){
        io.axiLiteSignal.r.payload.data := regSrcH
      }
      is(2 * Dw/8){
        io.axiLiteSignal.r.payload.data := regThreshold
      }
      is(3 * Dw/8){
        io.axiLiteSignal.r.payload.data := regApStart
      }
      default{
        io.axiLiteSignal.r.payload.data := 0
      }
    }
  }otherwise {
    io.axiLiteSignal.r.valid := False
    io.axiLiteSignal.r.payload.data := 0
  }
  when(io.ap_done){
    regApStart := 0
  }
  //io.src_Width := 0
  io.src_Width := regSrcW
  io.src_Height := regSrcH
  io.threshold := regThreshold
  io.ap_start := regApStart
}

object OutConfigSim_L extends App{
  val DW = 32
  val AW = 32
  val list_addr = List(0,4,8,12)
  val list_wdata = List(0,1,2,3)
  var idx = 0
  var idy = 0
  var idz = 0
  var idr = 0
  var idp = 0
 // SpinalVerilog(OutConfig_L(DW,AW))
  val compiled = SimConfig.withFstWave.allOptimisation.compile(OutConfig_L(DW,AW))
  compiled.doSim { dut =>
    import dut.{clockDomain, io}
    clockDomain.forkStimulus(10)
    io.axiLiteSignal.aw.payload.addr #= 1023
    io.axiLiteSignal.w.payload.data  #= 1023
    io.axiLiteSignal.w.payload.strb #= 15
    while (true) {
      clockDomain.waitSampling()
      if (idz <= 5) {
      io.ap_done #= false
      io.axiLiteSignal.b.ready #= true
      io.axiLiteSignal.aw.valid.randomize()
      if (dut.io.axiLiteSignal.aw.valid.toBoolean && dut.io.axiLiteSignal.aw.ready.toBoolean) {
        if (idx != 3) {
          idx = idx + 1
        } else {
          idx = idx
        }
      }
      io.axiLiteSignal.aw.payload.addr #= list_addr(idx)
      println(s"idy : $idy")
      io.axiLiteSignal.w.valid.randomize()
      if (io.axiLiteSignal.w.valid.toBoolean & io.axiLiteSignal.w.ready.toBoolean) {
        if (idy != 3) {
          idy = idy + 1
        } else {
          idy = idy
        }
        idz = idz + 1
      }
      io.axiLiteSignal.w.payload.data #= list_wdata(idy)
      //      if(idz == 5){
      //        simSuccess()
      //      }
     }else{
        io.axiLiteSignal.ar.valid.randomize()
        if (dut.io.axiLiteSignal.ar.valid.toBoolean && dut.io.axiLiteSignal.ar.ready.toBoolean) {
          if (idr != 3) {
            idr = idr + 1
          } else {
            idr = idr
          }
        }
        io.axiLiteSignal.ar.payload.addr #= list_addr(idr)
        io.axiLiteSignal.r.ready.randomize()
        if(io.axiLiteSignal.r.valid.toBoolean & io.axiLiteSignal.r.ready.toBoolean){
          println(io.axiLiteSignal.r.payload.data.toBigInt)
          println(io.axiLiteSignal.r.payload.resp.toInt)
          idp = idp + 1
          println(s"idp : ${idp}")
        }
        if(idp == 5){simSuccess()}
        //simSuccess()
      }
    }
  }
}