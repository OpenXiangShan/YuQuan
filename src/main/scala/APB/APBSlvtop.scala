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
import utils._
import bus.apb3._
import bus.apb3.APBParameters.{regBits => regBits}

class APBSlvtop extends RawModule with HasRegConst {
    val io = IO(new Bundle {
        val apb = new APB3
        val cclk = Input(Clock())
        val crst = Input(AsyncReset())
        val regio = new RegIO
        val sen = Input(Bool())
        val qen = Input(Bool())
        val apbDone = Output(Bool())
    })

    
    val prst = (!io.apb.presetn.asBool).asAsyncReset
    // apb clock domain
    withClockAndReset(io.apb.pclk, prst) {
        // forward reference to value used in FSM
        val wen = Wire(Bool())
        val ack = Wire(Bool())
        val isDynamicReg = Wire(Bool())
        val isRONeedSync = Wire(Bool())

        // Finite State Machine(FSM)
        val s_pwrup :: s_init :: s_delay :: s_idle :: s_access :: s_waitack :: s_ready :: Nil = Enum(7)
        val state = RegInit(s_pwrup)

        val cnt = Counter(0x7f)
        val dly = ShiftRegister(true.B, 3, false.B, state === s_delay)

        switch (state) {
            is (s_pwrup) { state := s_init }
            is (s_init) {
                when (ack) { state := s_delay }
                .elsewhen (cnt.inc()) { state := s_idle }
            }
            is (s_delay) { when (dly) { state := s_idle } }
            is (s_idle) {
                cnt.reset()
                when (io.apb.psel & io.apb.penable) { state := s_access }
            }
            is (s_access) {
                when (isRONeedSync || isDynamicReg && wen) { state := s_waitack }
                .otherwise { state := s_ready }
            }
            is (s_waitack) { when (ack || cnt.inc()) { state := s_ready } }
            is (s_ready) { state := s_idle }
        }

        // reg interface(slvif)
        // axi2ui
        val a2uaxirdcnt = Wire(UInt(regBits.W))
        val a2uaxiwrcnt = Wire(UInt(regBits.W))
        val a2uuirdcnt = Wire(UInt(regBits.W))
        val a2uuiwrcnt = Wire(UInt(regBits.W))
        val a2uuirbcnt = Wire(UInt(regBits.W))
        val a2udebug = Wire(UInt(regBits.W))
        val a2utoken = Wire(UInt(regBits.W))

        val a2uaxirdcntcd = Wire(UInt(regBits.W))
        val a2uaxiwrcntcd = Wire(UInt(regBits.W))
        val a2uuirdcntcd = Wire(UInt(regBits.W))
        val a2uuiwrcntcd = Wire(UInt(regBits.W))
        val a2uuirbcntcd = Wire(UInt(regBits.W))
        val a2udebugcd = Wire(UInt(regBits.W))
        val a2utokencd = Wire(UInt(regBits.W))
        // filter
        val ftctrl = RegInit(UInt(regBits.W), "h4".U)
        val ftadrbdh1 = RegInit(UInt(regBits.W), "h0".U)
        val ftadrbdh0 = RegInit(UInt(regBits.W), "hffff_ffff".U)
        val ftadrbdl1 = RegInit(UInt(regBits.W), "h0".U)
        val ftadrbdl0 = RegInit(UInt(regBits.W), "h0001_0000".U)
        val ftdebug = Wire(UInt(regBits.W))
        val ftrdcnt = Wire(UInt(regBits.W))
        val ftwrcnt = Wire(UInt(regBits.W))
        val ftrbcnt = Wire(UInt(regBits.W))

        val ftdebugcd = Wire(UInt(regBits.W))
        val ftrdcntcd = Wire(UInt(regBits.W))
        val ftwrcntcd = Wire(UInt(regBits.W))
        val ftrbcntcd = Wire(UInt(regBits.W))

        // addrmap
        val addrmap = RegInit(UInt(32.W), "h0".U)

        //scheduler
        val schedulerwr = RegInit(UInt(regBits.W), "h0000_0702".U)
        val schedulerrd = RegInit(UInt(regBits.W), "h0000_8040".U)
  
        // scg
        val scgtmchk0 = RegInit(UInt(regBits.W), "h1061a410".U)//tRRD_S/tRRD_L/tFAW/tRCD/tRP
        val scgtmchk1 = RegInit(UInt(regBits.W), "h0408040a".U)//tCCD_S/tCCD_L/tWTR_S/tWTR_L
        val scgtmchk2 = RegInit(UInt(regBits.W), "h0c140a2a".U)//tRTW/tWR/tRTP/tRAS
        val scgtmchk3 = RegInit(UInt(regBits.W), "h00000c08".U)//al/rl/wl/bl 
        val scgref0 = RegInit(UInt(regBits.W), "h80".U)//tzqintvl 128
        val scgref1 = RegInit(UInt(regBits.W), "h1b58".U)//tREFI
        val scgref2 = RegInit(UInt(regBits.W), "h19080".U)//tRFC/tZQCS
        val scgdfi0 = RegInit(UInt(regBits.W), "h900000c".U)   
        val scgdfi1 = RegInit(UInt(regBits.W), "h202".U)
        val scgdfi2 = RegInit(UInt(regBits.W), "h15041504".U)
        val scgdfi3 = RegInit(UInt(regBits.W), "h1".U)  //dfi mode
        val scgclspg = RegInit(UInt(regBits.W), "h190".U)
        val scgdebug = Wire(UInt(regBits.W))
        val scgmcctrl = RegInit(UInt(regBits.W), "h0".U)
        val scgddrstat = Wire(UInt(regBits.W))
        val scginitmparam0 = RegInit(UInt(regBits.W), "h1d4f0145".U)
        val scginitmparam1 = RegInit(UInt(regBits.W), "h928b018".U)
        val scginitmparam2 = RegInit(UInt(regBits.W), "h8400".U)
        val scgmrsmode0 = RegInit(UInt(regBits.W), "h10a30".U)
        val scgmrsmode1 = RegInit(UInt(regBits.W), "h180000".U)
        val scgmrsmode2 = RegInit(UInt(regBits.W), "h40".U)
        val scgmrsmode3 = RegInit(UInt(regBits.W), "h800".U)
        val scggeardownmode = RegInit(UInt(regBits.W), "h40404040".U)
        val scgraparam = RegInit(UInt(regBits.W), "h0a0a0e04".U)

        val scgdebugcd = Wire(UInt(regBits.W))
        val scgmcctrlcd = Wire(UInt(regBits.W))
        val scgddrstatcd = Wire(UInt(regBits.W))

        val scgctrl = RegInit(UInt(regBits.W), "h0".U)

        // apbcfg done
        val apbcfg = RegInit(UInt(regBits.W), "h0".U)
        val apbcfgcd = Wire(UInt(regBits.W))

        // apb reg map
        val mappingReg = Map(
            // axi2ui
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.axirdcnt), a2uaxirdcnt, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.axiwrcnt), a2uaxiwrcnt, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.uirdcnt ), a2uuirdcnt , wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.uiwrcnt ), a2uuiwrcnt , wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.uirbcnt ), a2uuirbcnt , wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.debug   ), a2udebug   , wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(axi2uiConst.absOff(axi2uiConst.token   ), a2utoken   , wmask = MaskedRegMap.UnwritableMask),

            // addrmap
            MaskedRegMap(addrmapRegConst.absOff(addrmapRegConst.addrmap), addrmap, smask = MaskedRegMap.EmptyMap),

            // filter
            MaskedRegMap(filterConst.absOff(filterConst.ctrl  ), ftctrl),
            MaskedRegMap(filterConst.absOff(filterConst.adrbdh1), ftadrbdh1),
            MaskedRegMap(filterConst.absOff(filterConst.adrbdh0), ftadrbdh0),
            MaskedRegMap(filterConst.absOff(filterConst.adrbdl1), ftadrbdl1),
            MaskedRegMap(filterConst.absOff(filterConst.adrbdl0), ftadrbdl0),
            MaskedRegMap(filterConst.absOff(filterConst.debug ), ftdebug, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(filterConst.absOff(filterConst.rdcnt ), ftrdcnt, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(filterConst.absOff(filterConst.wrcnt ), ftwrcnt, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(filterConst.absOff(filterConst.wbcnt ), ftrbcnt, wmask = MaskedRegMap.UnwritableMask),

            // scg
            MaskedRegMap(scgConst.absOff(scgConst.tmchk0), scgtmchk0),
            MaskedRegMap(scgConst.absOff(scgConst.tmchk1), scgtmchk1),
            MaskedRegMap(scgConst.absOff(scgConst.tmchk2), scgtmchk2),
            MaskedRegMap(scgConst.absOff(scgConst.tmchk3), scgtmchk3),
            MaskedRegMap(scgConst.absOff(scgConst.ref0  ),   scgref0),
            MaskedRegMap(scgConst.absOff(scgConst.ref1  ),   scgref1),
            MaskedRegMap(scgConst.absOff(scgConst.ref2  ),   scgref2),
            MaskedRegMap(scgConst.absOff(scgConst.dfi0  ),   scgdfi0),
            MaskedRegMap(scgConst.absOff(scgConst.dfi1  ),   scgdfi1),
            MaskedRegMap(scgConst.absOff(scgConst.dfi2  ),   scgdfi2),
            MaskedRegMap(scgConst.absOff(scgConst.dfi3  ),   scgdfi3),
            MaskedRegMap(scgConst.absOff(scgConst.clspg ),  scgclspg),
            MaskedRegMap(scgConst.absOff(scgConst.debug ),  scgdebug, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(scgConst.absOff(scgConst.mcctrl), scgmcctrl, smask = MaskedRegMap.EmptyMap),
            MaskedRegMap(scgConst.absOff(scgConst.ddrstat ), scgddrstat, wmask = MaskedRegMap.UnwritableMask),
            MaskedRegMap(scgConst.absOff(scgConst.initmparam0), scginitmparam0),
            MaskedRegMap(scgConst.absOff(scgConst.initmparam1), scginitmparam1),
            MaskedRegMap(scgConst.absOff(scgConst.initmparam2), scginitmparam2),
            MaskedRegMap(scgConst.absOff(scgConst.mrsmode0), scgmrsmode0),
            MaskedRegMap(scgConst.absOff(scgConst.mrsmode1), scgmrsmode1),
            MaskedRegMap(scgConst.absOff(scgConst.mrsmode2), scgmrsmode2),
            MaskedRegMap(scgConst.absOff(scgConst.mrsmode3), scgmrsmode3),
            MaskedRegMap(scgConst.absOff(scgConst.geardownmode), scggeardownmode),
            MaskedRegMap(scgConst.absOff(scgConst.raparam), scgraparam),

            MaskedRegMap(scgConst.absOff(scgConst.mode), scgctrl, smask = MaskedRegMap.EmptyMap),

            //scheduler
            MaskedRegMap(asConst.absOff(asConst.aswr), schedulerwr, smask = MaskedRegMap.EmptyMap),
            MaskedRegMap(asConst.absOff(asConst.asrd), schedulerrd, smask = MaskedRegMap.EmptyMap),

            // apbcfg
            MaskedRegMap(apbcfgConst.offset, apbcfg, smask = MaskedRegMap.EmptyMap)
        )

        // addrdecode
        val addr = io.apb.paddr >> 2
        val rdata = Wire(UInt(regBits.W))
        val wdata = io.apb.pwdata
        wen := state === s_access && io.apb.pwrite

        // reg wr & rd
        MaskedRegMap.generate(mappingReg, addr, rdata, wen, wdata, io.sen, io.qen)
        val isIllegalAddr = MaskedRegMap.isIllegalAddr(mappingReg, addr)
        isDynamicReg := MaskedRegMap.isDynamicReg(mappingReg, addr)

        // CDC map
        val mappingCDC = Map(
            // axi2ui
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.axirdcnt), io.cclk, io.crst, io.apb.pclk, prst, a2uaxirdcntcd, a2uaxirdcnt),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.axiwrcnt), io.cclk, io.crst, io.apb.pclk, prst, a2uaxiwrcntcd, a2uaxiwrcnt),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.uirdcnt ), io.cclk, io.crst, io.apb.pclk, prst, a2uuirdcntcd, a2uuirdcnt),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.uiwrcnt ), io.cclk, io.crst, io.apb.pclk, prst, a2uuiwrcntcd, a2uuiwrcnt),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.uirbcnt ), io.cclk, io.crst, io.apb.pclk, prst, a2uuirbcntcd, a2uuirbcnt),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.debug   ), io.cclk, io.crst, io.apb.pclk, prst, a2udebugcd, a2udebug),
            MultiClockPath(axi2uiConst.absOff(axi2uiConst.token   ), io.cclk, io.crst, io.apb.pclk, prst, a2utokencd, a2utoken),

            // addrmap
            MultiClockPath(addrmapRegConst.absOff(addrmapRegConst.addrmap), io.apb.pclk, prst, io.cclk, io.crst, addrmap.asTypeOf(new ADDRMAPStruct).mapMode, io.regio.amapRegio.MEM_ADDR_MAP),

            // filter
            MultiClockPath(filterConst.absOff(filterConst.debug), io.cclk, io.crst, io.apb.pclk, prst, ftdebugcd, ftdebug),
            MultiClockPath(filterConst.absOff(filterConst.rdcnt), io.cclk, io.crst, io.apb.pclk, prst, ftrdcntcd, ftrdcnt),
            MultiClockPath(filterConst.absOff(filterConst.wrcnt), io.cclk, io.crst, io.apb.pclk, prst, ftwrcntcd, ftwrcnt),
            MultiClockPath(filterConst.absOff(filterConst.wbcnt), io.cclk, io.crst, io.apb.pclk, prst, ftrbcntcd, ftrbcnt),
            
            // scg
            MultiClockPath(scgConst.absOff(scgConst.debug), io.cclk, io.crst, io.apb.pclk, prst, scgdebugcd, scgdebug),
            MultiClockPath(scgConst.absOff(scgConst.mcctrl), io.apb.pclk, prst, io.cclk, io.crst, scgmcctrl, scgmcctrlcd, ro = false),
            MultiClockPath(scgConst.absOff(scgConst.ddrstat), io.cclk, io.crst, io.apb.pclk, prst, scgddrstatcd, scgddrstat),
            MultiClockPath(scgConst.absOff(scgConst.mode), io.apb.pclk, prst, io.cclk, io.crst, scgctrl.asTypeOf(new SCGctrlStruct).scgMode, io.regio.scgio.scgMode),


            //scheduler
            MultiClockPath(asConst.absOff(asConst.aswr), io.apb.pclk, prst, io.cclk, io.crst, schedulerwr.asTypeOf(new ASwrStruct).wrhigh, io.regio.asRegio.wrhigh),
            MultiClockPath(asConst.absOff(asConst.aswr), io.apb.pclk, prst, io.cclk, io.crst, schedulerwr.asTypeOf(new ASwrStruct).wrlow, io.regio.asRegio.wrlow),
            MultiClockPath(asConst.absOff(asConst.asrd), io.apb.pclk, prst, io.cclk, io.crst, schedulerrd.asTypeOf(new ASrdStruct).rdhigh, io.regio.asRegio.rdhigh),
            MultiClockPath(asConst.absOff(asConst.asrd), io.apb.pclk, prst, io.cclk, io.crst, schedulerrd.asTypeOf(new ASrdStruct).rdlow, io.regio.asRegio.rdlow),


            // apbcfg
            MultiClockPath(apbcfgConst.offset, io.apb.pclk, prst, io.cclk, io.crst, apbcfg, apbcfgcd, ro = false)
        )

        // CDC
        isRONeedSync := MultiClockPath.isRONeedSync(mappingCDC, addr) && state === s_access && !io.apb.pwrite
        val levelSend = Wire(Bool())
        levelSend := RegNext(isRONeedSync ^ levelSend, false.B)
        val send = Mux(isDynamicReg, RegNext(wen, false.B),
            withClockAndReset(io.cclk, io.crst) { MultiClockPath.syncPulseGen(levelSend)._1 } )
        ack := MultiClockPath.generate(mappingCDC, addr, send, init = state === s_pwrup)

        // io
        // apb
        io.apb.pready := state === s_ready
        io.apb.prdata := rdata
        io.apb.pslverr := isIllegalAddr && state === s_ready

        // axi2ui
        a2uaxirdcntcd := io.regio.a2uio.axiRdCmdCnt
        a2uaxiwrcntcd := io.regio.a2uio.axiWrCmdCnt
        a2uuirdcntcd := io.regio.a2uio.uiRdCmdCnt
        a2uuiwrcntcd := io.regio.a2uio.uiWrCmdCnt
        a2uuirbcntcd := io.regio.a2uio.uiRbCmdCnt
        a2udebugcd := Cat(io.regio.a2uio.readyStall)
        a2utokencd := Cat(io.regio.a2uio.tokenCnt)

        //addrmap
        io.regio.amapRegio.MEM_ADDR_MAP := addrmap.asTypeOf(new ADDRMAPStruct).mapMode

        // filter
        io.regio.filterio.en := ftctrl.asTypeOf(new FTctrlStruct).en
        io.regio.filterio.mode := ftctrl.asTypeOf(new FTctrlStruct).mode
        io.regio.filterio.adrbdh := Cat(ftadrbdh1, ftadrbdh0)
        io.regio.filterio.adrbdl := Cat(ftadrbdl1, ftadrbdl0)

        ftdebugcd := Cat(io.regio.filterio.cmdValid, io.regio.filterio.rcacheEn, io.regio.filterio.wcacheEn)
        ftrdcntcd := io.regio.filterio.rdCmdCnt
        ftwrcntcd := io.regio.filterio.wrCmdCnt
        ftrbcntcd := io.regio.filterio.rdBackCnt

        // scg
        io.regio.scgio.tRRDS := scgtmchk0.asTypeOf(new SCGtmchk0Struct).tRRDS
        io.regio.scgio.tRRDL := scgtmchk0.asTypeOf(new SCGtmchk0Struct).tRRDL
        io.regio.scgio.tFAW := scgtmchk0.asTypeOf(new SCGtmchk0Struct).tFAW
        io.regio.scgio.tRCD := scgtmchk0.asTypeOf(new SCGtmchk0Struct).tRCD
        io.regio.scgio.tRP := scgtmchk0.asTypeOf(new SCGtmchk0Struct).tRP
        io.regio.scgio.tCCDS := scgtmchk1.asTypeOf(new SCGtmchk1Struct).tCCDS
        io.regio.scgio.tCCDL := scgtmchk1.asTypeOf(new SCGtmchk1Struct).tCCDL
        io.regio.scgio.tWTRS := scgtmchk1.asTypeOf(new SCGtmchk1Struct).tWTRS
        io.regio.scgio.tWTRL := scgtmchk1.asTypeOf(new SCGtmchk1Struct).tWTRL
        io.regio.scgio.tRTW := scgtmchk2.asTypeOf(new SCGtmchk2Struct).tRTW
        io.regio.scgio.tWR := scgtmchk2.asTypeOf(new SCGtmchk2Struct).tWR
        io.regio.scgio.tRTP := scgtmchk2.asTypeOf(new SCGtmchk2Struct).tRTP
        io.regio.scgio.tRAS := scgtmchk2.asTypeOf(new SCGtmchk2Struct).tRAS
        io.regio.scgio.AL := scgtmchk3.asTypeOf(new SCGtmchk3Struct).AL
        io.regio.scgio.RL := scgtmchk3.asTypeOf(new SCGtmchk3Struct).RL
        io.regio.scgio.WL := scgtmchk3.asTypeOf(new SCGtmchk3Struct).WL
        io.regio.scgio.BL := scgtmchk3.asTypeOf(new SCGtmchk3Struct).BL
        io.regio.scgio.tZQINTVL := scgref0
        io.regio.scgio.tREFI := scgref1
        io.regio.scgio.tRFC := scgref2.asTypeOf(new SCGref2Struct).tRFC
        io.regio.scgio.tZQCS := scgref2.asTypeOf(new SCGref2Struct).tZQCS
        io.regio.scgio.tphyWrlat := scgdfi0.asTypeOf(new SCGdfi0Struct).tphyWrlat
        io.regio.scgio.tphyWrcslat := scgdfi0.asTypeOf(new SCGdfi0Struct).tphyWrcslat
        io.regio.scgio.tphyWrdata := scgdfi0.asTypeOf(new SCGdfi0Struct).tphyWrdata
        io.regio.scgio.trddataEn := scgdfi0.asTypeOf(new SCGdfi0Struct).trddataEn
        io.regio.scgio.tphyRdcslat := scgdfi1.asTypeOf(new SCGdfi1Struct).tphyRdcslat
        io.regio.scgio.tphyRdlat := scgdfi1.asTypeOf(new SCGdfi1Struct).tphyRdlat
        io.regio.scgio.wrOdtDelay := scgdfi2.asTypeOf(new SCGdfi2Struct).wrOdtDelay
        io.regio.scgio.wrOdtHold := scgdfi2.asTypeOf(new SCGdfi2Struct).wrOdtHold
        io.regio.scgio.rdOdtDelay := scgdfi2.asTypeOf(new SCGdfi2Struct).rdOdtDelay
        io.regio.scgio.rdOdtHold := scgdfi2.asTypeOf(new SCGdfi2Struct).rdOdtHold
        io.regio.scgio.dfiMode := scgdfi3
        io.regio.scgio.clspgTmInit := scgclspg.asTypeOf(new SCGclspgStruct).clspgTmInit
        io.regio.scgio.prePolicy := scgclspg.asTypeOf(new SCGclspgStruct).prePolicy
        io.regio.scgio.gen := scgmcctrl
        io.regio.scgio.dramRstn := scginitmparam0.asTypeOf(new SCGinitmparam0Struct).dramRstn
        io.regio.scgio.postCke := scginitmparam0.asTypeOf(new SCGinitmparam0Struct).postCke
        io.regio.scgio.preCke := scginitmparam1.asTypeOf(new SCGinitmparam1Struct).preCke
        io.regio.scgio.mrs2other := scginitmparam1.asTypeOf(new SCGinitmparam1Struct).mrs2other
        io.regio.scgio.mrs2mrs := scginitmparam2.asTypeOf(new SCGinitmparam2Struct).mrs2mrs
        io.regio.scgio.zqinit := scginitmparam2.asTypeOf(new SCGinitmparam2Struct).zqinit
        io.regio.scgio.mrs1 := scgmrsmode0.asTypeOf(new SCGmrsmode0Struct).mrs1
        io.regio.scgio.mrs0 := scgmrsmode0.asTypeOf(new SCGmrsmode0Struct).mrs0
        io.regio.scgio.mrs3 := scgmrsmode1.asTypeOf(new SCGmrsmode1Struct).mrs3
        io.regio.scgio.mrs2 := scgmrsmode1.asTypeOf(new SCGmrsmode1Struct).mrs2
        io.regio.scgio.mrs5 := scgmrsmode2.asTypeOf(new SCGmrsmode2Struct).mrs5
        io.regio.scgio.mrs4 := scgmrsmode2.asTypeOf(new SCGmrsmode2Struct).mrs4
        io.regio.scgio.mrs6 := scgmrsmode3
        io.regio.scgio.cmdGear := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).cmdGear
        io.regio.scgio.syncGear := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).syncGear
        io.regio.scgio.gearHold := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).gearHold
        io.regio.scgio.gearSetup := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).gearSetup
        io.regio.scgio.blkTGeardown := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).blkTGeardown
        io.regio.scgio.geardownMode := scggeardownmode.asTypeOf(new SCGgeardownmodeStruct).geardownMode
        io.regio.scgio.w2wdr := scgraparam.asTypeOf(new SCGraparamStruct).w2wdr
        io.regio.scgio.r2rdr := scgraparam.asTypeOf(new SCGraparamStruct).r2rdr
        io.regio.scgio.w2rdr := scgraparam.asTypeOf(new SCGraparamStruct).w2rdr
        io.regio.scgio.r2wdr := scgraparam.asTypeOf(new SCGraparamStruct).r2wdr

        io.regio.scgio.scgMode := scgctrl.asTypeOf(new SCGctrlStruct).scgMode

        // scgdebugcd := Cat(io.regio.scgio.RGState.asUInt, io.regio.scgio.refState)
        scgdebugcd := Cat(0.U(3.W), io.regio.scgio.refState)
        scgddrstatcd := io.regio.scgio.ddrInitEnd

        //scheduler
        io.regio.asRegio.wrhigh := schedulerwr.asTypeOf(new ASwrStruct).wrhigh
        io.regio.asRegio.wrlow := schedulerwr.asTypeOf(new ASwrStruct).wrlow
        io.regio.asRegio.rdhigh := schedulerrd.asTypeOf(new ASrdStruct).rdhigh
        io.regio.asRegio.rdlow := schedulerrd.asTypeOf(new ASrdStruct).rdlow

        // apbcfg
        io.apbDone := apbcfgcd
    }
}
