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
import chisel3.experimental.FlatIO
import java.util.ResourceBundle

class osmc_cmd_dispatcher (VirtualChannelNum : Int , PerRobEntryNum : Int)extends Module {
  val io = IO(new Bundle {
    val axi_command_enq = Flipped(DecoupledIO(new SplitCommandQueueBundle(AXI_PARAM.AXI_IDW)))
    val axi_command_deq = DecoupledIO(new CMDIO())
    val Vid2Rob         = Vec(VirtualChannelNum, DecoupledIO(new TokenEnqBundle))
  })
    val VID   = WireInit(log2Ceil(VirtualChannelNum).U)
    if (VirtualChannelNum == 1){
        VID := 0.U
    }else if (VirtualChannelNum == 2){
        VID := io.axi_command_enq.bits.id(0)
    }else{
        VID := io.axi_command_enq.bits.id(log2Ceil(VirtualChannelNum)-1,0)
    }
    val ChildToken  = RegInit(VecInit(Seq.fill(VirtualChannelNum)(0.U(log2Ceil(PerRobEntryNum).W))))
    io.axi_command_enq.ready            := io.Vid2Rob(VID).ready & io.axi_command_deq.ready
    io.axi_command_deq.bits.token       := Cat(VID,ChildToken(VID)).asTypeOf(io.axi_command_deq.bits.token)
    io.axi_command_deq.bits.addr        := io.axi_command_enq.bits.addr
    io.axi_command_deq.bits.pri         := false.B
    io.axi_command_deq.valid            := io.axi_command_enq.valid & io.Vid2Rob(VID).ready
    for(i <- 0 until VirtualChannelNum){
        io.Vid2Rob(i).valid             := VID === i.U && io.axi_command_enq.fire
        ChildToken(i)                   := Mux(VID === i.U && io.axi_command_enq.fire,ChildToken(i)+1.U, ChildToken(i))
        io.Vid2Rob(i).bits.token        := Mux(io.Vid2Rob(i).fire,ChildToken(i),0.U)
        io.Vid2Rob(i).bits.addr         := Mux(io.Vid2Rob(i).fire,io.axi_command_enq.bits.addr,0.U)
        io.Vid2Rob(i).bits.id           := Mux(io.Vid2Rob(i).fire,io.axi_command_enq.bits.id,0.U)
        io.Vid2Rob(i).bits.len          := Mux(io.Vid2Rob(i).fire,io.axi_command_enq.bits.len,0.U)
        io.Vid2Rob(i).bits.size         := Mux(io.Vid2Rob(i).fire,io.axi_command_enq.bits.size,0.U)
    }
}
 