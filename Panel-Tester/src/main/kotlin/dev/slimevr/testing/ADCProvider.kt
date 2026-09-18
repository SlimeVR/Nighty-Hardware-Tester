package dev.slimevr.testing

import com.pi4j.io.i2c.I2CConfigBuilder
import com.pi4j.io.i2c.I2CProvider
import dev.slimevr.hardware.ads1x15.ADS1115_ADDRESS_GND
import dev.slimevr.hardware.ads1x15.ADS1115_ADDRESS_VCC
import dev.slimevr.hardware.ads1x15.ADS1X15

class ADCProvider(
    i2CProvider: I2CProvider
) {
    val ADS1X15_1 = ADS1X15(i2CProvider.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(ADS1115_ADDRESS_GND.toInt()).build()))
    val ADS1X15_2 = ADS1X15(i2CProvider.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(ADS1115_ADDRESS_VCC.toInt()).build()))
}
