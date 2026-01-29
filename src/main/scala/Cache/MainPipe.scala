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

abstract class CacheBundle extends Bundle with CacheConst
abstract class CacheModule extends Module with CacheConst

class CacheTagArrayReadBus extends CacheBundle {
    val req = Decoupled(new TagReadReq)
    val resp = Input(new TagReadResult)

    def apply(valid: Bool, bankIdx: UInt, setIdx: UInt) = {
        this.req.valid := valid
        this.req.bits.bank := bankIdx
        this.req.bits.set := setIdx
        this
    }
}

class CacheDataArrayReadBus extends CacheBundle {
    val req = Decoupled(new DataReadReq)
    val resp = Input(new DataReadResult)

    def apply(valid: Bool, bankIdx: UInt, setIdx: UInt) = {
        this.req.valid := valid
        this.req.bits.bank := bankIdx
        this.req.bits.set := setIdx
        this
    }
}

class CacheTagArrayWriteBus extends CacheBundle {
    val req = Decoupled(new TagWriteReq_by)

    def apply(valid: Bool, wdata: TagDataBundle, bankIdx: UInt, setIdx: UInt, waymask: UInt) = {
        this.req.valid := valid
        this.req.bits.bank := bankIdx
        this.req.bits.set := setIdx
        this.req.bits.waymask := waymask
        this.req.bits.wdata := wdata
    }
}

class CacheDataArrayWriteBus extends CacheBundle {
    val req = Decoupled(new DataWriteReq_by)

    def apply(valid: Bool, wdata: Bits, bankIdx: UInt, setIdx: UInt, waymask: UInt) = {
        this.req.valid := valid
        this.req.bits.bank := bankIdx
        this.req.bits.set := setIdx
        this.req.bits.waymask := waymask
        this.req.bits.wdata := wdata
    }
}

sealed class Stage1IO extends CacheBundle {
    val req = new SplitCmdIO {val CmdType = Output(Bool())}
}
sealed class CacheStage1IO extends CacheBundle {
    val in = Flipped(Decoupled(new SplitCmdIO {val CmdType = Output(Bool())}))
    val out = Decoupled(new Stage1IO)
    val tagReadBus = new CacheTagArrayReadBus
    val dataReadBus = new CacheDataArrayReadBus
    // From Stage2
    val blockedReq = Flipped(Decoupled(new SplitCmdIO {val CmdType = Output(Bool())}))
}

// Stage1
sealed class CacheStage1 extends CacheModule {
    val io = IO(new CacheStage1IO)

    // read tag array and data array
    io.blockedReq.ready := io.tagReadBus.req.ready && io.dataReadBus.req.ready
    val readBusValid = true.B
    //val readBusValid = io.blockedReq.fire || (io.in.valid && io.out.ready)

    val addr = Mux(io.blockedReq.valid, getAddr(io.blockedReq.bits), getAddr(io.in.bits))
    io.tagReadBus.apply(valid = readBusValid, bankIdx = getBankIdex(addr), setIdx = getSetIdex(addr))
    io.dataReadBus.apply(valid = readBusValid, bankIdx = getBankIdex(addr), setIdx = getSetIdex(addr))

    io.out.bits.req := io.in.bits
    io.out.valid := io.in.valid && io.tagReadBus.req.ready && io.dataReadBus.req.ready
    io.in.ready := (!io.in.valid || io.out.fire) && io.tagReadBus.req.ready && io.dataReadBus.req.ready
}

sealed class Stage2IO extends CacheBundle {
    val req = new SplitCmdIO {val CmdType = Output(Bool())} // 0-write, 1-read
    val tags = new TagReadResult    // multiways
    val datas = new DataReadResult  // multiways
    val hit = Output(Bool())
    val waymask = Output(UInt(Ways.W))  // pretreatment-3
    val isForwardData = Output(Bool())
    val forwardData = Output(new DataWriteReq_by)
    val wDataBundle = Output(new WrDataIO)
    val conflictWCB = Output(Bool())     
    val conflictWCBData= Output(UInt(CacheLineBits.W))
}
sealed class CacheStage2IO extends CacheBundle {
    val in = Flipped(Decoupled(new Stage1IO))
    val out = Decoupled(new Stage2IO)
    val inFire = Input(Bool())
    val readReqFire = Input(Bool())
    val tagReadResp = Input(new TagReadResult)
    val dataReadResp = Input(new DataReadResult)
    val tagWriteBus = Input(new CacheTagArrayWriteBus)    // from stage3
    val dataWriteBus = Input(new CacheDataArrayWriteBus)  // form stage3
    // pretreatment-1
    val RequestwData = Flipped(Decoupled(new WrDataIO))
    // From WCB
    val searchReq = Decoupled(new SplitCmdIO{ val CmdType = Output(Bool())})
    val conflictWCB = Input(Bool())
    val conflictWCBData = Input(UInt(CacheLineBits.W))
    // To Stage1
    val blockedReq = Decoupled(new SplitCmdIO {val CmdType = Output(Bool())})
}

// Stage 2
sealed class CacheStage2 extends CacheModule {
    val io = IO(new CacheStage2IO)

    val req = io.in.bits.req
    val addr = getAddr(req).asTypeOf(addrBundle)
    val readRespValid = RegNext(io.readReqFire, false.B)//创建一个延迟一拍的有效信号


    val isForwardTag = io.in.valid && io.tagWriteBus.req.valid && 
                        io.tagWriteBus.req.bits.bank === addr.bank &&
                        io.tagWriteBus.req.bits.set === addr.set
    val isForwardTagReg = RegInit(false.B)
    when(isForwardTag) { isForwardTagReg := true.B }
    when(io.in.fire || !io.in.valid) { isForwardTagReg := false.B }
    val forwardTagReg = RegEnable(io.tagWriteBus.req.bits, isForwardTag)//只有当使能信号有效时，才将输入数据写入寄存器

    val tagWays = Wire(Vec(Ways, new TagDataBundle))
    val tagReadRespHold = ReadRespHold(io.tagReadResp, io.inFire)
    val dataReadRespHold = ReadRespHold(io.dataReadResp, io.inFire)
    val tagReadRespRealHold = ReadRespHold(io.tagReadResp, readRespValid)    // First readResp 
    val dataReadRespRealHold = ReadRespHold(io.dataReadResp, readRespValid)  
    val pickForwardTag = isForwardTag || isForwardTagReg
    val forwardTag = Mux(isForwardTag, io.tagWriteBus.req.bits, forwardTagReg)
    val forwardWaymask = forwardTag.waymask.asBools
    forwardWaymask.zipWithIndex.map { case(w, i) =>
        tagWays(i) := Mux(pickForwardTag && w, forwardTag.wdata, tagReadRespHold.TagWays(i))
    }

    // block when tag match, but ready=0
    // block when tag miss,  and 4 ways all unready
    val blockVec = VecInit(tagReadRespRealHold.TagWays.map(t => t.valid && (!t.ready) && (t.tag === addr.tag) && io.in.valid)).asUInt
    val allUnReadyBlock = VecInit(tagReadRespRealHold.TagWays.map(t => t.valid && (!t.ready) && io.in.valid)).asUInt.andR
    val forwardBlock = isForwardTag && (!io.tagWriteBus.req.bits.wdata.ready)
    val forwardBlockReg = RegInit(false.B)
    when(forwardBlock) { forwardBlockReg := true.B }
    when(io.blockedReq.fire)  { forwardBlockReg := false.B }
    val block = Mux(forwardBlock || forwardBlockReg, true.B, blockVec.orR || allUnReadyBlock)
    val blockReg = RegInit(false.B)
    val isBlockReq = block || blockReg
    when(block) { blockReg := true.B }
    when(io.out.fire) { blockReg := false.B} 
    val blockTagReadRespHold = ReadRespHold(io.tagReadResp, !block && RegNext(block))   // Hold readResp when block release
    val blockDataReadRespHold = ReadRespHold(io.dataReadResp, !block && RegNext(block))
    

    val hitVec = VecInit(tagWays.map(t => t.valid && (t.tag === addr.tag) && io.in.valid)).asUInt//val hitVec = Mux(blockNeg, RegNext(blockVec), VecInit(tagWays.map(t => t.valid && t.ready && (t.tag === addr.tag) && io.in.valid)).asUInt)
    val invalidVec = VecInit(tagWays.map(t => !t.valid)).asUInt//只有可遍历的集合才能调用map，map参数列表是传入一个转换函数：参数 => 转换逻辑，函数，模式匹配
    val undirtyVec = VecInit(tagWays.map(t => !t.dirty)).asUInt//tagWays中的值是不会改变的，只有生成的新集合undirtyVec才是被map作用生成的值
    val readyVec = VecInit(tagWays.map(t => t.ready)).asUInt
    val hasInvalidWay = invalidVec.orR
    val hasUndirtyWay = undirtyVec.orR
    val hasUnreadyWay = !(readyVec.andR)
    val allUnreadyWay = !(readyVec.orR)

    val victimWaymask = if(Ways > 1) (1.U << LFSR64()(log2Up(Ways)-1,0)) else "b1".U    // random initial value based on simulation seed
    val refillInvalidWaymask = Mux(invalidVec >= 8.U, "b1000".U,
        Mux(invalidVec >= 4.U, "b0100".U,
        Mux(invalidVec >= 2.U, "b0010".U, "b0001".U)))
    val refillUndirtyWaymask = Mux(undirtyVec >= 8.U, "b1000".U,
        Mux(undirtyVec >= 4.U, "b0100".U,
        Mux(undirtyVec >= 2.U, "b0010".U, "b0001".U)))
    val refillReadyWaymask = Mux(readyVec >= 8.U, "b1000".U,
        Mux(readyVec >= 4.U, "b0100".U,
        Mux(readyVec >= 2.U, "b0010".U, "b0001".U)))
    val firstReadyVec = VecInit(tagReadRespRealHold.TagWays.map(t => t.valid && t.ready && io.in.valid)).asUInt
    val firstReadyWaymask = ReadRespHold(firstReadyVec, !block && RegNext(block)) 
    val waymask = Mux(io.out.bits.hit, hitVec, Mux(hasInvalidWay, refillInvalidWaymask, 
                        Mux(hasUndirtyWay, refillUndirtyWaymask, Mux(!hasUnreadyWay, victimWaymask, 
                            Mux(allUnreadyWay, firstReadyWaymask, refillReadyWaymask)))))       // all unready block: chose the way first become ready

    io.out.bits.hit := io.in.valid && hitVec.orR
    io.out.bits.waymask := waymask
    io.out.bits.tags  := Mux(isBlockReq, blockTagReadRespHold,  tagWays.asTypeOf(new TagReadResult))
    io.out.bits.datas := Mux(isBlockReq, blockDataReadRespHold, dataReadRespHold) //io.out.bits.datas := dataReadRespHold

    val isForwardData = io.in.valid && (io.dataWriteBus.req match { case r =>
        r.valid && r.bits.bank === addr.bank && r.bits.set === addr.set
    })
    val isForwardDataReg = RegInit(false.B)
    when(isForwardData) {isForwardDataReg := true.B}
    when(io.in.fire || !io.in.valid) {isForwardDataReg := false.B}
    val forwardDataReg = RegEnable(io.dataWriteBus.req.bits, 0.U.asTypeOf(chiselTypeOf(io.dataWriteBus.req.bits)), isForwardData)
    io.out.bits.isForwardData := isForwardData || isForwardDataReg
    io.out.bits.forwardData := Mux(isForwardData, io.dataWriteBus.req.bits, forwardDataReg)

    io.out.bits.req <> req
    io.out.valid := io.in.valid && (!block)
    io.in.ready := !io.in.valid || io.out.fire 
    io.blockedReq.valid := block
    io.blockedReq.bits := req
    io.RequestwData.ready := io.out.fire && (!io.in.bits.req.CmdType)
    io.out.bits.wDataBundle := io.RequestwData.bits

    // Search WCB
    io.searchReq.valid := io.inFire
    io.searchReq.bits := req
    val conflictWCBReg = RegInit(false.B)
    when(io.conflictWCB) {conflictWCBReg := true.B} 
    when(io.out.fire) { conflictWCBReg := false.B}
    io.out.bits.conflictWCB := io.conflictWCB || conflictWCBReg
    io.out.bits.conflictWCBData := ReadRespHold(io.conflictWCBData, io.conflictWCB)
}

sealed class CacheStage3IO extends CacheBundle {
    val in = Flipped(Decoupled(new Stage2IO))

    val mshrReq = Decoupled(new MSHRInfo)
    val wcbReq = Decoupled(new WCBReqBundle)
    val clearConflict = Decoupled(new WrDataIO { val overWrite = Output(Bool())})
    val hitReadData =  Decoupled(new RdDataIO)
    val dataWriteBus = new CacheDataArrayWriteBus
    val tagWriteBus = new CacheTagArrayWriteBus
    val isFinish = Output(Bool())
}

sealed class CacheStage3 extends CacheModule {
    val io = IO(new CacheStage3IO)

    val req = io.in.bits.req
    val addr = getAddr(req).asTypeOf(addrBundle)//asuint 将集合转换为无符号整数，astypeof将内容的二进制转换为指定类型
    val hit = io.in.valid && io.in.bits.hit
    val miss = io.in.valid && !io.in.bits.hit
    val tag = Mux1H(io.in.bits.waymask, io.in.bits.tags.TagWays).tag
    val valid = Mux1H(io.in.bits.waymask, io.in.bits.tags.TagWays).valid
    val dirty = Mux1H(io.in.bits.waymask, io.in.bits.tags.TagWays).dirty
    //val data = Mux1H(io.in.bits.waymask, io.in.bits.datas)

    val useForwardData = io.in.bits.isForwardData && io.in.bits.waymask === io.in.bits.forwardData.waymask
    val dataReadArray = Mux1H(io.in.bits.waymask, io.in.bits.datas.dataWays)
    val readData = Mux(useForwardData, io.in.bits.forwardData.wdata, dataReadArray)

    val wcbReqFireReg = RegInit(false.B)
    val clearConflictFireReg = RegInit(false.B)
    val mshrReqFireReg = RegInit(false.B)
    val arrayFireReg = RegInit(false.B)
    val hitReadDataFireReg = RegInit(false.B)
    val wcbReqFireHold = io.wcbReq.fire || wcbReqFireReg
    val clearConflictFireHold = io.clearConflict.fire || clearConflictFireReg
    val mshrReqFireHold = io.mshrReq.fire || mshrReqFireReg
    val arrayFireHold = io.tagWriteBus.req.fire || arrayFireReg
    val hitReadDataFireHold = io.hitReadData.fire || hitReadDataFireReg

    // 1. Normal Hit Write
    val hitWrite = hit && !req.CmdType && io.in.valid
    val arrayWriteBusFire = io.dataWriteBus.req.fire && io.tagWriteBus.req.fire
    val hitWriteIsFinish = hitWrite && arrayWriteBusFire

    // 2. Normal Hit Read
    val hitRead = hit && req.CmdType && io.in.valid
    val hitReadIsFinish = hitRead && io.hitReadData.fire
    
    // 3. Full Write Miss
    val missFullWrite = !hit && !req.CmdType && io.in.valid
    val missFullWriteIsFinish = missFullWrite && Mux(valid && dirty, wcbReqFireHold && arrayFireHold, arrayFireHold)
    
    // 4. Read Miss
    val missRead = !hit && req.CmdType && io.in.valid
    // val missReadIsFinish = missRead && Mux(io.in.bits.conflictWCB, io.hitReadData.fire, 
    //                                         Mux(valid && dirty, wcbReqFireHold && mshrReqFireHold && arrayFireHold, mshrReqFireHold && arrayFireHold))
    val missReadIsFinish = missRead && Mux(io.in.bits.conflictWCB, hitReadDataFireHold, mshrReqFireHold) && arrayFireHold && Mux(valid && dirty, wcbReqFireHold, true.B)

    io.mshrReq.valid := missRead && !io.in.bits.conflictWCB && !mshrReqFireReg
    // TODO: Prefetch
    io.mshrReq.bits.splitCmd := io.in.bits.req
    io.mshrReq.bits.waymask := io.in.bits.waymask
    io.mshrReq.bits.prefetch := false.B 

    // hitReadData
    // -- normal read hit
    // -- read miss but conflict with WCB
    io.hitReadData.valid := hitRead || (missRead && io.in.bits.conflictWCB && !hitReadDataFireReg)
    io.hitReadData.bits := Mux(hitRead, Cat(readData ,req.token).asTypeOf(io.hitReadData.bits), Cat(io.in.bits.conflictWCBData, req.token).asTypeOf(io.hitReadData.bits))
    // Send evict dirty block to WCB
    io.wcbReq.valid := valid && dirty && (missFullWrite || missRead) && !wcbReqFireReg
    io.wcbReq.bits := Cat(readData, tag, addr.set, addr.bank).asTypeOf(io.wcbReq.bits)
    // Overwrite the same addr in WCB
    io.clearConflict.valid :=  (missRead || missFullWrite) && io.in.bits.conflictWCB && !clearConflictFireReg
    io.clearConflict.bits := Cat(io.in.bits.wDataBundle.wdata, io.in.bits.wDataBundle.wstrb, Mux(missFullWrite, true.B, false.B)).asTypeOf(io.clearConflict.bits)

    // Stage3 write DataArray and TagArray
    val dataArrayWriteValid = (hitWrite || missFullWrite || (missRead && io.in.bits.conflictWCB)) && !arrayFireReg
    val tagArrayWriteValid = (hitWrite || missFullWrite || missRead) && !arrayFireReg 
    val dataArrayWrite = MuxCase(0.U, List(
        hitWrite -> io.in.bits.wDataBundle.wdata,
        missFullWrite -> io.in.bits.wDataBundle.wdata,
        (missRead && io.in.bits.conflictWCB) -> io.in.bits.conflictWCBData
    ))
    val tagArrayWrite = MuxCase(0.U.asTypeOf(new TagDataBundle), List(
        hitWrite -> Cat(addr.tag, true.B, true.B, true.B).asTypeOf(new TagDataBundle),
        missFullWrite -> Cat(addr.tag, true.B, Mux(io.in.bits.conflictWCB, false.B, true.B), true.B).asTypeOf(new TagDataBundle),
        missRead -> Cat(addr.tag, true.B, Mux(io.in.bits.conflictWCB, "b01".U, "b10".U)).asTypeOf(new TagDataBundle)  
    ))
    io.dataWriteBus.apply(valid = dataArrayWriteValid, wdata = dataArrayWrite, bankIdx = addr.bank, setIdx = addr.set, waymask = io.in.bits.waymask)
    io.tagWriteBus.apply(valid = tagArrayWriteValid, wdata = tagArrayWrite.asTypeOf(new TagDataBundle), bankIdx = addr.bank, setIdx = addr.set, waymask = io.in.bits.waymask)

    io.isFinish := hitReadIsFinish || hitWriteIsFinish || missFullWriteIsFinish || missReadIsFinish
    //io.in.ready := (!io.in.valid || io.isFinish) && io.dataWriteBus.req.ready && io.tagWriteBus.req.ready
    io.in.ready := !io.in.valid || io.isFinish
    
    val addr_tag = addr.tag  
    dontTouch(addr_tag)
    dontTouch(hitWrite)
    dontTouch(hitRead)
    dontTouch(missFullWrite)
    dontTouch(missRead)

    when(io.isFinish) {wcbReqFireReg := false.B}.elsewhen(io.wcbReq.fire) {wcbReqFireReg := true.B}
    when(io.isFinish) {clearConflictFireReg := false.B}.elsewhen(io.clearConflict.fire) {clearConflictFireReg := true.B}
    when(io.isFinish) {mshrReqFireReg := false.B}.elsewhen(io.mshrReq.fire) {mshrReqFireReg := true.B}
    when(io.isFinish) {arrayFireReg := false.B}.elsewhen(io.tagWriteBus.req.fire) {arrayFireReg := true.B}
    when(io.isFinish) {hitReadDataFireReg := false.B}.elsewhen(io.hitReadData.fire) {hitReadDataFireReg := true.B}
}

sealed class MainPipeIO extends CacheBundle {
    // Interface With Request Queue
    val RequestCmd = Flipped(Decoupled(new SplitCmdIO { val CmdType = Output(Bool())}))  // Output cmd add 1bit: --0 write, --1 read
    val RequestwData = Flipped(Decoupled(new WrDataIO))
    val rdData2AddrMap = Decoupled(new RdDataIO)

    // Interface With WCB
    val searchReq = Decoupled(new SplitCmdIO{ val CmdType = Output(Bool())})
    val conflictWCB = Input(Bool())
    val conflictWCBData = Input(UInt(CacheLineBits.W))
    val wcbReq = Decoupled(new WCBReqBundle)
    val clearConflict = Decoupled(new WrDataIO{val overWrite = Output(Bool())})

    // Interface With MSHR
    val mshrReq = Decoupled(new MSHRInfo)

    // Interface With CacheArray
    val tagReadBus = new CacheTagArrayReadBus
    val dataReadBus = new CacheDataArrayReadBus
    val dataWriteBus = new CacheDataArrayWriteBus
    val tagWriteBus = new CacheTagArrayWriteBus
    val empty = Output(Bool())
}

class MainPipe_by extends CacheModule {
    val io = FlatIO(new MainPipeIO)

    val s1 = Module(new CacheStage1)
    val s2 = Module(new CacheStage2)
    val s3 = Module(new CacheStage3)
    val flush_s1 = false.B 
    val flush_s2 = false.B 
    val flush_s3 = false.B  

    //s1.io.in <> io.RequestCmd
    PipelineConnectDefault(io.RequestCmd, s1.io.in, s1.io.out.fire, flush_s1)
    PipelineConnectDefault(s1.io.out, s2.io.in, s2.io.out.fire, flush_s2)
    PipelineConnectDefault(s2.io.out, s3.io.in, s3.io.isFinish, flush_s3)
    io.empty := !s2.io.in.valid && !s3.io.in.valid
    s1.io.blockedReq <> s2.io.blockedReq

    io.tagReadBus <> s1.io.tagReadBus
    io.tagWriteBus <> s3.io.tagWriteBus
    io.dataReadBus <> s1.io.dataReadBus
    io.dataWriteBus <> s3.io.dataWriteBus

    s2.io.tagWriteBus := s3.io.tagWriteBus
    s2.io.dataWriteBus := s3.io.dataWriteBus
    s2.io.tagReadResp := io.tagReadBus.resp
    s2.io.dataReadResp := io.dataReadBus.resp
    s2.io.inFire := RegNext(s1.io.out.fire, false.B)
    s2.io.readReqFire := s1.io.tagReadBus.req.fire

    io.RequestwData <> s2.io.RequestwData
    io.rdData2AddrMap <> s3.io.hitReadData

    io.wcbReq <> s3.io.wcbReq
    io.clearConflict <> s3.io.clearConflict
    io.mshrReq <> s3.io.mshrReq
    io.searchReq <> s2.io.searchReq
    s2.io.conflictWCB := io.conflictWCB
    s2.io.conflictWCBData := io.conflictWCBData
}
