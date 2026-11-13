package dev.slimevr.hardware.i2c

import com.pi4j.io.i2c.I2C
import com.pi4j.io.i2c.I2CRegister

const val MCP23017_REG_IODIR = 0x00 // I/O Direction: 1 - Input (default), 0 - output
const val MCP23017_REG_IPOL = 0x01 // Input polarity: 0 - normal (default), 1 - opposite
const val MCP23017_REG_GPINTEN = 0x02 // Interrupt on change: 0 - disabled (default), 1 - enabled
const val MCP23017_REG_DEFVAL = 0x03 // Default value: 0 - low (default), 1 - high
const val MCP23017_REG_INTCON = 0x04 // Interrupt on change control: 0 - compare to previous value (default), 1 - compare to DEFVAL
const val MCP23017_REG_IOCON = 0x05 // I/O Configuration
const val IOCON_BANK: Int = 0x80 // Bank addressing: 0 - interlaced (default), 1 - segregated/separated (PORTA: 00h - 0Ah, PORTB: 10h - 1Ah)
const val IOCON_MIRROR: Int = 0x40 // INT mirroring: 0 - separated (default), 1 - connected/duplicated
const val IOCON_SEQOP: Int = 0x20 // Sequential op mode: 0 - enabled (default), 1 - disabled
const val IOCON_DISSLW: Int = 0x10 // Slew rate for SDA: 0 - enabled (default), 1 - disabled
const val IOCON_ODR: Int = 0x04 // INT Open-Drain: 0 - full push-pull (default), 1 - open drain (override INTPOL=0)
const val IOCON_INTPOL: Int = 0x02 // INT polatory: 0 - active low (default), 1 - active high (only when ODR=0)

const val MCP23017_REG_GPPU = 0x06 // Pull-up resistors: 0 - disabled (default), 1 - enabled
const val MCP23017_REG_INTF = 0x07 // Interrupt flag (RO): 0 - default, 1 - flagged
const val MCP23017_REG_INTCAP = 0x08 // Interrupt captured value (RO)

const val MCP23017_REG_GPIO = 0x09 // GPIO Port (RW)
const val MCP23017_REG_OLAT = 0x0A // Output latch (RW)

class MCP23017(
    val device: I2C
) {
    private val bankMode: Int = 0

    init {
        writeRegister(MCP23017_REG_IOCON, IOCON_MIRROR or IOCON_ODR)
    }

    private fun getRegister(address: Int): I2CRegister = device.getRegister(2 * address)

    /**
     * Write 16-bit to unbanked registers:
     * A - lower byte, B - higher byte
     */
    private fun writeRegister(reg: Int, value: Int) {
        if(reg == MCP23017_REG_IOCON) {
            if(value and IOCON_BANK != 0)
                require(false) { "Banked mode not supported" }
        }
        val byteLow = (value and 0xFF).toByte()
        val byteHigh = (value ushr 8).toByte()
        device.writeRegister(2 * reg, byteLow)
        if(reg != MCP23017_REG_IOCON) {
            device.writeRegister(2 * reg + 1, byteHigh)
        }
    }

    /**
     * Read 16-bit from unbanked registers: A - lower byte, B - higher byte
     */
    private fun readRegister(reg: Int): Int {
        val a = device.readRegister(2 * reg)
        val b = device.readRegister(2 * reg + 1)
        return a or (b shl 8)
    }

    /**
     * 16-bit mask (0xBBAA)
     * 1 - Input (default), 0 - Output
     */
    fun setDir(mask: Int) = writeRegister(MCP23017_REG_IODIR, mask)

    /**
     * 16-bit mask (0xBBAA)
     * 0 - Disabled (default), 1 - Enabled
     */
    fun setPullUps(mask: Int) = writeRegister(MCP23017_REG_GPPU, mask)

    /**
     * 16-bit mask (0xBBAA)
     */
    fun writePort(mask: Int) = writeRegister(MCP23017_REG_GPIO, mask)

    /**
     * Returns 16-bit mask (0xBBAA)
     */
    fun readPort(): Int = readRegister(MCP23017_REG_GPIO)

    fun dumpAllRegs() {
        for(reg in 0..10) {
            val v = readRegister(reg)
            val r = String.format("[%02xh]", reg)
            val s = String.format("%04X", v)
            println("$r=$s")
        }
    }
}
