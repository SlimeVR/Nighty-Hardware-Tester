package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort

interface SerialPortChecker {
    fun isValidPort(port: SerialPort): Boolean
}
