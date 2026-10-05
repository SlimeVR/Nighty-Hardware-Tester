#!/bin/bash

cd ~/panel_tester
#screen -m -S tester \
	sudo \
	TESTER_STAGE=555 \
	java -cp "*" dev.slimevr.testing.Main
