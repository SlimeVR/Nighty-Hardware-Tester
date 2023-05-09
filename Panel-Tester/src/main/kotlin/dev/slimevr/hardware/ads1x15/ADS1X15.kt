package dev.slimevr.hardware.ads1x15

import com.pi4j.io.i2c.I2C
import com.pi4j.io.i2c.I2CRegister

const val ADS1015_CONVERSION_DELAY = 1u
const val ADS1115_CONVERSION_DELAY = 8u

//  Kept #defines a bit in line with Adafruit library.

//  REGISTERS
const val ADS1X15_REG_CONVERT = 0x00u
const val ADS1X15_REG_CONFIG = 0x01u
const val ADS1X15_REG_LOW_THRESHOLD = 0x02u
const val ADS1X15_REG_HIGH_THRESHOLD = 0x03u

//  CONFIG REGISTER

//  BIT 15      Operational Status           // 1 << 15
const val ADS1X15_OS_BUSY = 0x0000u
const val ADS1X15_OS_NOT_BUSY = 0x8000u
const val ADS1X15_OS_START_SINGLE = 0x8000u

//  BIT 12-14   read differential
const val ADS1X15_MUX_DIFF_0_1 = 0x0000u
const val ADS1X15_MUX_DIFF_0_3 = 0x1000u
const val ADS1X15_MUX_DIFF_1_3 = 0x2000u
const val ADS1X15_MUX_DIFF_2_3 = 0x3000u
//              read single
const val ADS1X15_READ_0 = 0x4000u   //  pin << 12
const val ADS1X15_READ_1 = 0x5000u   //  pin = 0..3
const val ADS1X15_READ_2 = 0x6000u
const val ADS1X15_READ_3 = 0x7000u

//  BIT 9-11    gain                         //  (0..5) << 9
const val ADS1X15_PGA_6_144V = 0x0000u   //  voltage
const val ADS1X15_PGA_4_096V = 0x0200u   //
const val ADS1X15_PGA_2_048V = 0x0400u   //  default
const val ADS1X15_PGA_1_024V = 0x0600u
const val ADS1X15_PGA_0_512V = 0x0800u
const val ADS1X15_PGA_0_256V = 0x0A00u

//  BIT 8       mode                         //  1 << 8
const val ADS1X15_MODE_CONTINUE = 0x0000u
const val ADS1X15_MODE_SINGLE = 0x0100u

//  BIT 5-7     data rate sample per second  // (0..7) << 5
/*
differs for different devices, check datasheet or readme.md
|  data rate  |  ADS101x  |  ADS111x  |   Notes   |
|:-----------:|----------:|----------:|:---------:|
|     0       |   128     |    8      |  slowest  |
|     1       |   250     |    16     |           |
|     2       |   490     |    32     |           |
|     3       |   920     |    64     |           |
|     4       |   1600    |    128    |  default  |
|     5       |   2400    |    250    |           |
|     6       |   3300    |    475    |           |
|     7       |   3300    |    860    |  fastest  |
*/

//  BIT 4 comparator modi                    // 1 << 4
const val ADS1X15_COMP_MODE_TRADITIONAL = 0x0000u
const val ADS1X15_COMP_MODE_WINDOW = 0x0010u

//  BIT 3 ALERT active value                 // 1 << 3
const val ADS1X15_COMP_POL_ACTIV_LOW = 0x0000u
const val ADS1X15_COMP_POL_ACTIV_HIGH = 0x0008u

//  BIT 2 ALERT latching                     // 1 << 2
const val ADS1X15_COMP_NON_LATCH = 0x0000u
const val ADS1X15_COMP_LATCH = 0x0004u

//  BIT 0-1 ALERT mode                       // (0..3)
const val ADS1X15_COMP_QUE_1_CONV = 0x0000u  //  trigger alert after 1 convert
const val ADS1X15_COMP_QUE_2_CONV = 0x0001u  //  trigger alert after 2 converts
const val ADS1X15_COMP_QUE_4_CONV = 0x0002u  //  trigger alert after 4 converts
const val ADS1X15_COMP_QUE_NONE = 0x0003u  //  disable comparator


// _CONFIG masks
//
//  |  bit  |  description           |
//  |:-----:|:-----------------------|
//  |   0   |  # channels            |
//  |   1   |  -                     |
//  |   2   |  resolution            |
//  |   3   |  -                     |
//  |   4   |  GAIN supported        |
//  |   5   |  COMPARATOR supported  |
//  |   6   |  -                     |
//  |   7   |  -                     |
//
const val ADS_CONF_CHAN_1 = 0x00u
const val ADS_CONF_CHAN_4 = 0x01u
const val ADS_CONF_RES_12 = 0x00u
const val ADS_CONF_RES_16 = 0x04u
const val ADS_CONF_NOGAIN = 0x00u
const val ADS_CONF_GAIN = 0x10u
const val ADS_CONF_NOCOMP = 0x00u
const val ADS_CONF_COMP = 0x20u

const val ADS1X15_OK = 0
const val ADS1X15_INVALID_VOLTAGE = -100
const val ADS1X15_INVALID_GAIN = 0xFF
const val ADS1X15_INVALID_MODE = 0xFE

const val ADS1115_ADDRESS_GND = 0x48u
const val ADS1115_ADDRESS_VCC = 0x49u
const val ADS1115_ADDRESS_SDA = 0x4Au
const val ADS1115_ADDRESS_SCL = 0x4Bu

class ADS1X15(
    device: I2C) {

    private var _config: UInt = 0u
    private var _conversionDelay: UInt = 0u
    private var _bitShift: UInt = 0u
    private var _gain: UInt = 0u
    private var _mode: UInt = 0u
    private var _datarate: UInt = 0u

    //  COMPARATOR variables
    private var _compMode: UInt = 0u
    private var _compPol: UInt = 0u
    private var _compLatch: UInt = 0u
    private var _compQueConvert: UInt = 0u

    private val registerConvert: I2CRegister = device.getRegister(ADS1X15_REG_CONVERT.toInt())
    private val registerConfig: I2CRegister = device.getRegister(ADS1X15_REG_CONFIG.toInt())
    private val registerLowTreshold: I2CRegister = device.getRegister(ADS1X15_REG_LOW_THRESHOLD.toInt())
    private val registerHighTreshold: I2CRegister = device.getRegister(ADS1X15_REG_HIGH_THRESHOLD.toInt())

    init {
        _gain = ADS1X15_PGA_6_144V
        _mode = ADS1X15_MODE_SINGLE
        _datarate = 4u // Middle speed
    }

    private fun _requestADC(readmode: UInt)
    {
        //  write to register is needed in continuous mode as other flags can be changed
        var config = ADS1X15_OS_START_SINGLE  //  bit 15     force wake up if needed
        config = config or readmode                         //  bit 12-14
        config = config or _gain                           //  bit 9-11
        config = config or _mode                            //  bit 8
        config = config or _datarate                        //  bit 5-7
        if (_compMode > 0u)
            config = config or ADS1X15_COMP_MODE_WINDOW;         //  bit 4      comparator modi
        else
            config = config or ADS1X15_COMP_MODE_TRADITIONAL;
        if (_compPol > 0u)
            config = config or ADS1X15_COMP_POL_ACTIV_HIGH;      //  bit 3      ALERT active value
        else
            config = config or ADS1X15_COMP_POL_ACTIV_LOW;
        if (_compLatch > 0u)
            config = config or ADS1X15_COMP_LATCH;
        else
            config = config or ADS1X15_COMP_NON_LATCH;           //  bit 2      ALERT latching
        config = config or _compQueConvert;                                  //  bit 0..1   ALERT mode
        registerConfig.write(config.toInt())
    }

    fun getVoltage(pin: UInt): Float = toVoltage(readADC(pin))

    fun readADC(pin: UInt): Int {
        if (pin >= 4u)
            return 0;
        val mode = (4u + pin) shl 12  //  pin to mask
        return _readADC(mode);
    }

    private fun _readADC(readmode: UInt): Int {
        _requestADC(readmode)
        if (_mode == ADS1X15_MODE_SINGLE)
        {
            while ( isBusy() )
                Thread.yield();   //  wait for conversion; yield for ESP.
        }
        else
        {
            Thread.sleep(_conversionDelay.toLong());      //  TODO needed in continuous mode?
        }
        return getValue();
    }

    fun getValue(): Int {
        var raw = registerConvert.readWord()
        if (_bitShift > 0u)
            raw = raw.shr(_bitShift.toInt())
        return raw
    }

    fun toVoltage(value: Int): Float {
        if (value == 0)
            return 0f

        var volts = getMaxVoltage()
        if (volts < 0)
            return volts

        volts *= value;
        if (_config and ADS_CONF_RES_16 > 0u)
        {
            volts /= 32767;  //  value = 16 bits - sign bit = 15 bits mantissa
        }
        else
        {
            volts /= 2047;   //  value = 12 bits - sign bit = 11 bit mantissa
        }
        return volts;
    }

    fun getMaxVoltage(): Float {
        return when (_gain) {
            ADS1X15_PGA_6_144V -> 6.144f
            ADS1X15_PGA_4_096V -> 4.096f
            ADS1X15_PGA_2_048V -> 2.048f
            ADS1X15_PGA_1_024V -> 1.024f
            ADS1X15_PGA_0_512V -> 0.512f
            ADS1X15_PGA_0_256V -> 0.256f
            else -> ADS1X15_INVALID_VOLTAGE.toFloat()
        }
    }

    fun isBusy(): Boolean = !isReady()

    fun isReady(): Boolean {
        return (registerConfig.read() and ADS1X15_OS_NOT_BUSY.toInt()) > 0
    }

}
