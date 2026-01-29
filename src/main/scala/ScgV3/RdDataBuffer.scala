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

class RdDataBuffer extends Module{
    val io = IO(new Bundle{
        val tokenEn   = Input(Bool())
        val tokenIn   = Input(UInt(BUNDLE_PARAM.TOKENBITS.W))//in token fifo

        val dataIn    = Input(UInt(BUNDLE_PARAM.DATA_WIDTH.W)) //in data fifo
        val dataEn    = Input(Bool())

        val dataOut   = DecoupledIO(new Bundle {
            val data  = Output(UInt(BUNDLE_PARAM.DATA_WIDTH.W))
            val token = Output(UInt(BUNDLE_PARAM.TOKENBITS.W))
        })
    })
    //token sync_fifo
    val tokenFifo  = Module{
        new Queue(UInt(BUNDLE_PARAM.TOKENBITS.W), 16)
    }
    //read data async_fifo
    val dataFifo = Module{
        new Queue(UInt(BUNDLE_PARAM.DATA_WIDTH.W), 16)
    }
    val ren = dataFifo.io.deq.valid & tokenFifo.io.deq.valid


    tokenFifo.io.enq.bits  := io.tokenIn 
    tokenFifo.io.enq.valid := io.tokenEn
    tokenFifo.io.deq.ready := ren & io.dataOut.ready
    assert(tokenFifo.io.enq.ready, "tokenFifo.enq.ready is not ready")
    
    dataFifo.io.enq.bits  := io.dataIn
    dataFifo.io.enq.valid := io.dataEn
    dataFifo.io.deq.ready := ren & io.dataOut.ready
    assert(dataFifo.io.enq.ready, "dataFifo.enq.ready is not ready")

    io.dataOut.valid      := ren
    io.dataOut.bits.data  := dataFifo.io.deq.bits
    io.dataOut.bits.token := tokenFifo.io.deq.bits
}