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

class WriteCombineBuf extends CacheModule {
    val io = IO(new Bundle {
        // req from main pipe
        val mpMissReq = Flipped(Decoupled(new WCBReqBundle)) 

        // TODO: add search WCB logic 
        val mpSearchReq = Flipped(Decoupled(new SplitCmdIO{ val CmdType = Output(Bool())}))
        val mpOverwrite = Flipped(Decoupled(new WrDataIO))
        val isMatch = Output(Bool())
        val isMatchData = Output(UInt(CacheLineBits.W))

        // req to AS 
        val asReq = Decoupled(new SplitCmdIO)
        val asData = Decoupled(new WrDataIO)
    })

    io.mpMissReq.ready := true.B 
    io.mpSearchReq.ready := true.B 
    io.mpOverwrite.ready := true.B 
    io.isMatch := false.B 
    io.isMatchData := 0.U
    io.asReq.valid := false.B 
    io.asData.valid := false.B 
}

class MissRegister extends CacheModule {
    val io = IO(new Bundle {
        // req from main pipe
        val mpReq = Flipped(Decoupled(new MSHRInfo))

        // req to AS 
       val asReq = Decoupled(new SplitCmdIO{val mshrID = Input(UInt(log2Up(nMissEntries).W))})
        
        // data from as 
        val asRetData = Flipped(Decoupled(UInt(BUNDLE_PARAM.DATA_WIDTH.W)))
        //val asRetToken = Flipped(Decoupled(UInt(token_width.W)))
        val asRetToken = Flipped(Decoupled(UInt(log2Up(nMissEntries).W))) 

        // req to refill pipeline 
        val rpReq = Decoupled(new MSHRInfo{ val rdata = UInt(BUNDLE_PARAM.DATA_WIDTH.W)})
    })

    io.mpReq.ready := true.B
    io.asReq.valid := false.B 
    io.asRetData.ready := true.B 
    io.asRetToken.ready := true.B 
    io.rpReq.valid := false.B 
}

sealed class CacheWraperIO extends CacheBundle {
    // Interface With AddrMap
    val RdCmdFromAddrMap = Flipped(Decoupled(new SplitCmdIO))
    val RdData2AddrMap = Decoupled(new RdDataIO)
    val WrCmdFromAddrMap = Flipped(Decoupled(new SplitCmdIO))
    val WrDataFromAddrMap = Flipped(Decoupled(new WrDataIO))

    // Interface With AS
    val RdCmd2AS = Decoupled(new SplitCmdIO(cTokenLen))
    val dataFromAS = Flipped(Decoupled(new DataIO(cTokenLen)))
    val WrCmd2AS = Decoupled(new SplitCmdIO)
    val WrData2AS = Decoupled(new WrDataIO)
}

class CacheWraper extends CacheModule {
    val io = FlatIO(new CacheWraperIO)

    val requestQueue = Module(new RequestQueue)
    val mainPipe = Module(new MainPipe_by)
    val refillPipe = Module(new RefillPipe_by) 
    val writeCombineBuf = Module(new WCB_by)
    val missRegister = Module(new MSHR_by)

    val tagArray = Module(new BankedTagArray_by)
    val dataArray = Module(new BankedDataArray_by)

    requestQueue.io.RdCmdFromAddrMap <> io.RdCmdFromAddrMap
    requestQueue.io.WrCmdFromAddrMap <> io.WrCmdFromAddrMap
    requestQueue.io.WrDataFromAddrMap <> io.WrDataFromAddrMap
    
    mainPipe.io.RequestCmd <> requestQueue.io.RequestCmd
    mainPipe.io.RequestwData <> requestQueue.io.RequestwData
    val empty = mainPipe.io.empty
    // TODO MainPipe Interface with WCB
    // TODO MainPipe Interface with MSHR
    writeCombineBuf.io.mpSearchReq <> mainPipe.io.searchReq
    mainPipe.io.conflictWCB := writeCombineBuf.io.isMatch
    mainPipe.io.conflictWCBData := writeCombineBuf.io.isMatchData
    writeCombineBuf.io.mpMissReq <> mainPipe.io.wcbReq
    writeCombineBuf.io.clearConflict <> mainPipe.io.clearConflict

    //refillPipe.io.rdDataFromAS <> io.RdDataFromAS
    // TODO RefillPipe Interface with MSHR
    missRegister.io.mpReq <> mainPipe.io.mshrReq
    refillPipe.io.mshrRefillReq <> missRegister.io.rpReq
    io.RdCmd2AS <> missRegister.io.asReq
    io.WrCmd2AS <> writeCombineBuf.io.asReq
    io.WrData2AS <> writeCombineBuf.io.asData
    io.dataFromAS <> missRegister.io.dataFromAS
    
    // Arbitrate rdData and dataWrite and tagWrite 
    val rdDataArb = Module(new Arbiter(new RdDataIO, 2))
    rdDataArb.io.in(0) <> refillPipe.io.rdData2AddrMap
    rdDataArb.io.in(1) <> mainPipe.io.rdData2AddrMap
    io.RdData2AddrMap <> rdDataArb.io.out

    val tagWriteArb = Module(new Arbiter(new TagWriteReq_by, 2))
    tagWriteArb.io.in(0) <> refillPipe.io.tagWriteBus.req
    tagWriteArb.io.in(1) <> mainPipe.io.tagWriteBus.req

    val dataWriteArb = Module(new Arbiter(new DataWriteReq_by, 2))
    dataWriteArb.io.in(0) <> refillPipe.io.dataWriteBus.req
    dataWriteArb.io.in(1) <> mainPipe.io.dataWriteBus.req

    tagArray.io.read <> mainPipe.io.tagReadBus.req
    tagArray.io.write <> tagWriteArb.io.out
    dataArray.io.read <> mainPipe.io.dataReadBus.req
    dataArray.io.write <> dataWriteArb.io.out
    mainPipe.io.tagReadBus.resp := tagArray.io.resp.asTypeOf(new TagReadResult)
    mainPipe.io.dataReadBus.resp := dataArray.io.resp
}
