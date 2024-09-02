package dev.slimevr.hardware.usb

import java.util.logging.Logger

class USBDmesgWatcher(
    private val notifyUSB: USBNotify
): Thread("USB Map Dmesg watcher") {

    val logger: Logger = Logger.getLogger("USBDmesgWatcher")
    private val usbAttachedRegex = "\\[ *\\d+\\.\\d+] .*usb ([^:]+): .* now attached to (ttyUSB\\d+)".toRegex()
    private val usbDetachedRegex = "\\[ *\\d+\\.\\d+] .* now disconnected from (ttyUSB\\d+)".toRegex()
    private val currentUSBMap = mutableMapOf<String,String>()
    private val reverseUSBMap = mutableMapOf<String,String>()

    override fun run() {
        val processBuilder = ProcessBuilder("dmesg","-W","--color=never").redirectErrorStream(true).redirectOutput(
            ProcessBuilder.Redirect.PIPE
        )
        val process = processBuilder.start()
        val inputReader = process.inputReader()
        while(true) {
            val line = inputReader.readLine() ?: break
            if(line.contains("now attached to tty")) {
                val res1 = usbAttachedRegex.matchEntire(line)
                if(res1 == null) {
                    logger.warning("Can't parse dmesg line: $line")
                } else {
                    synchronized(this) {
                        currentUSBMap[res1.groupValues[1]] = res1.groupValues[2]
                        reverseUSBMap[res1.groupValues[2]] = res1.groupValues[1]
                    }
                    notifyUSB.setUSB(res1.groupValues[1], res1.groupValues[2])
                }
            }
            if(line.contains("now disconnected from tty")) {
                val res1 = usbDetachedRegex.matchEntire(line)
                if(res1 == null) {
                    logger.warning("Can't parse dmesg line: $line")
                } else {
                    val addr = synchronized(this) {
                        reverseUSBMap[res1.groupValues[1]]
                    }
                    if(addr == null) {
                        logger.warning("Disconnected not connected usb: $line")
                    } else {
                        synchronized(this) {
                            reverseUSBMap.remove(res1.groupValues[1])
                            currentUSBMap.remove(addr)
                        }
                        notifyUSB.setUSB(addr, "")
                    }
                }
            }
        }
    }
}
