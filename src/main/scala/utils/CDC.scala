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
package utils

import chisel3._
import chisel3.util._

object MultiClockPath {
    def apply(addr: Int, sclk: Clock, srst: AsyncReset, dclk: Clock, drst: AsyncReset, sdata: UInt, ddata: UInt, ro: Boolean = true, detectchg: Boolean = false) =
        (addr, (sclk, srst, dclk, drst, sdata, ddata, ro, detectchg))
    def syncPulseGen(d: Bool) = {
        // level to pulse
        val p = RegNext(RegNext(d, false.B), false.B)
        val q = RegNext(p, false.B)
        (p ^ q, q)
    }
    def detectChangePulseGen(d: UInt): Bool = {
        val send = Wire(Bool())
        // detect change
        val dd = RegNext(d, 0.U)
        val chg = dd =/= d
        // generate pulse
        send := RegNext((RegNext(chg, false.B) || chg) && !send, false.B)
        send
    }
    def generate(mapping: Map[Int, (Clock, AsyncReset, Clock, AsyncReset, UInt, UInt, Boolean, Boolean)], addr: UInt, send: Bool, init: Bool = false.B): Bool = {
        val chiselMapping = mapping.map { case (a, (sc, sr, dc, dr, sd, dd, ro, dchg)) => (a.U, sc, sr, dc, dr, sd, dd, ro, dchg) }
        chiselMapping.map { case (a, sc, sr, dc, dr, sd, dd, ro, dchg) => {
            val sen = Wire(Bool())
            if (dchg) { withClockAndReset(sc, sr) { sen := detectChangePulseGen(sd) } }
            else { sen := send && a === addr || init }
            // CDC signals
            val tmd = Wire(UInt(32.W))
            val tmen = Wire(Bool())
            val den = Wire(Bool())
            val back = Wire(Bool())
            // source domains
            withClockAndReset(sc, sr) {
                tmd := RegEnable(sd, 0.U, sen)
                tmen := RegNext(sen ^ tmen, false.B)
            }
            // destination domain
            withClockAndReset(dc, dr) {
                val (p, q) = syncPulseGen(tmen)
                den := p
                back := q
                dd := RegEnable(tmd, 0.U, den)
            }
            // feedback
            if (ro) den else withClockAndReset(sc, sr) { syncPulseGen(back)._1 }
        }}.reduce(_||_)
    }
    def isRONeedSync(mapping: Map[Int, (Clock, AsyncReset, Clock, AsyncReset, UInt, UInt, Boolean, Boolean)], addr: UInt): Bool = {
        val RONeedSync = Wire(Bool())
        val chiselMapping = mapping.map { case (a, (sc, sr, dc, dr, sd, dd, ro, dchg)) => (a.U, sc, sr, dc, dr, sd, dd, ro.B, dchg.B) }
        RONeedSync := MuxLookup(addr, false.B)(chiselMapping.map { case (a, sc, sr, dc, dr, sd, dd, ro, dchg) => (a, ro && !dchg) }.toSeq)
        RONeedSync
    }
}
