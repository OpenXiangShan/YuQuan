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
package  OpenMc

import  chisel3._
import chisel3.util._
import chisel3.experimental._



class ddr4_init(BGBITS: Int,BABITS :Int , ABITS: Int) extends Module{
    val io = IO(new Bundle{
        val apbDone                     =               Input(Bool()            )   //abp disposition complete
        val mrs0                        =               Input(UInt(16.W)        )   //MRS0 register(A[15:00]) configuration input
        val mrs1                        =               Input(UInt(16.W)        )   //MRS1 register(A[15:00]) configuration input
        val mrs2                        =               Input(UInt(16.W)        )   //MRS2 register(A[15:00]) configuration input
        val mrs3                        =               Input(UInt(16.W)        )   //MRS3 register(A[15:00]) configuration input
        val mrs4                        =               Input(UInt(16.W)        )   //MRS4 register(A[15:00]) configuration input
        val mrs5                        =               Input(UInt(16.W)        )   //MRS5 register(A[15:00]) configuration input
        val mrs6                        =               Input(UInt(16.W)        )   //MRS6 register(A[15:00]) configuration input
        val mrs_to_other                =               Input(UInt(8.W)         )   //time to wait between MRS/MRW(load mode comman) to other valid command (non-MRS)
        val mrs_to_mrs                  =               Input(UInt(4.W)         )   //time to wait between MRS/MRW(load mode comman)
        val dram_rstn                   =               Input(UInt(21.W)        )   //cycles to wait for dram reset to stay low, in 1024 core clock cycles
                                                                                    //100:deep power down
        val phy_dfi_init_complete       =               Input(Bool()            )   //PHY initialization complete.All DFI signals that communicate commands or status
                                                                                    //must be held at their default values until the dfi_init_complete signal asserts
        val pre_cke                     =               Input(UInt(24.W)        )   //cycles to wait before enabling clock
        val post_cke                    =               Input(UInt(11.W)        )   //cycles to wait after enabling clock     
        val sync_gear                   =               Input(UInt(8.W)         )   //tSYNC_GEAR : MRS command to Sync pulse time(T3)
        val cmd_gear                    =               Input(UInt(6.W)         )   //tCMD GEAR: Sync pulse to First valid command(T4)
        val gear_setup                  =               Input(UInt(8.W)         )   //tGEAR_setup: Geardown setup time
        val gear_hold                   =               Input(UInt(8.W)         )   //tGEAR_hold : Geardown hold time
        val geardown_mode               =               Input(Bool()            )   //Geardown Mode 1:geardown mode(2N) 0:normal mode(1N)
        val block_tgeardown             =               Input(Bool()            )   //gear-down mode:block tgeardown delay
        val zqinit                      =               Input(UInt(12.W)        )   //wait time during init in DDR4
        val zqlreq                      =               Output(Bool()           )   //ZQ calibration 
        val dram_rst_n                  =               Output(Bool()           )   //ddrc to dram active low reset
        val init_cke                    =               Output(Bool()           )   //CKE value during init
        val MrsReq                      =               Output(Bool()           )   //MRS request
        val geardown_mode_init          =               Output(Bool()           )   //gear-down mode:load mode command 
        val geardown_sync_pulse_init    =               Output(Bool()           )   //gear-down mode:geardown sync pluse
        val cal_on_init                 =               Output(Bool()           )   //CAL-on timing
        val MrsRank                     =               Output(Bool()           )   //rank address
        val MrsBG                       =               Output(UInt(BGBITS.W)   )   //grup address line during init
        val MrsBA                       =               Output(UInt(BABITS.W)   )   //bank address line during init
        val MrsAddr                     =               Output(UInt(ABITS.W)    )   //address lines during init
        val end_init_ddr                =               Output(Bool()           )   //end of DDR initizlization
        val init_in_progress            =               Output(Bool()           )   //Initializing

        //debug                     
        val init_curr_state             =               Output(UInt()           )
        val init_next_state             =               Output(UInt()           )
    })
    dontTouch(io)
//----------------------------------------------------------  State Definition     -------------------------------------------------------------------//
    //定义状态类型
    val Seq(init_FSM_START, init_FSM_RESET, init_FSM_CKE, init_FSM_GEARDOWN_MRS, init_FSM_GEARDOWN_SYNC, init_FSM_MR3, init_FSM_MR6, init_FSM_MR5, init_FSM_MR4, init_FSM_MR2, init_FSM_MR1, init_FSM_MR0, init_FSM_ZQCL, init_FSM_END) = Enum(14)
    val current_state                   =                RegInit     (init_FSM_START      )           
    val next_state                      =                WireDefault (init_FSM_START      )           
    val next_ddr_state                  =                WireDefault (init_FSM_START      )           
    val last_state                      =                RegInit     (init_FSM_START      )           
//----------------------------------------------------------------------------------------------------------------------------------------------------//


//--------------------------------------------------------     Other   signal      --------------------------------------------------------------------//
    val resetb_ff                        =               RegInit     (false.B            )           //在reset拉高一拍后，给计数器赋值，              
    val zqcl_ddr4                        =               RegInit     (false.B            )           //寄存ZQ校验标志
    val load_mode                        =               WireDefault (false.B            )           //每加载一个模式寄存器MRS就输出一个脉冲
    val geardown_sync_toggle             =               RegInit     (false.B            )           //geardown模式的同步信号
    val MrsAddr_wire                     =               WireDefault (0.U(ABITS.W)  )                //暂存存储Addr的地址
    val mrs_bg_a                         =               WireDefault (0.U((BABITS + BGBITS).W)  )
//-----------------------------------------------------------------------------------------------------------------------------------------------------//


//----------------------------------------------------------  Counter Definition   --------------------------------------------------------------------//
    val timer_dram_rstn                 =               RegInit     (255.U(21.W)        )           //reset保持至少200us计时。
    val timer_dram_rstn_w               =               WireDefault (255.U(21.W)        )
    val timer_cke                       =               RegInit     (1.U(24.W)          )           //计数器，即500u0s和tXPR(CKE到MRS命令间的等待时间)
    val timer_cke_value                 =               WireDefault (1.U(24.W)          )           //寄存500us计数值和tXPR的值
    val set_cke_timer                   =               WireDefault (false.B            )           //timer_cke赋值允许信号
    val timer_x1                        =               RegInit     (0.U(8.W)           )           //计数器，mrs_to_mrs和geardown模式下使用
    val set_timer_x1                    =               WireDefault (false.B            )           //timer_x1赋值允许信号
    val timer_x1_value                  =               WireDefault (0.U(10.W)          )           //寄存值，不同条件下设置不同的值
    val timer_zq                        =               RegInit     (0.U(12.W)          )           //ZQ校验时的等待时间(tZQINIT)
    val set_timer_zq                    =               WireDefault (false.B            )           
    val timer_mode                      =               RegInit     (10.U(8.W)          )           //mrs_to_other(tMOD:mrs命令到其他命令间额等待时间)
    val Mrs_Phase                       =               RegInit     (0.U(1.W)           )           //dual rank MRS phase
//-----------------------------------------------------------------------------------------------------------------------------------------------------//


//--------------------------------------------------------     Counter logic       --------------------------------------------------------------------// 
    //set_cke_timer
    set_cke_timer                       :=              (((current_state =/= init_FSM_CKE) &&(next_state === init_FSM_CKE) && 
                                                        (io.apbDone)) || (current_state =/= init_FSM_RESET) && (next_state === init_FSM_RESET))

    //timer_cke_value 
    timer_cke_value                     :=              Mux((!resetb_ff) || (!io.apbDone) 
                                                         || (current_state === init_FSM_START), io.pre_cke,  io.post_cke)
    //set_timer_x1      
    set_timer_x1                        :=              ((next_state =/= current_state) & (next_ddr_state =/= init_FSM_CKE) &  (next_state =/= init_FSM_END) &
                                                        (next_state =/= init_FSM_RESET) & (next_state =/= init_FSM_START) & (next_state =/= init_FSM_ZQCL) & (next_state =/= init_FSM_MR0))
    //timer_x1_value
    when(next_state === init_FSM_GEARDOWN_MRS){
        timer_x1_value                  :=              Cat("h0".U(5.W), io.sync_gear) + Cat("h0".U(8.W), io.gear_setup) + Cat("h0".U(8.W),io.gear_hold)
    }.elsewhen(next_state === init_FSM_GEARDOWN_SYNC){
        timer_x1_value                  :=              Cat("h0".U(5.W), io.sync_gear) + Cat("h0".U(8.W), io.gear_setup)
    }.elsewhen(io.geardown_mode && (!io.mrs_to_mrs(0))){
        timer_x1_value                  :=              io.mrs_to_mrs + "b1".U(10.W)
    }.otherwise{        
        timer_x1_value                  :=              io.mrs_to_mrs
    }       
    //timer_x1
    timer_x1                            :=              Mux(set_timer_x1, timer_x1_value,
                                                        Mux(timer_x1 =/= 0.U, (timer_x1 - 1.U), timer_x1) )
    // set_timer_zq      
    set_timer_zq                       :=              ((next_state =/= current_state) & (next_state === init_FSM_ZQCL))
    //ZQinit
    timer_zq                           :=               Mux(set_timer_zq, io.zqinit,
                                                        Mux(timer_zq.orR, timer_zq - 1.U, timer_zq))
    //timer_mode
    //mrs_to_other
    when(current_state =/= init_FSM_MR0){
        timer_mode                      :=              io.mrs_to_other   
    }.elsewhen(timer_mode.orR ){
        timer_mode                      :=              timer_mode - 1.U
    }
    //timer_dram_rstn
    when(timer_dram_rstn.orR & io.apbDone){
        timer_dram_rstn                 :=              timer_dram_rstn - 1.U
    }.elsewhen(!timer_dram_rstn.orR){
        timer_dram_rstn                 :=              timer_dram_rstn
    }.elsewhen(io.apbDone){
        timer_dram_rstn                 :=              io.dram_rstn
    }
    //timer_cke
    when(!resetb_ff || (current_state === init_FSM_START) || (current_state === init_FSM_CKE)
            ||(current_state === init_FSM_RESET)){
        timer_cke                     :=              Mux(set_cke_timer, timer_cke_value, Mux(timer_cke.orR, timer_cke - 1.U, timer_cke))
    }
    //MRS_phase
    Mrs_Phase  := Mux(current_state === init_FSM_MR0 & next_ddr_state === init_FSM_MR3,Mrs_Phase + 1.U, Mrs_Phase)

//-----------------------------------------------------------------------------------------------------------------------------------------------------//


//--------------------------------------------------------     Other   logic   ------------------------------------------------------------------------//
    //加载mrs时的脉冲信号
    load_mode                            :=              ((last_state =/= current_state) && ((current_state === init_FSM_MR0) || 
                                                         (current_state === init_FSM_MR1) || (current_state === init_FSM_MR2) ||
                                                         (current_state === init_FSM_MR3) || (current_state === init_FSM_MR4) ||
                                                         (current_state === init_FSM_MR5) || (current_state === init_FSM_MR6) ||
                                                         (current_state === init_FSM_GEARDOWN_MRS)))
    //gear-down下的脉冲同步
    when(io.geardown_sync_pulse_init){
        geardown_sync_toggle            :=              true.B
    }.elsewhen(current_state === init_FSM_END){
        geardown_sync_toggle            :=              false.B
    }.otherwise{
        geardown_sync_toggle            :=              false.B
    }
    //时序逻辑
    resetb_ff                           :=              true.B
    last_state                          :=              current_state
    zqcl_ddr4                           :=              ((current_state === init_FSM_ZQCL) && (last_state =/= init_FSM_ZQCL))
//-----------------------------------------------------------------------------------------------------------------------------------------------------//


//--------------------------------------------------------     MrsAddr  And Group/Bank Output  --------------------------------------------------------//
    when(current_state === init_FSM_MR0){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs0)
    }.elsewhen(current_state === init_FSM_MR1){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs1)
    }.elsewhen(current_state === init_FSM_MR2){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs2)
    }.elsewhen(current_state === init_FSM_MR3){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs3)
    }.elsewhen(current_state === init_FSM_MR4){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs4)
    }.elsewhen(current_state === init_FSM_MR5){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs5)
    }.elsewhen(current_state === init_FSM_MR6){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs6)
    }.elsewhen(current_state === init_FSM_GEARDOWN_MRS){
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),io.mrs3) | Cat(0.U((ABITS - 16).W),Cat("h0".U(12.W), Cat(io.geardown_mode, "h0".U(3.W))))
    }.otherwise{
        MrsAddr_wire                    :=              Cat(0.U((ABITS - 16).W),"hFFFF".U)
    }

    //mrs_bg:输出bank和group地址
    mrs_bg_a                            :=              Mux(current_state === init_FSM_MR0, 0.U,
                                                        Mux(current_state === init_FSM_MR1, 1.U,
                                                        Mux(current_state === init_FSM_MR2, 2.U,
                                                        Mux(current_state === init_FSM_MR3 || current_state === init_FSM_GEARDOWN_MRS, 3.U,
                                                        Mux(current_state === init_FSM_MR4, 4.U,
                                                        Mux(current_state === init_FSM_MR5, 5.U,
                                                        Mux(current_state === init_FSM_MR6, 6.U, 0.U)))))))
//-----------------------------------------------------------------------------------------------------------------------------------------------------// 


//--------------------------------------------------------   State machine transition logic    --------------------------------------------------------//
    //next_ddr_state状态机转换
    switch(current_state){
        is(init_FSM_START){
            next_ddr_state              :=              Mux(timer_dram_rstn.orR, init_FSM_START, init_FSM_RESET)
        }           
        is(init_FSM_RESET){         
            next_ddr_state              :=              Mux(timer_cke.orR, init_FSM_RESET, init_FSM_CKE)
        }           
        is(init_FSM_CKE){           
            next_ddr_state              :=              Mux(timer_cke.orR, init_FSM_CKE, Mux(io.geardown_mode, init_FSM_GEARDOWN_MRS, init_FSM_MR3))
        }           
        is(init_FSM_GEARDOWN_MRS){          
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_GEARDOWN_MRS, init_FSM_GEARDOWN_SYNC)
        }           
        is(init_FSM_GEARDOWN_SYNC){         
            next_ddr_state              :=              Mux((timer_x1.orR && !io.block_tgeardown), init_FSM_GEARDOWN_SYNC, init_FSM_MR6)
        }           
        is(init_FSM_MR3){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR3, init_FSM_MR6)
        }           
        is(init_FSM_MR6){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR6, init_FSM_MR5)
        }           
        is(init_FSM_MR5){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR5, init_FSM_MR4)
        }           
        is(init_FSM_MR4){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR4, init_FSM_MR2)
        }           
        is(init_FSM_MR2){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR2, init_FSM_MR1)
        }           
        is(init_FSM_MR1){           
            next_ddr_state              :=              Mux(timer_x1.orR, init_FSM_MR1, init_FSM_MR0)
        }           
        is(init_FSM_MR0){           
            next_ddr_state              :=              Mux(timer_mode.orR, init_FSM_MR0, Mux(Mrs_Phase.asBool,init_FSM_ZQCL,init_FSM_MR3))
        }           
        is(init_FSM_ZQCL){          
            next_ddr_state              :=              Mux(timer_zq.orR, init_FSM_ZQCL, init_FSM_END)
        }
        is(init_FSM_END){
            next_ddr_state              :=              init_FSM_END
        }
    }
    //next_state状态机转换
    next_state                          :=              Mux(!io.apbDone, init_FSM_START,next_ddr_state)
    //current_state     
    current_state                       :=              next_state
//-----------------------------------------------------------------------------------------------------------------------------------------------------//     


//-------------------------------------------------------------------IO Output logic    ----------------------------------------------------------------//
    //初始化结束
    io.end_init_ddr                     :=              (current_state === init_FSM_END)
    //初始化中CKE的值
    io.init_cke                         :=              ((current_state =/= init_FSM_START) && (current_state =/= init_FSM_RESET))
    //发送加载模式寄存器命令
    io.MrsReq                           :=              load_mode
    //ZQ请求
    io.zqlreq                           :=              zqcl_ddr4
    //初始化时DDRC到DRAM解复位
    io.dram_rst_n                       :=              ((!(timer_dram_rstn.orR)) & (io.apbDone) & (current_state =/= init_FSM_START))
    //gear-down mode init
    io.geardown_mode_init               :=              ((last_state =/= current_state) && (current_state === init_FSM_GEARDOWN_MRS))
    io.geardown_sync_pulse_init         :=              ((last_state =/= current_state) && (current_state === init_FSM_GEARDOWN_SYNC))
    //cal_on timing(命令地址延迟开启时序)
    io.cal_on_init                      :=              (current_state === init_FSM_MR4) & (io.mrs4(8,6) =/= 0.U)
    //MrsAddr
    io.MrsAddr                          :=              MrsAddr_wire
    io.init_in_progress                 :=              Mux( (current_state =/= init_FSM_END), true.B, false.B)
    //输出MrsAddr/BANK/GROUP
    io.MrsAddr                          :=              MrsAddr_wire
    io.MrsBG                            :=              mrs_bg_a((BGBITS + BABITS - 1), BGBITS)    
    io.MrsBA                            :=              mrs_bg_a(BGBITS, 0) 
    io.MrsRank                          :=              Mrs_Phase.asBool
    //调试接口，初始化状态跳转
    io.init_curr_state                  :=              current_state
    io.init_next_state                  :=              next_state
//-----------------------------------------------------------------------------------------------------------------------------------------------------// 
}