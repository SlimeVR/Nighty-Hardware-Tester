package dev.slimevr.testing

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance

class Switchboard(
    pi4j: Context
) {

    private var enablePullUp = true
    private var ledPullUp = false
    private var defaultOutputState = if (enablePullUp) DigitalState.LOW else DigitalState.HIGH
    private var defaultLedState = if (ledPullUp) DigitalState.LOW else DigitalState.HIGH

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
    private var rstPin = DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.LOW).initial(DigitalState.LOW).address(19).provider("pigpio-digital-output").build()
    private var flashPin = DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.LOW).initial(DigitalState.LOW).address(13).provider("pigpio-digital-output").build()

    private var ledPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(18).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(27).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(22).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).initial(defaultLedState).address(17).provider("pigpio-digital-output").build()
    )

    private var buttonPin =
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.PULL_UP).address(4)
            .debounce(3000L)
            .provider("pigpio-digital-input"))

    init {
        powerOff()
        disableAll()
    }

    fun resetMode(mode: Boolean) {
        if (mode) {
            rstPin.high()
        } else {
            rstPin.low()
        }
    }

    fun flashMode(mode: Boolean) {
        if (mode) {
            flashPin.high()
        } else {
            flashPin.low()
        }
    }

    fun enableDevice(deviceNum: Int) {
        enable(enablePins[deviceNum])
    }

    fun disableDevice(deviceNum: Int) {
        disable(enablePins[deviceNum])
    }

    fun disableAll() {
        flashMode(false)
        resetMode(false)
        enablePins.forEach {
            disable(it)
        }
    }

    fun powerOff() {
        disable(batteryEnablePin)
        disable(vbusEnablePin)
    }

    fun powerBattery() {
        disable(vbusEnablePin)
        enable(batteryEnablePin)
    }

    fun powerVbus() {
        disable(batteryEnablePin)
        enable(vbusEnablePin)
    }

    fun enable(output: DigitalOutput) {
        if (enablePullUp) {
            output.high()
        } else {
            output.low()
        }
    }

    fun disable(output: DigitalOutput) {
        if (enablePullUp) {
            output.low()
        } else {
            output.high()
        }
    }

    fun ledEnable(ledNum: Int) {
        if (ledPullUp) {
            ledPins[ledNum].high()
        } else {
            ledPins[ledNum].low()
        }
    }

    fun ledDisable(ledNum: Int) {
        if (ledPullUp) {
            ledPins[ledNum].low()
        } else {
            ledPins[ledNum].high()
        }
    }

    fun isButtonPressed() = buttonPin.isLow
}
