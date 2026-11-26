package dev.slimevr.hardware

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalOutputProvider
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.io.gpio.digital.DigitalOutputConfigBuilder
import com.pi4j.io.i2c.I2CProvider
import com.pi4j.io.i2c.I2CConfigBuilder
import com.pi4j.io.spi.SpiConfigBuilder
import com.pi4j.io.spi.SpiMode
import com.pi4j.io.spi.Spi
import com.pi4j.io.spi.SpiProvider
import dev.slimevr.hardware.i2c.MCP23017
import dev.slimevr.hardware.spi.MCP3008

class SwitchboardStage6(
    pi4j: Context,
    i2CProvider: I2CProvider? = null,
    spiProvider: SpiProvider? = null
) {
    companion object {
        const val LOW: Byte = 0
        const val HIGH: Byte = 1

        // GPIO pins mask
        const val A0 = 0x0001
        const val A1 = 0x0002
        const val A2 = 0x0004
        const val A3 = 0x0008
        const val A4 = 0x0010
        const val A5 = 0x0020
        const val A6 = 0x0040
        const val A7 = 0x0080
        const val B0 = 0x0100
        const val B1 = 0x0200
        const val B2 = 0x0400
        const val B3 = 0x0800
        const val B4 = 0x1000
        const val B5 = 0x2000
        const val B6 = 0x4000
        const val B7 = 0x8000

        const val GPIO1_3_DEFAULT = A0 or A1 or A3 or A4 or A6 or A7 or B1 or B2 or B4 or B5
        const val GPIO2_4_DEFAULT = B6 or B5 or B3 or B2 or B0 or A7 or A5 or A4 or A2 or A1
    }

    private var digitalOutputProvider: DigitalOutputProvider = pi4j.provider("pigpio-digital-output")

    private var csPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(18).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(17).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(16).provider("pigpio-digital-output").build()
    )

    private var swdRunPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.LOW).initial(DigitalState.LOW).address(7).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.LOW).initial(DigitalState.LOW).address(8).provider("pigpio-digital-output").build()
    )

    private var intPins = arrayOf(
        DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(22).provider("pigpio-digital-input").build(),
        DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(24).provider("pigpio-digital-input").build(),
        DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(26).provider("pigpio-digital-input").build(),
        DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(29).provider("pigpio-digital-input").build(),
    )

    private var rstPin : DigitalOutput =
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(4).provider("pigpio-digital-output").build()

    // ADC input has no filteing caps and sampling time is fixed by 1.5CLK time
    // Lower the SPI clock to have longer sampling time through 47k/20pF
    val ADC_SPI_BAUD = 100_000 // Spi.DEFAULT_BAUD
    val MCP3008_1: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(0).baud(ADC_SPI_BAUD).mode(SpiMode.MODE_0).build()))
    }
    val MCP3008_2: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(1).baud(ADC_SPI_BAUD).mode(SpiMode.MODE_0).build()))
    }
    val MCP3008_3: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(2).baud(ADC_SPI_BAUD).mode(SpiMode.MODE_0).build()))
    }

    val GPIO_1: MCP23017 by lazy {
        MCP23017(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(0x21).build()))
    }
    val GPIO_2: MCP23017 by lazy {
        MCP23017(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(0x22).build()))
    }
    val GPIO_3: MCP23017 by lazy {
        MCP23017(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(0x23).build()))
    }
    val GPIO_4: MCP23017 by lazy {
        MCP23017(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(0x24).build()))
    }

    init {
        // Reset GPIO (all to High-Z Inputs)
        rstPin.low()
        Thread.sleep(2) // Min 1uS
        rstPin.high()
        // Set EN_USB and EN_3V3 high for default OFF
        // Set EN_USB and EN_3V3 as outputs
        GPIO_1.setDefault(0xFFFF xor SwitchboardStage6.GPIO1_3_DEFAULT, SwitchboardStage6.GPIO1_3_DEFAULT)
        GPIO_2.setDefault(0xFFFF xor SwitchboardStage6.GPIO2_4_DEFAULT, SwitchboardStage6.GPIO2_4_DEFAULT)
        GPIO_3.setDefault(0xFFFF xor SwitchboardStage6.GPIO1_3_DEFAULT, SwitchboardStage6.GPIO1_3_DEFAULT)
        GPIO_4.setDefault(0xFFFF xor SwitchboardStage6.GPIO2_4_DEFAULT, SwitchboardStage6.GPIO2_4_DEFAULT)

        swd(false)
    }

    fun disableAll() {
        disableGPIO()
        swd(false)
    }

    fun disableGPIO() {
        // Do not reset GPIOs, only disable open drains outputs
        GPIO_1.writePort(SwitchboardStage6.GPIO1_3_DEFAULT)
        GPIO_2.writePort(SwitchboardStage6.GPIO2_4_DEFAULT)
        GPIO_3.writePort(SwitchboardStage6.GPIO1_3_DEFAULT)
        GPIO_4.writePort(SwitchboardStage6.GPIO2_4_DEFAULT)
    }

    fun swd(enable: Boolean) {
        if(enable) {
            // Set RUN pins high (SWD = 3v3, SWC = 0v)
            swd(0, HIGH)
            swd(1, HIGH)
        } else {
            // Switch off SWD (it will keep pins in floating state)
            swd(0, LOW)
            swd(1, LOW)
        }
    }

    fun swd(idx: Int, enable: Byte) {
        require(idx in 0..1)
        require(enable in LOW..HIGH)
        swdRunPins[idx].setState(enable)
    }

    fun Int.toHexString(): String = String.format("%02x", this)
    fun Int.bitClear(mask: Int): Int = this and mask.inv()
    fun Int.bitSet(mask: Int): Int = this or mask

    /**
     * Control device power
     */
    fun power(device: Int, pin: GpioPinMode, enable: Boolean) {
        require(pin in GpioPinMode.USB..GpioPinMode.V3V3)
        val gpio = mapDeviceToGpio(device)
        val usbMask = mapDeviceToGpioPin(device, GpioPinMode.USB)
        val v3v3Mask = mapDeviceToGpioPin(device, GpioPinMode.V3V3)
        val pinMask = if(pin == GpioPinMode.USB) usbMask else v3v3Mask
        var prevMask = gpio.getPreviousWritePort()
        if(pin == GpioPinMode.V3V3 && enable && (prevMask and usbMask) == 0) {
            // Disable 5V power first
            prevMask = gpio.writePort(prevMask or usbMask)
            Thread.sleep(100)
        }
        if(pin == GpioPinMode.USB && enable && (prevMask and v3v3Mask) == 0) {
            // Disable 3V3 power first
            prevMask = gpio.writePort(prevMask or v3v3Mask)
            Thread.sleep(100)
        }
        val mask = if(enable) {
            prevMask.bitClear(pinMask) // Assert EN active low
        } else {
            prevMask.bitSet(pinMask) // Disable EN signal
        }
        gpio.writePort(mask)
    }

    /**
     * Check 5V power overcurrent flag
     */
    fun is5vFaultAsserted(device: Int): Boolean {
        val gpio = mapDeviceToGpio(device)
        val pinMask = mapDeviceToGpioPin(device, GpioPinMode.FAULT)
        val portMask = gpio.readPort()
        return (portMask and pinMask == 0)
    }

    /**
     * Measure 3V3 bus voltage.
     */
    fun readADC(device: Int): Float {
        val ch = mapDeviceToAdcChannel(device)
        val adc = mapDeviceToAdc(device)
        return measureADC(adc, ch)
    }

    // GPIO EXTENDERS

    private fun mapDeviceToGpio(device: Int): MCP23017 {
        check(device in 0..20)
        return when(device) {
            0, 2, 4, 6, 8 -> GPIO_1
            1, 3, 5, 7, 9 -> GPIO_2
            10, 12, 14, 16, 18 -> GPIO_3
            11, 13, 15, 17, 19 -> GPIO_4
            else -> throw IllegalArgumentException("Invalid index {$device}")
        }
    }

    enum class GpioPinMode {
        USB,
        V3V3,
        FAULT
    }

    private fun mapDeviceToGpioPin(device: Int, pin: GpioPinMode): Int {
        require(device in 0..20)
        return when(device) {
            0, 10 -> when(pin) {
                GpioPinMode.USB -> A0
                GpioPinMode.V3V3 -> A1
                GpioPinMode.FAULT -> A2
            }
            1, 11 -> when(pin) {
                GpioPinMode.USB -> B6
                GpioPinMode.V3V3 -> B5
                GpioPinMode.FAULT -> B4
            }
            2, 12 -> when(pin) {
                GpioPinMode.USB -> A3
                GpioPinMode.V3V3 -> A4
                GpioPinMode.FAULT -> A5
            }
            3, 13 -> when(pin) {
                GpioPinMode.USB -> B3
                GpioPinMode.V3V3 -> B2
                GpioPinMode.FAULT -> B1
            }
            4, 14 -> when(pin) {
                GpioPinMode.USB -> A6
                GpioPinMode.V3V3 -> A7
                GpioPinMode.FAULT -> B0
            }
            5, 15 -> when(pin) {
                GpioPinMode.USB -> B0
                GpioPinMode.V3V3 -> A7
                GpioPinMode.FAULT -> A6
            }
            6, 16 -> when(pin) {
                GpioPinMode.USB -> B1
                GpioPinMode.V3V3 -> B2
                GpioPinMode.FAULT -> B3
            }
            7, 17 -> when(pin) {
                GpioPinMode.USB -> A5
                GpioPinMode.V3V3 -> A4
                GpioPinMode.FAULT -> A3
            }
            8, 18 -> when(pin) {
                GpioPinMode.USB -> B4
                GpioPinMode.V3V3 -> B5
                GpioPinMode.FAULT -> B6
            }
            9, 19 -> when(pin) {
                GpioPinMode.USB -> A2
                GpioPinMode.V3V3 -> A1
                GpioPinMode.FAULT -> A0
            }
            else -> throw IllegalArgumentException("Invalid index {$device}")
        }
    }

    // === ADC ===

    private fun measureADC(adc: MCP3008, channel: Int): Float {
        val raw = adc.readChannel(channel)
        val ratio = 2f * 3.3f / 1024f // Vcc = 3.3V, 10bit full range, 1/2 divider
        return raw * ratio
    }

    private fun mapDeviceToAdc(device: Int): MCP3008 {
        require(device in 0..20)
        return when {
            device < 8 -> MCP3008_1
            device < 16 -> MCP3008_2
            else -> MCP3008_3
        }
    }

    private fun mapDeviceToAdcChannel(device: Int): Int {
        require(device in 0..20)
        return when (device) {
            7, 15, 19 -> 0
            5, 13, 17 -> 1
            3, 11 -> 2
            1, 9 -> 3
            0, 8 -> 4
            2, 10 -> 5
            4, 12, 16 -> 6
            6, 14, 18 -> 7
            else -> throw IllegalArgumentException("Invalid index {$device}")
        }
    }

    // === SWD ===

    fun mapDeviceToSwdDevice(num: Int): Int {
        require(num in 0..20)
        // SWD2: #1..10
        // SWD1: #11..20
        return when {
            (num < 10) -> 1
            (num < 20) -> 0
            else -> -1
        }
    }

    fun mapDeviceToSwdInterface(num: Int): Int {
        require(num in 0..20)
        // SWD1: #1..10
        // SWD2: #11..20
        return when {
            (num < 10) -> num
            (num < 20) -> num - 10
            else -> -1
        }
    }
}
