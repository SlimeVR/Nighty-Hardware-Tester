package dev.slimevr.testing.stage5

import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.SwitchboardStage5
import dev.slimevr.hardware.SwitchboardStage5.Companion.PowerMode
import dev.slimevr.hardware.SwitchboardStage5.Companion.ChannelMode
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.*
import dev.slimevr.ui.stage5.*
import java.util.logging.Level
import java.util.logging.Logger

typealias PowerMode = dev.slimevr.hardware.SwitchboardStage5.Companion.PowerMode
typealias ChannelMode = dev.slimevr.hardware.SwitchboardStage5.Companion.ChannelMode

class ButterflyTrackerPanelTestingSuite(
    private val switchboard: SwitchboardStage5,
    private val adcProvider: ADCProvider,
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi: TesterButterflyTrackerUI,
    private val devices: Int,
    private val logger: Logger,
    private var statusLogger: Logger
) : Thread("Testing suit thread") {

    private val powerBalanceTimeMS = 100L

    private val serialManager = SerialManager()

    private val deviceMap = arrayOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8, 1, 3, 5, 7, 9, 1, 3, 5, 7, 9)
    private val switchboardMap = arrayOf(0, 10, 1, 11, 2, 12, 3, 13, 4, 14)

    private val deviceTests = mutableListOf<DeviceTest>()
    private var testStart = 0L
    private var isTesting = false
    private val committedSuccessfulDeviceIds = mutableListOf<String>()
    private var startTest: Boolean = false
    private var btnPressed: Boolean = false
    private var testOnlyDevices = setOf<Int>()
    private val usbMap = mutableMapOf<String, Int>()
    private var isReady = false
    private var activeChannel: Int = 0
    private var testRepeat = false

    override fun run() {
        try {
            selfTest()
        } catch (exception: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", exception)
            return
        }
        isReady = true
    }

    fun isReady() = isReady

    fun startTest(vararg devices: Int) {
        synchronized(this) {
            if (isTesting)
                return
        }
        // TODO one device doesn't work somewhere, maybe hardware flash/reset error
        testOnlyDevices = setOf(*devices.toTypedArray())
        startTest = true
    }

    fun btnPressed() {
        btnPressed = true
    }

    private fun selfTest() {
        switchboard.disableAll()
        switchboard.power(PowerMode.OFF)
        sleep(powerBalanceTimeMS)
        logger.info("=== Testing suite self-test: ===")
        logger.info("VBUS voltage: ${adcProvider.getVBUSVoltage()}")
        logger.info("BAT voltage: ${adcProvider.getBatVoltage()}")
        logger.info("3v3 voltage: ${adcProvider.get3v3Voltage()}")
        logger.info("VCC voltage: ${adcProvider.getVCCVoltage()}")
        logger.info("Chrg voltage: ${adcProvider.getChrgVoltage()}")
        logger.info("Full voltage: ${adcProvider.getFullVoltage()}")
    }

    private fun shouldSkipDevice(deviceNum: Int) = testOnlyDevices.isNotEmpty() && !testOnlyDevices.contains(deviceNum)

    fun getFailedDevices(): List<Int> {
        return deviceTests.filter { it.testStatus == TestStatus.ERROR }.map { it.deviceNum }
    }

    private fun mapDeviceToSwitchboard(num: Int): Int {
        return deviceMap[num]
    }

    private fun unmapDeviceFromSwitchboard(num: Int): Int {
        if(activeChannel == 1)
            return switchboardMap[num]
        else if(activeChannel == 2)
            return switchboardMap[num] + 5
        return 0
    }

    private fun deviceNumToChannel(num: Int): Int {
        if (num < 5)
            return 1
        else if (num < 10)
            return 2
        else if (num < 15)
            return 1
        else if (num < 20)
            return 2
        return 0
    }
}
