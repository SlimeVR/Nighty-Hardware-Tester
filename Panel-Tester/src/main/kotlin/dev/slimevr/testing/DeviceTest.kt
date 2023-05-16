package dev.slimevr.testing

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortEvent
import com.fazecast.jSerialComm.SerialPortMessageListener
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.*

class DeviceTest(
    var deviceNum: Int
): SerialPortMessageListener {

    var deviceId = ""
    var testStatus = TestStatus.TESTING
    val testsList = mutableListOf<TestResult>()
    val startTime = System.currentTimeMillis()
    var endTime = -1L
    var serialPort: SerialPort? = null
    var serialLog = mutableListOf<String>()
    var serialDisconnected = false

    /**
     * If the device test should be saved to the database
     * at the end of the testing process
     */
    var commitDevice = false

    fun addTestResult(result: TestResult) {
        testsList.add(result)
        if(result.status == TestStatus.ERROR) {
            testStatus = TestStatus.ERROR
        }
    }

    fun endTest() {
        endTime = System.currentTimeMillis()
    }

    override fun serialEvent(event: SerialPortEvent?) {
        if (event!!.eventType == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            val newData = event.receivedData
            val s: String = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(newData)).toString()
            serialLog.add(s)
        } else if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            serialDisconnected = true // Mark as disconnected, it will be checked and erred by the test
        }
    }

    override fun getListeningEvents() = (SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
        or SerialPort.LISTENING_EVENT_DATA_RECEIVED)


    override fun getMessageDelimiter() = byteArrayOf(0x0A)

    override fun delimiterIndicatesEndOfMessage() = true
}
