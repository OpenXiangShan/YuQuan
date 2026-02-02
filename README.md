# YuQuan介绍

本项目拟基于 Chisel 开发开源 DDR3/4/5 内存控制器，向香山开源生态体系提供可自主演进的、开源共享的高性能内存控制器 IP 及相关测试验证工具。

当前版本 DDR4 Baiyang-V0.8 代码已在FPGA部署且经过SPEC CPU2006 基准测试（ref, int+float）Full Trace 满速压力测试。最新版本DDR4 Baiyang-V0.9 代码已在帕拉丁集成香山昆明湖-v2核测试，基于SPEC CPU2006 基准测试（ref, int+float）跑分评估可达 14分/GHz，接近商用MC IP性能水平，该版本拟于2026年春节后开源。

玉泉 (YuQuan) 项目主要包括：

1、开源高性能内存控制器 (Baiyang IP)

2、精准内存子系统模拟器

3、周期精准PHY仿真器 

 一、Baiyang IP
 
Baiyang是一种高性能内存控制器设计和实现方案。通过对地址映射、分流、系统缓存、调度、内存颗粒时序控制的功能划分和模块化处理，可实现自主演进。

当前版本 IP 支持：

AXI4 总线接口协议

DFI3.1 PHY总线接口协议

DDR4-2400

Baiyang的设计文档请参考本项目提供的设计文档。

二、精准内存子系统模拟器

MCSim是一个针对内存控制器的模拟器，高度参数化，可以模拟各种内存控制器配置，并输出各种性能指标。目前和 DDR4 Baiyang-V0.8 在相同SPEC trace上关键性能指标接近。

三、周期精准PHY仿真器
通过协议转换、跨时钟域信号处理、存储介质运行命令管理等方法，支持DFI PHY协议，支持多频点的模拟仿真，支持周期精准的访存时序，解决两个问题：

FPGA 平台缺少支持 DFI PHY 接口的 IP

FPGA 平台验证存在CPU降频 SDRAM芯片不降频的“频率倒挂”现象，导致CPU 性能测试结果不准确

拟2026年6月30日开源

其他测试验证工具参考：https://github.com/OpenXiangShan/MemoryTools

# DDR4 Baiyang-V0.8 生成 Verilog

运行 make verilog 以生成 verilog 代码。该命令会在 build/ 目录下生成多个 .sv 文件。

更多信息详见 Makefile。

# DDR4 Baiyang-V0.8 FPGA平台极简测试环境

为方便部署 DDR4 Baiyang-V0.8 到 FPGA平台，搭建了集成 microblaze核、Baiyang IP 和 可编程PHY模拟器 (TinyPHY) 的极简测试环境。

更多信息详见 Release版本 DDR4-Baiyang-fpga-V1.0.tar.gz 或 DDR4-Baiyang-fpga-V1.0.zip 的 .md 文件和Makefile。

