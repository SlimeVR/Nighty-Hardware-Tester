package dev.slimevr.testing.stage6

import dev.slimevr.hardware.SwitchboardStage6
import dev.slimevr.hardware.spi.MCP3008
import dev.slimevr.hardware.i2c.MCP23017
import dev.slimevr.hardware.i2c.*
import dev.slimevr.testing.ADCProvider
import java.util.logging.Logger

class TestDongleTester(
        val board: SwitchboardStage6,
        val adc: ADCProvider,
        val logger: Logger
    ) {

    fun start() {
        println("=== Measure adc voltages ===")

        val device = 14

        board.pinReset(SwitchboardStage6.ON)
        Thread.sleep(10)
        board.pinReset(SwitchboardStage6.OFF)

        val gpio = mapDeviceToGpio(device)
        val usbMask = mapDeviceToGpioPin(device, GpioPinMode.USB)
        val v3v3Mask = mapDeviceToGpioPin(device, GpioPinMode.V3V3)
        val faultMask = mapDeviceToGpioPin(device, GpioPinMode.FAULT)

        val portMaskUsb = (usbMask or v3v3Mask) xor usbMask
        val portMask3V = (usbMask or v3v3Mask) xor v3v3Mask
        val portMaskOff = (usbMask or v3v3Mask)

        gpio.writePort(portMaskOff)
        gpio.setDir(0xFFFF xor (usbMask or v3v3Mask))

        Thread.sleep(100)
        print("INIT OFF: ")

        val adcIC = mapDeviceToAdc(device)
        val adcCH = mapDeviceToAdcChannel(device)
        var v = measure(adcIC, adcCH)
        var f = gpio.readPort() and faultMask
        println("${device}=$v FAULT=$f")

        Thread.sleep(1000)
        print("USB 5V: ")
        gpio.writePort(portMaskUsb)
        Thread.sleep(100)
        v = measure(adcIC, adcCH)
        f = gpio.readPort() and faultMask
        println("${device}=$v FAULT=$f")

        Thread.sleep(1000)
        print("BUS 3V: ")
        gpio.writePort(portMask3V)
        Thread.sleep(100)

        v = measure(adcIC, adcCH)
        f = gpio.readPort() and faultMask
        println("${device}=$v FAULT=$f")

        Thread.sleep(1000)

        print("END OFF: ")
        gpio.writePort(portMaskOff)
        Thread.sleep(100)
        v = measure(adcIC, adcCH)
        f = gpio.readPort() and faultMask
        println("${device}=$v FAULT=$f")

        // do {
        //     for(device in 0..19) {
        //         val ch = mapDeviceToAdcChannel(device)
        //         val adc = mapDeviceToAdc(device)
        //         val v = measure(adc, ch)
        //         print("${device}=$v ")
        //     }
        //     println()
        //     Thread.sleep(1000)
        // } while(true)

        println("=== Done ===")
    }

    fun Int.toHexString(): String = String.format("%02x", this)

    fun measure(adc: MCP3008, channel: Int): Float {
        val raw = adc.readChannel(channel)
        val ratio = 3.3f / 1024f
        return raw * ratio
    }

    fun mapDeviceToAdc(device: Int): MCP3008 {
        require(device in 0..20)
        return when {
            device < 8 -> adc.MCP3008_1
            device < 16 -> adc.MCP3008_2
            else -> adc.MCP3008_3
        }
    }

    fun mapDeviceToAdcChannel(device: Int): Int {
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

    fun mapDeviceToGpio(device: Int): MCP23017 {
        require(device in 0..20)
        return when(device) {
            0, 2, 4, 6, 8 -> adc.GPIO_1
            1, 3, 5, 7, 9 -> adc.GPIO_2
            10, 12, 14, 16, 18 -> adc.GPIO_3
            11, 13, 15, 17, 19 -> adc.GPIO_4
            else -> throw IllegalArgumentException("Invalid index {$device}")
        }
    }

    enum class GpioPinMode {
        USB,
        V3V3,
        FAULT
    }

    companion object {
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
    }

    fun mapDeviceToGpioPin(device: Int, pin: GpioPinMode): Int {
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
}
