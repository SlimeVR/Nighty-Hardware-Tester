package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class ESPPortChecker: SerialPortChecker {

    override fun isValidPort(port: SerialPort): Boolean {
        if(!port.systemPortPath.startsWith("/dev/ttyUSB") && !port.systemPortPath.startsWith("\\\\.\\COM"))
            return false
        arrayOf("ch340", "cp21", "ch910", "usb", "seri").forEach {
            if(port.descriptivePortName.lowercase().contains(it) && !port.descriptivePortName.lowercase().contains("bluetooth"))
                return true
        }
        return false
    }
}
