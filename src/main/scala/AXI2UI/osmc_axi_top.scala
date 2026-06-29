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
// import scala.annotation.newMain
import APB.AXI2UIRegIO

class AXI_TOPIO extends Bundle{
// axi
    val awio    = new AXI_AWIO()
    val wio     = new AXI_WIO()
    val bio     = new AXI_BIO()
    val ario    = new AXI_ARIO()
    val rio     = new AXI_RIO()
// ui
    val ui_wreq = Decoupled(new WriteReqIO())
    val ui_ario = Decoupled(new CMDIO())
    val ui_rio  = Flipped(Decoupled(new RdDataIO()))
// a2u regs
    val a2uregio = Flipped(new AXI2UIRegIO)
// apb config done 
    val apb_config_done = Flipped(Bool())
}

class osmc_axi_top[T <: AXI2UI_PARAMETER](
//AXI parameter define
    AXITOP_PARAM : T
) extends Module {

//IO define

val io = IO(new AXI_TOPIO()) 
io.a2uregio <> DontCare

//parameter transmit
val AXIW_PARAMETER  = AXITOP_PARAM
val AXIR_PARAMETER  = AXITOP_PARAM
val TOKEN_G_PARAMETER   = AXITOP_PARAM
val CONSIS_PARAMETER    = AXITOP_PARAM

val u_axi_write = Module(new osmc_axi_write(AXIW_PARAMETER))
    //AXI system
    u_axi_write.io.axi_awio                 <> io.awio
    u_axi_write.io.axi_wio                  <> io.wio
    u_axi_write.io.axi_bio                  <> io.bio
    u_axi_write.io.ui_wreq                  <> io.ui_wreq
    u_axi_write.io.apb_config_done          := io.apb_config_done

val u_axi_read  = Module(new osmc_axi_read(AXIR_PARAMETER))
    u_axi_read.io.axi_ario                  <> io.ario
    u_axi_read.io.axi_rio                   <> io.rio
    u_axi_read.io.ui_ario                   <> io.ui_ario
    u_axi_read.io.ui_rio                    <> io.ui_rio
    io.a2uregio.axiRdCmdCnt                 <> u_axi_read.io.axi_rdcmd_counter
    io.a2uregio.uiRdCmdCnt                  <> u_axi_read.io.ui_rdcmd_counter
    io.a2uregio.uiRbCmdCnt                  <> u_axi_read.io.ui_rdback_counter
    u_axi_read.io.apb_config_done           := io.apb_config_done


val u_axi_consis = Module(new osmc_axi_consis(CONSIS_PARAMETER))
    u_axi_consis.io.consis_io.wconsis       <> u_axi_write.io.wconsis
    u_axi_consis.io.consis_io.rconsis       <> u_axi_read.io.rconsis
    u_axi_consis.io.consis_waddr_io         <> u_axi_write.io.consis_addr_io
    u_axi_consis.io.consis_raddr_io         <> u_axi_read.io.consis_addr_io
}
