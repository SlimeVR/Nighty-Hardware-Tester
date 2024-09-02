package dev.slimevr.hardware.usb

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.hardware.serial.SerialManager
import java.util.logging.Logger

class USBSerialWatcher(
    private val notifyUSB: USBNotify,
    private val serialManager: SerialManager
): Thread("USB Serial watcher") {

    val logger: Logger = Logger.getLogger("USBSerialWatcher")

    override fun run() {
        while(true) {
            val newPorts = serialManager.findNewPorts()
            if(newPorts.isNotEmpty()) {
                for(port in newPorts)
                    notifyUSB.setUSB(port.portLocation, port.systemPortName)
            }
            sleep(1000)
        }
    }
}
