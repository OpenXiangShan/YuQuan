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
import chisel3.experimental.FlatIO
import chisel3.util._
import BUNDLE_PARAM._
import os.read





class DFIAdapter extends Module{
    val io = IO(new Bundle {
        
        val dfi_ctrl_in                 =  Flipped( new dfiCtl) //from group 
        val dfi_ctrl_out                = new dfiCtl //控制命令输出
        val dfi_wrdata                  = Flipped(new WrDataIO)
        val dfi_write_ph                = Flipped(Vec(2, Bool()))
        val dfi_read_ph                 = Flipped(Vec(2, Bool()))
        val dfi_wrdata_ch_out           = new dfiWrData()
        val dfi_rddata_in               = new dfiRdData()
        val dfi_rddata_out              = UInt(BUNDLE_PARAM.DATA_WIDTH.W)
        val dfi_rddata_ready            = Bool()

        val dfi_parameter_mode          =     Flipped(UInt())             //0:MC 1:dram
        val tphy_wrlat                  =     Flipped(UInt(PARAMETERWIDTH.W))//
        val tphy_wrcslat                =     Flipped(UInt(PARAMETERWIDTH.W))//
        val tphy_wrdata                 =     Flipped(UInt(PARAMETERWIDTH.W))//

        val trddata_en                  =     Flipped(UInt(PARAMETERWIDTH.W))//
        val tphy_rdcslat                =     Flipped(UInt(PARAMETERWIDTH.W))//
        val tphy_rdlat                  =     Flipped(UInt(PARAMETERWIDTH.W))
        val wr_odt_delay                =     Flipped(UInt(PARAMETERWIDTH.W))
        val wr_odt_hold                 =     Flipped(UInt(PARAMETERWIDTH.W))
        val rd_odt_delay                =     Flipped(UInt(PARAMETERWIDTH.W))
        val rd_odt_hold                 =     Flipped(UInt(PARAMETERWIDTH.W))

        // val dfiupdate   = new dfiUpdate
        // val dfistatus   = new dfiStatus
        // val dfitraining = new dfiTraining
        // val dfilp       = new dfiLP/*  */
    })


dontTouch(io.dfi_ctrl_out)
dontTouch(io.dfi_rddata_in)
dontTouch(io.tphy_wrlat   )
dontTouch(io.tphy_wrcslat )
dontTouch(io.tphy_wrdata  )
dontTouch(io.trddata_en   )
dontTouch(io.tphy_rdcslat )
dontTouch(io.tphy_rdlat   )
dontTouch(io.wr_odt_delay    )
dontTouch(io.wr_odt_hold     )
dontTouch(io.rd_odt_delay    )
dontTouch(io.rd_odt_hold     )
    //CMD
    val  WRITE            = Wire(UInt(5.W))
    val  READ             = Wire(UInt(5.W))
    WRITE                 := "b01100".U
    READ                  := "b01101".U
    val  WL               = Wire(UInt(BUNDLE_PARAM.McParamWidth.W))
    WL                    := io.tphy_wrlat + io.tphy_wrdata 

    //写数据偏移处理
    val  write_vld          = io.dfi_write_ph.reduce(_||_)
    val  read_vld           = io.dfi_read_ph.reduce(_||_)
    
    val wrdata_delay = Module (new WrData_offset(BUNDLE_PARAM.DATABITS))
    wrdata_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    wrdata_delay.io.vld := RegNext(write_vld)
    wrdata_delay.io.write_latency := WL
    wrdata_delay.io.write_phase0 := RegNext(io.dfi_write_ph(0))
    wrdata_delay.io.write_phase1 := RegNext(io.dfi_write_ph(1))
    wrdata_delay.io.write_data   := io.dfi_wrdata.wdata
    io.dfi_wrdata_ch_out.dfi_wdata <> wrdata_delay.io.offset_data 
    //wrdata_mask 
    val  wrdata_mask_delay = Module (new WrData_offset(BUNDLE_PARAM.STRBBITS))
    wrdata_mask_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    wrdata_mask_delay.io.vld := RegNext(write_vld)
    wrdata_mask_delay.io.write_latency := WL
    wrdata_mask_delay.io.write_phase0 := RegNext(io.dfi_write_ph(0))
    wrdata_mask_delay.io.write_phase1 := RegNext(io.dfi_write_ph(1))
    wrdata_mask_delay.io.write_data   := io.dfi_wrdata.wstrb
    io.dfi_wrdata_ch_out.dfi_wdata_mask := wrdata_mask_delay.io.offset_data

    

    val wrdata_en_delay = Module (new dfi_en_offset(BUNDLE_PARAM.WrDataEnWidth))
    wrdata_en_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    wrdata_en_delay.io.vld := write_vld
    wrdata_en_delay.io.latency := io.tphy_wrlat
    wrdata_en_delay.io.vld_ph0 := io.dfi_write_ph(0)
    wrdata_en_delay.io.vld_ph1 := io.dfi_write_ph(1)
    wrdata_en_delay.io.offset_out <> io.dfi_wrdata_ch_out.dfi_wrdata_en

    val wrdata_cs_delay = Module(new dfi_en_offset(BUNDLE_PARAM.WrDataCsNWidth))
    wrdata_cs_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    wrdata_cs_delay.io.latency <> io.tphy_wrcslat
    wrdata_cs_delay.io.vld     <> write_vld
    wrdata_cs_delay.io.vld_ph0 <> io.dfi_write_ph(0)
    wrdata_cs_delay.io.vld_ph1 <> io.dfi_write_ph(1)
    io.dfi_wrdata_ch_out.dfi_wdata_cs_n := ~(wrdata_cs_delay.io.offset_out)

    val rddata_cs_delay  = Module(new dfi_en_offset(BUNDLE_PARAM.RdDataCsNWidth))
    rddata_cs_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    rddata_cs_delay.io.latency <> io.tphy_rdcslat
    rddata_cs_delay.io.vld     <> read_vld
    rddata_cs_delay.io.vld_ph0 <> io.dfi_read_ph(0)
    rddata_cs_delay.io.vld_ph1 <> io.dfi_read_ph(1)
    io.dfi_rddata_in.dfi_rddata_cs_n <> ~(rddata_cs_delay.io.offset_out)
    

    
    val rddata_en_delay = Module (new dfi_en_offset(BUNDLE_PARAM.RdDataEnWidth))
    rddata_en_delay.io.dfi_parameter_mode <> io.dfi_parameter_mode
    rddata_en_delay.io.vld := read_vld
    rddata_en_delay.io.latency := io.trddata_en
    rddata_en_delay.io.vld_ph0 := io.dfi_read_ph(0)
    rddata_en_delay.io.vld_ph1 := io.dfi_read_ph(1)
    io.dfi_rddata_in.dfi_rddata_en  := rddata_en_delay.io.offset_out


   val odt_delay = Module (new offset_odt_delay)
    odt_delay.io.read_vld_phase0 <> io.dfi_read_ph(0)
    odt_delay.io.read_vld_phase1 <> io.dfi_read_ph(1)
    odt_delay.io.write_vld_phase0<> io.dfi_write_ph(0)
    odt_delay.io.write_vld_phase1<> io.dfi_write_ph(1)
    odt_delay.io.rd_odt_delay    <> io.rd_odt_delay
    odt_delay.io.rd_odt_hold     <> io.rd_odt_hold
    odt_delay.io.wr_odt_delay    <> io.wr_odt_delay
    odt_delay.io.wr_odt_hold     <> io.wr_odt_hold
    io.dfi_ctrl_out.dfi_odt := Cat(odt_delay.io.odt_out,odt_delay.io.odt_out).asTypeOf(io.dfi_ctrl_out.dfi_odt)


    val RdValid         = dontTouch(RegInit(0.U(1.W)))
    val RdValidCntr     = dontTouch(RegInit(0.U(1.W)))


    val RdData_buf      = dontTouch(RegInit(VecInit.fill(2)(0.U(DATABITS.W))))

    RdValidCntr        := Mux((io.dfi_rddata_in.dfi_rddata_valid).orR, RdValidCntr + 1.U, RdValidCntr)


    when((RdValidCntr.asBool)&&((io.dfi_rddata_in.dfi_rddata_valid).orR)) {
        RdData_buf(1)  := io.dfi_rddata_in.dfi_rddata
        RdValid        := 1.U
    }.elsewhen((~(RdValidCntr.asBool))&&((io.dfi_rddata_in.dfi_rddata_valid).orR)) {
        RdData_buf(0)  := io.dfi_rddata_in.dfi_rddata
        RdValid        := 0.U
    }.otherwise {
        RdData_buf     := RdData_buf
        RdValid        := 0.U
    }

    io.dfi_rddata_ready := RdValid
    io.dfi_rddata_out   := RdData_buf.asUInt


    val cke             = RegInit(0.U((CKEBITS<<1).W))
    cke                 := VecInit.fill((CKEBITS<<1))(1.U).asTypeOf(cke)
    val reset_n           = RegInit(0.U((ResetNWidth<<1).W))
    reset_n               := VecInit.fill((ResetNWidth<<1))(1.U).asTypeOf(reset_n)
    
    io.dfi_ctrl_out :<>= io.dfi_ctrl_in
}