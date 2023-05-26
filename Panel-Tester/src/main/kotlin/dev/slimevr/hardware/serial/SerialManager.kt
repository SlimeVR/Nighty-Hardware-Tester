package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class SerialManager {

    private val knownPorts = mutableListOf<SerialPort>()

    fun findNewPorts(): List<SerialPort> {
        return SerialPort.getCommPorts().filter { (knownPorts.count { p -> p.systemPortPath.equals(it.systemPortPath) } == 0) and isValidPort(it) }
    }

    fun openPort(port: SerialPort, listener: SerialPortMessageListener): Boolean {
        if(port.openPort(200)) {
            port.setBaudRate(115200)
            port.addDataListener(listener)
            return true
        }
        return false
    }

    fun markAsKnown(port: SerialPort) {
        knownPorts.add(port)
    }

    fun closePort(port: SerialPort) {
        port.closePort()
        knownPorts.remove(port)
    }

    fun closeAllPorts() {
        knownPorts.forEach { it.closePort() }
        knownPorts.clear()
    }

    private fun isValidPort(port: SerialPort): Boolean {
        if(!port.systemPortPath.startsWith("/dev/ttyUSB"))
            return false
        arrayOf("ch340", "cp21", "ch910", "usb", "seri").forEach {
            if(port.descriptivePortName.lowercase().contains(it))
                return true
        }
        return false
    }
}
