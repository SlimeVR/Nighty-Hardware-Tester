#!/bin/bash

cd /home/rpi4/panel_tester
ITF="cmsis-dap usb interface $2"
PROG="init;program $3 verify;reset;exit"
GDB="gdb port $4"
sudo openocd -d0 -f "$1" -c "$GDB" -c "$ITF" -c "$PROG"
