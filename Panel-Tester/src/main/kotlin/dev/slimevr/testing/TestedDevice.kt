package dev.slimevr.testing

import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPortEvent
import com.fazecast.jSerialComm.SerialPortMessageListener
import dev.slimevr.ui.Reporter
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.logging.Level
import java.util.logging.Logger


class TestedDevice(
    private val reporter: Reporter
): SerialPortMessageListener {

    var testStatus = TestStatus.DISCONNECTED

    fun fireError(error: String) {
        // Ignore errors if it's not in testing
        if(testStatus == TestStatus.TESTING) {

        }
    }

    fun startTesting() {

    }

    fun resetTesting() {

    }

    fun testingSuccess() {

    }

    override fun getListeningEvents(): Int {
        return (SerialPort.LISTENING_EVENT_PORT_DISCONNECTED
            or SerialPort.LISTENING_EVENT_DATA_RECEIVED)
    }

    override fun serialEvent(event: SerialPortEvent) {
        if (event.eventType == SerialPort.LISTENING_EVENT_DATA_RECEIVED) {
            val newData = event.receivedData
            val s: String = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(newData)).toString()
            reporter.log(Level.FINE, s)
        } else if (event.eventType == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
            reporter.log(Level.INFO, "Serial port disconnected")
            fireError("Serial port disconnected")
        }
    }

    override fun getMessageDelimiter(): ByteArray = byteArrayOf(0x0A)

    override fun delimiterIndicatesEndOfMessage(): Boolean = true

}

