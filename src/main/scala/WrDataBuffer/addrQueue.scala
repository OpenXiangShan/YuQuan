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


class addrQueue(entnum : Int)extends Module{
    val addrWidth = log2Ceil(entnum) //向上取整
    val io = IO(new Bundle {
        val enq   = Flipped(DecoupledIO(UInt(addrWidth.W)))
        val deq   = DecoupledIO(UInt(addrWidth.W))
        val calDone = Flipped(Bool())
    })
    val mem    = Reg(Vec(entnum,UInt(addrWidth.W)))
    val w_addr = RegInit(0.U(addrWidth.W))
    val r_addr = RegInit(0.U(addrWidth.W))
    val entryCount = RegInit(entnum.U((addrWidth+1).W))
    //fill queue
    when(!io.calDone){    
        for(i <- 0 until entnum){
            mem(i) := i.U
        }
    }.otherwise{
        mem(w_addr) := Mux(io.enq.fire,io.enq.bits,mem(w_addr))
    }
    switch(Cat(io.enq.fire,io.deq.fire)){
        is("b10".U){
            entryCount := entryCount + 1.U 
        }
        is("b01".U){
            entryCount := entryCount - 1.U
        }
        is("b00".U,"b11".U){
            entryCount := entryCount
        }
    }
    w_addr := Mux(w_addr === (entnum - 1 ).U & io.enq.fire , 0.U ,w_addr + io.enq.fire)
    r_addr := Mux(r_addr === (entnum - 1 ).U & io.deq.fire , 0.U ,r_addr + io.deq.fire)
    io.deq.bits  := mem(r_addr)
    io.enq.ready := entryCount <= entnum.U
    io.deq.valid := entryCount =/= 0.U


}