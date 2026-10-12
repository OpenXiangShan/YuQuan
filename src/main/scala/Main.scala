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
import chisel3._
import _root_.circt.stage.ChiselStage
import OpenMc._
import OpenMc.mc_top

object test extends App {
  ChiselStage.emitSystemVerilogFile(
    new mc_top,
    args = Array(
      "--target-dir", "build/",
      "--split-verilog"
    ),
    firtoolOpts = Array(
      "-strip-debug-info",
      "--lowering-options=disallowLocalVariables",
      "-disable-all-randomization"
    )
  )
}