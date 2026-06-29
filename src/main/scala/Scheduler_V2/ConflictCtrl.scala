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
import chisel3.util.experimental.decode.decoder

class ConflictCtrl[T <: SchedulerSCGCmd](gen: T) extends Module {
  val io = IO(new Bundle {
    val cmdIn     = Flipped(Decoupled(gen))
    val cmdDetect = Valid(new SplitCmdIO(Token_Width = 0))
    val conflict  = Vec(2, Flipped(Bool())) // 0 -> read, 1 -> write
    val cmdOut    = Decoupled(gen) // to cmd station
  })
  io.cmdDetect.valid := io.cmdIn.valid
  io.cmdDetect.bits.unsafe :<>= io.cmdIn.bits.unsafe

  val conflict = io.conflict.reduce(_|_)

  io.cmdOut.valid := io.cmdIn.valid & !conflict
  io.cmdOut.bits.unsafe :<>= io.cmdIn.bits.unsafe
  io.cmdIn.ready := io.cmdOut.ready & !conflict
}
