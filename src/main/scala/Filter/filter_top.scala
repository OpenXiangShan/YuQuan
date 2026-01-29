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
import apb.FilterRegIO

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
        val wcmd           = Flipped(Decoupled(new CMDIO()))
        val wdata          = Flipped(Decoupled(new WrDataIO()))
        val wcache_en      = Output(Bool())
        val rcmd           = Flipped(Decoupled(new CMDIO()))
        val rcache_en      = Output(Bool())
        val rui_data      = Decoupled(new RdDataIO())

        //ADDR MAP
        val wcmd_addrmap  = Decoupled(new CMDIO())
        val waddr_data    = Decoupled(new WrDataIO())
        val rcmd_addrmap  = Decoupled(new CMDIO())
        val raddr_data     = Flipped(Decoupled(new RdDataIO()))

        // ftregio regs
        val ftregio = Flipped(new FilterRegIO)
    })
    io.ftregio := DontCare
    io.ftregio.rdCmdCnt := wCounter(io.rcmd_addrmap.fire, 32)
    io.ftregio.wrCmdCnt := wCounter(io.wcmd_addrmap.fire, 32)
    io.ftregio.rdBackCnt := wCounter(io.rui_data.fire, 32)

    ////////////////////////////////////////////////////////////////////////////////
    val wdata_queue = Module(new Queue(new WrDataIO(), 8, useSyncReadMem = true))
    wdata_queue.io.enq :<>= io.wdata
////////////////////////////////////////////////////////////////////////////////
// ---------------------readback data fifo------------------------
    val rdata_queue = Module(new Queue(new RdDataIO(), 8, useSyncReadMem = true))
    rdata_queue.io.enq :<>= io.raddr_data
    io.rui_data :<>= rdata_queue.io.deq
////////////////////////////////////////////////////////////////////////////////
    val wrcmd_conflict = WireInit(false.B)
    // val wwbrcmd_token  = WireInit(false.B)
    val wcmd_fifo = Module(new filter_fifo(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH,8,3,ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH   -1 , PRIORITY_WIDTH  + TOKEN_WIDTH))  
    val rcmd_fifo = Module(new filter_fifo(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH,8,3,ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH   -1 , PRIORITY_WIDTH  + TOKEN_WIDTH))  
// wfifo enq and conflict
    wcmd_fifo.io.wconflict_en     := io.rcmd.valid
    wcmd_fifo.io.wconflict_data   := Cat(io.rcmd.bits.addr , io.rcmd.bits.pri , io.rcmd.bits.token)
    wcmd_fifo.io.write_en           := (!(rcmd_fifo.io.conflict) && !(wcmd_fifo.io.conflict ) ) && io.wcmd.valid
    io.wcmd.ready                   := (!(rcmd_fifo.io.conflict) && !(wcmd_fifo.io.conflict ) ) && !wcmd_fifo.io.full
    wcmd_fifo.io.data_i             := Cat(io.wcmd.bits.addr , io.wcmd.bits.pri , io.wcmd.bits.token)
    val wcmd_split = Module(new filter_cmd_split (ADDR_WIDTH , PRIORITY_WIDTH , TOKEN_WIDTH ))
    val wsync = (io.wcmd_addrmap.ready && io.waddr_data.ready && !wcmd_fifo.io.empty && wdata_queue.io.deq.valid)
    val wdeqValid = WireInit(!wcmd_fifo.io.empty && wdata_queue.io.deq.valid)
    val wdeqReady = io.wcmd_addrmap.ready && io.waddr_data.ready
    wcmd_split.io.addr_boundary := Cat(io.ftregio.adrbdh(35, 0), io.ftregio.adrbdl(35, 0))
    wcmd_split.io.mode          := io.ftregio.mode
    wcmd_split.io.cmd           := wcmd_fifo.io.data_o(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH   -1 , PRIORITY_WIDTH  + TOKEN_WIDTH )
    wcmd_split.io.cmd_en        :=  !wcmd_fifo.io.empty
    wdata_queue.io.deq.ready := wdeqReady & wdeqValid
    wcmd_fifo.io.read_en        := wdeqValid & wdeqReady

    io.waddr_data.bits := wdata_queue.io.deq.bits
    io.waddr_data.valid := wdeqValid 

    io.wcache_en                 := wcmd_split.io.cache_en 
    io.wcmd_addrmap.bits.addr   := wcmd_fifo.io.data_o(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH   -1 , PRIORITY_WIDTH  + TOKEN_WIDTH )
    io.wcmd_addrmap.bits.pri    := wcmd_fifo.io.data_o(PRIORITY_WIDTH + TOKEN_WIDTH  - 1 ,TOKEN_WIDTH)
    io.wcmd_addrmap.bits.token  := wcmd_fifo.io.data_o(TOKEN_WIDTH  - 1 , 0)
    io.wcmd_addrmap.valid       := wdeqValid 
////////////////////////////////////////////////////////////////////////////////
// rfifo enq and conflict
    rcmd_fifo.io.wconflict_en       := io.wcmd.valid
    rcmd_fifo.io.wconflict_data     := Cat(io.wcmd.bits.addr , io.wcmd.bits.pri , io.wcmd.bits.token)
    rcmd_fifo.io.write_en  :=  (!(rcmd_fifo.io.conflict) && !(wcmd_fifo.io.conflict ) ) && io.rcmd.valid
    io.rcmd.ready          :=  (!(rcmd_fifo.io.conflict) && !(wcmd_fifo.io.conflict ) ) && !rcmd_fifo.io.full
    rcmd_fifo.io.data_i    :=  Cat( io.rcmd.bits.addr , io.rcmd.bits.pri , io.rcmd.bits.token)
    // rfifo deq
    val rcmd_split = Module(new filter_cmd_split(ADDR_WIDTH,PRIORITY_WIDTH,TOKEN_WIDTH))  
    rcmd_split.io.addr_boundary := Cat(io.ftregio.adrbdh(35, 0), io.ftregio.adrbdl(35, 0))
    rcmd_split.io.mode          := io.ftregio.mode
    rcmd_split.io.cmd           := rcmd_fifo.io.data_o(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH -1 , PRIORITY_WIDTH + TOKEN_WIDTH)
    rcmd_split.io.cmd_en        := !rcmd_fifo.io.empty
    rcmd_fifo.io.read_en        := io.rcmd_addrmap.ready.asUInt
    io.rcmd_addrmap.bits.addr    := rcmd_fifo.io.data_o(ADDR_WIDTH + PRIORITY_WIDTH + TOKEN_WIDTH -1 , PRIORITY_WIDTH + TOKEN_WIDTH)
    io.rcmd_addrmap.bits.pri     := rcmd_fifo.io.data_o(PRIORITY_WIDTH + TOKEN_WIDTH - 1 , TOKEN_WIDTH)
    io.rcmd_addrmap.bits.token   := rcmd_fifo.io.data_o(TOKEN_WIDTH - 1 , 0)
    io.rcache_en                 := rcmd_split.io.cache_en
    io.rcmd_addrmap.valid        := rcmd_split.io.cmd_valid
}