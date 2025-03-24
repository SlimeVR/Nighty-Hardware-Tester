package dev.slimevr.hardware.usb

import java.util.logging.Logger

class USBDmesgWatcher(
    private val notifyUSB: USBNotify
): Thread("USB Map Dmesg watcher") {

    val logger: Logger = Logger.getLogger("USBDmesgWatcher")
    private val usbAttachedRegexes = arrayOf(
        "\\[ *\\d+\\.\\d+] .*usb ([^:]+): .* now attached to (ttyUSB\\d+)".toRegex(),
        "\\[ *\\d+\\.\\d+] cdc_acm ([^:]+):[^:]+: (ttyACM\\d+).*".toRegex()
    )
    private val usbDetachedRegexesTTY = arrayOf(
        "\\[ *\\d+\\.\\d+] .* now disconnected from (ttyUSB\\d+)".toRegex()
    )
    private val usbDetachedRegexesAddr = arrayOf(
        "\\[ *\\d+\\.\\d+] usb ([^:]+): USB disconnect.*".toRegex()
    )
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
            logger.info("[DMESG] $line")
            for(usbAttachedRegex in usbAttachedRegexes) {
                val res1 = usbAttachedRegex.matchEntire(line)
                if(res1 != null) {
                    val addr = res1.groupValues[1]
                    val tty = res1.groupValues[2]
                    synchronized(this) {
                        currentUSBMap[addr] = tty
                        reverseUSBMap[tty] = addr
                    }
                    notifyUSB.setUSB(addr, tty)
                }
            }
            for(usbDetachedRegex in usbDetachedRegexesTTY) {
                val res1 = usbDetachedRegex.matchEntire(line)
                if(res1 != null) {
                    val tty = res1.groupValues[1]
                    val addr = synchronized(this) {
                        reverseUSBMap[tty]
                    }
                    if(addr == null) {
                        logger.warning("Disconnected not connected usb: $line")
                    } else {
                        synchronized(this) {
                            reverseUSBMap.remove(tty)
                            currentUSBMap.remove(addr)
                        }
                        notifyUSB.setUSB(addr, "")
                    }
                }
            }
            for(usbDetachedRegex in usbDetachedRegexesAddr) {
                val res1 = usbDetachedRegex.matchEntire(line)
                if(res1 != null) {
                    val addr = res1.groupValues[1]
                    val tty = synchronized(this) {
                        currentUSBMap[addr]
                    }
                    if(tty == null) {
                        logger.warning("Disconnected not connected usb: $line")
                    } else {
                        synchronized(this) {
                            reverseUSBMap.remove(tty)
                            currentUSBMap.remove(addr)
                        }
                        notifyUSB.setUSB(addr, "")
                    }
                }
            }
        }
    }
}
