package dev.slimevr.hardware

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance
import com.pi4j.ktx.io.digital.onLow

class SwitchboardStage5(
    pi4j: Context
) {
    companion object {
        enum class PowerMode {
            OFF,
            USB,
            BATTERY
        }

        enum class ChannelMode {
            OFF,
            A,
            B,
            BOTH
        }

        enum class LedColor(
            val r: Int,
            val g: Int,
            val b: Int,
        )
        {
            OFF(0, 0, 0),
            BLUE(0, 0, 1),
            GREEN(0, 1, 0),
            CYAN(0, 1, 1),
            RED(1, 0, 0),
            PURPLE(1, 0, 1),
            YELLOW(1, 1, 0),
            WHITE(1, 1, 1)
        }

        val CHANNELS : List<ChannelMode> = listOf(ChannelMode.A, ChannelMode.B)

        const val ON: Boolean = true
        const val OFF: Boolean = false
        const val ALL: Int = Int.MAX_VALUE

        private val enablePullUp = true
        private val ledPullUp = true
        private val defaultOutputState = DigitalState.LOW
        private val defaultLedState = if (ledPullUp) DigitalState.HIGH else DigitalState.LOW
        private val useResetPin = false
    }

    private var enablePins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(26).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(24).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(23).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(21).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(20).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(25).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(16).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(12).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(8).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(7).provider("pigpio-digital-output").build(),
    )

    private var batteryEnablePin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(6).provider("pigpio-digital-output").build()
    private var vbusEnablePin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(5).provider("pigpio-digital-output").build()
    private var rstPin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(19).provider("pigpio-digital-output").build()

    private var ledPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(18).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(27).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(22).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(17).provider("pigpio-digital-output").build()
    )

    private var buttonPin =
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(4)
            .debounce(3000L)
            .provider("pigpio-digital-input"))

    private var enableChAPin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(9).provider("pigpio-digital-output").build()
    private var enableChBPin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).initial(defaultOutputState).address(11).provider("pigpio-digital-output").build()

    private var powerFaultPin = pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(10).provider("pigpio-digital-input"))
    private var sensePin = pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(13).provider("pigpio-digital-input"))

    private var powerFaultTrigged = false
    private var activeChannel: ChannelMode = ChannelMode.OFF

    init {
        disableAll()
        powerFaultPin.onLow { powerFaultTrigged = true }
    }

    fun pinReset(enable: Boolean) {
        // Resed by dedicated signal
        rstPin.setState(enable)
    }

    fun resetSWD(deviceChannel : ChannelMode, deviceNum : Int) {
        // TODO: Reset by SWD command
    }

    /**
     * Switch row by index (0-based)
     *
     * Usage:
     * - device(1, ON)
     * - device(1, OFF)
     * - device(ALL, OFF)
     */
    fun device(deviceNum: Int, enable: Boolean) {
        when(deviceNum) {
            ALL -> enablePins.forEach { it.setState(enable) }
            else -> enablePins[deviceNum].setState(enable)
        }
    }

    fun isDeviceOn(deviceNum: Int): Boolean = enablePins[deviceNum].isHigh

    fun disableAll() {
        channel(ChannelMode.OFF)
        power(PowerMode.OFF)
        pinReset(OFF)
        device(ALL, OFF)
    }

    fun isPowerFault() = powerFaultPin.isLow
    fun resetPowerFault() {
        powerFaultTrigged = false
    }
    fun isPowerFaultTriggered() : Boolean {
        val isTriggered = powerFaultTrigged
        powerFaultTrigged = false
        return isTriggered
    }
    fun isChannelPresent() = sensePin.isLow

    /**
     * Select column A/B
     */
    fun channel(mode: ChannelMode) {
        activeChannel = mode
        when(mode) {
            ChannelMode.OFF -> {
                enableChAPin.low()
                enableChBPin.low()
            }
            ChannelMode.A -> {
                enableChBPin.low()
                enableChAPin.high()
            }
            ChannelMode.B -> {
                enableChAPin.low()
                enableChBPin.high()
            }
            ChannelMode.BOTH -> {
                // Can be both powered but USB is disconnected
                enableChAPin.high()
                enableChBPin.high()
            }
        }
    }

    fun channel(): ChannelMode {
        return activeChannel
    }

    fun power(mode: PowerMode) {
        when(mode) {
            PowerMode.OFF -> {
                batteryEnablePin.low()
                vbusEnablePin.low()
            }
            PowerMode.USB -> {
                batteryEnablePin.low()
                vbusEnablePin.high()
            }
            PowerMode.BATTERY -> {
                vbusEnablePin.low()
                batteryEnablePin.high()
            }
        }
    }

    fun led(ledNum: Int, enable: Boolean) {
        var d = if (enable xor ledPullUp) DigitalState.HIGH else DigitalState.LOW
        ledPins[ledNum].state(d)
    }

    fun led(c: LedColor) {
        led(0, c.r == 1)
        led(1, c.g == 1)
        led(2, c.b == 1)
    }

    fun isButtonPressed() = buttonPin.isLow


    // TODO Rework device channel mapping into fixed arrays

    fun mapDeviceToSwitchboard(num: Int): Int {
        //  A,  B   CH
        //  0,  5 -> 0
        //  1,  6 -> 1
        //  2,  7 -> 2
        //  3,  8 -> 3
        //  4,  9 -> 4
        // 10, 15 -> 5
        // 11, 16 -> 6
        // 12, 17 -> 7
        // 13, 18 -> 8
        // 14, 19 -> 9
        return when {
            (num < 5) -> num
            (num < 10) -> num - 5
            (num < 15) -> num - 5
            (num < 20) -> num - 10
            else -> -1
        }
    }

    fun mapDeviceToSwdInterface(num: Int): Int {
        // A B  A B
        // 0 5  0 5
        // 1 6  1 6
        // 2 7  2 7
        // 3 8  3 8
        // 4 9  4 9
        return when {
            (num < 10) -> num
            (num < 20) -> num - 10
            else -> -1
        }
    }

    fun unmapDeviceFromSwitchboard(num: Int): Int {
        return when (activeChannel) {
            ChannelMode.A -> if (num < 5) num else num + 5
            ChannelMode.B -> if (num < 5) num + 5 else num + 10
            else -> 0
        }
    }

    fun deviceNumToChannel(num: Int): ChannelMode {
        // A  B  A  B
        // 0  5 10 15
        // 1  6 11 16
        // 2  7 12 17
        // 3  8 13 18
        // 4  9 14 19
        return when {
            (num < 5) -> ChannelMode.A
            (num < 10) -> ChannelMode.B
            (num < 15) -> ChannelMode.A
            (num < 20) -> ChannelMode.B
            else -> ChannelMode.OFF
        }
    }
}
