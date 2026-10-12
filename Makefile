#***************************************************************************************
# Copyright (c) 2024-2025 Institute of Computing Technology, Chinese Academy of Sciences
# Baiyang is licensed under Mulan PSL v2.
# You can use this software according to the terms and conditions of the Mulan PSL v2.
# You may obtain a copy of Mulan PSL v2 at:
#          http://license.coscl.org.cn/MulanPSL2
# THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
# EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
# MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
# See the Mulan PSL v2 for more details.
#***************************************************************************************/
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

# SOURCES = $(wildcard **/*.scala)
# SOURCES := $(shell find src -name '*.scala')
TARGET_DIR := ./build
# TARGET  := $(TARGET_DIR)/xxx.sv
CIRCT_FLAGS := --target-dir=$(TARGET_DIR) --split-verilog

SCALA_FILES := $(wildcard src/main/scala/*.scala)
help:
	@echo ${SCALA_FILES}

bsp:
	@$(MILL) -i mill.bsp.BSP/install

verilog: $(SOURCES) build.sc
	@echo Generate verilog files
	@$(MILL) -i OpenMc.run $(CIRCT_FLAGS)

#format scala files
reformat:
	@echo Formatting scala files
	@$(MILL) -i OpenMc.reformat
	@$(MILL) -i OpenMc.test.reformat

checkformat:
	@$(MILL) -i OpenMc.checkFormat

test:
	@echo + Run all test
	@$(MILL) -i OpenMc.test.test

# Usage: make testOnly testcase="OpenMc.ReadBackSpec"
testOnly:
	@echo Test only ${testcase}
	@$(MILL) -i OpenMc.test.testOnly ${testcase}

clean:
	@echo remove build files
	@rm -rfv $(TARGET_DIR)

.PHONY: verilog clean reformat checkformat test testOnly
