package dev.slimevr.hardware.serial

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortMessageListener

class SerialManager(
    private val portChecker: SerialPortChecker = ESPPortChecker(),
    private val sendReset: Boolean = true
    ) {

    private val knownPorts = mutableListOf<SerialPort>()

    @Synchronized
    fun findNewPorts(): List<SerialPort> {
        return SerialPort.getCommPorts().filter { (knownPorts.count { p -> p.systemPortPath.equals(it.systemPortPath) } == 0) and portChecker.isValidPort(it) }
    }

    fun openPort(port: SerialPort, listener: SerialPortMessageListener): Boolean {
        port.setBaudRate(115200)
        if(sendReset) {
            port.clearRTS()
            port.clearDTR()
        }
        if(port.openPort(0)) {
            port.addDataListener(listener)
            return true
        }
        return false
    }
    @Synchronized
    fun markAsKnown(port: SerialPort) {
        knownPorts.add(port)
    }

    fun getKnownPorts() = knownPorts

    @Synchronized
    fun areKnownPortsConnected(): Boolean {
        return SerialPort.getCommPorts()
            .any { (knownPorts.count { p -> p.systemPortPath.equals(it.systemPortPath) } > 0) }
    }

    fun isPortConnected(port: SerialPort): Boolean {
        return SerialPort.getCommPorts().any { port.systemPortPath.equals(it.systemPortPath) }
    }


    fun closePort(port: SerialPort) {
        port.closePort()
        port.removeDataListener()
    }

    @Synchronized
    fun removePort(port: SerialPort) {
        knownPorts.remove(port)
    }

    @Synchronized
    fun closeAllPorts() {
        knownPorts.forEach { closePort(it) }
        knownPorts.clear()
    }
}
