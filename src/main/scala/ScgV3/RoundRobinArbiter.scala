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

class RoundRobinArbiter(n: Int) extends Module {
  val io = IO(new Bundle{
    val requests = Flipped(Vec(n, Bool()))
    val chosen = Output(UInt(log2Ceil(n).W))
    val valid = Output(Bool())
    val accept = Input(Bool())
  })
  
  // 轮询指针寄存器
  val pointer = RegInit(0.U(log2Ceil(n).W))
  val chosen = Wire(UInt(log2Ceil(n).W))

  // 生成请求掩码
  val requests = io.requests
  val maskedRequests = VecInit(requests.zipWithIndex.map { case (req, i) =>
    req && (i.U >= pointer)
  })

  
  // 选择逻辑
  io.valid := false.B
  chosen := pointer
  for (i <- n - 2 to 0 by -1) {
    when (requests(i)) {
      chosen := i.U
      io.valid := true.B
    }
  }

  for (i <- n - 1 to 0 by -1) {
    when (maskedRequests(i)) {
      chosen := i.U
      io.valid := true.B
    }
  }

  
  // 更新轮询指针
  when (requests(chosen) && io.accept) {
    pointer := Mux(chosen === (n-1).U, 0.U, chosen + 1.U)
  }
  
  io.chosen := chosen
}