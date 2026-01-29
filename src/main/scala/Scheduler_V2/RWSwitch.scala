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

class RWSwitch(org:StationOrg.Value) extends Module {
    val bgNum = 1<<(BUNDLE_PARAM.BG_WIDTH+BUNDLE_PARAM.RANK_WIDTH)
    val baNum = bgNum<<BUNDLE_PARAM.BANK_WIDTH
    val stationNum = org match {
        case StationOrg.Unified => 1
        case StationOrg.PerBg => bgNum
        case StationOrg.PerBa => baNum
    }
    val io = IO(new Bundle {
        val grantWrite = Output(Bool())
        val grantRead  = Output(Bool())

        val readUrgent = Input(Vec(stationNum, Bool()))
        val readHigh = Input(Vec(stationNum, Bool()))
        val readLow = Input(Vec(stationNum, Bool()))
        val writeHigh = Input(Vec(stationNum, Bool()))
        val writeLow = Input(Vec(stationNum, Bool()))
        val writeUrgent = Input(Vec(stationNum, Bool()))
    })
    val rUrgent = io.readUrgent.reduce(_||_)
    val wUrgent = io.writeUrgent.reduce(_||_)
    val rHigh = io.readHigh.reduce(_||_)
    val rLow = io.readLow.reduce(_&&_)
    val wHigh = io.writeHigh.reduce(_||_)
    val wLow = io.writeLow.reduce(_&&_)

    val rHold = RegInit(0.U(8.W))
    val wHold = RegInit(0.U(8.W))

    val rGrant = Wire(Bool())



    rGrant := MuxCase(
        default = false.B,
        Seq(
            rUrgent -> true.B,
            wUrgent -> false.B,
            (io.grantRead && rHold > 27.U && wHigh) -> false.B,
            io.grantRead -> true.B,
            (wHold > 81.U) -> true.B,
            (wHold > 16.U) -> wLow // (wLow || rHigh)
        )
    )

    // update hold values
    when(io.grantRead) {
        rHold := Mux(rHold === 127.U, 127.U, rHold + 1.U)  
        wHold := 0.U
    }.otherwise {
        rHold := 0.U
        wHold := Mux(wHold === 127.U, 127.U, wHold + 1.U)
    }


    // Reg out
    io.grantRead := RegNext(rGrant, true.B)
    io.grantWrite := RegNext(!rGrant, false.B)
}