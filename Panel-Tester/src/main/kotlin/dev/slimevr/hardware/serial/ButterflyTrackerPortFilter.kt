package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.hardware.serial.PortFilter

class ButterflyTrackerPortFilter: PortFilter {

    override fun isValidPort(port: SerialPort): Boolean {
        if(!port.systemPortPath.startsWith("/dev/ttyACM"))
            return false
        arrayOf("butterfly").forEach {
            if(port.descriptivePortName.lowercase().contains(it) && !port.descriptivePortName.lowercase().contains("bluetooth"))
                return true
        }
        return false
    }
}
