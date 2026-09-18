package dev.slimevr.testing.stage5

import com.pi4j.Pi4J
import com.pi4j.context.Context
import com.pi4j.io.gpio.digital.DigitalOutput
import com.pi4j.io.gpio.digital.DigitalState
import com.pi4j.io.i2c.I2CProvider
import dev.slimevr.hardware.SwitchboardStage5
import dev.slimevr.testing.ADCProvider
import dev.slimevr.testing.pi4j
import dev.slimevr.testing.destroy
import java.util.logging.Logger

class CalibrateADC(
        val adc: ADCProvider,
        val board: SwitchboardStage5,
        val logger: Logger
    ) {

    companion object {
        val CHANNELS: List<ChannelMode> = listOf(ChannelMode.A, ChannelMode.B)
        const val ON: Boolean = SwitchboardStage5.ON
        const val OFF: Boolean = SwitchboardStage5.OFF
        const val ALL: Int = SwitchboardStage5.ALL
    }

    /**
     * Current shunt 1R drop x100-gained to VBat power source
     *
     * Scale 10mA / 1V
     */
    private fun getIoutVoltage(): Float = adc.ADS1X15_1.getVoltage(0u)

    /** VBat target test-point */
    private fun getBatVoltage(): Float = adc.ADS1X15_1.getVoltage(1u)

    /** 3V targer test-point */
    private fun getBusVoltage(): Float = adc.ADS1X15_1.getVoltage(2u)

    /** VDD targer test-point */
    private fun getOutVoltage(): Float = adc.ADS1X15_1.getVoltage(3u)

    fun calibrate() {
        println("=== Measure Iout reference voltages ===")
        board.power(PowerMode.BATTERY)

        for (ch in CHANNELS) {
            board.channel(ch)
            for (idx in 0 until 10) {
                board.device(idx, ON)
                Thread.sleep(1)
                val bat1 = measureBat()
                if (bat1 > 3.5f) {
                    print("${ch}${idx + 1} = NO DRAW, ")
                } else {
                    val bat9 = measureBat(4)
                    val cur = measure(5)
                    val bat = (bat1 + bat9 * 4f) / 5f
                    print("${ch}${idx + 1} = $cur @ $bat, ")
                }
                board.device(idx, OFF)
            }
        }
        println()
        board.channel(ChannelMode.OFF)
        board.power(PowerMode.OFF)

        println("=== Measure Iout offset voltages ===")
        for (ch in CHANNELS) {
            board.channel(ch)
            for (idx in 0 until 10) {
                board.device(idx, ON)
                Thread.sleep(1)
                val offset = measure(5)
                print("${ch}${idx + 1} = $offset, ")
                board.device(idx, OFF)
            }
        }
        println()
        board.channel(ChannelMode.OFF)

        println("=== Done ===")
    }

    fun measure(count: Int = 1): Float {
        var acc = 0f
        for(i in 0 until count) {
            acc += getIoutVoltage()
        }
        return acc / count
    }

    fun measureBat(count: Int = 1): Float {
        var acc = 0f
        for (i in 0 until count)
            acc += getBatVoltage()
        return acc / count
    }
}
