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

sealed abstract class AddrMapBundle extends Bundle with OSMCParameter 
sealed abstract class AddrMapModule extends Module with OSMCParameter


class BufferWithArb(nRdData: Int = 2, qDepth: Int = 64) extends AddrMapModule {
    val io = IO(new Bundle {
        val inAS = Flipped(Decoupled(new RdDataIO))
        val inCache = Flipped(Decoupled(new RdDataIO))
        val out = Decoupled(new RdDataIO)
    })
    // queue(0) --Cache, queue(1) --As
    val queues = Seq.fill(nRdData)(Module(new Queue(chiselTypeOf(io.out.bits), qDepth)))
    queues(0).io.enq <> io.inCache
    queues(1).io.enq <> io.inAS

    val  rDataArbiter = Module(new Arbiter(chiselTypeOf(io.out.bits), nRdData))
    rDataArbiter.io.in <> queues.map(_.io.deq)
    
    io.out <> rDataArbiter.io.out
    io.out.bits := Mux(rDataArbiter.io.out.fire, rDataArbiter.io.out.bits, 0.U.asTypeOf(new RdDataIO))
}

class AddrMapIO extends AddrMapBundle {
    // read channel with Filter
    val RdCmdFromFilter = Flipped(Decoupled(new CMDIO))
    val RdData2Filter = Decoupled(new RdDataIO)
    val RdIsToAS = Input(Bool())
    // write channel with Filter
    val WrReqFromFilter = Flipped(Decoupled(new WriteReqIO))
    val WrIsToAS = Input(Bool())

    // read channel with Cache
    val RdCmd2Cache = Decoupled(new SplitCmdIO)
    val RdDataFromCache = Flipped(Decoupled(new RdDataIO))
    // write channel with Cache
    val WrCmd2Cache = Decoupled(new SplitCmdIO)
    val WrData2Cache = Decoupled(new WrDataIO)

    // read channel with AS
    val RdCmd2AS = Decoupled(new SplitCmdIO)
    val RdDataFromAS = Flipped(Decoupled(new RdDataIO))
    // write channel with AS
    val WrCmd2AS = Decoupled(new SplitCmdIO)
    val WrData2AS = Decoupled(new WrDataIO)

    // register interface
    val ADDRMAP = Input(UInt(2.W))
}

// -------------***********************----------------
// -------------***********************----------------
class AddrMap extends AddrMapModule {
    val io = FlatIO(new AddrMapIO)

    val RANK_LSB = COL_WIDTH + ROW_WIDTH + BANK_WIDTH + BG_WIDTH
    val RANK_MSB = RANK_LSB + RANK_WIDTH - 1
    val ROW_LSB_0 = COL_WIDTH + BANK_WIDTH + BG_WIDTH
    val ROW_MSB_0 = ROW_LSB_0 + ROW_WIDTH - 1
    val ROW_LSB_1 = COL_WIDTH
    val ROW_MSB_1 = ROW_LSB_1 + ROW_WIDTH - 1
    val ROW_LSB_2 = ROW_LSB_0
    val ROW_MSB_2 = ROW_MSB_0
    val COL_LSB_0 = BANK_WIDTH + BG_WIDTH + 3
    val COL_MSB_0 = BANK_WIDTH + BG_WIDTH + COL_WIDTH - 1

    val MEM_ADDR_MAP = RegNext(io.ADDRMAP)


    val rdCmdSplit = Wire(chiselTypeOf(io.RdCmd2AS.bits))
    val wrCmdSplit = Wire(chiselTypeOf(io.RdCmd2AS.bits))
    wrCmdSplit := DontCare
    rdCmdSplit := DontCare

    val rdIsToAS = io.RdIsToAS
    val wrIsToAS = io.WrIsToAS
    val rdCmdAddr = io.RdCmdFromFilter.bits.addr >> 3
    val wrCmdAddr = io.WrReqFromFilter.bits.cmd.addr >> 3
    // Extract rank number
    val rdRank = if(RANKS == 1) 0.U else rdCmdAddr(RANK_MSB, RANK_LSB)
    val wrRank = if(RANKS == 1) 0.U else wrCmdAddr(RANK_MSB, RANK_LSB)
    rdCmdSplit.rank := rdRank
    wrCmdSplit.rank := wrRank
        when(MEM_ADDR_MAP === 0.U) {
            rdCmdSplit.row := rdCmdAddr(ROW_MSB_0, ROW_LSB_0)
            rdCmdSplit.col := Cat(rdCmdAddr(COL_MSB_0, COL_LSB_0), rdCmdAddr(2, 0))
            rdCmdSplit.bg := rdCmdAddr(3+BG_WIDTH-1, 3)
            rdCmdSplit.bank := rdCmdAddr(3+BG_WIDTH+BANK_WIDTH-1, 3+BG_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 1.U) {
            rdCmdSplit.row := rdCmdAddr(ROW_MSB_1, ROW_LSB_1)
            rdCmdSplit.col := rdCmdAddr(COL_WIDTH-1, 0)
            rdCmdSplit.bg := rdCmdAddr(COL_WIDTH+ROW_WIDTH+BANK_WIDTH+BG_WIDTH-1, COL_WIDTH+ROW_WIDTH+BANK_WIDTH)
            rdCmdSplit.bank := rdCmdAddr(COL_WIDTH+ROW_WIDTH+BANK_WIDTH-1, COL_WIDTH+ROW_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 2.U) {
            rdCmdSplit.row := rdCmdAddr(ROW_MSB_2, ROW_LSB_2)
            rdCmdSplit.col := rdCmdAddr(COL_WIDTH-1, 0)
            rdCmdSplit.bg := rdCmdAddr(COL_WIDTH+BANK_WIDTH+BG_WIDTH-1, COL_WIDTH+BANK_WIDTH)
            rdCmdSplit.bank := rdCmdAddr(COL_WIDTH+BANK_WIDTH-1, COL_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 3.U){
            rdCmdSplit.row  := rdCmdAddr(BANK_WIDTH+BG_WIDTH+COL_WIDTH+ROW_WIDTH-1,BANK_WIDTH+BG_WIDTH+COL_WIDTH)
            rdCmdSplit.col  := Cat(rdCmdAddr(BG_WIDTH+COL_WIDTH-1,3+BG_WIDTH),rdCmdAddr(2,0))
            rdCmdSplit.bg   := rdCmdAddr(3+BG_WIDTH-1,3)
            rdCmdSplit.bank := rdCmdAddr(BG_WIDTH+COL_WIDTH+BANK_WIDTH-1,BG_WIDTH+COL_WIDTH)
        }
        when(MEM_ADDR_MAP === 0.U) {
            wrCmdSplit.row := wrCmdAddr(ROW_MSB_0, ROW_LSB_0)
            wrCmdSplit.col := Cat(wrCmdAddr(COL_MSB_0, COL_LSB_0), wrCmdAddr(2, 0))
            wrCmdSplit.bg := wrCmdAddr(3+BG_WIDTH-1, 3)
            wrCmdSplit.bank := wrCmdAddr(3+BG_WIDTH+BANK_WIDTH-1, 3+BG_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 1.U) {
            wrCmdSplit.row := wrCmdAddr(ROW_MSB_1, ROW_LSB_1)
            wrCmdSplit.col := wrCmdAddr(COL_WIDTH-1, 0)
            wrCmdSplit.bg := wrCmdAddr(COL_WIDTH+ROW_WIDTH+BANK_WIDTH+BG_WIDTH-1, COL_WIDTH+ROW_WIDTH+BANK_WIDTH)
            wrCmdSplit.bank := wrCmdAddr(COL_WIDTH+ROW_WIDTH+BANK_WIDTH-1, COL_WIDTH+ROW_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 2.U) {
            wrCmdSplit.row := wrCmdAddr(ROW_MSB_2, ROW_LSB_2)
            wrCmdSplit.col := wrCmdAddr(COL_WIDTH-1, 0)
            wrCmdSplit.bg  := wrCmdAddr(COL_WIDTH+BANK_WIDTH+BG_WIDTH-1, COL_WIDTH+BANK_WIDTH)
            wrCmdSplit.bank := wrCmdAddr(COL_WIDTH+BANK_WIDTH-1, COL_WIDTH)
        }.elsewhen(MEM_ADDR_MAP === 3.U){
            wrCmdSplit.row  := wrCmdAddr(BANK_WIDTH+BG_WIDTH+COL_WIDTH+ROW_WIDTH-1,BANK_WIDTH+BG_WIDTH+COL_WIDTH)
            wrCmdSplit.col  := Cat(wrCmdAddr(BG_WIDTH+COL_WIDTH-1,3+BG_WIDTH),wrCmdAddr(2,0))
            wrCmdSplit.bg   := wrCmdAddr(3+BG_WIDTH-1,3)
            wrCmdSplit.bank := wrCmdAddr(BG_WIDTH+COL_WIDTH+BANK_WIDTH-1,BG_WIDTH+COL_WIDTH)
        }

    val rdDataBuffWithArb = Module(new BufferWithArb)
    rdDataBuffWithArb.io.inAS <> io.RdDataFromAS
    rdDataBuffWithArb.io.inCache <> io.RdDataFromCache
    io.RdData2Filter <> rdDataBuffWithArb.io.out
        rdCmdSplit.pri := io.RdCmdFromFilter.bits.pri
        rdCmdSplit.token := io.RdCmdFromFilter.bits.token
        wrCmdSplit.pri := io.WrReqFromFilter.bits.cmd.pri
        wrCmdSplit.token := io.WrReqFromFilter.bits.cmd.token
    io.RdCmdFromFilter.ready := Mux(io.RdIsToAS,io.RdCmd2AS.ready,io.RdCmd2Cache.ready)
    io.WrReqFromFilter.ready := Mux(io.WrIsToAS ,io.WrData2AS.ready,io.WrData2Cache.ready)
    io.RdCmd2AS.valid := rdIsToAS & io.RdCmdFromFilter.valid
    io.RdCmd2AS.bits :=   rdCmdSplit.asTypeOf(chiselTypeOf(io.RdCmd2AS.bits))
    io.RdCmd2Cache.valid := (~rdIsToAS) & io.RdCmdFromFilter.valid
    io.RdCmd2Cache.bits :=rdCmdSplit.asTypeOf(chiselTypeOf(io.RdCmd2Cache.bits))
    io.WrCmd2AS.valid := wrIsToAS & io.WrReqFromFilter.valid 
    io.WrCmd2AS.bits :=wrCmdSplit.asTypeOf(chiselTypeOf(io.WrCmd2AS.bits))
    io.WrCmd2Cache.valid := !wrIsToAS & io.WrReqFromFilter.valid       
    io.WrCmd2Cache.bits := wrCmdSplit.asTypeOf(chiselTypeOf(io.WrCmd2Cache.bits))                  
    io.WrData2AS.valid := wrIsToAS & io.WrReqFromFilter.valid 
    io.WrData2AS.bits := io.WrReqFromFilter.bits.data
    io.WrData2Cache.valid := !wrIsToAS & io.WrReqFromFilter.valid 
    io.WrData2Cache.bits := io.WrReqFromFilter.bits.data
    
}