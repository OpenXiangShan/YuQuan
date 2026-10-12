package OpenMc

import chisel3._
import chisel3.util._
import chisel3.experimental.FlatIO
import java.util.ResourceBundle
import bus.axi4._
import bus.apb3._
import APB._

// import scala.annotation.newMain

//IO define
class TOPIO extends Bundle with CacheConst{
    // clock and reset
    val clk = Input(Clock())
    val rst = Input(AsyncReset())
    // axi interface
    val awio = new AXI_AWIO()
    val wio = new AXI_WIO()
    val bio = new AXI_BIO()
    val ario = new AXI_ARIO()
    val rio = new AXI_RIO()
    // dfi interface
    // val calDone = Input(Bool())
    val dfi = new dfiBundle
    // apb interface
    val apb = new APB3
    val mig_phy_done                = Flipped(Bool())
}

class mc_top extends RawModule {
    val io = IO(new TOPIO())
    // APBSlvtop
    val apbslv = Module(new APBSlvtop)
    apbslv.io.apb <> io.apb
    apbslv.io.cclk := io.clk
    apbslv.io.crst := io.rst
    apbslv.io.sen := !apbslv.io.apbDone
    apbslv.io.qen := false.B
    apbslv.io.regio.filterio <> DontCare
    apbslv.io.regio.a2uio <> DontCare
    // core clock
    withClockAndReset (io.clk, io.rst) {

        //parameter transmit
        val AXI_PARAMETER  = AXI2UI_PARAM
        val u_axi2ui = Module(new osmc_axi_top(AXI_PARAMETER))
        val u_filter = Module(new filter_top(BUNDLE_PARAM.ADDR_WIDTH, BUNDLE_PARAM.PRI_WIDTH, BUNDLE_PARAM.TOKEN_WIDTH, BUNDLE_PARAM.DATA_WIDTH, BUNDLE_PARAM.STRB_WIDTH))
        val u_addr_map = Module(new AddrMap())
        val u_cache = Module(new CacheWraper())
        val u_scheduler = Module(new Scheduler())
        val u_scg       = Module(new SCG_V3())
        val u_gwdb      = Module(new GWDB())
        //AXI system
        u_axi2ui.io.awio   <>  io.awio
        u_axi2ui.io.wio    <>  io.wio
        u_axi2ui.io.bio    <>  io.bio
        u_axi2ui.io.ario   <>  io.ario
        u_axi2ui.io.rio    <>  io.rio
        u_axi2ui.io.a2uregio <> apbslv.io.regio.a2uio
        u_axi2ui.io.apb_config_done := apbslv.io.regio.scgio.gen

        u_axi2ui.io.ui_rio.dataFormScg <> u_scg.io.Scg2AxiRddata
        u_axi2ui.io.ui_rio.dataFromCache <> u_cache.io.RdData2AddrMap
        // filter
        u_axi2ui.io.ui_wreq        <> u_filter.io.wreq
        u_axi2ui.io.ui_ario        <> u_filter.io.rcmd
        u_filter.io.ftregio         <> apbslv.io.regio.filterio
        apbslv.io.regio.filterio.wcacheEn := u_filter.io.wcache_en
        apbslv.io.regio.filterio.rcacheEn := u_filter.io.rcache_en
        // addrmap
        u_filter.io.wreq_addrmap      <>  u_addr_map.io.WrReqFromFilter
        u_filter.io.rcmd_addrmap      <>  u_addr_map.io.RdCmdFromFilter
        u_addr_map.io.WrIsToAS        := !u_filter.io.wcache_en
        u_addr_map.io.RdIsToAS        := !u_filter.io.rcache_en
        u_addr_map.io.ADDRMAP         :=  apbslv.io.regio.amapRegio.MEM_ADDR_MAP
        // system cache
        u_addr_map.io.RdCmd2Cache       <> u_cache.io.RdCmdFromAddrMap
        u_addr_map.io.WrCmd2Cache       <> u_cache.io.WrCmdFromAddrMap
        u_addr_map.io.WrData2Cache      <> u_cache.io.WrDataFromAddrMap
        u_cache.io.WrData2AS.ready      := u_cache.io.WrCmd2AS.ready
        //GWDB
        u_gwdb.io.calDone     := apbslv.io.apbDone & io.mig_phy_done
        u_gwdb.io.CmdEnqFromAddrmap    <> u_addr_map.io.WrCmd2AS
        u_gwdb.io.CmdEnqFromCache      <> u_cache.io.WrCmd2AS
        u_gwdb.io.DataEnqFromAddrap    <> u_addr_map.io.WrData2AS
        u_gwdb.io.DataEnqFromCache     <> u_cache.io.WrData2AS
        u_gwdb.io.DeqtoDFI.takeToken   := u_scg.io.globalWdata.wtoken
        u_gwdb.io.DeqtoDFI.ready       := u_scg.io.globalWdata.ready
        u_scg.io.globalWdata.valid     := u_gwdb.io.DeqtoDFI.valid
        u_scg.io.globalWdata.wdata     := u_gwdb.io.DeqtoDFI.wdata
        u_scg.io.globalWdata.wstrb     := u_gwdb.io.DeqtoDFI.wstrb
        //advanced scheduler 
        u_scheduler.io.cmdInWrite.filterCmd   :<>= u_gwdb.io.AddrmapCmd2AS
        u_scheduler.io.cmdInRead.filterCmd   :<>= u_addr_map.io.RdCmd2AS

        u_scheduler.io.cmdInWrite.cacheCmd    :<>= u_gwdb.io.CacheCmd2AS
        u_scheduler.io.cmdInRead.cacheCmd        :<>= u_cache.io.RdCmd2AS  
        u_cache.io.dataFromAS       :<>= u_scg.io.Scg2CacheRddata
        
        //scg
        u_scg.io.dfi                 <> io.dfi
        u_scg.io.scgregio            <> apbslv.io.regio.scgio
        u_scg.io.calDone             :=  apbslv.io.apbDone & io.mig_phy_done
        u_scg.io.SchedulerQueueIsEmpty <> u_scheduler.io.SchedulerQueueIsEmpty
        u_scg.io.schedulerPendingSameRowHit <> u_scheduler.io.pendingSameRowHit
        u_scheduler.io.scgOpenRowState <> u_scg.io.openRowState
        u_scg.io.AsScgCmd zip u_scheduler.io.cmdOut foreach {case (scgCmd, schedulerCmd) =>
            scgCmd.bits.cmdtype      <> schedulerCmd.bits.isRd.asUInt
            scgCmd.bits.adr.rank     <> schedulerCmd.bits.rank
            scgCmd.bits.adr.group    <> schedulerCmd.bits.bg
            scgCmd.bits.adr.bank     <> schedulerCmd.bits.bank
            scgCmd.bits.adr.row      <> schedulerCmd.bits.row
            scgCmd.bits.adr.col      <> schedulerCmd.bits.col
            scgCmd.bits.adr.cmdToken <> schedulerCmd.bits.token
            scgCmd.valid             <> schedulerCmd.valid
            scgCmd.ready             <> schedulerCmd.ready
        }


        
    }
}
