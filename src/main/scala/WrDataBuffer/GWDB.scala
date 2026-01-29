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
import apb._
import OpenMc.BUNDLE_PARAM
import os.truncate

class DeqtoDFIIO (Token_Width : Int)extends Bundle{
            val wdata   = UInt(BUNDLE_PARAM.DATA_WIDTH.W)
            val wstrb   = UInt((BUNDLE_PARAM.DATA_WIDTH>>3).W)
            val takeToken=Flipped(UInt(Token_Width.W))
            val valid   = Bool()
            val ready   = Flipped(Bool())
}

class GWDB extends Module{
    val entryNums = CONFIGURABLE_PARAM.WrSchedulerQueueDepth << (BUNDLE_PARAM.RANK_WIDTH + BUNDLE_PARAM.BG_WIDTH) 
    val ScgQueueEntry = 1<<(BUNDLE_PARAM.RANK_WIDTH + BUNDLE_PARAM.BG_WIDTH + BUNDLE_PARAM.BANK_WIDTH)
    val io = IO(new Bundle{
        //cmd and data form addrmap
        val CmdEnqFromAddrmap =Flipped(Decoupled(new SplitCmdIO))
        val DataEnqFromAddrap =Flipped(Decoupled(new WrDataIO))
        //cmd and data from cache
        val CmdEnqFromCache  = Flipped(Decoupled(new SplitCmdIO))
        val DataEnqFromCache = Flipped(Decoupled(new WrDataIO))
        //cmd to AS
        val AddrmapCmd2AS    = Decoupled(new SplitCmdIO(Token_Width = log2Ceil(entryNums+ScgQueueEntry)))
        val CacheCmd2AS      = Decoupled(new SplitCmdIO(Token_Width = log2Ceil(entryNums+ScgQueueEntry)))
        //data to DFI
        val DeqtoDFI  = new DeqtoDFIIO(Token_Width = log2Ceil(entryNums+ScgQueueEntry))
        //
        val calDone    = Flipped(Bool())
    })
        //定义存储体
        val dataMem     = SyncReadMem(entryNums,UInt(BUNDLE_PARAM.DATA_WIDTH.W)) 
        val dataMemAdd  = SyncReadMem(ScgQueueEntry,UInt(BUNDLE_PARAM.DATA_WIDTH.W))
        val wstrbMem    = SyncReadMem(entryNums,UInt((BUNDLE_PARAM.DATA_WIDTH >> 3).W))
        val wstrbMemAdd = SyncReadMem(ScgQueueEntry,UInt((BUNDLE_PARAM.DATA_WIDTH >> 3).W))
        val MaxBit = log2Ceil(entryNums+ScgQueueEntry)
        //定义可用地址队列
        val AddrQueue = Module(new addrQueue(entryNums+ScgQueueEntry))
        AddrQueue.io.calDone := io.calDone
        val conflict  = io.CmdEnqFromAddrmap.valid & io.CmdEnqFromCache.valid
        
        io.CmdEnqFromCache.ready := AddrQueue.io.enq.ready & io.CacheCmd2AS.ready
        io.DataEnqFromCache.ready:= AddrQueue.io.enq.ready & io.CacheCmd2AS.ready
        io.CmdEnqFromAddrmap.ready := Mux(conflict,false.B,AddrQueue.io.enq.ready & io.AddrmapCmd2AS.ready)
        io.DataEnqFromAddrap.ready := Mux(conflict,false.B,AddrQueue.io.enq.ready & io.AddrmapCmd2AS.ready) 
        //
        io.AddrmapCmd2AS.valid    := io.CmdEnqFromAddrmap.valid & AddrQueue.io.deq.valid
        io.CacheCmd2AS.valid      := io.CmdEnqFromCache.valid   & AddrQueue.io.deq.valid
        //
        io.CacheCmd2AS.bits.bank  := io.CmdEnqFromCache.bits.bank
        io.CacheCmd2AS.bits.bg    := io.CmdEnqFromCache.bits.bg
        io.CacheCmd2AS.bits.col   := io.CmdEnqFromCache.bits.col
        io.CacheCmd2AS.bits.pri   := io.CmdEnqFromCache.bits.pri
        io.CacheCmd2AS.bits.rank  := io.CmdEnqFromCache.bits.rank
        io.CacheCmd2AS.bits.row   := io.CmdEnqFromCache.bits.row
        io.CacheCmd2AS.bits.token := Mux(io.CacheCmd2AS.fire & AddrQueue.io.deq.fire,AddrQueue.io.deq.bits,0.U.asTypeOf(io.CacheCmd2AS.bits.token))
        AddrQueue.io.deq.ready       := io.AddrmapCmd2AS.fire | io.CacheCmd2AS.fire
        //
        io.AddrmapCmd2AS.bits.bank := io.CmdEnqFromAddrmap.bits.bank
        io.AddrmapCmd2AS.bits.bg   := io.CmdEnqFromAddrmap.bits.bg
        io.AddrmapCmd2AS.bits.col  := io.CmdEnqFromAddrmap.bits.col
        io.AddrmapCmd2AS.bits.pri  := io.CmdEnqFromAddrmap.bits.pri
        io.AddrmapCmd2AS.bits.rank := io.CmdEnqFromAddrmap.bits.rank
        io.AddrmapCmd2AS.bits.row  := io.CmdEnqFromAddrmap.bits.row
        io.AddrmapCmd2AS.bits.token:= Mux(io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire,AddrQueue.io.deq.bits,0.U.asTypeOf(io.AddrmapCmd2AS.bits.token))
        //write mem
        val wdata  = WireInit(0.U(BUNDLE_PARAM.DATA_WIDTH.W))
        val wstrb  = WireInit(0.U((BUNDLE_PARAM.DATA_WIDTH >> 3).W))
        when(io.CacheCmd2AS.fire & AddrQueue.io.deq.fire){
            wdata := io.DataEnqFromCache.bits.wdata
            wstrb := io.DataEnqFromCache.bits.wstrb
        }.elsewhen(io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire){
            wdata := io.DataEnqFromAddrap.bits.wdata
            wstrb := io.DataEnqFromAddrap.bits.wstrb
        }
        dataMem.do_readWrite(AddrQueue.io.deq.bits,wdata,!AddrQueue.io.deq.bits(MaxBit-1) &((io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire) | (io.CacheCmd2AS.fire & AddrQueue.io.deq.fire)),true.B)
        wstrbMem.do_readWrite(AddrQueue.io.deq.bits,wstrb,!AddrQueue.io.deq.bits(MaxBit-1) &((io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire) | (io.CacheCmd2AS.fire & AddrQueue.io.deq.fire)),true.B)
        dataMemAdd.do_readWrite(AddrQueue.io.deq.bits,wdata,AddrQueue.io.deq.bits(MaxBit-1) &((io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire) | (io.CacheCmd2AS.fire & AddrQueue.io.deq.fire)),true.B)
        wstrbMemAdd.do_readWrite(AddrQueue.io.deq.bits,wstrb,AddrQueue.io.deq.bits(MaxBit-1) &((io.AddrmapCmd2AS.fire & AddrQueue.io.deq.fire) | (io.CacheCmd2AS.fire & AddrQueue.io.deq.fire)),true.B)
        //enq AddrQueue
        io.DeqtoDFI.valid := AddrQueue.io.enq.ready
        io.DeqtoDFI.wdata := Mux(RegNext(io.DeqtoDFI.valid & io.DeqtoDFI.ready) ,Mux(RegNext(io.DeqtoDFI.takeToken(MaxBit-1)),dataMemAdd.read(io.DeqtoDFI.takeToken,io.DeqtoDFI.ready&io.DeqtoDFI.valid),dataMem.read(io.DeqtoDFI.takeToken,io.DeqtoDFI.ready&io.DeqtoDFI.valid))  ,0.U.asTypeOf(io.DeqtoDFI.wdata) )
        io.DeqtoDFI.wstrb := Mux(RegNext(io.DeqtoDFI.valid & io.DeqtoDFI.ready) ,Mux(RegNext(io.DeqtoDFI.takeToken(MaxBit-1)),wstrbMemAdd.read(io.DeqtoDFI.takeToken,io.DeqtoDFI.ready&io.DeqtoDFI.valid),wstrbMem.read(io.DeqtoDFI.takeToken,io.DeqtoDFI.ready&io.DeqtoDFI.valid)) ,0.U.asTypeOf(io.DeqtoDFI.wstrb) )
        AddrQueue.io.enq.valid := io.DeqtoDFI.ready
        AddrQueue.io.enq.bits  := io.DeqtoDFI.takeToken

}

