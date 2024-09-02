package dev.slimevr.testing.extensions

import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.usb.USBDmesgWatcher
import dev.slimevr.hardware.usb.USBNotify
import java.util.logging.Logger
import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortEvent
import com.fazecast.jSerialComm.SerialPortMessageListener
import dev.slimevr.OperatingSystem
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.hardware.usb.USBSerialWatcher
import dev.slimevr.testing.TestStatus
import dev.slimevr.ui.extensions.ExtensionsUpdaterUI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class ExtensionsPanelTestingSuite(
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi:ExtensionsUpdaterUI,
    private val devices: Int,
    private val logger: Logger,
    private var deviceLoggers: Array<Logger?>
) : Thread("Testing suit thread"), USBNotify {

    private val serialManager = SerialManager()
    private var leftHalf: SerialPort? = null
    private var rightHalf: SerialPort? = null
    private var lineRegex = ".*\\[(\\d)].*".toRegex()

    override fun run() {
        if(OperatingSystem.getCurrentPlatform() == OperatingSystem.LINUX) {
            USBDmesgWatcher(this).start()
        } else {
            USBSerialWatcher(this, serialManager).start()
        }
    }

    private fun parseDeviceLine(deviceId: Int, line: String) {
        deviceLoggers[deviceId]?.info(line
            .replace("[SlimeVR]","", true)
            .replace("[INFO]", "", true)
        )
        if(line.contains("Test passed")) {
            testerUi.setStatus(deviceId, TestStatus.PASS)
        } else if(line.contains("Test failed")) {
            testerUi.setStatus(deviceId, TestStatus.ERROR)
        } else if(line.contains("Scanning:")) {
            testerUi.setStatus(deviceId, TestStatus.TESTING)
        }
    }

    private fun clearDevices(right: Boolean) {
        for(i in 0 .. 9) {
            val device = toDevice(i, right)
            testerUi.clear(device)
        }
    }

    fun serialEvent(event: SerialPortEvent, right: Boolean) {
        val side = if(right) "RIGHT" else "LEFT"
        if (event.eventType == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            val newData = event.receivedData
            val lines = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(newData)).toString()
                .replace("[^a-zA-Z0-9_\\-\\[\\]()*., \n:'\"]".toRegex(), "*").split('\n')
            val nonBlank = lines.filter { s -> s.isNotBlank() }
            synchronized(this) {
                if (right && rightHalf == null)
                    return
                if (!right && leftHalf == null)
                    return
            }
            for(line in nonBlank) {
                if(line.contains("Scanning BNO085s..."))
                    clearDevices(right)
                val match = lineRegex.matchEntire(line)
                if(match != null) {
                    parseDeviceLine(toDevice(match.groupValues[1].toInt(), right), line)
                } else {
                    logger.info("[${side}] $line")
                }
            }
        } else if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            synchronized(this) {
                if(right && rightHalf == null)
                    return
                if(!right && leftHalf == null)
                    return
                if(right) {
                    rightHalf = null
                } else {
                    leftHalf = null
                }
            }
            if(right) {
                logger.info("[${side}] Right half disconnected")
            } else {
                logger.info("[${side}] Left half disconnected")
            }

        } else {
            logger.info("[${side}] Serial event ${event.eventType}")
        }
    }

    private fun toDevice(number: Int, isRight: Boolean): Int {
        if(number < 5) {
            if(isRight)
                return number + 5
            return number
        } else {
            if(isRight)
                return number + 10
            return number + 5
        }
    }

    override fun setUSB(addr: String, tty: String) {
        if(leftHalf == null) {
            leftHalf = SerialPort.getCommPort(tty)
            if(leftHalf != null) {
                serialManager.markAsKnown(leftHalf!!)
                serialManager.openPort(leftHalf!!, object: SerialPortMessageListener{
                    override fun serialEvent(event: SerialPortEvent?) {
                        if(event != null)
                            serialEvent(event, false)
                    }
                    override fun getListeningEvents() = (SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                        or SerialPort.LISTENING_EVENT_DATA_RECEIVED)
                    override fun getMessageDelimiter() = byteArrayOf(0x0A)
                    override fun delimiterIndicatesEndOfMessage() = true
                })
                logger.info("Left half connected on $addr / $tty")
            } else {
                logger.info("Can't find port for new connected $addr / $tty")
            }
        } else if(rightHalf == null) {
            rightHalf = SerialPort.getCommPort(tty)
            if(rightHalf != null) {
                serialManager.markAsKnown(rightHalf!!)
                serialManager.openPort(rightHalf!!, object: SerialPortMessageListener{
                    override fun serialEvent(event: SerialPortEvent?) {
                        if(event != null)
                            serialEvent(event, true)
                    }
                    override fun getListeningEvents() = (SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
                        or SerialPort.LISTENING_EVENT_DATA_RECEIVED)
                    override fun getMessageDelimiter() = byteArrayOf(0x0A)
                    override fun delimiterIndicatesEndOfMessage() = true
                })
                logger.info("Right half connected on $addr / $tty")
            } else {
                logger.info("Can't find port for new connected $addr / $tty")
            }
        } else {
            logger.severe("Third device connected, can't continue: $addr / $tty")
        }
    }
}
