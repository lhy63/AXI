package lhy

import spinal.core.Component
import spinal.lib.bus.amba4.axi._
import spinal.core._
import spinal.core.sim._
import spinal.lib._

import scala.collection.mutable.ListBuffer

case class AXI4MasterRead(AW:Int, DW:Int,IDW:Int,RLEN:BigInt) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )
  val io = new Bundle{
    val dataOut = master Stream(Bits(DW bits))
    val apStart = in Bool()

    val RAddrOffset = in UInt(AW bits)
    val Rlen = in UInt(AW bits)

    val apDone = out Bool()
    val axiAR = master Stream(Axi4Ar(axiConfig))
    val axiR = slave Stream(Axi4R(axiConfig))
  }
  val regApStart = RegNext(io.apStart)init(False)
  val regRAO = RegNext(io.RAddrOffset)init(U(0))
  val regALLRlen = RegNext(io.Rlen)init(U(0))
  val dataCounter = Counter(0,RLEN - 1)

  val regArAddr = Reg(UInt(AW bits))init(io.RAddrOffset)
  val regArLen = Reg(UInt(8 bits))init(U(0))
  val regConfigFinish = Reg(Bool())init(False)
  val regApDone = Reg(Bool())init(False)
  val firstIn = Reg(Bool())init(True)
  val regReadFinsh = Reg(Bool())init(False)
  val resdata = UInt(AW bits)

  val buffer = StreamFifo(Bits(DW bits),32)


  resdata := regALLRlen - dataCounter.value
  when(regApStart & !regConfigFinish & !regReadFinsh ){
    when(regALLRlen < 256){
      regArAddr := regRAO
      regArLen := (regRAO - 1).resized
      firstIn := False
    }otherwise{
      when(firstIn){
        regArAddr := regRAO
        firstIn := False
      }otherwise {
        regArAddr := (regArAddr + (axiConfig.bytePerWord)<<8).resized
      }
      when(resdata < 256){
        regArLen := (resdata - 1).resized
      }otherwise{
        regArLen := 255
      }
    }
    regConfigFinish := True
  }
  when(regApStart & regConfigFinish & !regReadFinsh){
    io.axiAR.valid := True
    io.axiAR.payload.addr := regArAddr
    io.axiAR.payload.burst := 1
    io.axiAR.payload.size := U(log2Up(DW/8))
    io.axiAR.payload.len := regArLen

    io.axiAR.payload.id := 0
    io.axiAR.payload.lock := 0
    io.axiAR.payload.region := 0
    io.axiAR.payload.cache := 0
    io.axiAR.payload.qos := 0
    io.axiAR.payload.user := 0
    io.axiAR.payload.prot := 0
  }otherwise{
    io.axiAR.valid := False
    io.axiAR.payload.addr := 0
    io.axiAR.payload.burst := 0
    io.axiAR.payload.size := 0
    io.axiAR.payload.len := 0

    io.axiAR.payload.id := 0
    io.axiAR.payload.lock := 0
    io.axiAR.payload.region := 0
    io.axiAR.payload.cache := 0
    io.axiAR.payload.qos := 0
    io.axiAR.payload.user := 0
    io.axiAR.payload.prot := 0
  }
  io.axiR.ready := (buffer.io.availability =/= 0)
  when(io.axiR.fire){
    dataCounter.increment()
    buffer.io.push.valid := True
    buffer.io.push.payload := io.axiR.payload.data
  }otherwise {
    buffer.io.push.valid := False
    buffer.io.push.payload := 0
  }
  when(io.axiR.payload.last){
    regConfigFinish := False
  }
  when(buffer.io.occupancy =/= 0 ){
    buffer.io.pop <> io.dataOut
  }otherwise {
    io.dataOut.valid := False
    io.dataOut.payload := 0
    buffer.io.pop.ready := False
  }
  when(dataCounter.willOverflow){
    regReadFinsh := True
  }
  when(regReadFinsh & buffer.io.occupancy === 1 &(io.dataOut.fire)){
    regApDone := True
  }
  io.apDone := regApDone

  when(regApDone){
    regConfigFinish := False
    firstIn := True
    regReadFinsh := False
    buffer.io.flush
    regApDone := False
  }

}

case class TopAxiMasterWandRWithSlave(AW:Int, DW:Int,IDW:Int,WLEN:BigInt) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )
  val io = new Bundle {
    val rdataOut = master Stream(Bits(DW bits))
    val rapStart = in Bool()
    val RAddrOffset = in UInt(AW bits)
    val Rlen = in UInt(AW bits)
    val rapDone = out Bool()

    val wapStart = in Bool()
    val wdata_in = slave(Stream(UInt(DW bits)))
    val WAddrOffset = in UInt(AW bits)
    val Wlen = in UInt(AW bits)
    val wapDone = out Bool()
  }
  val axiSlave = Axi4_transmission(AW:Int, DW:Int, IDW:Int)
  val axiMasterRead = AXI4MasterRead(AW,DW,IDW,WLEN)
  val axiMasterWrite = AXI4MasterWriteWithBuffer(AW,DW,IDW,WLEN)
  axiMasterRead.io.axiAR >> axiSlave.io.axiSignal.ar
  axiSlave.io.axiSignal.r >> axiMasterRead.io.axiR

  io.rdataOut <> axiMasterRead.io.dataOut
  axiMasterRead.io.apStart := io.rapStart
  axiMasterRead.io.RAddrOffset := io.RAddrOffset
  axiMasterRead.io.Rlen := io.Rlen
  io.rapDone := axiMasterRead.io.apDone

  axiMasterWrite.io.axiAW >> axiSlave.io.axiSignal.aw
  axiMasterWrite.io.axiW >> axiSlave.io.axiSignal.w
  axiSlave.io.axiSignal.b >> axiMasterWrite.io.axiB

  axiSlave.io.axireset := False

  axiSlave.io.apstart := io.wapStart | io.rapStart
  axiMasterWrite.io.apStart := io.wapStart
  axiMasterWrite.io.Wlen := io.Wlen
  axiMasterWrite.io.WAddrOffset := io.WAddrOffset
  io.wapDone := axiMasterWrite.io.apDone
  io.wdata_in >> axiMasterWrite.io.data_in

}

object AXI4MasterReadMain extends App{
  val Dw = 8
  val Aw = 32
  val IDw = 4
  val WLEN = 300
  var dataIdx = 0
  val dataList = (0 to WLEN).map(idx => idx).toList
  //SpinalVerilog(AXI4MasterRead(Aw,Dw,IDw,WLEN))
  val compiled = SimConfig.withFstWave.allOptimisation.compile(TopAxiMasterWandRWithSlave(Aw,Dw,IDw,WLEN))
  compiled.doSim{ dut =>
    import dut.{clockDomain, io}
    clockDomain.forkStimulus(10)
    io.rapStart #= false
    io.wapStart #= false
    io.Wlen #= 2
    io.Rlen #= 2
    io.WAddrOffset #= 0
    io.RAddrOffset #= 0
    io.rdataOut.ready #= false
    io.wdata_in.valid #= false
    io.wdata_in.payload #= 0
    //io.axiB.valid #= false
    while (true){
      clockDomain.waitSampling()
      io.wapStart #= true
      io.Wlen #= WLEN
      io.WAddrOffset #= 0
      //io.wdata_in.valid #= true
      io.wdata_in.valid.randomize()
      if(io.wdata_in.valid.toBoolean & io.wdata_in.ready.toBoolean){
        if(dataIdx < 255) {
          dataIdx = dataIdx + 1
        }else{
          dataIdx = 0
        }
      }
      io.wdata_in.payload #= dataList(dataIdx)

      val rdata = new ListBuffer[Int]
      if(io.wapDone.toBoolean){
        io.wapStart #= false
        println(s"finish write data")
        while (true){
          clockDomain.waitSampling()
          io.rapStart #= true
          io.Rlen #= WLEN
          io.RAddrOffset #= 0
          //io.rdataOut.ready #= true
          io.rdataOut.ready.randomize()
          if(io.rdataOut.valid.toBoolean & io.rdataOut.ready.toBoolean){
            rdata.append(io.rdataOut.payload.toInt)
          }
          if(io.rapDone.toBoolean){
            io.rapStart #= false
            clockDomain.waitSampling()
            rdata.map(idx => println(idx))
            simSuccess()
          }
        }
      }
    }


  }
}
