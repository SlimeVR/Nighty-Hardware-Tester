package dev.slimevr.hardware

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance

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

        val CHANNELS : List<ChannelMode> = listOf(ChannelMode.A, ChannelMode.B)

        const val ON: Boolean = true
        const val OFF: Boolean = false
        const val ALL: Int = Int.MAX_VALUE

        private val enablePullUp = true
        private val ledPullUp = false
        private val defaultOutputState = DigitalState.LOW
        private val defaultLedState = if (ledPullUp) DigitalState.LOW else DigitalState.HIGH
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

    init {
        power(PowerMode.OFF)
        disableAll()
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

    fun disableAll() {
        device(ALL, OFF)
        pinReset(OFF)
    }

    fun isPowerFault() = powerFaultPin.isLow
    fun isChannelPresent() = sensePin.isLow

    /**
     * Select column A/B
     */
    fun channel(mode: ChannelMode) {
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

    fun isButtonPressed() = buttonPin.isLow
}
