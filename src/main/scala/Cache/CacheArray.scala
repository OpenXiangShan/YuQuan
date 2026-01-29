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

class DataReadReq extends CacheBundle {
    val bank = Bits(BankBits.W)
    val set = Bits(BankSetBits.W) 
}

class DataWriteReq_by extends DataReadReq { 
    val waymask = Bits(Ways.W)
    val wdata = Bits(CacheLineBits.W)
}

class DataReadResult extends CacheBundle {
    val dataWays = Vec(Ways, Bits(CacheLineBits.W))  // read data of multiways
}

class DataBankWriteReq extends CacheBundle {
    val en = Bool()
    val set = UInt(BankSetBits.W)
    val waymask = UInt(Ways.W)
    val data = UInt(CacheLineBits.W)
}

class TagDataBundle extends CacheBundle {
    val tag = UInt(TagBits.W)
    val valid = Bool()
    val dirty = Bool()
    val ready = Bool()
}

class TagReadReq extends CacheBundle {
    val bank = Bits(BankBits.W)
    val set = Bits(BankSetBits.W)
}

class TagReadResult extends CacheBundle {
    val TagWays = Vec(Ways, new TagDataBundle)
}

class TagWriteReq_by extends TagReadReq {
    val waymask  = UInt(Ways.W)
    val wdata = new TagDataBundle
}

class BankTagWriteReq extends CacheBundle {
    val en = Bool()
    val set = UInt(BankSetBits.W)
    val waymask = UInt(Ways.W)
    val wdata = new TagDataBundle
}


class BankedDataArray_by extends CacheModule {
    val io = FlatIO(new CacheBundle {
        val read = Flipped(Decoupled(new DataReadReq))
        val write = Flipped(Decoupled(new DataWriteReq_by))
        
        val resp = Output(new DataReadResult)
    })

    // wrap data rows of 4 ways
    class DataSRAMBank_by(BankIndex: Int) extends Module {
        val io = IO(new Bundle{
            val w = Input(new DataBankWriteReq)

            val r = new Bundle(){
                val en = Input(Bool())
                val set = Input(UInt(BankSetBits.W))
                val dataWays = Output(Vec(Ways, UInt(CacheLineBits.W)))  // multiways read data
                val ready = Output(Bool())
            }
        })

        // multiways data bank
        val data_bank = Module(new SRAMTemplate_by(
            Bits(CacheLineBits.W),
            set = BankSets,
            way = Ways,
            shouldReset = false,
            holdRead = false,
            singlePort = true
        ))

        data_bank.io.w.req.valid := io.w.en
        data_bank.io.w.req.bits.apply(
            setIdx = io.w.set,
            data = io.w.data,
            waymask = io.w.waymask
        )
        data_bank.io.r.req.valid := io.r.en
        data_bank.io.r.req.bits.apply(setIdx = io.r.set)

        io.r.dataWays := data_bank.io.r.resp.data
        io.r.ready := data_bank.io.r.req.ready
        
    }

    val data_banks = List.tabulate(Banks)(i => Module(new DataSRAMBank_by(i)))//List.tabulate(n)(f) 是 Scala 中用于创建列表的函数

    // read request
    val rset = io.read.bits.set
    val rbank = io.read.bits.bank
    //val readReq_ready = data_banks.map(_.io.r.ready)
   
    val banks_result = Wire(Vec(Banks, new DataReadResult()))
    dontTouch(banks_result)

    for(bank_index <- 0 until Banks) {
        val read_enable_bank = WireInit((bank_index.U === rbank) && io.read.valid)

        // read data
        val data_bank = data_banks(bank_index)
        data_bank.io.r.en := read_enable_bank
        data_bank.io.r.set := rset
        banks_result(bank_index).dataWays := data_bank.io.r.dataWays
    }

    val rbank_reg = RegNext(rbank)
    val readFireReg = RegNext(io.read.fire)
    val arrayResp = MuxCase(0.U.asTypeOf(new DataReadResult), banks_result.zipWithIndex.map {
                case(resp, i) => (rbank_reg === i.U) -> resp })
    io.resp := Mux(readFireReg, arrayResp, 0.U.asTypeOf(new DataReadResult))
    //io.read.ready := readReq_ready.reduce(_ && _)
    val readBankReady = MuxCase(true.B, data_banks.zipWithIndex.map {
        case(data_bank, i) => (rbank_reg === i.U) -> data_bank.io.r.ready })


    // write data bank
    val wset = io.write.bits.set
    val wbank = io.write.bits.bank
    val waymask = io.write.bits.waymask
    val wdata = io.write.bits.wdata
    for(bank_index <- 0 until Banks) {
        val write_enable_bank = WireInit((bank_index.U === wbank) && io.write.valid)

        val data_bank = data_banks(bank_index)
        data_bank.io.w.en := write_enable_bank
        data_bank.io.w.waymask := waymask
        data_bank.io.w.set := wset
        data_bank.io.w.data := wdata
    }

    // for single port SRAM, do not allow read and write the same bank in the same cycle
    // write block read
    io.write.ready := true.B
    io.read.ready := Mux(io.write.valid, (wbank =/= rbank), readBankReady)
}

class BankedTagArray_by extends CacheModule {
    val io = FlatIO(new Bundle() {
        val read = Flipped(DecoupledIO(new TagReadReq))
        val write = Flipped(DecoupledIO(new TagWriteReq_by))
        val resp = Output(Vec(Ways, new TagDataBundle))
    })

    class TagSRAMBank(BankIndex: Int) extends Module {
        val io = IO(new Bundle{
            val w = Input(new BankTagWriteReq)
            val r = new Bundle() {
                val en = Input(Bool())
                val set = Input(UInt(BankSetBits.W))
                val tagdata = Output(Vec(Ways, new TagDataBundle))
                val ready = Output(Bool())
            }
        })

        // multiways tag bank
        val tag_bank  = Module(new SRAMTemplate_by(
            new TagDataBundle,
            set = BankSets,
            way = Ways,
            shouldReset = true,
            holdRead = false,
            singlePort = true
        ))

        tag_bank.io.w.req.valid := io.w.en
        tag_bank.io.w.req.bits.apply(
            setIdx = io.w.set,
            data = io.w.wdata,
            waymask = io.w.waymask
        )
        tag_bank.io.r.req.valid := io.r.en
        tag_bank.io.r.req.bits.apply(setIdx = io.r.set)

        io.r.tagdata := tag_bank.io.r.resp.data
        io.r.ready := tag_bank.io.r.req.ready
    }

    val tag_banks = List.tabulate(Banks)(i => Module(new TagSRAMBank(i)))

    // read request
    val rset = io.read.bits.set
    val rbank = io.read.bits.bank
    val banks_result = Wire(Vec(Banks, Vec(Ways, new TagDataBundle)))
    dontTouch(banks_result)

    for(bank_index <- 0 until Banks) {
        val read_enable_bank = WireInit((bank_index.U === rbank) && io.read.valid)

        // read data
        val tag_bank = tag_banks(bank_index)
        tag_bank.io.r.en := read_enable_bank
        tag_bank.io.r.set := rset
        banks_result(bank_index) := tag_bank.io.r.tagdata
    }

    val rbank_reg = RegNext(rbank)
    val readFireReg = RegNext(io.read.fire)
    val arrayResp = MuxCase(0.U.asTypeOf(io.resp), banks_result.zipWithIndex.map {
                case(resp, i) => (rbank_reg === i.U) -> resp })
    io.resp := Mux(readFireReg, arrayResp, 0.U.asTypeOf(Vec(Ways, new TagDataBundle)))

    val readBankReady = MuxCase(true.B, tag_banks.zipWithIndex.map {
        case(tag_bank, i) => (rbank_reg === i.U) -> tag_bank.io.r.ready })

    // write request
    val wbank = io.write.bits.bank
    val wset = io.write.bits.set
    val wdata = io.write.bits.wdata
    val waymask = io.write.bits.waymask
    for(bank_index <- 0 until Banks) {
        val write_enable_bank = WireInit((bank_index.U === wbank) && io.write.valid)

        val tag_bank = tag_banks(bank_index)
        tag_bank.io.w.en := write_enable_bank
        tag_bank.io.w.waymask := waymask
        tag_bank.io.w.set := wset
        tag_bank.io.w.wdata := wdata
    }

    // for single port SRAM, do not allow read and write the same bank in the same cycle
    // write block read
    io.write.ready := true.B
    io.read.ready := Mux(io.write.valid, wbank =/= rbank, readBankReady)
}