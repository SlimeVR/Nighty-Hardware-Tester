package dev.slimevr.hardware

import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalInput
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.gpio.digital.PullResistance

class SwitchboardStage6(
    pi4j: Context
) {
    companion object {
        const val ON: Byte = 0
        const val OFF: Byte = 1
    }

    private var csPins = arrayOf(
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(18).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(17).provider("pigpio-digital-output").build(),
        DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(16).provider("pigpio-digital-output").build()
    )

    private var intPins = arrayOf(
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(22).provider("pigpio-digital-input")),
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(24).provider("pigpio-digital-input")),
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(26).provider("pigpio-digital-input")),
        pi4j.create(DigitalInput.newConfigBuilder(pi4j).pull(PullResistance.OFF).address(29).provider("pigpio-digital-input")),
    )

    private var rstPin : DigitalOutput = DigitalOutput.newBuilder(pi4j).shutdown(DigitalState.HIGH).initial(DigitalState.HIGH).address(4).provider("pigpio-digital-output").build()

    init {
        disableAll()
    }

    fun pinReset(enable: Byte) {
        // Resed by dedicated signal
        rstPin.setState(enable)
    }

    fun disableAll() {
        // TODO
        pinReset(OFF)
    }
}
