package dev.slimevr.testing

import com.pi4j.io.i2c.I2CProvider
import dev.slimevr.hardware.ads1x15.ADS1115_ADDRESS_GND
import dev.slimevr.hardware.ads1x15.ADS1115_ADDRESS_VCC
import dev.slimevr.hardware.ads1x15.ADS1X15

class ADCProvider(
    i2CProvider: I2CProvider
) {
    private var ADS1X15_1 = ADS1X15(i2CProvider.create(1, ADS1115_ADDRESS_GND.toInt()))
    private var ADS1X15_2 = ADS1X15(i2CProvider.create(1, ADS1115_ADDRESS_VCC.toInt()))

    fun get3v3Voltage() : Float = ADS1X15_1.getVoltage(0u)
    fun getVCCVoltage() : Float = ADS1X15_1.getVoltage(1u)
    fun getVBUSVoltage() : Float = ADS1X15_1.getVoltage(2u)
    fun getBatVoltage() : Float = ADS1X15_1.getVoltage(3u)
    fun getChrgVoltage() : Float = ADS1X15_2.getVoltage(0u)
    fun getFullVoltage() : Float = ADS1X15_2.getVoltage(1u)
}
