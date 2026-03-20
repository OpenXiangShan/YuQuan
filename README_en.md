# YuQuan Project
A Tape-Out-Targeted DDR3/DDR4/DDR5 Memory Controller and Its Agile Development and Verification Toolchain

中文说明[在此](https://github.com/OpenXiangShan/YuQuan/blob/DDR4_Baiyang_V0.8/README_zh.md).

- [YuQuan Project](#yuquan-project)
  - [Introduction](#introduction)
  - [Open-Source Status](#open-source-status)
  - [Quick Start Guide](#quick-start-guide)
    - [DDR4 Baiyang-V0.8 generates Verilog](#ddr4-baiyang-v08-generates-verilog)
    - [DDR4 Baiyang-V0.8 FPGA platform minimal test environment](#ddr4-baiyang-v08-fpga-platform-minimal-test-environment)
  - [Open-Source Roadmap](#open-source-roadmap)
    - [FAMSE](#famse)
    - [Other Tools](#other-tools)


## Introduction

This project aims to develop an open-source DDR3/4/5 memory controller in Chisel, providing the XiangShan open-source ecosystem with a high-performance，open-source and continuously evolving memory controller IP with a full-stack, systematic tool suite for agile development and verification.

The memory controller IP has been meticulously engineered with the following features:
1. **Architecturally parameterizable design** – supporting configurable structural parameters for flexible customization
2. **Decoupled runtime parameters from controller functionality** – enabling modular decomposition and independent optimization
3. **Modular integration of performance-enhancing components** – incorporating cache, prefetching, filtering, and scheduling modules to support diverse performance optimization strategies.

The project also features a suite of agile verification tools:
1. **Memory Controller Simulator (MCSim)** – We developed a timing-precise, RTL-aligned simulator to accelerate memory controller evolution and architectural exploration.
2. **FPGA-Based and Cycle-Accurate Memory System Emulator Utilizing Real DRAM  (FAMSE)** – This tool mitigates core-to-memory frequency asymmetry, enabling realistic performance evaluation prior to tape-out. 
3. **Outer Trace Tester (OTT)** – This tool enables high-speed replay of full memory traces from real applications, supporting full-speed pressure testing of memory controllers.
4. **Deterministic Memory Address Replayer (DMAR)** – This tool enables deterministic CPU memory trace acquisition across multiple platforms with comparative analysis capabilities.

The DDRx Memory Controller and its Agile Verification Suite are shown in Figure 1.Some tools are open-sourced now (such as MCSim, TinyPHY), while others are coming soon(FAMSE, OTT, DMAR, etc). See Chapter 4 for the full roadmap.

<figure>
<img src="./doc/pics/1.png"
style="width:7.65027in;height:4in" />
<figcaption><p>Figure 1 YuQuan: DDRx Memory Controller & Agile Development and Evaluation Suite</p></figcaption>
</figure>

## Open-Source Status

The current version of DDR4 Baiyang-V0.8 code has been deployed on FPGA and has passed the full speed stress test with full memory traces from SPEC CPU2006 benchmark test (ref, int+fp). 

DDR4 Baiyang-V0.8 IP supports:
<p>1.AXI4 bus interface protocol</p>
<p>2.DFI3.1 PHY bus interface protocol</p>
<p>3.DDR4-2400</p>
For details, refer to Baiyang IP Design Document-v8.0. 

The upcoming DDR4 Baiyang-V0.9 release has been integrated with the Xiangshan Kunminghu-V2 core on the Cadence Palladium Z2 emulation platform. Based on SPEC CPU2006 benchmark testing (ref, int+fp), the evaluated performance reaches 14 points/GHz, approaching the performance level of commercial memory controller IP. This release is expected within 3 months. We also plan to release Testing Environment with Xiangshan Nanhu core.</p>

The currently open-sourced agile development and verification tools include MCSim and TinyPHY. 

<p>MCSim is a highly parameterizable memory controller simulator supporting diverse architectural configurations and comprehensive performance profiling. Its key metrics closely match those of DDR4 Baiyang-V0.8 under identical SPEC trace conditions.</p>

TinyPHY emulates PHY functionality on FPGA platforms, correctly handling DFI interface read/write requests to enable modular verification of the memory controller independent of physical PHY.

For detailed documentation and usage, refer to [TinyPhy](https://github.com/OpenXiangShan/MemoryTools/blob/master/TinyPHY/README.md) and [MCSim](https://github.com/OpenXiangShan/MCSim/blob/MCSim-v1.0/README.md).

## Quick Start Guide
### DDR4 Baiyang-V0.8 generates Verilog
<p>Run "make verilog" to generate verilog code.This command will generate multiple .sv files in the build/ directory.</p>
<p>See the Makefile for more information.</p>

### DDR4 Baiyang-V0.8 FPGA platform minimal test environment
<p>To facilitate deployment of DDR4 Baiyang-V0.8 on FPGA platforms, we constructed a minimal test environment integrating a MicroBlaze core, the Baiyang IP and TinyPHY —— a programmable PHY simulator.</p>
<p>For more information, see the .md file and Makefile of the Release version DDR4-Baiyang-fpga-V1.0.tar.gz or DDR4-Baiyang-fpga-V1.0.zip.</p>

## Open-Source Roadmap

### FAMSE
FAMSE is scheduled for open-source release on July 1, 2026.

Through protocol conversion, cross-clock-domain signal processing, and storage medium command management, FAMSE supports the DFI PHY protocol, multi-frequency simulation, and cycle-accurate memory access timing. It addresses two critical challenges:

1.The lack of DFI PHY interface IP on FPGA platforms.

2.The frequency asymmetry in FPGA-based verification, where the CPU frequency is scaled down while SDRAM operates at full speed, leading to inaccurate CPU performance measurements.

### Other Tools
Other Tools, such as OTT, will release in H2 2026. We also have plan to release some Test Traces in H2 2026. Moreover, we plan to develop more tools like DFI-master to accelerate evaluation. 

We welcome researchers and developers interested in building memory controller research platforms to join us in cultivating an open-source memory controller ecosystem.
