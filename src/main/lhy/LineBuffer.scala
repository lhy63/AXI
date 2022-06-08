package lhy

import spinal.lib._
import spinal.core._
import spinal.core.sim._

import scala.collection.mutable.ListBuffer
import scala.math.random
import scala.util.Random._

case class LineBuffer_config(IMG_width : Int, IMG_height: Int)
case class LineBuffer(config: LineBuffer_config)extends Component{
  val io = new Bundle{
    val data_in = slave(Stream(UInt(8 bits)))
    val f_s_in = in Bool
    val r_e_in = in Bool
    val intpl_in = in Bool

    val data_out_0 = master(Stream(UInt(8 bits)))
    val f_s_out_0  = out Bool
    val r_e_out_0 = out Bool
    val intpl_out_0 = out Bool

    val data_out_1 = master(Stream(UInt(8 bits)))
    val f_s_out_1  = out Bool
    val r_e_out_1 = out Bool
    val intpl_out_1 = out Bool

    val test = out Bool(false)   // after completion delete
  }
  val row_0_FIFO = L_StreamFifo(UInt(8 bits), config.IMG_width)
  val row_1_FIFO = L_StreamFifo(UInt(8 bits), config.IMG_width)

  val f_FIFO_0 = L_StreamFifo(Bool, config.IMG_width)
  val f_FIFO_1 = L_StreamFifo(Bool, config.IMG_width)

  val r_FIFO_0 = L_StreamFifo(Bool, config.IMG_width)
  val r_FIFO_1 = L_StreamFifo(Bool, config.IMG_width)

  val intpl_FIFO_0 = L_StreamFifo(Bool, config.IMG_width)
  val intpl_FIFO_1 = L_StreamFifo(Bool, config.IMG_width)

  val current_data  = Reg(UInt(8 bits))init(0)
  val current_f     = Reg(Bool)init(false)
  val current_r     = Reg(Bool)init(false)
  val current_intpl = Reg(Bool)init(false)

  val pixel_in_w_id = Counter(0,config.IMG_width)   // current input data 's row index
  val pixel_in_h_id = Counter(1,config.IMG_height)  // current input data 's col index

  val success = Reg(Bool)init(false)
  val is_last_pixel = Bool
  val work_state = Reg(UInt(2 bits))init(0)

  io.data_in.ready := (work_state === 0)
  when(io.data_in.valid & io.data_in.ready){
    work_state := 1

    current_data := io.data_in.payload
    current_f    := io.f_s_in
    current_r    := io.r_e_in
    current_intpl:= io.intpl_in

    pixel_in_w_id.increment()
    when(pixel_in_w_id.willOverflow){
      pixel_in_h_id.increment()
      pixel_in_w_id := U(1)
    }
  }
  is_last_pixel := (pixel_in_h_id === config.IMG_height) & (pixel_in_w_id === config.IMG_width)
  row_0_FIFO.io.flush := False
  row_1_FIFO.io.flush := False
  f_FIFO_0.io.flush := False
  f_FIFO_1.io.flush := False
  r_FIFO_0.io.flush := False
  r_FIFO_1.io.flush := False
  intpl_FIFO_0.io.flush := False
  intpl_FIFO_1.io.flush := False

  when(work_state === 1){
    when(row_0_FIFO.io.occupancy < config.IMG_width){
      row_0_FIFO.io.push.payload := current_data
      row_0_FIFO.io.push.valid := True
      row_0_FIFO.io.pop.ready := False

      row_1_FIFO.io.push.valid := False
      row_1_FIFO.io.push.payload := 0
      row_1_FIFO.io.pop.ready := False

      f_FIFO_0.io.push.payload := current_f
      f_FIFO_0.io.push.valid := True
      f_FIFO_0.io.pop.ready := False

      f_FIFO_1.io.push.valid := False
      f_FIFO_1.io.push.payload := False
      f_FIFO_1.io.pop.ready := False

      r_FIFO_0.io.push.payload := current_r
      r_FIFO_0.io.push.valid := True
      r_FIFO_0.io.pop.ready := False

      r_FIFO_1.io.push.valid := False
      r_FIFO_1.io.push.payload := False
      r_FIFO_1.io.pop.ready := False

      intpl_FIFO_0.io.push.payload := current_intpl
      intpl_FIFO_0.io.push.valid := True
      intpl_FIFO_0.io.pop.ready := False

      intpl_FIFO_1.io.push.valid := False
      intpl_FIFO_1.io.push.payload := False
      intpl_FIFO_1.io.pop.ready := False

      when(is_last_pixel){
        work_state := 2
      }otherwise{
        work_state := 0
      }
      io.data_out_0.valid := False
      io.data_out_0.payload := 0
      io.data_out_1.valid := False
      io.data_out_1.payload := 0
      io.f_s_out_0 := False
      io.f_s_out_1 := False
      io.r_e_out_0 := False
      io.r_e_out_1 := False
      io.intpl_out_0 := False
      io.intpl_out_1 := False
    }elsewhen(row_0_FIFO.io.occupancy === config.IMG_width & row_1_FIFO.io.occupancy < config.IMG_width){
      row_0_FIFO.io.push.valid := False
      row_0_FIFO.io.push.payload := 0

      row_0_FIFO.io.pop.ready := True
      row_1_FIFO.io.push.valid := True
      row_1_FIFO.io.push.payload := row_0_FIFO.io.pop.payload
      row_1_FIFO.io.pop.ready := False

      f_FIFO_0.io.push.valid := False
      f_FIFO_0.io.push.payload := False

      f_FIFO_0.io.pop.ready := True
      f_FIFO_1.io.push.valid := True
      f_FIFO_1.io.push.payload := f_FIFO_0.io.pop.payload
      f_FIFO_1.io.pop.ready := False

      r_FIFO_0.io.push.valid := False
      r_FIFO_0.io.push.payload := False

      r_FIFO_0.io.pop.ready := True
      r_FIFO_1.io.push.valid := True
      r_FIFO_1.io.push.payload := r_FIFO_0.io.pop.payload
      r_FIFO_1.io.pop.ready := False

      intpl_FIFO_0.io.push.valid := False
      intpl_FIFO_0.io.push.payload := False

      intpl_FIFO_0.io.pop.ready := True
      intpl_FIFO_1.io.push.valid := True
      intpl_FIFO_1.io.push.payload := intpl_FIFO_0.io.pop.payload
      intpl_FIFO_1.io.pop.ready := False

      io.data_out_0.valid := False
      io.data_out_0.payload := 0
      io.data_out_1.valid := False
      io.data_out_1.payload := 0
      io.f_s_out_0 := False
      io.f_s_out_1 := False
      io.r_e_out_0 := False
      io.r_e_out_1 := False
      io.intpl_out_0 := False
      io.intpl_out_1 := False
    }otherwise{
      row_0_FIFO.io.push.valid := False
      row_0_FIFO.io.push.payload := 0

      f_FIFO_0.io.push.valid := False
      f_FIFO_0.io.push.payload := False

      r_FIFO_0.io.push.valid := False
      r_FIFO_0.io.push.payload := False

      intpl_FIFO_0.io.push.valid := False
      intpl_FIFO_0.io.push.payload := False
      when(io.data_out_0.ready & io.data_out_1.ready){
        io.data_out_0.valid := True
        io.data_out_0.payload := row_0_FIFO.io.pop.payload
        io.data_out_1.valid := True
        io.data_out_1.payload := row_1_FIFO.io.pop.payload
        io.f_s_out_0 := f_FIFO_0.io.pop.payload
        io.f_s_out_1 := f_FIFO_1.io.pop.payload
        io.r_e_out_0 := r_FIFO_0.io.pop.payload
        io.r_e_out_1 := r_FIFO_1.io.pop.payload
        io.intpl_out_0 := intpl_FIFO_0.io.pop.payload
        io.intpl_out_1 := intpl_FIFO_1.io.pop.payload
        success := True
      }otherwise{
        io.data_out_0.valid := False
        io.data_out_0.payload := 0
        io.data_out_1.valid := False
        io.data_out_1.payload := 0
        io.f_s_out_0 := False
        io.f_s_out_1 := False
        io.r_e_out_0 := False
        io.r_e_out_1 := False
        io.intpl_out_0 := False
        io.intpl_out_1 := False
      }
      when(success){
        row_1_FIFO.io.pop.ready := True
        row_0_FIFO.io.pop.ready  := False
        row_1_FIFO.io.push.valid := False
        row_1_FIFO.io.push.payload := 0

        f_FIFO_1.io.pop.ready := True
        f_FIFO_0.io.pop.ready  := False
        f_FIFO_1.io.push.valid := False
        f_FIFO_1.io.push.payload := False

        r_FIFO_1.io.pop.ready := True
        r_FIFO_0.io.pop.ready  := False
        r_FIFO_1.io.push.valid := False
        r_FIFO_1.io.push.payload := False

        intpl_FIFO_1.io.pop.ready := True
        intpl_FIFO_0.io.pop.ready  := False
        intpl_FIFO_1.io.push.valid := False
        intpl_FIFO_1.io.push.payload := False

        success := False

        io.data_out_0.valid := False
        io.data_out_0.payload := 0
        io.data_out_1.valid := False
        io.data_out_1.payload := 0
        io.f_s_out_0 := False
        io.f_s_out_1 := False
        io.r_e_out_0 := False
        io.r_e_out_1 := False
        io.intpl_out_0 := False
        io.intpl_out_1 := False
      }otherwise{
        row_0_FIFO.io.pop.ready  := False
        row_1_FIFO.io.push.valid := False
        row_1_FIFO.io.push.payload := 0
        row_1_FIFO.io.pop.ready := False

        f_FIFO_0.io.pop.ready  := False
        f_FIFO_1.io.push.valid := False
        f_FIFO_1.io.push.payload := False
        f_FIFO_1.io.pop.ready := False

        r_FIFO_0.io.pop.ready  := False
        r_FIFO_1.io.push.valid := False
        r_FIFO_1.io.push.payload := False
        r_FIFO_1.io.pop.ready := False

        intpl_FIFO_0.io.pop.ready  := False
        intpl_FIFO_1.io.push.valid := False
        intpl_FIFO_1.io.push.payload := False
        intpl_FIFO_1.io.pop.ready := False
      }
    }
  }elsewhen(work_state === 2){
    row_0_FIFO.io.push.valid := False
    row_0_FIFO.io.push.payload := 0
    row_1_FIFO.io.push.valid := False
    row_1_FIFO.io.push.payload := 0

    row_0_FIFO.io.pop.ready := io.data_out_0.ready
    row_1_FIFO.io.pop.ready := io.data_out_1.ready

    f_FIFO_0.io.push.valid := False
    f_FIFO_0.io.push.payload := False
    f_FIFO_1.io.push.valid := False
    f_FIFO_1.io.push.payload := False

    f_FIFO_0.io.pop.ready := io.data_out_0.ready
    f_FIFO_1.io.pop.ready := io.data_out_1.ready

    r_FIFO_0.io.push.valid := False
    r_FIFO_0.io.push.payload := False
    r_FIFO_1.io.push.valid := False
    r_FIFO_1.io.push.payload := False

    r_FIFO_0.io.pop.ready := io.data_out_0.ready
    r_FIFO_1.io.pop.ready := io.data_out_1.ready

    intpl_FIFO_0.io.push.valid := False
    intpl_FIFO_0.io.push.payload := False
    intpl_FIFO_1.io.push.valid := False
    intpl_FIFO_1.io.push.payload := False

    intpl_FIFO_0.io.pop.ready := io.data_out_0.ready
    intpl_FIFO_1.io.pop.ready := io.data_out_1.ready

    io.data_out_0.payload := row_0_FIFO.io.pop.payload
    io.data_out_1.payload := row_1_FIFO.io.pop.payload

    io.data_out_0.valid := row_0_FIFO.io.pop.valid
    io.data_out_1.valid := row_1_FIFO.io.pop.valid

    io.f_s_out_0 := f_FIFO_0.io.pop.payload
    io.f_s_out_1 := f_FIFO_1.io.pop.payload

    io.r_e_out_0 := r_FIFO_0.io.pop.payload
    io.r_e_out_1 := r_FIFO_1.io.pop.payload

    io.intpl_out_0 := intpl_FIFO_0.io.pop.payload
    io.intpl_out_1 := intpl_FIFO_1.io.pop.payload

    when(row_0_FIFO.io.occupancy === 0 & row_1_FIFO.io.occupancy === 0){
      work_state := 1
      io.test := True
    }
  }otherwise{
    row_0_FIFO.io.push.payload := 0
    row_0_FIFO.io.push.valid := False
    row_0_FIFO.io.pop.ready := False
    row_1_FIFO.io.push.payload := 0
    row_1_FIFO.io.push.valid:= False
    row_1_FIFO.io.pop.ready := False

    f_FIFO_0.io.push.payload := False
    f_FIFO_0.io.push.valid := False
    f_FIFO_0.io.pop.ready := False
    f_FIFO_1.io.push.payload := False
    f_FIFO_1.io.push.valid:= False
    f_FIFO_1.io.pop.ready := False

    r_FIFO_0.io.push.payload := False
    r_FIFO_0.io.push.valid := False
    r_FIFO_0.io.pop.ready := False
    r_FIFO_1.io.push.payload := False
    r_FIFO_1.io.push.valid:= False
    r_FIFO_1.io.pop.ready := False

    intpl_FIFO_0.io.push.payload := False
    intpl_FIFO_0.io.push.valid := False
    intpl_FIFO_0.io.pop.ready := False
    intpl_FIFO_1.io.push.payload := False
    intpl_FIFO_1.io.push.valid:= False
    intpl_FIFO_1.io.pop.ready := False

    io.data_out_0.payload := 0
    io.data_out_1.payload := 0

    io.data_out_0.valid := False
    io.data_out_1.valid := False

    io.f_s_out_0 := False
    io.f_s_out_1 := False
    io.r_e_out_0 := False
    io.r_e_out_1 := False
    io.intpl_out_0 := False
    io.intpl_out_1 := False
  }
}

object FIFOtest extends App{

  //  val compiled = SimConfig.withFstWave.allOptimisation.compile(StreamFifo(UInt(8 bits),8))
  //  var idx  =  0
  //  compiled.doSim { dut =>
  //    dut.clockDomain.forkStimulus(10)
  //    val fork_in = fork {
  //      while (true) {
  //        dut.clockDomain.waitSampling()
  //        dut.io.pop.ready.randomize()
  //        dut.io.push.valid.randomize()
  //        dut.io.flush #= false
  //        if(dut.io.push.ready.toBoolean & dut.io.push.valid.toBoolean){
  //          dut.io.push.payload.randomize()
  //          idx = idx +1
  //        }
  //        if(idx > 16){
  //          simSuccess()
  //        }
  //      }
  //    }
  //    fork_in.join()
  //  }
  //SpinalVerilog(LineBuffer(LineBuffer_config(12,12)))
  val data_in_record = ListBuffer(0)
  val data_in_record_f = ListBuffer(false)
  val data_in_record_r = ListBuffer(false)
  val data_in_record_intpl = ListBuffer(false)

  val data_out_0_record = ListBuffer(0)
  val data_out_1_record = ListBuffer(0)

  val f_out_0_record = ListBuffer(false)
  val f_out_1_record = ListBuffer(false)

  val r_out_0_record = ListBuffer(false)
  val r_out_1_record = ListBuffer(false)

  val intpl_out_0_record = ListBuffer(false)
  val intpl_out_1_record = ListBuffer(false)

  var idx = 0
  var a = false
  val compiled = SimConfig.withFstWave.allOptimisation.compile(LineBuffer(LineBuffer_config(12,4)))
  compiled.doSim{dut =>
    dut.clockDomain.forkStimulus(10)
    val fork_in = fork{
      while(true){
        dut.clockDomain.waitSampling()
        dut.io.data_in.valid.randomize()
        a = nextBoolean()
        dut.io.data_out_0.ready #= a
        dut.io.data_out_1.ready #= a
        if(dut.io.data_in.valid.toBoolean & dut.io.data_in.ready.toBoolean){
          dut.io.data_in.payload.randomize()
          dut.io.f_s_in.randomize()
          dut.io.r_e_in.randomize()
          dut.io.intpl_in.randomize()

          data_in_record.append(dut.io.data_in.payload.toInt)
          data_in_record_f.append(dut.io.f_s_in.toBoolean)
          data_in_record_r.append(dut.io.r_e_in.toBoolean)
          data_in_record_intpl.append(dut.io.intpl_in.toBoolean)
        }
      }
    }
    val fork_out = fork{
      while(true){
        dut.clockDomain.waitSampling()
        if(dut.io.data_out_0.valid.toBoolean & dut.io.data_out_0.ready.toBoolean){
          data_out_0_record.append(dut.io.data_out_0.payload.toInt)
          f_out_0_record.append(dut.io.f_s_out_0.toBoolean)
          r_out_0_record.append(dut.io.r_e_out_0.toBoolean)
          intpl_out_0_record.append(dut.io.intpl_out_0.toBoolean)
        }
        if(dut.io.data_out_1.valid.toBoolean & dut.io.data_out_1.ready.toBoolean){
          data_out_1_record.append(dut.io.data_out_1.payload.toInt)
          f_out_1_record.append(dut.io.f_s_out_1.toBoolean)
          r_out_1_record.append(dut.io.r_e_out_1.toBoolean)
          intpl_out_1_record.append(dut.io.intpl_out_1.toBoolean)
        }
        if(dut.io.test.toBoolean){
          println(s"data_in_record_size:${data_in_record.length}")
          println(s"data_out_0_record_size:${data_out_0_record.length}")
          println(s"data_out_1_record_size:${data_out_1_record.length}")
          println(s"f_in_record_size:${data_in_record_f.length}")
          println(s"f_out_0_record_size:${f_out_0_record.length}")
          println(s"f_out_1_record_size:${f_out_1_record.length}")
          println(s"r_in_record_size:${data_in_record_r.length}")
          println(s"r_out_0_record_size:${r_out_0_record.length}")
          println(s"r_out_1_record_size:${r_out_1_record.length}")
          println(s"intpl_in_record_size:${data_in_record_intpl.length}")
          println(s"intpl_out_0_record_size:${intpl_out_0_record.length}")
          println(s"intpl_out_1_record_size:${intpl_out_1_record.length}")
          println()
          println("the data_in")
          for(i<-1 until 49 by 12){
            println(s"${data_in_record(i)} ${data_in_record(i+1)} ${data_in_record(i+2)} ${data_in_record(i+3)} ${data_in_record(i+4)} ${data_in_record(i+5)} " +
              s"${data_in_record(i+6)} ${data_in_record(i+7)} ${data_in_record(i+8)} ${data_in_record(i+9)} ${data_in_record(i+10)} ${data_in_record(i+11)}")
          }
          println()
          println("the data_out_0")
          for(i<-1 until 37 by 12){
            println(s"${data_out_0_record(i)} ${data_out_0_record(i+1)} ${data_out_0_record(i+2)} ${data_out_0_record(i+3)} ${data_out_0_record(i+4)} ${data_out_0_record(i+5)} " +
              s"${data_out_0_record(i+6)} ${data_out_0_record(i+7)} ${data_out_0_record(i+8)} ${data_out_0_record(i+9)} ${data_out_0_record(i+10)} ${data_out_0_record(i+11)}")
          }
          println()
          println("the data_out_1")
          for(i<-1 until 37 by 12 ){
            println(s"${data_out_1_record(i)} ${data_out_1_record(i+1)} ${data_out_1_record(i+2)} ${data_out_1_record(i+3)} ${data_out_1_record(i+4)} ${data_out_1_record(i+5)} " +
              s"${data_out_1_record(i+6)} ${data_out_1_record(i+7)} ${data_out_1_record(i+8)} ${data_out_1_record(i+9)} ${data_out_1_record(i+10)} ${data_out_1_record(i+11)}")
          }
          println()
          println("the f_in")
          for(i<-1 until 49 by 12){
            println(s"${data_in_record_f(i)} ${data_in_record_f(i+1)} ${data_in_record_f(i+2)} ${data_in_record_f(i+3)} ${data_in_record_f(i+4)} ${data_in_record_f(i+5)} " +
              s"${data_in_record_f(i+6)} ${data_in_record_f(i+7)} ${data_in_record_f(i+8)} ${data_in_record_f(i+9)} ${data_in_record_f(i+10)} ${data_in_record_f(i+11)}")
          }
          println()
          println("the f_out_0")
          for(i<-1 until 37 by 12){
            println(s"${f_out_0_record(i)} ${f_out_0_record(i+1)} ${f_out_0_record(i+2)} ${f_out_0_record(i+3)} ${f_out_0_record(i+4)} ${f_out_0_record(i+5)} " +
              s"${f_out_0_record(i+6)} ${f_out_0_record(i+7)} ${f_out_0_record(i+8)} ${f_out_0_record(i+9)} ${f_out_0_record(i+10)} ${f_out_0_record(i+11)}")
          }
          println()
          println("the f_out_1")
          for(i<-1 until 37 by 12 ){
            println(s"${f_out_1_record(i)} ${f_out_1_record(i+1)} ${f_out_1_record(i+2)} ${f_out_1_record(i+3)} ${f_out_1_record(i+4)} ${f_out_1_record(i+5)} " +
              s"${f_out_1_record(i+6)} ${f_out_1_record(i+7)} ${f_out_1_record(i+8)} ${f_out_1_record(i+9)} ${f_out_1_record(i+10)} ${f_out_1_record(i+11)}")
          }
          println()
          println("the r_in")
          for(i<-1 until 49 by 12){
            println(s"${data_in_record_r(i)} ${data_in_record_r(i+1)} ${data_in_record_r(i+2)} ${data_in_record_r(i+3)} ${data_in_record_r(i+4)} ${data_in_record_r(i+5)} " +
              s"${data_in_record_r(i+6)} ${data_in_record_r(i+7)} ${data_in_record_r(i+8)} ${data_in_record_r(i+9)} ${data_in_record_r(i+10)} ${data_in_record_r(i+11)}")
          }
          println()
          println("the r_out_0")
          for(i<-1 until 37 by 12){
            println(s"${r_out_0_record(i)} ${r_out_0_record(i+1)} ${r_out_0_record(i+2)} ${r_out_0_record(i+3)} ${r_out_0_record(i+4)} ${r_out_0_record(i+5)} " +
              s"${r_out_0_record(i+6)} ${r_out_0_record(i+7)} ${r_out_0_record(i+8)} ${r_out_0_record(i+9)} ${r_out_0_record(i+10)} ${r_out_0_record(i+11)}")
          }
          println()
          println("the r_out_1")
          for(i<-1 until 37 by 12 ){
            println(s"${r_out_1_record(i)} ${r_out_1_record(i+1)} ${r_out_1_record(i+2)} ${r_out_1_record(i+3)} ${r_out_1_record(i+4)} ${r_out_1_record(i+5)} " +
              s"${r_out_1_record(i+6)} ${r_out_1_record(i+7)} ${r_out_1_record(i+8)} ${r_out_1_record(i+9)} ${r_out_1_record(i+10)} ${r_out_1_record(i+11)}")
          }
          println()
          println("the intpl_in")
          for(i<-1 until 49 by 12){
            println(s"${data_in_record_intpl(i)} ${data_in_record_intpl(i+1)} ${data_in_record_intpl(i+2)} ${data_in_record_intpl(i+3)} ${data_in_record_intpl(i+4)} ${data_in_record_intpl(i+5)} " +
              s"${data_in_record_intpl(i+6)} ${data_in_record_intpl(i+7)} ${data_in_record_intpl(i+8)} ${data_in_record_intpl(i+9)} ${data_in_record_intpl(i+10)} ${data_in_record_intpl(i+11)}")
          }
          println()
          println("the intpl_out_0")
          for(i<-1 until 37 by 12){
            println(s"${intpl_out_0_record(i)} ${intpl_out_0_record(i+1)} ${intpl_out_0_record(i+2)} ${intpl_out_0_record(i+3)} ${intpl_out_0_record(i+4)} ${intpl_out_0_record(i+5)} " +
              s"${intpl_out_0_record(i+6)} ${intpl_out_0_record(i+7)} ${intpl_out_0_record(i+8)} ${intpl_out_0_record(i+9)} ${intpl_out_0_record(i+10)} ${intpl_out_0_record(i+11)}")
          }
          println()
          println("the intpl_out_1")
          for(i<-1 until 37 by 12 ){
            println(s"${intpl_out_1_record(i)} ${intpl_out_1_record(i+1)} ${intpl_out_1_record(i+2)} ${intpl_out_1_record(i+3)} ${intpl_out_1_record(i+4)} ${intpl_out_1_record(i+5)} " +
              s"${intpl_out_1_record(i+6)} ${intpl_out_1_record(i+7)} ${intpl_out_1_record(i+8)} ${intpl_out_1_record(i+9)} ${intpl_out_1_record(i+10)} ${intpl_out_1_record(i+11)}")
          }
          println()
          println(s"data_out_0 is equal to the last three columns of the data_in list : ${data_out_0_record.tail == data_in_record.slice(13,49)}")
          println(s"data_out_1 is equal to the first three columns of the data_in list : ${data_out_1_record.tail == data_in_record.slice(1,37)}")
          println(s"f_out_0 is equal to the last three columns of the f_in list : ${f_out_0_record.tail == data_in_record_f.slice(13,49)}")
          println(s"f_out_1 is equal to the first three columns of the f_in list : ${f_out_1_record.tail == data_in_record_f.slice(1,37)}")
          println(s"r_out_0 is equal to the last three columns of the r_in list : ${r_out_0_record.tail == data_in_record_r.slice(13,49)}")
          println(s"r_out_1 is equal to the first three columns of the r_in list : ${r_out_1_record.tail == data_in_record_r.slice(1,37)}")
          println(s"intpl_out_0 is equal to the last three columns of the intpl_in list : ${intpl_out_0_record.tail == data_in_record_intpl.slice(13,49)}")
          println(s"intpl_out_1 is equal to the first three columns of the intpl_in list : ${intpl_out_1_record.tail == data_in_record_intpl.slice(1,37)}")
          simSuccess()

        }
      }
    }
    fork_in.join()
    fork_out.join()
  }
}