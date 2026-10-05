#!/bin/bash

cd ~/panel_tester
screen -m -S tester \
	sudo \
	TESTER_RPC_URL=https://board-tester.slimevr.io/api/rpc \
	TESTER_RPC_PASSWORD= \
	TESTER_REPORT_TYPE=btf-trackers-s1 \
	TESTER_NAME=butterfly-tester-1 \
	TESTER_STAGE=5 \
	TESTER_FIRMWARE_FILE=butterfly_v1_full_056219c2_202610051125.hex \
	TESTER_FIRMWARE_COMMIT=056219c2 \
	java -cp "*" dev.slimevr.testing.Main 
