package OpenMc

import chisel3._
import chisel3.util._
import chisel3.experimental.FlatIO
import java.util.ResourceBundle



class osmc_axi_write[T <: AXI2UI_PARAMETER](
//AXI parameter define
    AXIW_PARAMETER   :   T
)extends Module{

class TOKEN_COUNTIO_W extends Bundle{
    val token_awen      =   Output(Bool())
    val token_awready   =   Output(Bool())
}
class AXI_WRITEIO extends Bundle{
    //AXI write address
    val axi_awio= new AXI_AWIO()
    //AXI write data
    val axi_wio = new AXI_WIO()
    //AXI write response    
    val axi_bio = new AXI_BIO()     
    //UI write
    val ui_wreq = Decoupled(new WriteReqIO())
    //consis
    val wconsis =   Input(Bool())
    val consis_addr_io  = new CONS_ADDR_IO()
    //apb config done 
    val apb_config_done  = Flipped(Bool())
}

/*********************************************************************************************************************************************************/
val CLIP_PAPAM = AXIW_PARAMETER
val TABLE_PARAM     = AXIW_PARAMETER  
val UI_PAPAM        = AXIW_PARAMETER.UI_PARAMETER
val WriteQueueDepth = AXI2UIQueueDepth

val AXI_BW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_BURSTW
val AXI_AW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_ADDRW
val AXI_LW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_LENW
val AXI_SW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_SIZEW
val AXI_QW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_QOSW
val AXI_IW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_IDW
val AXI_UW  =   AXIW_PARAMETER.AXI_PARAMETER.AXI_USERW

//IO define
val io = IO(new AXI_WRITEIO())  

/*************************************************************** consistency ***********************************************************************/
val addr0  =   RegInit(0.U(AXI_AW.W))
val len    =   RegInit(0.U(AXI_LW.W))
val size   =   RegInit(0.U(AXI_SW.W))
val burst  =   RegInit(0.U(AXI_BW.W))
val qos    =   RegInit(0.U(AXI_QW.W))
val awid   =   RegInit(0.U(AXI_IW.W))
val awuser =   RegNext(0.U(AXI_UW.W))

val avalid =   RegNext(io.axi_awio.awvalid)
val aready =   RegNext(io.axi_awio.awready)


val cmd_en      =   Wire(Bool())
val cmd_end0    =   RegNext(cmd_en)
val cmd_hold    =   RegInit(false.B)
val cmd_hold_wid =  RegInit(false.B)
val axi_wtcmd_cnt =   RegInit(0.U(32.W))
/*********************************************************************************************************************************************************/

val u_axi_write_burst_clip = Module(new osmc_axi_write_burst_clip(CLIP_PAPAM))
axi_wtcmd_cnt := Mux(avalid && aready, axi_wtcmd_cnt + 1.U, axi_wtcmd_cnt)
//AW FIFO
val u_axi_aw_fifol1 = Module(new Queue(new AXIWriteAddrInfo, WriteQueueDepth.writeAwL1))
    u_axi_write_burst_clip.io.awIn <> u_axi_aw_fifol1.io.deq
    u_axi_aw_fifol1.io.enq.valid :=  ((avalid & aready)  | cmd_hold) & ~io.wconsis
    u_axi_aw_fifol1.io.enq.bits.addr  := addr0
    u_axi_aw_fifol1.io.enq.bits.burst := burst
    u_axi_aw_fifol1.io.enq.bits.len   := len
    u_axi_aw_fifol1.io.enq.bits.size  := size
    u_axi_aw_fifol1.io.enq.bits.qos   := qos

val u_axi_aw_fifol2 = Module(new Queue(new CMDIO(), WriteQueueDepth.writeAwL2))
    u_axi_aw_fifol2.io.enq <> u_axi_write_burst_clip.io.cmdOut

//W FIFO
val u_axi_w_fifol1  = Module(new Queue(new AXIWriteDataInfo, WriteQueueDepth.writeWL1))
    u_axi_write_burst_clip.io.wIn <> u_axi_w_fifol1.io.deq
    u_axi_w_fifol1.io.enq.valid := io.axi_wio.wvalid & io.axi_wio.wready
    u_axi_w_fifol1.io.enq.bits.data := io.axi_wio.wdata
    u_axi_w_fifol1.io.enq.bits.strb := io.axi_wio.wstrb
    u_axi_w_fifol1.io.enq.bits.last := io.axi_wio.wlast

val u_axi_w_fifol2 = Module(new Queue(new WrDataIO, WriteQueueDepth.writeWL2))
    u_axi_w_fifol2.io.enq <> u_axi_write_burst_clip.io.dataOut


//UI
    io.ui_wreq.valid := u_axi_aw_fifol2.io.deq.valid & u_axi_w_fifol2.io.deq.valid
    io.ui_wreq.bits.cmd := u_axi_aw_fifol2.io.deq.bits
    io.ui_wreq.bits.data := u_axi_w_fifol2.io.deq.bits
    u_axi_aw_fifol2.io.deq.ready := io.ui_wreq.fire
    u_axi_w_fifol2.io.deq.ready := io.ui_wreq.fire

//B channel
//w channel handshak
val u_axi_wb_fifo = Module(new Queue(Bool(), WriteQueueDepth.writeBOrder))
    u_axi_wb_fifo.io.enq.valid := io.axi_wio.wvalid & io.axi_wio.wready & io.axi_wio.wlast
    u_axi_wb_fifo.io.enq.bits  := true.B
//aw channel handshak
val u_axi_awb_fifo_out = Module(new Queue(new AXIWriteRespInfo, WriteQueueDepth.writeB))
    u_axi_awb_fifo_out.io.enq.valid := ((avalid & aready)  | cmd_hold_wid) & ~io.wconsis
    u_axi_awb_fifo_out.io.enq.bits.id := awid
    u_axi_awb_fifo_out.io.enq.bits.user := awuser

val w_axi_consis_table = Module(new osmc_axi_consis_table(TABLE_PARAM)) 
    w_axi_consis_table.io.ui_aio.addr   :=  io.ui_wreq.bits.cmd.addr
    w_axi_consis_table.io.ui_aio.token  :=  io.ui_wreq.bits.cmd.token
    w_axi_consis_table.io.ui_aio.pri    :=  io.ui_wreq.bits.cmd.pri
    w_axi_consis_table.io.ui_hsio.ready :=  io.ui_wreq.ready
    w_axi_consis_table.io.ui_hsio.valid :=  io.ui_wreq.valid
    w_axi_consis_table.io.consis_addr_io<>  io.consis_addr_io
    w_axi_consis_table.io.axi_aio.aaddr <>  io.axi_awio.awaddr
    w_axi_consis_table.io.axi_aio.aburst<>  io.axi_awio.awburst
    w_axi_consis_table.io.axi_aio.alen  <>  io.axi_awio.awlen
    w_axi_consis_table.io.axi_aio.aqos  <>  io.axi_awio.awqos
    w_axi_consis_table.io.axi_aio.aready:=  io.axi_awio.awready
    w_axi_consis_table.io.axi_aio.asize <>  io.axi_awio.awsize
    w_axi_consis_table.io.axi_aio.auser <>  io.axi_awio.awuser
    w_axi_consis_table.io.axi_aio.avalid<>  io.axi_awio.awvalid


/******************************************************************************************************************************/
    cmd_hold    :=  Mux((cmd_end0 & !u_axi_aw_fifol1.io.enq.ready) | (io.wconsis & cmd_end0), true.B, Mux(u_axi_aw_fifol1.io.enq.fire, false.B, cmd_hold))
    cmd_hold_wid := Mux((cmd_end0 & !u_axi_awb_fifo_out.io.enq.ready) | (io.wconsis & cmd_end0), true.B, Mux(u_axi_awb_fifo_out.io.enq.fire, false.B, cmd_hold_wid))
    cmd_en  :=  io.axi_awio.awvalid &   io.axi_awio.awready

    addr0  :=  Mux(cmd_en, io.axi_awio.awaddr, Mux(u_axi_aw_fifol1.io.enq.fire, 0.U, addr0))   //clear to avoid unnecessary stall                                                 
    len    :=  Mux(cmd_en, io.axi_awio.awlen , Mux(u_axi_aw_fifol1.io.enq.fire, 0.U, len  ))   //clear to avoid unnecessary stall                     
    size   :=  Mux(cmd_en, io.axi_awio.awsize, Mux(u_axi_aw_fifol1.io.enq.fire, 0.U, size ))   //clear to avoid unnecessary stall
    burst  :=  Mux(cmd_en, io.axi_awio.awburst,Mux(u_axi_aw_fifol1.io.enq.fire, 0.U, burst))   
    qos    :=  Mux(cmd_en, io.axi_awio.awqos  ,Mux(u_axi_aw_fifol1.io.enq.fire, 0.U, qos  ))  
    awid   :=  Mux(cmd_en, io.axi_awio.awid   ,Mux(u_axi_awb_fifo_out.io.enq.fire, 0.U, awid ))
    awuser :=  Mux(cmd_en, io.axi_awio.awuser,Mux(u_axi_awb_fifo_out.io.enq.fire, 0.U, awuser))
/********************************************************************************************************************************/
//ready
    io.axi_awio.awready := u_axi_aw_fifol1.io.enq.ready & ~io.wconsis & ~cmd_hold & ~cmd_hold_wid & u_axi_awb_fifo_out.io.enq.ready & io.apb_config_done
    io.axi_wio.wready := u_axi_w_fifol1.io.enq.ready & u_axi_wb_fifo.io.enq.ready & io.apb_config_done

//for test initialized
    val bFire = io.axi_bio.bvalid & io.axi_bio.bready
    u_axi_wb_fifo.io.deq.ready := bFire
    u_axi_awb_fifo_out.io.deq.ready := bFire
    io.axi_bio.bid      :=  u_axi_awb_fifo_out.io.deq.bits.id
    io.axi_bio.bresp    :=  0.U   
    io.axi_bio.buser    :=  u_axi_awb_fifo_out.io.deq.bits.user   
    io.axi_bio.bvalid   :=  u_axi_wb_fifo.io.deq.valid & u_axi_awb_fifo_out.io.deq.valid

  
}
