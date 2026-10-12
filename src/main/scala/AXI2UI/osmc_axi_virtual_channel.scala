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
  val childTokenWidth = log2Ceil(PerRobEntryCmdNum)
  val vidWidth = math.max(1, log2Ceil(VirtualChannelNum))
  val io = IO(new Bundle {
    val SplitCommandQueue = Flipped(Decoupled(new SplitCommandQueueBundle(AXI_PARAM.AXI_IDW) ))//command input 
    val UiCommand         = DecoupledIO(new CMDIO())//ui command output
    val RobDeq            = DecoupledIO(Vec(2,new ReadQueueBundle(1)))//rob UiData deq
    val ui_rio            = Flipped(new ui_rdadtaIO())
  })
  val CmdDispatcher       = Module(new osmc_cmd_dispatcher(VirtualChannelNum, PerRobEntryCmdNum))
  val ChildRob            = Seq.fill(VirtualChannelNum)(Module(new osmc_axi_read_rob(PerRobEntryCmdNum)))
  val DataArbiter         = Module(new RRArbiter(new RdDataIO(), 2))
  val RobArbiter          = Module(new RRArbiter(Vec(2,new ReadQueueBundle(1)), VirtualChannelNum))

/************************************************************************************/
  //connect SplitCommandQueue to CmdDispatcher
  io.SplitCommandQueue              <> CmdDispatcher.io.axi_command_enq
  //connect CmdDispatcher to UiCommand
  CmdDispatcher.io.axi_command_deq  <> io.UiCommand
  
  DataArbiter.io.in(0).valid := io.ui_rio.dataFormScg.valid
  DataArbiter.io.in(0).bits  := io.ui_rio.dataFormScg.bits
  io.ui_rio.dataFormScg.ready := DataArbiter.io.in(0).ready
  DataArbiter.io.in(1).valid := io.ui_rio.dataFromCache.valid
  DataArbiter.io.in(1).bits  := io.ui_rio.dataFromCache.bits
  io.ui_rio.dataFromCache.ready := DataArbiter.io.in(1).ready

  val  VID = Wire(UInt(vidWidth.W))
  if (VirtualChannelNum == 1){
    VID := 0.U
  }else if (VirtualChannelNum == 2){
    VID := DataArbiter.io.out.bits.rtoken(BUNDLE_PARAM.TOKEN_WIDTH -1)
  }else{
    VID := DataArbiter.io.out.bits.rtoken(BUNDLE_PARAM.TOKEN_WIDTH -1, BUNDLE_PARAM.TOKEN_WIDTH -log2Ceil(VirtualChannelNum))
  }
  val selectedRobDataReady = if (VirtualChannelNum == 1) {
    ChildRob(0).io.ui_rio.ready
  } else {
    Mux1H((0 until VirtualChannelNum).map(i => (VID === i.U) -> ChildRob(i).io.ui_rio.ready))
  }
  DataArbiter.io.out.ready := selectedRobDataReady
  for(i <- 0 until VirtualChannelNum){
    /************************************************************************************/
        ChildRob(i).io.ui_rio.bits.rdata        := DataArbiter.io.out.bits.rdata
        ChildRob(i).io.ui_rio.valid             := DataArbiter.io.out.valid & i.U === VID
        ChildRob(i).io.ui_rio.bits.rtoken       := DataArbiter.io.out.bits.rtoken(childTokenWidth-1,0)
        ChildRob(i).io.TokenQueueEnq            <> CmdDispatcher.io.Vid2Rob(i)
    /************************************************************************************/
        RobArbiter.io.in(i)                     <> ChildRob(i).io.RobDeq
  }
  io.RobDeq.bits                                := RobArbiter.io.out.bits
  io.RobDeq.valid                               := RobArbiter.io.out.valid
  RobArbiter.io.out.ready                       := io.RobDeq.ready



    
}