#!/bin/bash

cd /home/rpi4/panel_tester
sudo \
TESTER_RPC_URL=https://board-tester.slimevr.io/api/rpc \
TESTER_RPC_PASSWORD= \
TESTER_REPORT_TYPE=panel-butterfly-devtest1 \
TESTER_NAME=devtest \
TESTER_STAGE=5 \
TESTER_FIRMWARE_FILE=butterfly_v1_full_056219c2_202610051125.hex \
TESTER_FIRMWARE_COMMIT=056219c2 \
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005 -cp "*" dev.slimevr.testing.Main
