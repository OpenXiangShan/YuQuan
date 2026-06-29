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
package APB

import chisel3._ 
import chisel3.util._

import OpenMc.BUNDLE_PARAM

// RegConst
trait HasConst {
    val offset: Int
    val regnum: Int
    def absOff(x: Int): Int = x + offset
}

class HasAXI2UIConst extends HasConst {
    val offset = 0x200 >> 2
    val regnum = 7
    // reg
    val axirdcnt = 0x0
    val axiwrcnt = 0x1
    val uirdcnt = 0x2
    val uiwrcnt = 0x3
    val uirbcnt = 0x4
    val debug = 0x5
    val token = 0x6
}

class HasFilterConst extends HasConst {
    val offset = 0x100 >> 2
    val regnum = 9
    // reg
    val ctrl = 0x0
    val adrbdh1 = 0x1
    val adrbdh0 = 0x2
    val adrbdl1 = 0x3
    val adrbdl0 = 0x4
    val debug = 0x5
    val rdcnt = 0x6
    val wrcnt = 0x7
    val wbcnt = 0x8
}

class HasSCGConst extends HasConst {
    val offset = 0x0
    val regnum = 25
    // reg
    val tmchk0 = 0x0
    val tmchk1 = 0x1
    val tmchk2 = 0x2
    val tmchk3 = 0xa
    val ref0 = 0x3
    val ref1 = 0xb
    val ref2 = 0x4
    val dfi0 = 0x5
    val dfi1 = 0x6
    val dfi2 = 0xc
    val dfi3 = 0x7
    val clspg = 0x8
    val debug = 0x9
    val mcctrl = 0xd
    val ddrstat = 0xf
    val initmparam0 = 0x10
    val initmparam1 = 0x11
    val initmparam2 = 0x12
    val mrsmode0 = 0x13
    val mrsmode1 = 0x14
    val mrsmode2 = 0x15
    val mrsmode3 = 0x16
    val geardownmode = 0x17
    val raparam = 0x18
    val mode = 0x19
}

class HasAddrMapRegConst extends HasConst {
    val offset = 0x300 >> 2
    val regnum = 1
    val addrmap = 0x0
}

class HasASConst extends HasConst {
    val offset = 0x400 >> 2
    val regnum = 2
    val aswr = 0x0
    val asrd = 0x1
}

class HasAPBCFGConst extends HasConst {
    val offset = 0xff4 >> 2
    val regnum = 1
}

trait HasRegConst {
    // axi2ui
    val axi2uiConst = new HasAXI2UIConst
    // filter
    val filterConst = new HasFilterConst
    // scg
    val scgConst = new HasSCGConst
    // apbcfg
    val apbcfgConst = new HasAPBCFGConst
    // addrmap
    val addrmapRegConst = new HasAddrMapRegConst
    // scheduler
    val asConst = new HasASConst
    // param
    val REGNUM = Seq(axi2uiConst, filterConst, scgConst, apbcfgConst, addrmapRegConst, asConst).map(p => p.regnum).reduce(_+_)
}

// RegStruct
class FTctrlStruct extends Bundle {
    val pad = UInt(29.W)
    val en = UInt(1.W)
    val mode = UInt(2.W)
}

class SCGtmchk0Struct extends Bundle {
    val tRRDS = UInt(6.W)
    val tRRDL = UInt(6.W)
    val tFAW = UInt(8.W)
    val tRCD = UInt(6.W)
    val tRP = UInt(6.W)
}

class SCGtmchk1Struct extends Bundle {
    val tCCDS = UInt(8.W)
    val tCCDL = UInt(8.W)
    val tWTRS = UInt(8.W)
    val tWTRL = UInt(8.W)
}

class SCGtmchk2Struct extends Bundle {
    val tRTW = UInt(8.W)
    val tWR = UInt(8.W)
    val tRTP = UInt(8.W)
    val tRAS = UInt(8.W)
}

class SCGtmchk3Struct extends Bundle {
    val AL = UInt(8.W)
    val RL = UInt(8.W)
    val WL = UInt(8.W)
    val BL = UInt(8.W)
}

class SCGref2Struct extends Bundle {
    val pad = UInt(12.W)
    val tRFC = UInt(12.W)
    val tZQCS = UInt(8.W)
}

class SCGdfi0Struct extends Bundle {
    val tphyWrlat = UInt(8.W)
    val tphyWrcslat = UInt(8.W)
    val tphyWrdata = UInt(8.W)
    val trddataEn = UInt(8.W)
}

class SCGdfi1Struct extends Bundle {
    val pad = UInt(16.W)
    val tphyRdcslat = UInt(8.W)
    val tphyRdlat = UInt(8.W)
}

class SCGdfi2Struct extends Bundle {
    val wrOdtDelay = UInt(8.W)
    val wrOdtHold = UInt(8.W)
    val rdOdtDelay = UInt(8.W)
    val rdOdtHold = UInt(8.W)
}

class SCGclspgStruct extends Bundle {
    val pad = UInt(21.W)
    val clspgTmInit = UInt(9.W)
    val prePolicy = UInt(2.W)
}

class SCGinitmparam0Struct extends Bundle {
    val dramRstn = UInt(21.W)
    val postCke = UInt(11.W)
}

class SCGinitmparam1Struct extends Bundle {
    val preCke = UInt(24.W)
    val mrs2other = UInt(8.W)
}

class SCGinitmparam2Struct extends Bundle {
    val pad = UInt(16.W)
    val mrs2mrs = UInt(4.W)
    val zqinit = UInt(12.W)
}

class SCGmrsmode0Struct extends Bundle {
    val mrs1 = UInt(16.W)
    val mrs0 = UInt(16.W)
}

class SCGmrsmode1Struct extends Bundle {
    val mrs3 = UInt(16.W)
    val mrs2 = UInt(16.W)
}

class SCGmrsmode2Struct extends Bundle {
    val mrs5 = UInt(16.W)
    val mrs4 = UInt(16.W)
}

class SCGgeardownmodeStruct extends Bundle {
    val cmdGear = UInt(6.W)
    val syncGear = UInt(8.W)
    val gearHold = UInt(8.W)
    val gearSetup = UInt(8.W)
    val blkTGeardown = Bool()
    val geardownMode = Bool()
}

class SCGraparamStruct extends Bundle {
    val w2wdr = UInt(8.W) // write to write delay
    val w2rdr = UInt(8.W) // write to read delay
    val r2rdr = UInt(8.W) // read to read delay
    val r2wdr = UInt(8.W) // read to write delay
}

class SCGctrlStruct extends Bundle {
    val pad = UInt(30.W)
    val scgMode = UInt(2.W)
}

// addrmap
class ADDRMAPStruct extends Bundle {
    val pad = UInt(30.W)
    val mapMode = UInt(2.W)
}

// scheduler
class ASwrStruct extends Bundle {
    val pad = UInt(16.W)
    val wrhigh = UInt(8.W)
    val wrlow = UInt(8.W)
}

class ASrdStruct extends Bundle {
    val pad = UInt(16.W)
    val rdhigh = UInt(8.W)
    val rdlow = UInt(8.W)
}



// RegIO
class AXI2UIRegIO extends Bundle {
    val axiRdCmdCnt = Input(UInt(32.W))
    val axiWrCmdCnt = Input(UInt(32.W))
    val uiRdCmdCnt = Input(UInt(32.W))
    val uiWrCmdCnt = Input(UInt(32.W))
    val uiRbCmdCnt = Input(UInt(32.W))
    val readyStall = Input(Bool())
    val tokenCnt = Input(UInt(8.W))
}

class FilterRegIO extends Bundle {
    val en = Output(Bool())
    val mode = Output(UInt(2.W))
    val adrbdh = Output(UInt(64.W))
    val adrbdl = Output(UInt(64.W))
    val cmdValid = Input(Bool())
    val rcacheEn = Input(Bool())
    val wcacheEn = Input(Bool())
    val rdCmdCnt = Input(UInt(32.W))
    val wrCmdCnt = Input(UInt(32.W))
    val rdBackCnt = Input(UInt(32.W))
}

class SCGRegIO extends Bundle {
    // tmchk0
    val tRRDS = Output(UInt(6.W))
    val tRRDL = Output(UInt(6.W))
    val tFAW = Output(UInt(8.W))
    val tRCD = Output(UInt(6.W))
    val tRP = Output(UInt(6.W))
    // tmchk1
    val tCCDS = Output(UInt(8.W))
    val tCCDL = Output(UInt(8.W))
    val tWTRS = Output(UInt(8.W))
    val tWTRL = Output(UInt(8.W))
    // tmchk2
    val tRTW = Output(UInt(8.W))
    val tWR = Output(UInt(8.W))
    val tRTP = Output(UInt(8.W))
    val tRAS = Output(UInt(8.W))
    //tmchk3
    val AL  = UInt(8.W)
    val WL  = UInt(8.W)
    val RL  = UInt(8.W)
    val BL  = UInt(8.W)   
    // ref0
    val tREFI = Output(UInt(16.W))
    val tZQINTVL = Output(UInt(32.W))
    // ref1
    val tRFC = Output(UInt(12.W))
    val tZQCS = Output(UInt(8.W))
    // dfi0
    val tphyWrlat = Output(UInt(8.W))
    val tphyWrcslat = Output(UInt(8.W))
    val tphyWrdata = Output(UInt(8.W))
    val trddataEn = Output(UInt(8.W))
    // dfi1
    val tphyRdcslat = Output(UInt(8.W))
    val tphyRdlat = Output(UInt(8.W))
    // dfi2
    val wrOdtDelay = Output(UInt(8.W))
    val wrOdtHold = Output(UInt(8.W))
    val rdOdtDelay = Output(UInt(8.W))
    val rdOdtHold = Output(UInt(8.W))
    // dfi3
    val dfiMode = Output(Bool())
    // clspg
    val clspgTmInit = Output(UInt(9.W))
    val prePolicy = Output(UInt(2.W))
    // debug
    // val RGState = Input(Vec((1<<(BUNDLE_PARAM.BGBITS+BUNDLE_PARAM.RANK_WIDTH)), UInt(3.W)))
    val refState = Input(UInt(3.W))
    // mcctrl
    val gen = Output(Bool())
    // ddrstat
    val ddrInitEnd = Input(Bool())
    // initmparam0
    val dramRstn = Output(UInt(21.W))
    val postCke = Output(UInt(11.W))
    // initmparam1
    val preCke = Output(UInt(24.W))
    val mrs2other = Output(UInt(8.W))
    // initmparam2
    val mrs2mrs = Output(UInt(4.W))
    val zqinit = Output(UInt(12.W))
    // mrsmode0
    val mrs1 = Output(UInt(16.W))
    val mrs0 = Output(UInt(16.W))
    // mrsmode1
    val mrs3 = Output(UInt(16.W))
    val mrs2 = Output(UInt(16.W))
    // mrsmode2
    val mrs5 = Output(UInt(16.W))
    val mrs4 = Output(UInt(16.W))
    // mrsmode3
    val mrs6 = Output(UInt(16.W))
    // geardownmode
    val cmdGear = Output(UInt(6.W))
    val syncGear = Output(UInt(8.W))
    val gearHold = Output(UInt(8.W))
    val gearSetup = Output(UInt(8.W))
    val blkTGeardown = Output(Bool())
    val geardownMode = Output(Bool())
    // raparam
    val w2wdr = Output(UInt(8.W)) // write to write delay
    val w2rdr = Output(UInt(8.W)) // write to read delay
    val r2rdr = Output(UInt(8.W)) // read to read delay
    val r2wdr = Output(UInt(8.W)) // read to write delay

    val scgMode = Output(UInt(2.W))
}

class AddrMapRegIO extends Bundle {
    val MEM_ADDR_MAP = Output(UInt(2.W))
}

class ASRegIO extends Bundle {
    // aswr
    val wrhigh = Output(UInt(8.W))
    val wrlow = Output(UInt(8.W))
    // asrd
    val rdhigh = Output(UInt(8.W))
    val rdlow = Output(UInt(8.W))
}


class RegIO extends Bundle {
    val a2uio = new AXI2UIRegIO
    val filterio = new FilterRegIO
    val scgio = new SCGRegIO
    val amapRegio = new AddrMapRegIO
    val asRegio = new ASRegIO
}
