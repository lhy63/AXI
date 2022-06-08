package lhy

import spinal.core.Component
import spinal.lib.bus.amba4.axi._
import spinal.core._
import spinal.core.sim._
import spinal.lib._

import scala.collection.mutable.ListBuffer

case class Axi4_transmission(AW:Int, DW:Int, IDW:Int) extends Component{
  val axiConfig = Axi4Config(
    addressWidth = AW,
    dataWidth    = DW,
    idWidth      = IDW,
    arUserWidth  = 1,
    awUserWidth  = 1
  )
  val io = new Bundle{
    val axiSignal = slave(Axi4(axiConfig))
    val apstart = in Bool()
    val axireset = in Bool()
  }
  noIoPrefix()
  // the registers of current slave
  val regVec = Vec(Reg(Bits(8 bits))init(0),4096)

  val regAwAddr = Reg(UInt(12 bits))init(U(0))simPublic()
  val regAwId = Reg(UInt(axiConfig.idWidth bits))init(U(0))
  val regAwRegion = Reg(Bits(4 bits))init(B(0))
  val regAwLen = Reg(UInt(8 bits))init(U(0))    // default burst len: 1
  val regAwSize = Reg(UInt(3 bits))init(U(5))   // default burst size: 32 byte
  val regAwBrust = Reg(Bits(2 bits))init(B(1))  // default burst type: INCR
  val regAwLock = Reg(Bits(1 bits))init(B(0))
  val regAwCache = Reg(Bits(4 bits))init(B(0))
  val regAwQos = Reg(Bits(4 bits))init(B(0))
  val regAwUser = Reg(Bits(axiConfig.awUserWidth bits))init(B(0))
  val regAwProt = Reg(Bits(3 bits))init(B(0))

  val regArAddr = Reg(UInt(12 bits))init(U(0))
  val regArId = Reg(UInt(axiConfig.idWidth bits))init(U(0))
  val regArRegion = Reg(Bits(4 bits))init(B(0))
  val regArLen = Reg(UInt(8 bits))init(U(0))    // default burst len: 1
  val regArSize = Reg(UInt(3 bits))init(U(5))   // default burst size: 32 byte
  val regArBrust = Reg(Bits(2 bits))init(B(1))  // default burst type: INCR
  val regArLock = Reg(Bits(1 bits))init(B(0))
  val regArCache = Reg(Bits(4 bits))init(B(0))
  val regArQos = Reg(Bits(4 bits))init(B(0))
  val regArUser = Reg(Bits(axiConfig.awUserWidth bits))init(B(0))
  val regArProt = Reg(Bits(3 bits))init(B(0))

  val updataAw = Reg(Bool)init(true)
  var writeSuccess = Reg(Bool())init(false)
  val updataAr = Reg(Bool)init(true)
  val rFireCounter = Counter(0,4096)
  //val rDataConca = Bits(DW bits)
  val bytePerWord = U(axiConfig.bytePerWord)
  var alignFlag =  UInt(log2Up(axiConfig.bytePerWord) bit)
  val AwArConflict = Reg(Bool())init(false)

  val resetSignalValue = Reg(Bool())init(false) //when reset become low

    //----------------------------------------AXI write logic, Addr Bus-------------------------------------
    io.axiSignal.aw.ready := updataAw & io.apstart

    when(io.axiSignal.aw.fire){
      updataAw := False
    }elsewhen(io.axiSignal.w.last & io.axiSignal.w.fire){
      updataAw := True
    }

    when(io.axiSignal.aw.fire){
      regAwAddr := io.axiSignal.aw.payload.addr.asBits(11 downto(0)).asUInt
      regAwId := io.axiSignal.aw.payload.id
      regAwRegion := io.axiSignal.aw.payload.region
      regAwLen := io.axiSignal.aw.payload.len
      regAwSize := io.axiSignal.aw.payload.size
      regAwBrust := io.axiSignal.aw.payload.burst
      regAwLock := io.axiSignal.aw.payload.lock
      regAwCache := io.axiSignal.aw.payload.cache
      regAwQos := io.axiSignal.aw.payload.qos
      if(axiConfig.useAwUser){
        regAwUser := io.axiSignal.aw.payload.user
      }
      regAwProt := io.axiSignal.aw.payload.prot
    }

    //----------------------------------------AXI write logic, data Bus-------------------------------------
    io.axiSignal.w.ready := !updataAw
    alignFlag := (regAwAddr % bytePerWord).resized
    when(io.axiSignal.w.fire){
      when(alignFlag === U(0)){
        for(i<- 0 until(axiConfig.bytePerWord)){
          regVec(regAwAddr + i) := io.axiSignal.w.payload.strb(i) ? io.axiSignal.w.payload.data(i<<3, 8 bits).asBits | regVec(regAwAddr + i)
        }
      }otherwise{
        if(axiConfig.bytePerWord == 4){
          switch(bytePerWord - alignFlag){
            Range(1,4).foreach{j=>
              is(U(j)){
                for(i<-0 until j){
                  //regVec(regAwAddr + U(i)) := io.axiSignal.w.payload.data((U(i) + alignFlag)<<3, 8 bits)
                  regVec(regAwAddr + U(i)) := io.axiSignal.w.payload.strb((U(i) + alignFlag)) ? io.axiSignal.w.payload.data((U(i) + alignFlag)<<3, 8 bits).asBits | regVec(regAwAddr + U(i))
                }
              }
            }
          }
        }else if(axiConfig.bytePerWord == 8){
          switch(bytePerWord - alignFlag){
            Range(1,8).foreach{j=>
              is(U(j)){
                for(i<-0 until j){
                  regVec(regAwAddr + U(i)) := io.axiSignal.w.payload.strb((U(i) + alignFlag)) ? io.axiSignal.w.payload.data((U(i) + alignFlag)<<3, 8 bits).asBits | regVec(regAwAddr + U(i))
                }
              }
            }
          }
        }else if (axiConfig.bytePerWord == 1){
          regVec(regAwAddr) := io.axiSignal.w.payload.strb(0) ? io.axiSignal.w.payload.data.asBits | regVec(regAwAddr)
        }
      }
      regAwAddr := L_incr(regAwAddr,regAwBrust,regAwLen,regAwSize,axiConfig.bytePerWord)
    }

    //----------------------------------------AXI write logic, resp Bus-------------------------------------
    io.axiSignal.b.payload.id := 0
    io.axiSignal.b.resp := 0
    when(io.axiSignal.w.last & io.axiSignal.w.fire){
      writeSuccess := True
    }
    io.axiSignal.b.valid := writeSuccess
    when(io.axiSignal.b.fire){
      writeSuccess := False
    }

    //----------------------------------------AXI read logic, addr Bus-------------------------------------
    io.axiSignal.ar.ready := updataAr & io.apstart
    when(io.axiSignal.ar.fire){
      updataAr := False
    }elsewhen(io.axiSignal.r.payload.last & io.axiSignal.r.fire){
      updataAr := True
    }

    when(io.axiSignal.ar.fire){
      regArAddr := io.axiSignal.ar.payload.addr.asBits(11 downto(0)).asUInt
      regArId := io.axiSignal.ar.payload.id
      regArRegion := io.axiSignal.ar.payload.region
      regArLen := io.axiSignal.ar.payload.len
      regArSize := io.axiSignal.ar.payload.size
      regArBrust := io.axiSignal.ar.payload.burst
      regArLock := io.axiSignal.ar.payload.lock
      regArCache := io.axiSignal.ar.payload.cache
      regArQos := io.axiSignal.ar.payload.qos
      regArUser := io.axiSignal.ar.payload.user
      regArProt := io.axiSignal.ar.payload.prot
    }

    //----------------------------------------AXI read logic, data Bus-------------------------------------

   when(io.axireset){
     io.axiSignal.r.valid := False
    }otherwise{
     io.axiSignal.r.valid := !updataAr & !AwArConflict
    }

    for(i<- 0 until(axiConfig.bytePerWord)){
      io.axiSignal.r.data(i<<3,8 bits) := regVec(regArAddr + i)
    }
    io.axiSignal.r.id := 0
    io.axiSignal.r.resp := 0
    when(io.axiSignal.r.fire){
      regArAddr := L_incr(regArAddr,regArBrust,regArLen,regArSize,axiConfig.bytePerWord)
    }
    when(io.axiSignal.r.fire){
      rFireCounter.increment()
    }
    when(rFireCounter.value === regArLen & io.axiSignal.r.fire){
      rFireCounter := 0
    }
    val regLast = Reg(Bool())init(false)
    io.axiSignal.r.payload.last := regLast & io.axiSignal.r.fire
    when(rFireCounter.value === regArLen - 1 & (regArLen =/= 0) &(io.axiSignal.r.fire)){
      regLast := True
    }elsewhen (regLast & io.axiSignal.r.payload.last){
      regLast := False
    }

  //----------------------------------------AXI read and write conflicts, read before write-------------------------------------
  when(io.axiSignal.aw.fire & io.axiSignal.ar.fire & (io.axiSignal.aw.payload.addr === io.axiSignal.ar.payload.addr)){
    AwArConflict := True
  }elsewhen(AwArConflict & io.axiSignal.b.fire & io.axiSignal.b.payload.resp === B(0)){
    AwArConflict := False
  }

}


object Axi4_main extends App{
  val AW = 32
  val DW = 32
  val INW = 4
  var wData = 0x11111111  //286331153 //h'1111 1111
  var wStrb = 15

  val aList = List(0,2)
  val rdata = new ListBuffer[Long]
  var idx = 0
  var idy = 0
  var idd = 0
  var idr = 0
  var iddr = 0
  var flag = 0
  val w_len = 10
  val r_len = 10

  val compiled = SimConfig.withFstWave.allOptimisation.compile(Axi4_transmission(AW,DW,INW))
  compiled.doSim{ dut =>
    import dut.{clockDomain, io}
    clockDomain.forkStimulus(10)
    val write_fork = fork{
      io.axiSignal.aw.valid #= false
      while(true){
        clockDomain.waitSampling()
        if(io.axiSignal.b.valid.toBoolean & io.axiSignal.b.ready.toBoolean){
          idy = 0
          idd = idd + 1
        }
        io.axiSignal.aw.valid #= true
        if(idd == 1 & io.axiSignal.w.payload.last.toBoolean){
          flag = 1
        }
        if(flag == 1){
          io.axiSignal.aw.valid #= false

//          clockDomain.waitSampling()
//          simSuccess()
        }
        io.apstart #= true
        io.axiSignal.aw.payload.burst #= 1
        if(io.axiSignal.aw.valid.toBoolean & io.axiSignal.aw.ready.toBoolean){
          if(idx < 1){
            idx = idx + 1
          }else{
            idx = idx
          }

          //seems no sense ,test
          val align = io.axiSignal.aw.payload.addr.toBigInt % (DW/8)
          if(align == 1){
            io.axiSignal.w.payload.strb #= 14
          }else if(align == 2){
            io.axiSignal.w.payload.strb #= 12
          }else if(align == 3){
            io.axiSignal.w.payload.strb #= 8
          }else{
            io.axiSignal.w.payload.strb #= 15
          }

        }
        io.axiSignal.aw.payload.addr #= aList(idx)
        io.axiSignal.aw.payload.len #= w_len
        io.axiSignal.aw.payload.size #= 2

        io.axiSignal.w.valid #= true
        if(io.axiSignal.w.ready.toBoolean & io.axiSignal.w.valid.toBoolean){
          wData = wData + 0x1111
          idy = idy + 1
          io.axiSignal.w.payload.strb #= 15
        }
        io.axiSignal.w.payload.data #= wData

        if (idy == w_len) {
          io.axiSignal.w.payload.last #= true
        }else{
          io.axiSignal.w.payload.last #= false
        }
        io.axiSignal.b.ready #= true
      }
    }
    val read_fork = fork{
      io.axiSignal.ar.valid #= false
      while (true){
        clockDomain.waitSampling()
        io.axiSignal.ar.valid #= true
        io.axiSignal.ar.payload.burst #= 1
        io.axiSignal.ar.payload.size  #= 2
        io.axiSignal.ar.payload.len #= r_len

        if(io.axiSignal.ar.valid.toBoolean & io.axiSignal.ar.ready.toBoolean){
          if(idr < 1){
            idr = idr + 1
          }else{
            idr = idr
          }
        }
        io.axiSignal.ar.payload.addr #= aList(idr)
        io.axiSignal.r.ready #= true
        if(io.axiSignal.r.ready.toBoolean & io.axiSignal.r.valid.toBoolean){
          rdata += io.axiSignal.r.payload.data.toLong
        }
        if(io.axiSignal.r.payload.last.toBoolean  & io.axiSignal.r.ready.toBoolean & io.axiSignal.r.valid.toBoolean){
           iddr = iddr + 1
          if(iddr == 2){
            clockDomain.waitSampling()
            simSuccess()
          }
        }
      }
    }
    write_fork.join()
    read_fork.join()

//  compiled.doSim { dut =>
//    import dut.{clockDomain, io}
//    clockDomain.forkStimulus(10)
//    val write_fork = fork{
//      while(true){
//        clockDomain.waitSampling()
//
//        if(io.axiSignal.b.valid.toBoolean & io.axiSignal.b.ready.toBoolean){
//          idy = 0
//          idd = idd + 1
//        }
//
//        io.axiSignal.aw.valid #= true
//        if(idd == 1 & io.axiSignal.w.payload.last.toBoolean){
//          flag = 1
//        }
//        if(flag == 1){
//          io.axiSignal.aw.valid #= false
//        }
//
//        io.apstart #= true
//        io.axiSignal.aw.payload.burst #= 1
//        if(io.axiSignal.aw.valid.toBoolean & io.axiSignal.aw.ready.toBoolean){
//          if(idx < 1){
//            idx = idx + 1
//          }else{
//            idx = idx
//          }
//
//          // seems no sense
//          val align = io.axiSignal.aw.payload.addr.toBigInt % (DW/8)
//          if(align == 1){
//            io.axiSignal.w.payload.strb #= 14
//          }else if(align == 2){
//            io.axiSignal.w.payload.strb #= 12
//          }else if(align == 3){
//            io.axiSignal.w.payload.strb #= 8
//          }else{
//            io.axiSignal.w.payload.strb #= 15
//          }
//
//        }
//
//        io.axiSignal.aw.payload.addr #= aList(idx)
//        io.axiSignal.aw.payload.len #= 3
//        io.axiSignal.aw.payload.size #= 2
//
//        io.axiSignal.w.valid #= true
//        if(io.axiSignal.w.ready.toBoolean & io.axiSignal.w.valid.toBoolean){
//          wData = wData + 0x1111
//          idy = idy + 1
//          io.axiSignal.w.payload.strb #= 15
//        }
//        io.axiSignal.w.payload.data #= wData
//
//        if (idy == 3) {
//          io.axiSignal.w.payload.last #= true
//        }else{
//          io.axiSignal.w.payload.last #= false
//        }
//        io.axiSignal.b.ready #= true
//      }
//    }
//    val read_fork = fork{
//      while (true){
//        clockDomain.waitSampling()
//        if(idd ==2 ){
//          io.axiSignal.ar.valid #= true
//          io.axiSignal.ar.payload.burst #= 1
//          io.axiSignal.ar.payload.size  #= 2
//          io.axiSignal.ar.payload.len #= 3
//          if(io.axiSignal.ar.valid.toBoolean & io.axiSignal.ar.ready.toBoolean){
//            if(idr < 1){
//              idr = idr + 1
//            }else{
//              idr = idr
//            }
//          }
//          io.axiSignal.ar.payload.addr #= aList(idr)
//          io.axiSignal.r.ready #= true
//          if(io.axiSignal.r.ready.toBoolean & io.axiSignal.r.valid.toBoolean){
//            rdata += io.axiSignal.r.payload.data.toLong
//          }
//          if(io.axiSignal.r.payload.last.toBoolean  & io.axiSignal.r.ready.toBoolean & io.axiSignal.r.valid.toBoolean){
//             iddr = iddr + 1
//            if(iddr == 2){
//              clockDomain.waitSampling()
//              simSuccess()
//            }
//          }
//        }else{
//          io.axiSignal.ar.valid #= false
//        }
//
//      }
//    }
//    write_fork.join()
//    read_fork.join()

  }



}
