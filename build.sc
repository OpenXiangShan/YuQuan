/***************************************************************************************
* Copyright (c) 2024-2025 Institute of Computing Technology, Chinese Academy of Sciences
* Baiyang is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
* See the Mulan PSL v2 for more details.
****************************************************************************************/
// import Mill dependency
import mill._
import mill.define.Sources
import mill.modules.Util
import mill.scalalib.TestModule.ScalaTest
import mill.scalalib.scalafmt.ScalafmtModule
import scalalib._
// support BSP
import mill.bsp._

val pwd = os.Path(sys.env("MILL_WORKSPACE_ROOT"))

trait CommonModule extends SbtModule with ScalafmtModule {
  override def millSourcePath = pwd
  override def scalaVersion = "2.13.12"
  override def scalacOptions = Seq(
    "-language:reflectiveCalls",
    "-deprecation",
    "-feature",
    "-Xcheckinit",
  )
  override def ivyDeps = Agg(
    ivy"org.chipsalliance::chisel:6.2.0",
  )
  override def scalacPluginIvyDeps = Agg(
    ivy"org.chipsalliance:::chisel-plugin:6.2.0",
  )
}

object OpenMc extends CommonModule { m =>
  override def mainClass = Some("Main")
  object test extends SbtModuleTests with TestModule.ScalaTest with ScalafmtModule {
    override def ivyDeps = m.ivyDeps() ++ Agg(
      ivy"org.scalatest::scalatest::3.2.16"
    )
  }
}
