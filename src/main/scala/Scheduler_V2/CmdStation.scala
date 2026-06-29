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
import chisel3.experimental.hierarchy.public

class AgeDetector(numEntries: Int, numEnq: Int, regOut: Boolean = true) extends Module {
  val io = IO(new Bundle {
    // NOTE: deq and enq may come at the same cycle.
    val enq = Input(UInt(numEntries.W))
    val deq = Input(UInt(numEntries.W))
    val out = Output(UInt(numEntries.W))
    val age = Vec(numEntries, Vec(numEntries, Output(Bool())))
  })

  // age(i)(j): entry i enters queue before entry j
  val age = Seq.fill(numEntries)(Seq.fill(numEntries)(RegInit(false.B)))
  val nextAge = Seq.fill(numEntries)(Seq.fill(numEntries)(Wire(Bool())))
  for (i <- 0 until numEntries) {
    for (j <- 0 until numEntries) {
      io.age(i)(j) := age(i)(j)
    }
  }
  

  // to reduce reg usage, only use upper matrix
  def get_age(row: Int, col: Int): Bool = if (row <= col) age(row)(col) else !age(col)(row)
  def get_age__(row: Int, col: Int): Bool = if (row <= col) io.age(row)(col) else !io.age(col)(row)
  def get_next_age(row: Int, col: Int): Bool = if (row <= col) nextAge(row)(col) else !nextAge(col)(row)
  def isFlushed(i: Int): Bool = io.deq(i)
  def isEnqueued(i: Int, numPorts: Int = -1): Bool = io.enq(i)

  for ((row, i) <- nextAge.zipWithIndex) {
    val thisValid = get_age(i, i) || isEnqueued(i)
    for ((elem, j) <- row.zipWithIndex) {
      when (isFlushed(i)) {
        // (1) when entry i is flushed or dequeues, set row(i) to false.B
        elem := false.B
      }.elsewhen (isFlushed(j)) {
        // (2) when entry j is flushed or dequeues, set column(j) to validVec
        elem := thisValid
      }.elsewhen (isEnqueued(i)) {
        elem := !get_age(j, j)
      }.otherwise {
        // default: unchanged
        elem := get_age(i, j)
      }
      age(i)(j) := elem
    }
  }

  def getOldest(get: (Int, Int) => Bool): UInt = {
    VecInit((0 until numEntries).map(i => {
      VecInit((0 until numEntries).map(j => get(i, j))).asUInt.andR
    })).asUInt
  }
  val best = getOldest(get_age)
  val nextBest = getOldest(get_next_age)

  io.out := (if (regOut) best else nextBest)


  def getMuskedOldest(musk:UInt) = {
    VecInit((0 until numEntries).map { i =>
      val isMasked = musk(i)
      VecInit((0 until numEntries).map { j =>
        // 条目被掩码时直接返回false（年轻）
        if (i == j) !isMasked
        else !isMasked && (musk(j) || get_age__(i,j))
      }).asUInt.andR
    }).asUInt
  }
}

object StationOrg extends Enumeration {
  val Unified, PerBg, PerBa = Value
}


// per bg station, with musked age matrix
class CmdStation[T <: SplitCmdIO](entNum:Int = 16, gen:T, org: StationOrg.Value, lWater: Int, hWater:Int) extends Module with OSMCParameter {
  val bgNum = 1<<(BUNDLE_PARAM.BG_WIDTH + BUNDLE_PARAM.RANK_WIDTH)
  val baPerBg = 1<<BUNDLE_PARAM.BANK_WIDTH
  val baNum = 1<<(BUNDLE_PARAM.RANK_WIDTH + BUNDLE_PARAM.BG_WIDTH + BUNDLE_PARAM.BANK_WIDTH)
  val flowNum = if (org == StationOrg.Unified) 1 else if (org == StationOrg.PerBg) baPerBg else baNum
  val io = IO(new Bundle {
    val grant         = Flipped(Bool())
    val cmdIn         = Flipped(Decoupled(gen))
    val cmdDetect     = Flipped(Valid(new SplitCmdIO))
    val conflict      = Bool()
    val cmdOut        = Vec(flowNum, Decoupled(gen))
    val low           = Bool()
    val high          = Bool()
    val VldentryNum   = UInt((log2Ceil(entNum)+1).W)
  })
  val ageMatrix = Module(new AgeDetector(entNum, 1))
  val prevRow   = RegInit(VecInit.fill(flowNum)(0.U(ROW_WIDTH.W)))
  val vld = RegInit(VecInit.fill(entNum)(false.B))//cmdslot vld
  val empty = RegInit(VecInit.fill(entNum)(true.B))//cmdslot empty
  val vldFlow = RegInit(VecInit.fill(flowNum, entNum)(false.B))//二维数组
  val cmdSlot = RegInit(VecInit.fill(entNum)(0.U.asTypeOf(gen)))//存16个请求
  
  val confOH = VecInit((vld zip cmdSlot).map({case (vld, slot) =>
    val detect = io.cmdDetect.bits
    org match {
      case StationOrg.Unified => io.cmdDetect.valid & vld & (slot.bg === detect.bg) & (slot.bank === detect.bank) & (slot.row === detect.row) & (slot.col === detect.col)
      case StationOrg.PerBg   => io.cmdDetect.valid & vld & (slot.bank === detect.bank) & (slot.row === detect.row) & (slot.col === detect.col) 
      case StationOrg.PerBa   => io.cmdDetect.valid & vld & (slot.row === detect.row) & (slot.col === detect.col)
    }
  }))//冲突检测逻辑
  // cmd in
  val conflict = confOH.reduce(_|_)
  val hasEmpty = empty.reduce(_|_)
  val emptyPos = PriorityEncoder(empty)//优先级编码器，输出第一个可用地址
  io.cmdIn.ready := !conflict && hasEmpty
  ageMatrix.io.enq := 0.U
  when(!conflict && hasEmpty && io.cmdIn.valid) {
    cmdSlot(emptyPos) := io.cmdIn.bits
    vld(emptyPos) := true.B
    empty(emptyPos) := false.B
    ageMatrix.io.enq := UIntToOH(emptyPos)
    if (org == StationOrg.Unified) {
      vldFlow(0)(emptyPos) := true.B 
    }

    if (org == StationOrg.PerBg) {
      vldFlow(io.cmdIn.bits.bank)(emptyPos) := true.B
    }

    if (org == StationOrg.PerBa) {
      val flow = (io.cmdIn.bits.bg << 2) + io.cmdIn.bits.bank
      vldFlow(flow)(emptyPos) := true.B
    }
  }


  // cmd out
  val age_deq = WireInit(VecInit.fill(entNum)(false.B))
  val hitVec = VecInit.tabulate(flowNum)(i => VecInit(vldFlow(i) zip cmdSlot map {case (vld, slot) => vld && prevRow(i) === slot.row}))
  dontTouch(hitVec)
  dontTouch(vldFlow)
  dontTouch(vld)
  dontTouch(empty)
  for (i <- 0 until flowNum) {
    io.cmdOut(i).valid := false.B
    io.cmdOut(i).bits  := DontCare
    when(io.grant & vldFlow(i).asUInt.orR) {
      val choose = WireInit(0.U)
      val hv = hitVec(i)
      when((vldFlow(i).asUInt & confOH.asUInt).orR) {  // has conflict
        choose := OHToUInt(confOH)
      }.elsewhen(hv.reduce(_|_)) {  // has row hit
        choose := OHToUInt(ageMatrix.getMuskedOldest(~hv.asUInt))
      }.otherwise {
        choose := OHToUInt(ageMatrix.getMuskedOldest(~vldFlow(i).asUInt))
      }
      io.cmdOut(i).valid := true.B
      io.cmdOut(i).bits  := cmdSlot(choose)
      when(io.cmdOut(i).fire) {
        age_deq(choose) := true.B
        vld(choose) := false.B
        vldFlow(i)(choose) := false.B
        empty(choose) := true.B
        prevRow(i) := cmdSlot(choose).row
      }
    }
  }
  ageMatrix.io.deq := age_deq.asUInt

  // low and high
  val vldNum = PopCount(vld)
  io.low  := vldNum <= lWater.U
  io.high := vldNum >= hWater.U
  io.conflict := conflict
  io.VldentryNum := vldNum
}
