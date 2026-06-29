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
class CmdDispatcher(fTokLen:Int, cTokLen:Int , WfTokLen:Int, WcTokLen:Int) extends Module with OSMCParameter {
  val stNum = math.pow(2, BG_WIDTH+RANK_WIDTH).toInt
  val tokMax = math.max(fTokLen, cTokLen)

  val io = IO(new Bundle {
    // Input command interfaces
    val filterWcmd    = Flipped(Decoupled(new SplitCmdIO(WfTokLen))) // write
    val cacheWcmd      = Flipped(Decoupled(new SplitCmdIO(WcTokLen))) // write
    val filterRcmd    = Flipped(Decoupled(new SplitCmdIO(fTokLen))) // read
    val cacheRcmd      = Flipped(Decoupled(new SplitCmdIO(cTokLen))) // read
    
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

  // Separate read and write commands
  val commandsRead = Seq(
    io.filterRcmd, // [0] Filter Read
    io.cacheRcmd   // [1] Cache Read
  )
  val commandsWrite = Seq(
    io.filterWcmd, // [0] Filter Write
    io.cacheWcmd   // [1] Cache Write
  )

  // Get group indices for read and write commands
  val grpIdxRead = commandsRead.map{c => Cat(c.bits.rank,c.bits.bg)}
  val grpIdxWrite = commandsWrite.map{c => Cat(c.bits.rank,c.bits.bg)}

  // Connect read commands
  commandArbiter.zipWithIndex.foreach { case (grpCmdQ, idx) =>
    // Handle Filter Read
    grpCmdQ.cmdIn(SchedCmdType.FilterRd.litValue.toInt).bits.exclude(_.token).unsafe :<>= 
      commandsRead(0).bits.exclude(_.token).unsafe
    grpCmdQ.cmdIn(SchedCmdType.FilterRd.litValue.toInt).valid := 
      (grpIdxRead(0) === idx.U) & commandsRead(0).valid
    grpCmdQ.cmdIn(SchedCmdType.FilterRd.litValue.toInt).bits.token := 
      Cat(0.U, ZeroExt(commandsRead(0).bits.token, tokMax))
    grpCmdQ.cmdIn(SchedCmdType.FilterRd.litValue.toInt).bits.isRd := true.B

    // Handle Cache Read
    grpCmdQ.cmdIn(SchedCmdType.CacheRd.litValue.toInt).bits.exclude(_.token).unsafe :<>= 
      commandsRead(1).bits.exclude(_.token).unsafe
    grpCmdQ.cmdIn(SchedCmdType.CacheRd.litValue.toInt).valid := 
      (grpIdxRead(1) === idx.U) & commandsRead(1).valid
    grpCmdQ.cmdIn(SchedCmdType.CacheRd.litValue.toInt).bits.token := 
      Cat(1.U, ZeroExt(commandsRead(1).bits.token, tokMax))
    grpCmdQ.cmdIn(SchedCmdType.CacheRd.litValue.toInt).bits.isRd := true.B

    // Handle Filter Write
    grpCmdQ.cmdIn(SchedCmdType.FilterWr.litValue.toInt).bits.exclude(_.token).unsafe :<>= 
      commandsWrite(0).bits.exclude(_.token).unsafe
    grpCmdQ.cmdIn(SchedCmdType.FilterWr.litValue.toInt).valid := 
      (grpIdxWrite(0) === idx.U) & commandsWrite(0).valid
    grpCmdQ.cmdIn(SchedCmdType.FilterWr.litValue.toInt).bits.token := 
      Cat(0.U, ZeroExt(commandsWrite(0).bits.token, tokMax))
    grpCmdQ.cmdIn(SchedCmdType.FilterWr.litValue.toInt).bits.isRd := false.B

    // Handle Cache Write
    grpCmdQ.cmdIn(SchedCmdType.CacheWr.litValue.toInt).bits.exclude(_.token).unsafe :<>= 
      commandsWrite(1).bits.exclude(_.token).unsafe
    grpCmdQ.cmdIn(SchedCmdType.CacheWr.litValue.toInt).valid := 
      (grpIdxWrite(1) === idx.U) & commandsWrite(1).valid
    grpCmdQ.cmdIn(SchedCmdType.CacheWr.litValue.toInt).bits.token := 
      Cat(1.U, ZeroExt(commandsWrite(1).bits.token, tokMax))
    grpCmdQ.cmdIn(SchedCmdType.CacheWr.litValue.toInt).bits.isRd := false.B
  }

  // Connect ready signals
  commandsRead(0).ready := commandArbiter(grpIdxRead(0)).cmdIn(SchedCmdType.FilterRd.litValue.toInt).ready
  commandsRead(1).ready := commandArbiter(grpIdxRead(1)).cmdIn(SchedCmdType.CacheRd.litValue.toInt).ready
  commandsWrite(0).ready := commandArbiter(grpIdxWrite(0)).cmdIn(SchedCmdType.FilterWr.litValue.toInt).ready
  commandsWrite(1).ready := commandArbiter(grpIdxWrite(1)).cmdIn(SchedCmdType.CacheWr.litValue.toInt).ready

  // Connect output
  (io.Cmd2Conf.zip(commandArbiter)).foreach {
    case (s, c) =>
      s :<>= c.cmdOut
  }
}
