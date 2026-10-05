#!/bin/bash

cd /home/rpi4/panel_tester
ITF="cmsis-dap usb interface $2"
PROG="init;program $3 verify;reset;exit"
GDB="gdb port $4"
echo sudo openocd -f "$1" -c "$GDB" -c "$ITF" -c "$PROG"
sudo openocd -f "$1" -c "$GDB" -c "$ITF" -c "$PROG"
