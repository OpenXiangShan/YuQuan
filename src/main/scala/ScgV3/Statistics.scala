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

class Statistics(allBankNum: Int) extends Module {
    val io = IO(new Bundle {
        val bankStatis = Vec(allBankNum, Flipped(new CmdGenStatis))
    })

    val totalActCnt = RegInit(0.U(32.W))
    val totalPreCnt = RegInit(0.U(32.W))
    val totalCasCnt = RegInit(0.U(32.W))
    val totalClspgZeroCnt = RegInit(0.U(32.W))

    totalActCnt := io.bankStatis.map(_.actCnt).reduce(_ + _)
    totalPreCnt := io.bankStatis.map(_.preCnt).reduce(_ + _)
    totalCasCnt := io.bankStatis.map(_.casCnt).reduce(_ + _)
    totalClspgZeroCnt := io.bankStatis.map(_.clspgZeroCnt).reduce(_ + _)

    dontTouch(totalActCnt)
    dontTouch(totalPreCnt)
    dontTouch(totalCasCnt)
    dontTouch(totalClspgZeroCnt)
}
