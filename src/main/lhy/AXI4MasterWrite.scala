package lhy

import spinal.core.Component
import spinal.lib.bus.amba4.axi._
import spinal.core._
import spinal.core.sim._
import spinal.lib._


case class AXI4MasterWrite(AW:Int, DW:Int,IDW:Int,WLEN:BigInt) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )

  val io = new Bundle{
    val apStart = in Bool()
    val data_in = slave(Stream(UInt(DW bits)))
    val WAddrOffset = in UInt(AW bits)
    val Wlen = in UInt(AW bits)


    val axiAW = master Stream(Axi4Aw(axiConfig))
    val axiW = master Stream(Axi4W(axiConfig))
    val axiB = slave Stream(Axi4B(axiConfig))

    val apDone = out Bool()
  }

  val regApStart = RegNext(io.apStart)init(False)
 // val regApStart = Reg(Bool())init(False)
  val regWAO = RegNext(io.WAddrOffset)init(U(0))
  val regALLWlen = RegNext(io.Wlen)init(U(0))
  val dataCounter = Counter(0,WLEN - 1)
  val data255flag = Counter(0,255)
  val regAwAddr = Reg(UInt(AW bits))init(io.WAddrOffset)
  val regAwLen = Reg(UInt(8 bits))init(U(0))
  val regConfigFinish = Reg(Bool())init(False)
  val regwlast = Reg(Bool())init(False)
  val regApDone = Reg(Bool())init(False)
  val resdata = UInt(AW bits)
  val firstIn = Reg(Bool())init(True)

  //regApStart := io.apStart

  //dataCounter.value is the number of  successed transfered data in this clk
  resdata := regALLWlen - dataCounter.value
  io.axiB.ready := True
  when(regApStart & !regConfigFinish & !regApDone & (firstIn | io.axiB.fire)){
    when(regALLWlen < 256){
      regAwAddr := regWAO
      regAwLen := (regWAO - 1).resized
      firstIn := False
    }otherwise{
      when(firstIn){
        regAwAddr := regWAO
        firstIn := False
      }otherwise{
        regAwAddr := (regAwAddr + (axiConfig.bytePerWord)<<8).resized
      }
      when(resdata < 256){
        regAwLen := (resdata - 1).resized
      }otherwise{
        regAwLen := 255
      }
    }
    regConfigFinish := True
  }

  when(regApStart & !regApDone & regConfigFinish){

    io.axiAW.valid := True
    io.axiAW.payload.addr := regAwAddr
    io.axiAW.payload.burst := 1
    io.axiAW.payload.size := U(log2Up(DW/8))
    io.axiAW.payload.len := regAwLen

    io.axiAW.payload.id := 0
    io.axiAW.payload.lock := 0
    io.axiAW.payload.region := 0
    io.axiAW.payload.cache := 0
    io.axiAW.payload.qos := 0
    io.axiAW.payload.user := 0
    io.axiAW.payload.prot := 0

    io.data_in.ready := io.axiW.ready
//    io.axiW.valid := io.data_in.valid
//    io.axiW.payload.data := io.data_in.payload.asBits
//    io.axiW.payload.last := regwlast
//    io.axiW.payload.strb := (0 to DW/8 - 1).map(idx => B(1)).asBits()

  }otherwise{
    io.data_in.ready := False

    io.axiAW.valid := False
    io.axiAW.payload.addr := 0
    io.axiAW.payload.burst := 0
    io.axiAW.payload.id := 0
    io.axiAW.payload.size := 0
    io.axiAW.payload.len := 0
    io.axiAW.payload.lock := 0
    io.axiAW.payload.region := 0
    io.axiAW.payload.cache := 0
    io.axiAW.payload.qos := 0
    io.axiAW.payload.user := 0
    io.axiAW.payload.prot := 0

//    io.axiW.valid := False
//    io.axiW.payload.data := 0
//    io.axiW.payload.strb := 0
//    io.axiW.payload.last := False

  }
  io.axiW.valid := io.data_in.valid
  io.axiW.payload.data := io.data_in.payload.asBits
  io.axiW.payload.last := regwlast
  io.axiW.payload.strb := (0 to DW/8 - 1).map(idx => B(1)).asBits()
  io.apDone := regApDone

  when(io.axiW.fire){
    dataCounter.increment()
    data255flag.increment()
  }
  when(data255flag.willOverflow){
    regConfigFinish := False
  }
  //when(dataCounter.value === io.Wlen){
  when(dataCounter.willOverflow){
    regApDone := True
    dataCounter.clear()
    data255flag.clear()
  }
  when(data255flag.value === 254 | dataCounter.value === io.Wlen - 2 & (dataCounter.value =/=0)){
    regwlast := True
  }otherwise{
    regwlast := False
  }
}

case class AXI4MasterWriteWithBuffer(AW:Int, DW:Int,IDW:Int,WLEN:BigInt) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )

  val io = new Bundle{
    val apStart = in Bool()
    val data_in = slave(Stream(UInt(DW bits)))
    val WAddrOffset = in UInt(AW bits)
    val Wlen = in UInt(AW bits)


    val axiAW = master Stream(Axi4Aw(axiConfig))
    val axiW = master Stream(Axi4W(axiConfig))
    val axiB = slave Stream(Axi4B(axiConfig))

    val apDone = out Bool()
  }

  val regApStart = RegNext(io.apStart)init(False)
  val regWAO = RegNext(io.WAddrOffset)init(U(0))
  val regALLWlen = RegNext(io.Wlen)init(U(0))
  val dataCounter = Counter(0,WLEN - 1)
  val dataInCounter = Counter(0,WLEN - 1)
  val data255flag = Counter(0,255)
  val regAwAddr = Reg(UInt(AW bits))init(io.WAddrOffset)
  val regAwLen = Reg(UInt(8 bits))init(U(0))
  val regConfigFinish = Reg(Bool())init(False)
  val regwlast = Reg(Bool())init(False)
  val regApDone = Reg(Bool())init(False)
  val resdata = UInt(AW bits)
  val firstIn = Reg(Bool())init(True)

  //val firstInForClearDone = Reg(Bool())init(True)

  val buffer = StreamFifo(Bits(DW bits),32)
  val regWriteFinish = Reg(Bool())init(False)

  io.data_in.ready := (buffer.io.availability =/= 0) & !regWriteFinish & regApStart
  when(io.data_in.fire){
    buffer.io.push.valid := True
    buffer.io.push.payload := io.data_in.payload.asBits
    dataInCounter.increment()
  }otherwise{
    buffer.io.push.valid := False
    buffer.io.push.payload := 0
  }

  resdata := regALLWlen - dataCounter.value
  io.axiB.ready := True
  when(regApStart & !regConfigFinish & !regWriteFinish & (firstIn | io.axiB.fire)){
    when(regALLWlen < 256){
      regAwAddr := regWAO
      regAwLen := (regWAO - 1).resized
      firstIn := False
    }otherwise{
      when(firstIn){
        regAwAddr := regWAO
        firstIn := False
      }otherwise{
        regAwAddr := (regAwAddr + (axiConfig.bytePerWord)<<8).resized
      }
      when(resdata < 256){
        regAwLen := (resdata - 1).resized
      }otherwise{
        regAwLen := 255
      }
    }
    regConfigFinish := True
  }

  when(regApStart & !regApDone & regConfigFinish){

    io.axiAW.valid := True
    io.axiAW.payload.addr := regAwAddr
    io.axiAW.payload.burst := 1
    io.axiAW.payload.size := U(log2Up(DW/8))
    io.axiAW.payload.len := regAwLen

    io.axiAW.payload.id := 0
    io.axiAW.payload.lock := 0
    io.axiAW.payload.region := 0
    io.axiAW.payload.cache := 0
    io.axiAW.payload.qos := 0
    io.axiAW.payload.user := 0
    io.axiAW.payload.prot := 0

  }otherwise{
    io.axiAW.valid := False
    io.axiAW.payload.addr := 0
    io.axiAW.payload.burst := 0
    io.axiAW.payload.id := 0
    io.axiAW.payload.size := 0
    io.axiAW.payload.len := 0
    io.axiAW.payload.lock := 0
    io.axiAW.payload.region := 0
    io.axiAW.payload.cache := 0
    io.axiAW.payload.qos := 0
    io.axiAW.payload.user := 0
    io.axiAW.payload.prot := 0
  }

  io.axiW.valid := buffer.io.pop.valid
  io.axiW.payload.data := buffer.io.pop.payload
  buffer.io.pop.ready := io.axiW.ready
  io.axiW.payload.last := regwlast & (io.axiW.fire)
  io.axiW.payload.strb := (0 to DW/8 - 1).map(idx => B(1)).asBits()
  io.apDone := regApDone

  when(io.axiW.fire){
    dataCounter.increment()
    data255flag.increment()
  }
  when(data255flag.willOverflow){
    regConfigFinish := False
  }
  buffer.io.flush := False
  when(dataCounter.willOverflow){
    regApDone := True
    dataCounter.clear()
    data255flag.clear()
  }
  when(dataInCounter.willOverflow){
    regWriteFinish := True
    dataInCounter.clear()
  }

  when((dataCounter.value =/=0) & (data255flag.value === 254  | dataCounter.value === io.Wlen - 2 ) & (io.axiW.fire)){
    regwlast := True
  }elsewhen (regwlast & io.axiW.fire){
    regwlast := False
  }
  when(regApDone){
    //regApStart := False   //???
    regConfigFinish := False
    firstIn := True
    buffer.io.flush := True
    regApDone := False   // only true in a clk, so need to update the value of apStart in this clk
    regWriteFinish := False
  }

}

case class TopAxiMasterWriteWithSlave(AW:Int, DW:Int,IDW:Int,WLEN:BigInt) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )
  val io = new Bundle{
    val apStart = in Bool()
    val data_in = slave(Stream(UInt(DW bits)))
    val WAddrOffset = in UInt(AW bits)
    val Wlen = in UInt(AW bits)
    val apDone = out Bool()


    //val axiR = master Stream(Axi4Ar(axiConfig))
    //val axiB = master Stream(Axi4B(axiConfig))
  }
  val axiSlaveWrite = Axi4_transmission(AW:Int, DW:Int, IDW:Int)
  val axiMasterWrite = AXI4MasterWrite(AW,DW,IDW,WLEN)

  axiMasterWrite.io.axiAW >> axiSlaveWrite.io.axiSignal.aw
  axiMasterWrite.io.axiW >> axiSlaveWrite.io.axiSignal.w
  axiSlaveWrite.io.axiSignal.b >> axiMasterWrite.io.axiB
  //io.axiB.valid := axiSlaveWrite.io.axiSignal.b

  axiSlaveWrite.io.apstart := io.apStart
  axiMasterWrite.io.apStart := io.apStart
  axiMasterWrite.io.Wlen := io.Wlen
  axiMasterWrite.io.WAddrOffset := io.WAddrOffset
  io.apDone := axiMasterWrite.io.apDone
  io.data_in >> axiMasterWrite.io.data_in


}
object AXI4MasterWriteMain_verilog extends App{
  val Dw = 8
  val Aw = 32
  val IDw = 4
  val WLEN = 300
  var dataIdx = 0
  val dataList = (0 to WLEN).map(idx => idx).toList
  SpinalVerilog(AXI4MasterWrite(Aw,Dw,IDw,WLEN))
}
object AXI4MasterWriteMain extends App{
  val Dw = 8
  val Aw = 32
  val IDw = 4
  val WLEN = 300
  var dataIdx = 0
  val dataList = (0 to WLEN).map(idx => idx).toList
  //SpinalVerilog(AXI4MasterWrite(Aw,Dw,IDw,WLEN))
  val compiled = SimConfig.withFstWave.allOptimisation.compile(TopAxiMasterWriteWithSlave(Aw,Dw,IDw,WLEN))
  compiled.doSim{ dut =>
    import dut.{clockDomain, io}
    clockDomain.forkStimulus(10)
    io.apStart #= false
    io.Wlen #= 2
    io.WAddrOffset #= 0
    io.data_in.valid #= false
    io.data_in.payload #= 0
    //io.axiB.valid #= false
    while (true){
      clockDomain.waitSampling()
      io.apStart #= true
      io.Wlen #= WLEN
      io.WAddrOffset #= 0
      io.data_in.valid #= true  //random test

      if(io.data_in.valid.toBoolean & io.data_in.ready.toBoolean){

        //if(dataIdx < WLEN -1){
        if(dataIdx < 255) {
          dataIdx = dataIdx + 1
        }else{
          dataIdx = 0
        }
      }
      io.data_in.payload #= dataList(dataIdx)
      if(io.apDone.toBoolean){
        clockDomain.waitSampling(10)
        simSuccess()
      }
    }
  }
}
