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
import utils._

class MSHREntry (token_width: Int = 10) extends Bundle {
    val cmd = new MSHRInfo
    val isValid = Bool()
}

class MSHR_by (mshr_depth : Int = 64) extends Module with CacheConst {
    val queueWidth = log2Up(mshr_depth)
    val token_width = BUNDLE_PARAM.TOKEN_WIDTH + queueWidth
    val io = IO(new Bundle {
        // req from main pipe
        val mpReq = Flipped(Decoupled(new MSHRInfo))

        // req to AS 
       val asReq = Decoupled(new SplitCmdIO(token_width))
        
        // data from as 
        val dataFromAS = Flipped(Decoupled(new DataIO(token_width)))

        // req to refill pipeline 
        val rpReq = Decoupled(new MSHRInfo {val rdata = UInt(BUNDLE_PARAM.DATA_WIDTH.W)})

    })
    dontTouch(io)


    // select index queue, pop an index when mpReq occurs
    val idxQueue = Module(new Queue(UInt(queueWidth.W), mshr_depth))
    // pop cmd queue, pop an index when sending cmd to as
    val popQueue = Module(new Queue(UInt(queueWidth.W), mshr_depth))
    // TODO: add queue for returned token 
    val retQueue = Module(new Queue(UInt(queueWidth.W), mshr_depth))


    val initQueueVec = Wire(Vec(mshr_depth, UInt(queueWidth.W)))
    for (i <- 0 until mshr_depth) {
        initQueueVec(i) := i.U(queueWidth.W) 
    }
    val initQueue = RegInit(initQueueVec)

        // mshr ram 
    val MSHRCmdRAM = Module(new SRAMTemplate_by(
        new MSHRInfo,
        set = mshr_depth, 
        way = 1, 
        shouldReset = true,
        holdRead = true,
        singlePort = true
    ))

    val MSHRDataRAM = Module (new SRAMTemplate_by (
        UInt(BUNDLE_PARAM.DATA_WIDTH.W),
        set = mshr_depth,
        way = 1,
        shouldReset = true,
        holdRead = true,
        singlePort = true

    ))

    //val tmpCmd = RegEnable(io.mpReq.bits, io.mpReq.fire) 
    val popCmdQueue = Module(new Queue(chiselTypeOf(io.mpReq.bits.splitCmd), mshr_depth))//chiselTypeOf是创建io.mpReq.bits.splitCmd这样的类型，但是生成的对象是没有硬连线的
    val mshrID = io.dataFromAS.bits.token(token_width-1, BUNDLE_PARAM.TOKEN_WIDTH)
    val refillIdx = RegEnable(mshrID, io.dataFromAS.fire) 
   
    val tmpRetCmd = RegEnable(MSHRCmdRAM.io.r.resp.data(0), io.dataFromAS.fire)
    val bypassRetData = RegNext(io.dataFromAS.bits.data)
    val bypassDataRAM = RegNext(io.dataFromAS.fire)

    // apply bits 
    MSHRCmdRAM.io.r.req.valid := io.dataFromAS.fire
    MSHRCmdRAM.io.r.req.bits.apply(setIdx = mshrID)  
    MSHRCmdRAM.io.w.req.valid := io.mpReq.fire
    MSHRCmdRAM.io.w.req.bits.apply(
        setIdx = idxQueue.io.deq.bits,
        data = io.mpReq.bits,
        waymask = 1.U
    )

    MSHRDataRAM.io.r.req.valid := bypassDataRAM
    MSHRDataRAM.io.r.req.bits.apply(setIdx = refillIdx) 
    MSHRDataRAM.io.w.req.valid := io.dataFromAS.fire
    MSHRDataRAM.io.w.req.bits.apply(
        setIdx = mshrID,
        data =  io.dataFromAS.bits.data,
        waymask = 1.U
    )

    //io.rpReq.bits.splitCmd :=  tmpRetCmd.splitCmd
    //io.rpReq.bits.waymask :=  tmpRetCmd.waymask
    //io.rpReq.bits.prefetch :=  tmpRetCmd.prefetch
    
    io.rpReq.bits.splitCmd :=  MSHRCmdRAM.io.r.resp.data(0).splitCmd
    io.rpReq.bits.waymask :=  MSHRCmdRAM.io.r.resp.data(0).waymask
    io.rpReq.bits.prefetch :=  MSHRCmdRAM.io.r.resp.data(0).prefetch
    io.rpReq.bits.rdata :=  Mux(bypassDataRAM, bypassRetData, MSHRDataRAM.io.r.resp.data(0))

    /*io.asReq.bits.rank := tmpCmd.splitCmd.rank
    io.asReq.bits.bg := tmpCmd.splitCmd.bg
    io.asReq.bits.bank := tmpCmd.splitCmd.bank
    io.asReq.bits.row := tmpCmd.splitCmd.row
    io.asReq.bits.col := tmpCmd.splitCmd.col
    io.asReq.bits.pri := tmpCmd.splitCmd.pri
    io.asReq.bits.token := Cat(popQueue.io.deq.bits, tmpCmd.splitCmd.token)*/

    io.asReq.bits.rank := popCmdQueue.io.deq.bits.rank
    io.asReq.bits.bg := popCmdQueue.io.deq.bits.bg
    io.asReq.bits.bank := popCmdQueue.io.deq.bits.bank
    io.asReq.bits.row := popCmdQueue.io.deq.bits.row
    io.asReq.bits.col := popCmdQueue.io.deq.bits.col
    io.asReq.bits.pri := popCmdQueue.io.deq.bits.pri
    io.asReq.bits.token := Cat(popQueue.io.deq.bits, popCmdQueue.io.deq.bits.token)



    // TODO: initialize queue
    // reset Counter width should be widden then queue depth
    val resetCnt = RegInit(UInt(8.W), 0.U)
    when (resetCnt < mshr_depth.asUInt(8.W)) {
        // reset 
        idxQueue.io.enq.bits := initQueue(resetCnt(queueWidth-1,0))
        idxQueue.io.enq.valid := Mux(idxQueue.io.enq.ready, true.B, false.B)
        idxQueue.io.deq.ready := false.B

        io.mpReq.ready := false.B 
        io.dataFromAS.ready := false.B

        resetCnt := resetCnt + 1.U
        
    }.otherwise { 
        // reset reg is 0, reset finished?
        idxQueue.io.enq.bits := refillIdx
        idxQueue.io.enq.valid := io.rpReq.fire
        idxQueue.io.deq.ready := io.mpReq.fire

        resetCnt := resetCnt

        io.mpReq.ready := idxQueue.io.deq.valid && popQueue.io.enq.ready & (!io.dataFromAS.fire) 

        // SRAM overwrites read command with write command 
        io.dataFromAS.ready := retQueue.io.enq.ready 
    }

    popQueue.io.enq.bits := idxQueue.io.deq.bits
    popQueue.io.enq.valid := io.mpReq.fire 
    popQueue.io.deq.ready := io.asReq.fire

    retQueue.io.enq.bits := mshrID 
    retQueue.io.enq.valid := io.dataFromAS.fire 
    retQueue.io.deq.ready := io.rpReq.fire 

    popCmdQueue.io.enq.bits := io.mpReq.bits.splitCmd
    popCmdQueue.io.enq.valid := io.mpReq.fire 
    popCmdQueue.io.deq.ready := io.asReq.fire

    dontTouch(idxQueue.io)
    dontTouch(popQueue.io)
    dontTouch(retQueue.io)
    dontTouch(popCmdQueue.io)

    io.asReq.valid := popQueue.io.deq.valid


    // TODO: read RAM has 1cycle delay, when RAM is written, can not read RAM
    //io.rpReq.valid := RegNext(retQueue.io.deq.valid)
    io.rpReq.valid := retQueue.io.deq.valid

}
