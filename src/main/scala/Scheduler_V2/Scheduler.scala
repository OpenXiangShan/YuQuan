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
import chisel3.experimental.BundleLiterals._
import scopt.Read

class SchedulerSCGCmd(tokLen:Int) extends SplitCmdIO(tokLen) {
  val isRd = Bool()
}


class Scheduler(WrentNum:Int = CONFIGURABLE_PARAM.WrSchedulerQueueDepth , RdentNum:Int = CONFIGURABLE_PARAM.RdSchedulerQueueDepth, fTokLen:Int = 10, cTokLen:Int = 16) extends Module with OSMCParameter {
  val bgNum  = math.pow(2, BG_WIDTH+RANK_WIDTH).toInt
  val baPerBg  = math.pow(2, BANK_WIDTH).toInt
  val tokMax  = math.max(fTokLen, cTokLen)
  val org = StationOrg.PerBg

  val io = IO(new Bundle {
    // 0 -> read, 1 -> write
    val cmdIn = Vec(
      2,
      Flipped(new Bundle {
        val filterCmd = Decoupled(new SplitCmdIO(fTokLen))
        val cacheCmd  = Decoupled(new SplitCmdIO(cTokLen))
      })
    )
    // 0 -> filter, 1 -> cache
    val cmdOut   = Vec(bgNum * baPerBg, Decoupled(new SchedulerSCGCmd(tokMax + 1)))
    val SchedulerQueueIsEmpty = Bool()
    val readBack = new ReadBackIO(new RbParam(cTokenLen = cTokLen, fTokenLen = fTokLen))
  })
  val inAdapter = Module(new CmdDispatcher(fTokLen, cTokLen)).io
  val confCtrl  = Seq.fill(bgNum)(Module(new ConflictCtrl(gen = inAdapter.Cmd2Conf(0).bits.cloneType))).map(_.io)
  val rdSt = Seq.fill(bgNum)(Module(new CmdStation(entNum = RdentNum, gen = new SplitCmdIO(confCtrl(0).cmdOut.bits.token.getWidth){val isRd = Bool()}, org, 3, 14))).map(_.io)
  val wrSt = Seq.fill(bgNum)(Module(new CmdStation(entNum = WrentNum, gen = confCtrl(0).cmdOut.bits.cloneType, org, 2, WrentNum))).map(_.io)
  val rwSwitch = Module(new RWSwitch(org)).io
  val readBack = Module(new ReadBack(new RbParam)).io
//------------------------------------------------------------
// Connect Submodules' Input
//------------------------------------------------------------
  // Adapter
  inAdapter.filterCmd :<>= io.cmdIn.map(_.filterCmd)
  inAdapter.cacheCmd  :<>= io.cmdIn.map(_.cacheCmd)
  // conflict control
  confCtrl.zipWithIndex.foreach{case (ctrl, idx) => ctrl.cmdIn :<>= inAdapter.Cmd2Conf(idx)}
  for (i <- 0 until bgNum) {
    confCtrl(i).conflict(0) := rdSt(i).conflict
    confCtrl(i).conflict(1) := wrSt(i).conflict 
  }
  // r/w station
  for (i <- 0 until bgNum) {
    rdSt(i).cmdDetect := confCtrl(i).cmdDetect
    rdSt(i).cmdIn.bits.unsafe :<>= confCtrl(i).cmdOut.bits.unsafe
    rdSt(i).cmdIn.valid := confCtrl(i).cmdOut.valid && confCtrl(i).cmdOut.bits.isRd

    wrSt(i).cmdDetect := confCtrl(i).cmdDetect
    wrSt(i).cmdIn.bits :<>= confCtrl(i).cmdOut.bits
    wrSt(i).cmdIn.valid := confCtrl(i).cmdOut.valid && !confCtrl(i).cmdOut.bits.isRd

    confCtrl(i).cmdOut.ready := Mux(confCtrl(i).cmdOut.bits.isRd, rdSt(i).cmdIn.ready, wrSt(i).cmdIn.ready)
  }
  rdSt.foreach(_.grant := rwSwitch.grantRead)
  wrSt.foreach(_.grant := rwSwitch.grantWrite)
  // rwSwitch
  rwSwitch.readUrgent   :<>= rdSt.map(_.conflict)
  rwSwitch.writeUrgent  :<>= wrSt.map(_.conflict)
  rwSwitch.readHigh     :<>= rdSt.map(_.high)
  rwSwitch.writeHigh    :<>= wrSt.map(_.high)
  rwSwitch.readLow      :<>= rdSt.map(_.low)
  rwSwitch.writeLow     :<>= wrSt.map(_.low)
//------------------------------------------------------------
// ReadBack Connection
//------------------------------------------------------------
  io.readBack :<>= readBack
//------------------------------------------------------------
// Output Connection
//------------------------------------------------------------
  io.cmdOut.foreach(_.valid := false.B)
  io.cmdOut.foreach(_.bits := DontCare)
  wrSt.foreach(_.cmdOut.foreach(_.ready := false.B))
  rdSt.foreach(_.cmdOut.foreach(_.ready := false.B))
  when(rwSwitch.grantRead) { // read
    for (i <- 0 until bgNum) {
      for (j <- 0 until baPerBg) {
        io.cmdOut(i * baPerBg + j).bits.unsafe :<>= rdSt(i).cmdOut(j).bits.unsafe
        io.cmdOut(i * baPerBg + j).valid := rdSt(i).cmdOut(j).valid
        rdSt(i).cmdOut(j).ready := io.cmdOut(i * baPerBg + j).ready
      }
    }
  }.elsewhen(rwSwitch.grantWrite) { // write
    for (i <- 0 until bgNum) {
      for (j <- 0 until baPerBg) {
        io.cmdOut(i * baPerBg + j) :<>= wrSt(i).cmdOut(j)
      }
    }
  }
//detect the number of cheduler queue entrys
val ReadVec = VecInit(Seq.fill(bgNum)(false.B))
val WriteVec = VecInit(Seq.fill(bgNum)(false.B))
dontTouch(ReadVec)
dontTouch(WriteVec)
for(i <- 0 until bgNum){
  ReadVec(i) := rdSt(i).VldentryNum.orR
}
for(i <- 0 until bgNum){
  WriteVec(i) := wrSt(i).VldentryNum.orR
}
io.SchedulerQueueIsEmpty := !(ReadVec.asUInt.orR) | !(WriteVec.asUInt.orR) 
dontTouch(io.SchedulerQueueIsEmpty)

}
