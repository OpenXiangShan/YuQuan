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
// import chisel3.util.Queue

case class RbItem(token: Int, data: Long)

class ReadBackSpec extends AnyFreeSpec with Matchers {
  "ReadBack module " in {
    simulate(new ReadBack(new RbParam)) { dut =>
      var count  = 0
      val queue  = scala.collection.mutable.Queue[DataBundle]()
      val random = new Random()

      //reset
      dut.reset.poke(true.B)
      dut.clock.step()
      dut.clock.step()
      dut.reset.poke(false.B)
      dut.clock.step()
      dut.clock.step()

      for (i <- 0 until 100000) {
        // gen random test
        println(f"cycle $i: count = $count")
        val randomToken = random.nextInt((1 << dut.mTokenLen))
        // 512b => 64B random data
        val randomData  = BigInt(random.nextBytes(64).map(b => f"$b%02x").mkString, 16)
        val randomRb    = (new DataBundle(tokenLen = dut.mTokenLen)).Lit(_.token -> randomToken.U, _.data -> randomData.U)
        val scgValid    = if (random.nextInt() % 2 == 0) false else true
        val cacheReady  = if (random.nextInt() % 2 == 0) false else true
        val filterReady = if (random.nextInt() % 2 == 0) false else true
        // println(f"$randomRb, $scgValid, $cacheReady, $filterReady, ${queue}")

        dut.io.dataFromScg.valid.poke(scgValid.B)
        dut.io.dataFromScg.bits.data.poke(randomRb.data)
        dut.io.dataFromScg.bits.token.poke(randomRb.token)
        dut.io.data2Cache.ready.poke(cacheReady.B)
        dut.io.data2Filter.ready.poke(filterReady.B)
        var nextCnt = count
        // enqueue
        if ((count != dut.mem.length) && scgValid) {
          println(f"enqueue $randomRb")
          queue += randomRb
          nextCnt += 1
        }
        // not empty
        if (0 != count) {
          val highest = 1 & (queue.front.token.litValue >> (dut.mTokenLen - 1))
          // dequeue
          val deqCache  = (1 == highest) && cacheReady
          val deqFilter = (0 == highest) && filterReady
          val deq       = deqCache | deqFilter
          dut.io.data2Cache.valid.expect((highest == 1).B)
          dut.io.data2Filter.valid.expect((highest == 0).B)
          if (deq) {
            println(f"dequeue ${queue.front}")
            if (deqCache) {
              dut.io.data2Cache.bits.data.expect(queue.front.data.litValue)
              dut.io.data2Cache.bits.token.expect(queue.front.token.litValue & 0xfff)
            }
            if (deqFilter) {
              dut.io.data2Filter.bits.data.expect(queue.front.data.litValue)
              dut.io.data2Filter.bits.token.expect(queue.front.token.litValue & 0x3ff)
            }
            nextCnt -= 1
            queue.dequeue()
          }
        }
        count = nextCnt
        dut.clock.step()
      }
    }
  }
}
