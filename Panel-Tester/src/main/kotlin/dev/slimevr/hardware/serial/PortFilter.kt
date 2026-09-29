package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort

interface PortFilter {
    fun isValidPort(port: SerialPort): Boolean
}
