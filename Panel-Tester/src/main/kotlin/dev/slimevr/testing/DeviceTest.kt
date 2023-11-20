package dev.slimevr.testing

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortEvent
import com.fazecast.jSerialComm.SerialPortMessageListener
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.logging.Logger


class DeviceTest(
    var deviceNum: Int
): SerialPortMessageListener {

    val logger: Logger = Logger.getLogger("devices")

    var deviceId = ""
    var testStatus = TestStatus.TESTING
    val testsList = mutableListOf<TestResult>()
    val startTime = System.currentTimeMillis()
    var endTime = -1L
    var serialPort: SerialPort? = null
    val serialLog = mutableListOf<String>()
    var serialDisconnected = false
    var serialLogRead = 0
    var ipAddress: String? = null

    /**
     * If the device test should be saved to the database
     * at the end of the testing process
     */
    var commitDevice = false
    var flashingRequired = true

    fun addTestResult(result: TestResult) {
        testsList.add(result)
        if(result.status == TestStatus.ERROR) {
            testStatus = TestStatus.ERROR
        }
    }

    fun endTest() {
        endTime = System.currentTimeMillis()
    }

    fun sendSerialCommand(command: String): Boolean {
        if(serialPort == null)
            return false
        with(serialPort!!) {
            val writer = OutputStreamWriter(outputStream, "UTF-8")
            try {
                writer.append(command).append("\n")
                writer.flush()
                flushIOBuffers()
                synchronized(serialLog) {
                    serialLog.add("-> $command")
                }
                logger.info("[${deviceNum+1}] Serial: -> $command")
            } catch (e: Throwable) {
                e.printStackTrace()
                return false
            }
        }
        return true
    }

    override fun serialEvent(event: SerialPortEvent?) {
        if (event!!.eventType == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            val newData = event.receivedData
            val lines = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(newData)).toString()
                .replace("[^a-zA-Z0-9_\\-\\[\\]()*., \n\r:'\"]".toRegex(), "*").split('\n')
            val nonBlank = lines.filter { s -> s.isNotBlank() }
            synchronized(serialLog) {
                serialLog.addAll(nonBlank)
            }
            for(line in nonBlank)
                    logger.info("[${deviceNum+1}] Serial: $line")
        } else if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            synchronized(serialLog) {
                if(serialDisconnected)
                    return
                serialDisconnected = true // Mark as disconnected, it will be checked and erred by the test
            }
            logger.info("[${deviceNum+1}] Serial disconnected")
        } else {
            logger.info("[${deviceNum+1}] Serial event ${event!!.eventType}")
        }
    }

    fun clearSerialLog() {
        synchronized(serialLog) {
            serialLogRead = 0
            serialLog.clear()
        }
    }

    override fun getListeningEvents() = (SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
        or SerialPort.LISTENING_EVENT_DATA_RECEIVED)


    override fun getMessageDelimiter() = byteArrayOf(0x0A)

    override fun delimiterIndicatesEndOfMessage() = true
}
