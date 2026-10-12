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
import _root_.circt.stage.ChiselStage
import OpenMc._
import APB.FilterRegIO

object wCounter {
    def apply(cond: Bool, width:Int): UInt = {
        val counter = RegInit(0.U(width.W))
        when(cond){
            counter := counter + 1.U
        }
        counter 
    }
}

class filter_top (val ADDR_WIDTH: Int, val PRIORITY_WIDTH: Int, val TOKEN_WIDTH: Int, 
                  val DATA_WIDTH: Int, val STRB_WIDTH: Int) extends Module {
    val io = IO(new Bundle{
        //UI
        val wreq           = Flipped(Decoupled(new WriteReqIO(TOKEN_WIDTH)))
        val wcache_en      = Output(Bool())
        val rcmd           = Flipped(Decoupled(new CMDIO()))
        val rcache_en      = Output(Bool())
        val rui_data      = Decoupled(new RdDataIO())

        //ADDR MAP
        val wreq_addrmap  = Decoupled(new WriteReqIO(TOKEN_WIDTH))
        val rcmd_addrmap  = Decoupled(new CMDIO())
        val raddr_data     = Flipped(Decoupled(new RdDataIO()))

        // ftregio regs
        val ftregio = Flipped(new FilterRegIO)
    })
    io.ftregio := DontCare
    io.ftregio.rdCmdCnt := wCounter(io.rcmd_addrmap.fire, 32)
    io.ftregio.wrCmdCnt := wCounter(io.wreq_addrmap.fire, 32)
// ---------------------readback data fifo------------------------
    val rdata_queue = Module(new Queue(new RdDataIO(), 8, useSyncReadMem = true))
    rdata_queue.io.enq :<>= io.raddr_data
    io.rui_data :<>= rdata_queue.io.deq
    ////////////////////////////////////////////////////////////////////////////////
    val wreq_fifo = Module(new filter_fifo(new WriteReqIO(TOKEN_WIDTH), (req: WriteReqIO) => req.cmd.addr, 8))
    val rcmd_fifo = Module(new filter_fifo(new CMDIO(TOKEN_WIDTH), (cmd: CMDIO) => cmd.addr, 8))

    val writeConflict = rcmd_fifo.io.conflict
    val readConflict = wreq_fifo.io.conflict
    val wreqEnqReady = wreq_fifo.io.enq.ready && !writeConflict
    wreq_fifo.io.enq.bits := io.wreq.bits
    wreq_fifo.io.enq.valid := io.wreq.valid && wreqEnqReady
    io.wreq.ready := wreqEnqReady
////////////////////////////////////////////////////////////////////////////////
// wfifo enq and conflict
    wreq_fifo.io.conflictCheck.valid := io.rcmd.valid
    wreq_fifo.io.conflictCheck.bits := io.rcmd.bits.addr


    val wcmd_split = Module(new filter_cmd_split (ADDR_WIDTH , PRIORITY_WIDTH , TOKEN_WIDTH ))
    val wdeqValid = WireInit(wreq_fifo.io.deq.valid)
    val wdeqReady = io.wreq_addrmap.ready
    wcmd_split.io.addr_boundary := Cat(io.ftregio.adrbdh(35, 0), io.ftregio.adrbdl(35, 0))
    wcmd_split.io.mode          := io.ftregio.mode
    wcmd_split.io.cmd           := wreq_fifo.io.deq.bits.cmd.addr
    wcmd_split.io.cmd_en        := wreq_fifo.io.deq.valid
    wreq_fifo.io.deq.ready := wdeqReady

    io.wcache_en                 := wcmd_split.io.cache_en 
    io.wreq_addrmap.bits := wreq_fifo.io.deq.bits
    io.wreq_addrmap.valid := wdeqValid
////////////////////////////////////////////////////////////////////////////////
// rfifo enq and conflict
    rcmd_fifo.io.conflictCheck.valid := io.wreq.valid
    rcmd_fifo.io.conflictCheck.bits := io.wreq.bits.cmd.addr
    val canReadBypass = rcmd_fifo.io.empty && !readConflict
    rcmd_fifo.io.enq.bits := io.rcmd.bits
    rcmd_fifo.io.enq.valid := io.rcmd.valid && !canReadBypass && !readConflict
    io.rcmd.ready := !readConflict && Mux(canReadBypass, io.rcmd_addrmap.ready, rcmd_fifo.io.enq.ready)
    // rfifo deq
    val rcmd_split = Module(new filter_cmd_split(ADDR_WIDTH,PRIORITY_WIDTH,TOKEN_WIDTH))  
    val rcmdOut = Wire(chiselTypeOf(io.rcmd.bits))
    rcmdOut := rcmd_fifo.io.deq.bits
    when(canReadBypass) {
        rcmdOut := io.rcmd.bits
    }
    rcmd_split.io.addr_boundary := Cat(io.ftregio.adrbdh(35, 0), io.ftregio.adrbdl(35, 0))
    rcmd_split.io.mode          := io.ftregio.mode
    rcmd_split.io.cmd           := rcmdOut.addr
    rcmd_split.io.cmd_en        := Mux(canReadBypass, io.rcmd.valid, rcmd_fifo.io.deq.valid)
    rcmd_fifo.io.deq.ready      := io.rcmd_addrmap.ready && !canReadBypass
    io.rcmd_addrmap.bits        := rcmdOut
    io.rcache_en                 := rcmd_split.io.cache_en
    io.rcmd_addrmap.valid        := rcmd_split.io.cmd_valid
}