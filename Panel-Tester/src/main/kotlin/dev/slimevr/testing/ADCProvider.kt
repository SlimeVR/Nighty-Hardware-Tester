package dev.slimevr.testing

import com.pi4j.io.i2c.I2CConfigBuilder
import com.pi4j.io.i2c.I2CProvider
import com.pi4j.io.spi.SpiProvider
import dev.slimevr.hardware.i2c.ADS1115_ADDRESS_GND
import dev.slimevr.hardware.i2c.ADS1115_ADDRESS_VCC
import dev.slimevr.hardware.i2c.ADS1X15
import dev.slimevr.hardware.i2c.MCP23017
import dev.slimevr.hardware.spi.MCP3008
import com.pi4j.io.spi.SpiConfig
import com.pi4j.io.spi.SpiConfigBuilder
import com.pi4j.io.spi.SpiMode
import com.pi4j.io.spi.Spi

class ADCProvider(
    i2CProvider: I2CProvider?,
    spiProvider: SpiProvider? = null
) {
    val ADS1X15_1: ADS1X15 by lazy {
        ADS1X15(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(ADS1115_ADDRESS_GND.toInt()).build()))
    }
    val ADS1X15_2: ADS1X15 by lazy {
        ADS1X15(i2CProvider!!.create(I2CConfigBuilder.newInstance(pi4j).bus(1).device(ADS1115_ADDRESS_VCC.toInt()).build()))
    }

    val MCP3008_1: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(0).baud(Spi.DEFAULT_BAUD).mode(SpiMode.MODE_0).build()))
    }
    val MCP3008_2: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(1).baud(Spi.DEFAULT_BAUD).mode(SpiMode.MODE_0).build()))
    }
    val MCP3008_3: MCP3008 by lazy {
        MCP3008(spiProvider!!.create(SpiConfigBuilder.newInstance(pi4j).bus(1).channel(2).baud(Spi.DEFAULT_BAUD).mode(SpiMode.MODE_0).build()))
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
}
