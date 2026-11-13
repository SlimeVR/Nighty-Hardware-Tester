package dev.slimevr.hardware.spi

import com.pi4j.io.spi.Spi

class MCP3008(
    val spi: Spi
) {
    val buffer: ByteArray = byteArrayOf(0, 0, 0)

    fun readChannel(index: Int): Int {
        return readChannel(index, false)
    }

    fun readDifferentialChannel(index: Int): Int {
        return readChannel(index, true)
    }

    private fun readChannel(index: Int, differential: Boolean): Int {
        require(index in 0..7) { "Invalid channel index" }

        buffer[0] = 1
        buffer[1] = (index shl 4).toByte()
        if (!differential)
            buffer[1] = (0x80 or buffer[1].toInt()).toByte()

        spi.transfer(buffer, buffer)

        return ((buffer[1].toInt() and 0x03) shl 8) or (buffer[2].toInt() and 0xff)
    }
}
