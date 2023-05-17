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
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(26).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(24).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(23).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(21).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(20).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(25).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(16).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(12).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(8).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(7).build(),
    )

    private var batteryEnablePin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(5).build()
    private var vbusEnablePin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(6).build()
    private var rstPin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(13).build()
    private var flashPin = DigitalOutput.newBuilder(pi4j).shutdown(defaultOutputState).address(19).build()

    private var ledPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).address(18).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).address(27).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).address(22).build(),
        DigitalOutput.newBuilder(pi4j).shutdown(defaultLedState).address(17).build()
    )

    private var buttonPin = DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.PULL_UP).address(4).build()

    init {
        powerOff()
        disableAll()
    }

    fun resetMode(mode: Boolean) {
        if (mode) {
            if (enablePullUp) {
                rstPin.high()
            } else {
                rstPin.low()
            }
        } else {
            if (enablePullUp) {
                rstPin.low()
            } else {
                rstPin.high()
            }
        }
    }

    fun flashMode(mode: Boolean) {
        if (mode) {
            if (enablePullUp) {
                flashPin.high()
            } else {
                flashPin.low()
            }
        } else {
            if (enablePullUp) {
                flashPin.low()
            } else {
                flashPin.high()
            }
        }
    }

    fun enableDevice(deviceNum: Int) {
        if (enablePullUp) {
            enablePins[deviceNum].high()
        } else {
            enablePins[deviceNum].low()
        }
    }

    fun disableDevice(deviceNum: Int) {
        if (enablePullUp) {
            enablePins[deviceNum].low()
        } else {
            enablePins[deviceNum].high()
        }
    }

    fun disableAll() {
        enablePins.forEach {
            if (enablePullUp) {
                it.low()
            } else {
                it.high()
            }
        }
    }

    fun powerOff() {
        if (enablePullUp) {
            batteryEnablePin.low()
            vbusEnablePin.low()
        } else {
            batteryEnablePin.high()
            vbusEnablePin.high()
        }
    }

    fun powerBattery() {
        if (enablePullUp) {
            vbusEnablePin.low()
            batteryEnablePin.high()
        } else {
            vbusEnablePin.high()
            batteryEnablePin.low()
        }
    }

    fun powerVbus() {
        if (enablePullUp) {
            batteryEnablePin.low()
            vbusEnablePin.high()
        } else {
            batteryEnablePin.high()
            vbusEnablePin.low()
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

    fun isButtonPressed() = buttonPin.onState.isLow
}