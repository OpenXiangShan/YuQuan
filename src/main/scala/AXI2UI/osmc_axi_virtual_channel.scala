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



class osmc_axi_virtual_channel extends Module {
  val RobEntryNum = ROB_PARAM.RobEntryNum
  val VirtualChannelNum = ROB_PARAM.VirtualChannelNum
  val PerRobEntryCmdNum = RobEntryNum / VirtualChannelNum
  val io = IO(new Bundle {
    val SplitCommandQueue = Flipped(Decoupled(new SplitCommandQueueBundle(AXI_PARAM.AXI_IDW) ))//command input 
    val UiCommand         = DecoupledIO(new CMDIO())//ui command output
    val RobDeq            = DecoupledIO(Vec(2,new ReadQueueBundle(1)))//rob UiData deq
    val ui_rio            = Flipped(DecoupledIO(new RdDataIO(BUNDLE_PARAM.TOKEN_WIDTH)))
  })
dontTouch(io)
  val CmdDispatcher       = Module(new osmc_cmd_dispatcher(VirtualChannelNum, PerRobEntryCmdNum))
  val ChildRob            = Seq.fill(VirtualChannelNum)(Module(new osmc_axi_read_rob(PerRobEntryCmdNum)))
  val DataArbiter         = Module(new RoundRobinArbiter(VirtualChannelNum))

/************************************************************************************/
  //connect SplitCommandQueue to CmdDispatcher
  io.SplitCommandQueue              <> CmdDispatcher.io.axi_command_enq
  //connect CmdDispatcher to UiCommand
  CmdDispatcher.io.axi_command_deq  <> io.UiCommand

  val  VID = RegInit(0.U(BUNDLE_PARAM.TOKEN_WIDTH.W))
  if (VirtualChannelNum == 1){
    VID := 0.U
  }else if (VirtualChannelNum == 2){
    VID := io.ui_rio.bits.rtoken(BUNDLE_PARAM.TOKEN_WIDTH -1)
  }else{
    VID := io.ui_rio.bits.rtoken(BUNDLE_PARAM.TOKEN_WIDTH -1, BUNDLE_PARAM.TOKEN_WIDTH -log2Ceil(VirtualChannelNum))
  }
  for(i <- 0 until VirtualChannelNum){
    /************************************************************************************/
        ChildRob(i).io.ui_rio.bits.rdata        := io.ui_rio.bits.rdata
        ChildRob(i).io.ui_rio.valid             := io.ui_rio.valid & i.U === VID
        ChildRob(i).io.ui_rio.bits.rtoken       := io.ui_rio.bits.rtoken(log2Ceil(PerRobEntryCmdNum)-1,0)
        ChildRob(i).io.TokenQueueEnq            <> CmdDispatcher.io.Vid2Rob(i)
    /************************************************************************************/
        DataArbiter.io.requests(i)              := ChildRob(i).io.RobDeq.valid & io.RobDeq.ready 
        ChildRob(i).io.RobDeq.ready             := DataArbiter.io.chosen === i.U & io.RobDeq.ready
        ChildRob(i).io.ui_rio.bits              := io.ui_rio.bits
        ChildRob(i).io.ui_rio.valid             := io.ui_rio.valid & i.U === VID 
        io.ui_rio.ready                         := true.B
  }
  io.RobDeq.bits                                := MuxCase(0.U.asTypeOf(io.RobDeq.bits),(0 until VirtualChannelNum).map{i => (DataArbiter.io.chosen === i.U) -> ChildRob(i).io.RobDeq.bits})
  io.RobDeq.valid                               := DataArbiter.io.valid
  DataArbiter.io.accept                         := io.RobDeq.fire



    
}