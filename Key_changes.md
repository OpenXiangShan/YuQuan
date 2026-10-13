## Key Changes

### DDR4 Baiyang-V1.0

#### Highlights

- Fixed the `tWTR_L` timing bug in TimingArb.
- Optimized the read data return path to reduce read latency.
- Added a timeout-based close-page policy, enabled through a register.
- Cache code is retained but not recommended to be enabled; the capacity is trimmed to 64 B. Cache will be updated in a later version.
- Upgraded mill to 0.12.3.

#### Detailed Changes

##### SCG/TimingArb: tWTR_L Fix

- The long write-to-read window (`tWTR_L`) was previously evaluated with a single `wtrlTimer` register shared by all ranks and bank groups, together with the bank group of the last write (`PreviousWrite`), which did not correspond to the request being checked.
- `wtrlTimer` is now a per rank+bank-group vector (`rankBgNum = 1 << (RANK_WIDTH + BGBITS)`), and the CAS timing check selects the entry for the target rank and bank group.
- The `PreviousWrite` register was removed. The read-after-write check on the same rank now requires both `wtrlTimer(rankBg)` and `wtrsTimer` to reach zero, and the same-rank and cross-rank read checks share this term.

##### Read Data Return Path: Latency Reduction

- Read data now goes straight from SCG to the AXI2UI read interface (`Scg2AxiRddata` to `u_axi2ui.io.ui_rio.dataFormScg`), instead of passing through the Scheduler `ReadBack` stage, AddrMap and Filter.
- Removed the `ReadBack` module and the `readBack` port from the Scheduler.
- Read data intended for the Cache is split off inside SCG on a dedicated port (`Scg2CacheRddata`) and delivered to the Cache wrapper.
- Dropping the intermediate stage shortens the read return path and reduces read latency.

##### SCG: Timeout Close-Page Policy

- `CommandGen` now supports a timeout-based close-page policy, selected through the new register fields of `SCGclspgStruct` in `src/main/scala/APB/Reg.scala`:
  - `prePolicy = 0`: open-page, never auto-precharge (default);
  - `prePolicy = 1`: static timeout close-page, the timer is reloaded from `clspgTmInit` on every CAS and counts down;
  - `prePolicy = 2`: dynamic close-page, reserved for a later version.
- `clspgTmInit` (9 bits) sets the initial timeout value.
- When the timer reaches zero and no same-row access is pending, the bank issues a precharge; these events are counted by `clspgZeroCnt`.
- A `pendingSameRowHit` signal from the Scheduler and the per-bank open row state (`openRowState`) keep the policy from closing a row while a request to the same row is still queued.
- Added command counters (`actCnt`, `preCnt`, `casCnt`, `clspgZeroCnt`) in `CmdGenStatis` for profiling.

##### Cache: Retained but Trimmed to 64 B

- The Cache code stays in the tree, but enabling it is not recommended for this version.
- The capacity is trimmed to 64 B (`TotalSize = 64`, `Ways = 1`, `Banks = 1` in `src/main/scala/Cache/CacheCommon.scala`), which leaves a single way and a single set.
- Cache is planned to be updated in a later version.

##### Build: mill 0.12.3

- Upgraded mill to 0.12.3 and added `.mill-version` to pin the version, so the launcher no longer queries the network for the latest one.
- Added `override def mainClass = Some("test")` in `build.sc`, because the mill 0.12 `run` target needs an explicit entry class.

### DDR4 Baiyang-V0.9 (previous release)

#### Highlights

- Added static virtual channel support in AXI2UI, enabling limited out-of-order response returns across multiple AXI IDs.
- Added write mask support in SCG.
- Merged the write command queue and write data queue in the Filter module, with corresponding UI write interface updates.
- Fixed SCG refresh issues, including the `tRP_timer` bug and the burst refresh bug.

#### Detailed Changes

##### AXI2UI: Static Virtual Channels

- Introduced static virtual channel support in the AXI2UI module.
- Improved support for multiple AXI IDs.
- Enabled limited out-of-order response returns across different IDs.
- The maximum number of supported out-of-order IDs can be statically selected through `VirtualChannelNum` in `src/main/scala/top/parameter.scala`.

##### SCG: Write Mask Support

- Added support for write data masks in the SCG path.
- Updated related write data handling logic to carry and apply mask information.

##### Filter: Queue Merge

- Merged the write command queue and write data queue in the Filter module.
- Updated the UI write interface to match the merged queue structure.
- Simplified write-side command/data coordination.

##### SCG: Refresh Bug Fixes

- Fixed the `tRP_timer` issue in the refresh module.
- Fixed the burst refresh bug.
- Improved refresh behavior correctness under burst-related scenarios.
