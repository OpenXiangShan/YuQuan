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
import java.nio.file.DirectoryStream.Filter

class CmdArbiter[T <: SchedulerSCGCmd](depth: Int = 2, gen: T) extends Module {
  val io = IO(new Bundle {
    // [0] -> Filter Rd, [1] -> Filter Wr, [2] -> cache Rd, [3]-> cache Wr
    val cmdIn  = Vec(4, Flipped(Decoupled(gen)))
    val cmdOut = Decoupled(gen) // to conflict control
  })
  val tFilterRd = SchedCmdType.FilterRd.asUInt
  val tFilterWr = SchedCmdType.FilterWr.asUInt
  val tCacheRd = SchedCmdType.CacheRd.asUInt
  val tCacheWr = SchedCmdType.CacheWr.asUInt

  val filterRd = io.cmdIn(0)
  val filterWr = io.cmdIn(1)
  val cacheRd = io.cmdIn(2)
  val cacheWr = io.cmdIn(3)

  val fcPrefer = RegInit(false.B) // prefer filter or cache
  val frwPrefer = RegInit(false.B) // prefer read or write
  val crwPrefer = RegInit(false.B)
  val fHoldPrefer = RegInit(0.U(2.W)) // 0 -> no hold, 1 -> hold read, 2 -> hold write
  val cHoldPrefer = RegInit(0.U(2.W)) // 0 -> no hold, 1 -> hold read, 2 -> hold write

  val PreferFilter = true.B
  val PreferCache  = false.B

  val HoldNone = 0.U
  val HoldRead = 1.U
  val HoldWrite = 2.U

  val PreferRd = true.B
  val PreferWr = false.B

  val fifo      = Module(new Queue(gen, depth, useSyncReadMem = true, pipe = true))


  // update prefers
// Sequential Logic
  fcPrefer := MuxCase(fcPrefer, Seq(
    (filterRd.fire | filterWr.fire) -> PreferCache,
    (cacheRd.fire | cacheWr.fire) -> PreferFilter
  ))

  def update_rw_prefer(rwPrefer:UInt, rd:DecoupledIO[T], wr:DecoupledIO[T]) = {
    rwPrefer := MuxCase(rwPrefer, Seq(
      rd.fire -> PreferWr,
      wr.fire -> PreferRd
    ))
  }
  // update hold prefers
  def update_holdPrefer(holdPrefer:UInt, rd:DecoupledIO[T], wr:DecoupledIO[T]) = {
    when(rd.fire | wr.fire) {
      holdPrefer := MuxCase(
        HoldNone,
        Seq(
          (rd.fire & wr.valid) -> HoldWrite,
          (wr.fire & rd.valid) -> HoldRead,
        )
      )
    }.otherwise {
      holdPrefer := MuxCase(
        holdPrefer,
        Seq(
          (holdPrefer === HoldNone & rd.valid) -> HoldRead,
          (holdPrefer === HoldNone & wr.valid) -> HoldWrite
        ) 
      )
    }
  }
  update_rw_prefer(frwPrefer, filterRd, filterWr)
  update_rw_prefer(crwPrefer, cacheRd, cacheWr)
  update_holdPrefer(fHoldPrefer, filterRd, filterWr)
  update_holdPrefer(cHoldPrefer, cacheRd, cacheWr)

// Combinatorial logic
// Enqueue selected command to FIFO
  val chosen = WireInit(OHToUInt(io.cmdIn.map(_.valid)))
  fifo.io.enq.valid := io.cmdIn(chosen).valid 
  fifo.io.enq.bits := io.cmdIn(chosen).bits
  // arbitrate between command inputs
  io.cmdIn.foreach(_.ready := false.B)
  io.cmdIn(chosen).ready := fifo.io.enq.ready

  def arbitrateCommands(
    rdValid: Bool, holdPrefer: UInt, rwPrefer: Bool, tRd: UInt, tWr: UInt
  ): UInt = {
    val result = WireInit(tRd)
    when(holdPrefer === HoldRead) {
      result := tRd
    }.elsewhen(holdPrefer === HoldWrite) {
      result := tWr
    }.otherwise {
      result := Mux((rwPrefer === PreferRd) && rdValid, tRd, tWr)
    }
    result
  }
  when(PopCount(VecInit(io.cmdIn.map(_.valid))) > 1.U) {
    when(fcPrefer) { // prefer filter
      when(filterRd.valid | filterWr.valid) { // look at filter
        chosen := arbitrateCommands(filterRd.valid, fHoldPrefer, frwPrefer, tFilterRd, tFilterWr)
      }.otherwise { // look at cache
        chosen := arbitrateCommands(cacheRd.valid, cHoldPrefer, crwPrefer, tCacheRd, tCacheWr)
      }
    }.otherwise { // prefer cache
      when(cacheRd.valid | cacheWr.valid) { // look at cache
        chosen := arbitrateCommands(cacheRd.valid, cHoldPrefer, crwPrefer, tCacheRd, tCacheWr)
      }.otherwise { // look at filter
        chosen := arbitrateCommands(filterRd.valid, fHoldPrefer, frwPrefer, tFilterRd, tFilterWr)
      }
    }
  }

  io.cmdOut :<>= fifo.io.deq
}