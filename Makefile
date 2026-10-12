ifeq ($(OS), Windows_NT)
    detected_OS := Windows
else
    detected_OS := $(shell uname -s)
endif

ifeq ($(detected_OS), Windows)
	MILL := ./millw
else ifeq ($(detected_OS), Linux)
	MILL := mill
else
	MILL := mill
endif

TARGET_DIR := ./build

ifeq ($(origin DISABLE_SPLIT_VERILOG),command line)
    CIRCT_FLAGS := --target-dir=$(TARGET_DIR)
    SPLIT_STATUS := disabled
else
    CIRCT_FLAGS := --target-dir=$(TARGET_DIR) --split-verilog
    SPLIT_STATUS := enabled
endif
bsp:
	@$(MILL) -i mill.bsp.BSP/install

verilog:
	@$(MAKE) _verilog

verilog-nosplit:
	@$(MAKE) _verilog DISABLE_SPLIT_VERILOG=1
	
_verilog: $(SOURCES) build.sc
ifneq ($(DISABLE_SPLIT_VERILOG),)
	@echo Generate verilog files without split-verilog
	@$(MILL) -i OpenMc.run --target-dir=$(TARGET_DIR)
else
	@echo Generate verilog files with split-verilog
	@$(MILL) -i OpenMc.run --target-dir=$(TARGET_DIR) --split-verilog
endif

clean:
	@echo remove build files
	@rm -rfv $(TARGET_DIR)

.PHONY: verilog clean reformat checkformat test testOnly


