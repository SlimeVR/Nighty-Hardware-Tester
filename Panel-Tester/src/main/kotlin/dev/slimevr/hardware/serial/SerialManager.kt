package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class SerialManager {

    val openPorts = ArrayList<SerialPort>()

    fun findNewPorts(): List<SerialPort> {
        return SerialPort.getCommPorts().filter { !openPorts.contains(it) }
    }

    fun openPort(port: SerialPort, listener: SerialPortMessageListener) {
        port.openPort(200)
        port.addDataListener(listener)
        openPorts.add(port)
    }

    fun closeAllPorts() {
        openPorts.forEach { it.closePort() }
        openPorts.clear()
    }
}
