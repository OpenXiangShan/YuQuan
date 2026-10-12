/** *************************************************************************************
  * Copyright (c) 2024-2025 Institute of Computing Technology, Chinese Academy of Sciences
  * Baiyang is licensed under Mulan PSL v2.
  * You can use this software according to the terms and conditions of the Mulan PSL v2.
  * You may obtain a copy of Mulan PSL v2 at:
  *          http://license.coscl.org.cn/MulanPSL2
  * THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
  * EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
  * MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
  * See the Mulan PSL v2 for more details.
  * **************************************************************************************
  */
package OpenMc

import chisel3._
import chisel3.util.Fill
import chisel3.experimental.BundleLiterals._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import scala.util.Random
import org.w3c.dom.css.Counter



class AddrMapSpec extends AnyFreeSpec with Matchers {
  "AddrMap module " in {
    simulate(new AddrMap_S) { dut =>
      val seqValues = for(i <- 0 to 100000) yield(i%1024, i*8)
      val random = new Random()
      
      //reset
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.clock.step()
      dut.clock.step()

      
      println(f"DDR4 ROW_COL_BANK MAPPING")
      for (i <- 0 until 100000) {
        val token = seqValues(i)._1
        // Sequential
        //val addr = seqValues(i)._2  
        //val addr_hex = addr.toHexString
        // Random
        val addr  = BigInt(random.nextBytes(4).map(b => f"$b%02x").mkString, 16)
        val addr_hex = addr.toString(16)
        val rank = (addr >> 29) & 0x1
        val row = (addr >> 14) & 0x7fff
        val col = (((addr >> 7) & 0x7f) << 3) | (addr & 0x7)
        val bank = (addr >> 5) & 0x3
        val bg = (addr >> 3) & 0x3
        val randomData1  = BigInt(random.nextBytes(64).map(b => f"$b%02x").mkString, 16)
        val randomData2  = BigInt(random.nextBytes(64).map(b => f"$b%02x").mkString, 16)
        println(f"$i, addr: 0x$addr_hex, [rank, row, col, bank, bg] = [$rank, $row, $col, $bank, $bg]")
        
        //val IsToAS = if((i%2) != 0) true else false
        val IsToAS = false
        dut.io.ADDRMAP.poke(0.U)
        // read channel with filter
        dut.io.RdCmdFromFilter.bits.token.poke(token.U)
        dut.io.RdCmdFromFilter.bits.addr.poke(addr.U)
        dut.io.RdCmdFromFilter.bits.pri.poke(0.U)
        dut.io.RdCmdFromFilter.bits.length.poke(0.U)
        dut.io.RdCmdFromFilter.valid.poke(true.B)
        dut.io.RdIsToAS.poke(IsToAS.B)
        
        //dut.io.RdDataA2Filter.bits.rdata.expect(randomData1.U)
        //dut.io.RdDataC2Filter.bits.rdata.expect(randomData2.U)   
        
        
        // write channel with filter
        dut.io.WrCmdFromFilter.bits.token.poke(token.U)
        dut.io.WrCmdFromFilter.bits.addr.poke(addr.U)
        dut.io.WrCmdFromFilter.bits.pri.poke(0.U)
        dut.io.WrCmdFromFilter.bits.length.poke(0.U)
        dut.io.WrCmdFromFilter.valid.poke(true.B)
        dut.io.WrIsToAS.poke(IsToAS.B)
        dut.io.WrDataFromFilter.bits.wdata.poke(randomData1.U)
        dut.io.WrDataFromFilter.bits.wtoken.poke(token.U)
        dut.io.WrDataFromFilter.valid.poke(true.B)

        // read cahnnel with Cache
        dut.io.RdCmd2Cache.ready.poke(true.B)
        if(dut.io.RdCmd2Cache.valid.peekValue().asBigInt == 1) {
          dut.io.RdCmd2Cache.bits.token.expect(token.U)
          println(f"token right!")
          dut.io.RdCmd2Cache.bits.rank.expect(rank.U)
          println(f"rank right!")
          dut.io.RdCmd2Cache.bits.row.expect(row.U)
          println(f"row right!")
          dut.io.RdCmd2Cache.bits.col.expect(col.U)
           println(f"col right!")
          dut.io.RdCmd2Cache.bits.bank.expect(bank.U)
           println(f"bank right!")
          dut.io.RdCmd2Cache.bits.bg.expect(bg.U)
           println(f"bg right!")
        }
        
        /*
        if ((i%2) == 0) {
          dut.io.RdCmd2Cache.bits.token.expect(token.U)
          dut.io.RdCmd2Cache.bits.rank.expect(rank.U)
          dut.io.RdCmd2Cache.bits.row.expect(row.U)
          dut.io.RdCmd2Cache.bits.col.expect(col.U)
          dut.io.RdCmd2Cache.bits.bank.expect(bank.U)
          dut.io.RdCmd2Cache.bits.bg.expect(bg.U)
        } else {
          dut.io.RdCmd2Cache.bits.token.expect(0.U)
          dut.io.RdCmd2Cache.bits.rank.expect(0.U)
          dut.io.RdCmd2Cache.bits.row.expect(0.U)
          dut.io.RdCmd2Cache.bits.col.expect(0.U)
          dut.io.RdCmd2Cache.bits.bank.expect(0.U)
          dut.io.RdCmd2Cache.bits.bg.expect(0.U)   
        }
        */
        dut.io.RdDataFromCache.valid.poke(true.B)
        dut.io.RdDataFromCache.bits.rdata.poke(randomData2.U)
        dut.io.RdDataFromCache.bits.rtoken.poke(token.U)
        dut.io.RdDataFromCache.bits.rend.poke(false.B)
        // write cahnnel with Cache
        dut.io.WrCmd2Cache.ready.poke(true.B)
        dut.io.WrData2Cache.ready.poke(true.B)

        // read channel with AS
        dut.io.RdCmd2AS.ready.poke(true.B)
        dut.io.RdDataFromAS.valid.poke(true.B)
        dut.io.RdDataFromAS.bits.rdata.poke(randomData1.U)
        dut.io.RdDataFromAS.bits.rtoken.poke(token.U)
        dut.io.RdDataFromAS.bits.rend.poke(false.B)
        // write channel with AS
        dut.io.WrCmd2AS.ready.poke(true.B)
        dut.io.WrData2AS.ready.poke(true.B)

        
        dut.clock.step()
      }
    }
  }
}
