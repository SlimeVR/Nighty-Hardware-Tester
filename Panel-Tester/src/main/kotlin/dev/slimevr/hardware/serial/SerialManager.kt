package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class SerialManager {

    private val knownPorts = mutableListOf<SerialPort>()

    fun findNewPorts(): List<SerialPort> {
        return SerialPort.getCommPorts().filter { (knownPorts.count { p -> p.systemPortPath.equals(it.systemPortPath) } == 0) and isValidPort(it) }
    }

    fun openPort(port: SerialPort, listener: SerialPortMessageListener): Boolean {
        port.setBaudRate(115200)
        port.clearRTS()
        port.clearDTR()
        if(port.openPort(0)) {
            port.addDataListener(listener)
            return true
        }
        return false
    }

    fun markAsKnown(port: SerialPort) {
        knownPorts.add(port)
    }

    fun areKnownPortsConnected(): Boolean {
        return SerialPort.getCommPorts()
            .any { (knownPorts.count { p -> p.systemPortPath.equals(it.systemPortPath) } > 0) }
    }

    fun closePort(port: SerialPort) {
        port.closePort()
        port.removeDataListener()
    }

    fun closeAllPorts() {
        knownPorts.forEach { closePort(it) }
        knownPorts.clear()
    }

    private fun isValidPort(port: SerialPort): Boolean {
        if(!port.systemPortPath.startsWith("/dev/ttyUSB") && !port.systemPortPath.startsWith("\\\\.\\COM"))
            return false
        arrayOf("ch340").forEach {
            if(port.descriptivePortName.lowercase().contains(it))
                return true
        }
        return false
    }
}
