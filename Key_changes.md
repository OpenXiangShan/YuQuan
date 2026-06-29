## Key Changes 

### Highlights

- Added static virtual channel support in AXI2UI, enabling limited out-of-order response returns across multiple AXI IDs.
- Added write mask support in SCG.
- Merged the write command queue and write data queue in the Filter module, with corresponding UI write interface updates.
- Fixed SCG refresh issues, including the `tRP_timer` bug and the burst refresh bug.

### Detailed Changes

#### AXI2UI: Static Virtual Channels

- Introduced static virtual channel support in the AXI2UI module.
- Improved support for multiple AXI IDs.
- Enabled limited out-of-order response returns across different IDs.
- The maximum number of supported out-of-order IDs can be statically selected through `VirtualChannelNum` in `src/main/scala/top/parameter.scala`.

#### SCG: Write Mask Support

- Added support for write data masks in the SCG path.
- Updated related write data handling logic to carry and apply mask information.

#### Filter: Queue Merge

- Merged the write command queue and write data queue in the Filter module.
- Updated the UI write interface to match the merged queue structure.
- Simplified write-side command/data coordination.

#### SCG: Refresh Bug Fixes

- Fixed the `tRP_timer` issue in the refresh module.
- Fixed the burst refresh bug.
- Improved refresh behavior correctness under burst-related scenarios.