/***************************************************************************************
* Copyright (c) 2021-2026 Beijing Institute of Open Source Chip (BOSC)
* Copyright (c) 2020-2026 Institute of Computing Technology, Chinese Academy of Sciences (ICT，CAS)
* 
* YuQuan is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*   
* See the Mulan PSL v2 for more details.
***************************************************************************************/
package OpenMc
import chisel3._
import chisel3.util._

/** Zero extension utility object */
object ZeroExt {
  def apply(a: UInt, len: Int): UInt = {
    val aLen = a.getWidth
    if (aLen >= len) a(len-1,0) 
    else Cat(0.U((len - aLen).W), a)
  }
}

object SchedCmdType extends ChiselEnum {
    val FilterRd, FilterWr, CacheRd, CacheWr = Value
}

/**
  * Command Adapter for handling filter and cache commands
  * @param fTokLen Token length for filter commands
  * @param cTokLen Token length for cache commands
  */
class CmdDispatcher(fTokLen:Int, cTokLen:Int) extends Module with OSMCParameter {
  val stNum = math.pow(2, BG_WIDTH+RANK_WIDTH).toInt
  val tokMax = math.max(fTokLen, cTokLen)

  val io = IO(new Bundle {
    // Input command interfaces
    val filterCmd  = Vec(2, Flipped(Decoupled(new SplitCmdIO(fTokLen)))) // [0]:read, [1]:write
    val cacheCmd   = Vec(2, Flipped(Decoupled(new SplitCmdIO(cTokLen)))) // [0]:read, [1]:write
    // Output to conflict control
    val Cmd2Conf = Vec(stNum, Decoupled(new SchedulerSCGCmd(tokMax + 1)))
  })

  val FilterRd = SchedCmdType.FilterRd.asUInt
  val FilterWr = SchedCmdType.FilterWr.asUInt
  val CacheRd = SchedCmdType.CacheRd.asUInt
  val CacheWr = SchedCmdType.CacheWr.asUInt


  // Create command queues for each bank group
  val commandArbiter = VecInit(
    Seq.fill(stNum)(Module(new CmdArbiter(gen = io.Cmd2Conf(0).bits.cloneType))).map(_.io)
  )
  // Combine all commands into a single sequence for processing
  val commands = Seq(
    io.filterCmd(0), // [0] Filter Read
    io.filterCmd(1), // [1] Filter Write
    io.cacheCmd(0),  // [2] Cache Read
    io.cacheCmd(1)   // [3] Cache Write
  )
  val grpIdx  = commands.map{c => Cat(c.bits.rank,c.bits.bg)}
  commandArbiter.zipWithIndex.foreach { case (grpCmdQ, idx) =>
      SchedCmdType.all.map(_.litValue.toInt).foreach{ cmdTyp =>
        // connect all fields except token
        grpCmdQ.cmdIn(cmdTyp).bits.exclude(_.token).unsafe :<>= commands(cmdTyp).bits.exclude(_.token).unsafe
        grpCmdQ.cmdIn(cmdTyp).valid  := (grpIdx(cmdTyp) === idx.U) & commands(cmdTyp).valid
        grpCmdQ.cmdIn(cmdTyp).bits.token := Cat(
          cmdTyp.U === CacheRd || cmdTyp.U === CacheWr,  // 最高位：0->filter, 1->cache
          ZeroExt(commands(cmdTyp).bits.token, tokMax)  // 扩展token位宽
        )
        grpCmdQ.cmdIn(cmdTyp).bits.isRd := (cmdTyp.U === FilterRd) | (cmdTyp.U === CacheRd)
      }
  }

  SchedCmdType.all.map(_.litValue.toInt).foreach{ cmdTyp =>
    commands(cmdTyp).ready := commandArbiter(grpIdx(cmdTyp)).cmdIn(cmdTyp).ready
  }

  (io.Cmd2Conf.zip(commandArbiter)).foreach {
    case (s, c) =>
      s :<>= c.cmdOut
  }

}