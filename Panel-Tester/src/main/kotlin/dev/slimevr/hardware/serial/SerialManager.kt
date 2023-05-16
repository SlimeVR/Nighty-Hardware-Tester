package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class SerialManager {

    val knownPorts = mutableListOf<SerialPort>()

    fun findNewPorts(): List<SerialPort> {
        return SerialPort.getCommPorts().filter { !knownPorts.contains(it) and isValidPort(it) }
    }

    fun openPort(port: SerialPort, listener: SerialPortMessageListener) {
        port.openPort(200)
        port.addDataListener(listener)
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

    fun isValidPort(port: SerialPort) =  port.portDescription in arrayOf("ch340", "cp21", "ch910", "usb", "seri")
}
