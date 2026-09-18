package dev.slimevr.testing.stage5

import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.SwitchboardStage5
import dev.slimevr.hardware.SwitchboardStage5.Companion.PowerMode
import dev.slimevr.hardware.SwitchboardStage5.Companion.ChannelMode
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.*
import dev.slimevr.testing.actions.PresenceAction
import dev.slimevr.ui.stage5.*
import java.util.logging.Level
import java.util.logging.Logger

typealias PowerMode = dev.slimevr.hardware.SwitchboardStage5.Companion.PowerMode
typealias ChannelMode = dev.slimevr.hardware.SwitchboardStage5.Companion.ChannelMode

class ButterflyTrackerPanelTestingSuite(
    private val switchboard: SwitchboardStage5,
    private val adc: ADCProvider,
    private val testingDatabases: List<TestingDatabase>,
    private val ui: TesterButterflyTrackerUI,
    private val devices: Int,
    private val logger: Logger,
    private var statusLogger: Logger
) : Thread("Testing suit thread") {

    companion object {
        val CHANNELS: List<ChannelMode> = listOf(ChannelMode.A, ChannelMode.B)
        const val ON: Boolean = SwitchboardStage5.ON
        const val OFF: Boolean = SwitchboardStage5.OFF
        const val ALL: Int = SwitchboardStage5.ALL
    }

    private val powerBalanceTimeMS = 100L
    // Signal propogation = 6.6 ns/m, Logic gate delay = 30nS, 45nS
    private val logicBalanceTimeNS: Int = 1000 // 0.5us

    private val serialManager = SerialManager()

    private val deviceTests = mutableListOf<DeviceTest>()
    private var testStart = 0L
    private var isTesting = false
    private val committedSuccessfulDeviceIds = mutableListOf<String>()
    private var startTest: Boolean = false
    private var btnPressed: Boolean = false
    private var testOnlyDevices = setOf<Int>()
    private val usbMap = mutableMapOf<String, Int>()
    private var isReady = false
    private var activeChannel: ChannelMode = ChannelMode.OFF
    private var testRepeat = false

    override fun run() {
        try {
            selfTest()
        } catch (exception: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", exception)
            return
        }
        isReady = true
        //dmesgWatcher.start()
        logger.info("Testing suit started~")
        while (true) {
            try {
                waitTestStart()
                testStart()
            } catch (exception: Throwable) {
                logger.log(Level.SEVERE, "Standby error, can't continue", exception)
                return
            }
            synchronized(this) {
                isTesting = true
            }
            try {
                //testPresense()
                testVoltage()
                /*testVBUSVoltages()
                testBATVoltages()
                setChannelA()
                if (enumerateSerialDevices()) {
                    readDeviceIDs()
                    flashDevices()
                }
                setChannelB()
                if (enumerateSerialDevices()) {
                    readDeviceIDs()
                    flashDevices()
                }
                setChannelA()
                if (enumerateSerialDevices()) {
                    testDevices()
                }
                setChannelB()
                if (enumerateSerialDevices()) {
                    testDevices()
                }
                checkTestResults()
                commitTestResults()
                reportTestResults()*/

                testEnd()
            } catch (exception: Throwable) {
                logger.log(Level.SEVERE, "Tester error", exception)
            }
            synchronized(this) {
                isTesting = false
            }
            if (!testRepeat) {
                var failed = getFailedDevices()
                if (failed.isNotEmpty()) {
                    logger.info("Repeating test for ${failed.size} devices...")
                    testRepeat = true
                    startTest(*failed.toIntArray())
                }
            } else {
                testRepeat = false
            }
        }
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

    private fun selfTest() {
        switchboard.disableAll()
        switchboard.power(PowerMode.OFF)

        logger.info("=== Testing suite self-test: ===")
        sleep(0, logicBalanceTimeNS)
        if (switchboard.isChannelPresent()) {
            logger.severe("Channel sense is abnormaly asserted")
        }
        if (switchboard.isPowerFault()) {
            logger.severe("Power fault is abnormaly asserted")
        }
        sleep(powerBalanceTimeMS)
        logger.info("Iout voltage: ${getIoutVoltage()}")
        logger.info("BAT voltage: ${getBatVoltage()}")
        logger.info("3v voltage: ${getBusVoltage()}")
        logger.info("VDD voltage: ${getOutVoltage()}")
    }

    private fun waitTestStart() {
        disableChannels()
        switchboard.disableAll()
        switchboard.power(PowerMode.OFF)
        statusLogger.info("Ready to start the test")
        while (!switchboard.isButtonPressed() && !startTest) {
            sleep(10)
        }
        startTest = false
    }

    private fun testStart() {
        if (testOnlyDevices.isNotEmpty()) {
            testOnlyDevices.forEach {
                ui.setStatus(it, TestStatus.TESTING)
                ui.setID(it, "")
                if (deviceTests.isEmpty()) {
                    //deviceTests.addAll((0..devices).map {i -> DeviceTest(i)})
                    for (i in 0 until devices) {
                        val device = DeviceTest(i)
                        deviceTests.add(device)
                    }
                }
                deviceTests[it] = DeviceTest(it)
                statusLogger.info("Retesting device ${it + 1}")
                testStart = System.currentTimeMillis()
            }

            return
        }
        ui.statusLogHandler.clear()
        ui.clearAll()
        deviceTests.clear()
        for (i in 0 until devices) {
            val device = DeviceTest(i)
            deviceTests.add(device)
        }
        statusLogger.info("We goin'~")
        testStart = System.currentTimeMillis()
        for (i in 0 until devices) {
            ui.setStatus(i, TestStatus.TESTING)
            ui.setID(i, "")
        }
    }

    private val presenceTest = PresenceAction("Presence")

    private fun testPresense() {
        switchboard.power(PowerMode.OFF)
        switchboard.disableAll()

        for (channel in CHANNELS) {
            activeChannel = channel
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (deviceNumToChannel(i) != activeChannel)
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum))
                    continue
                statusLogger.info("[${i + 1}/$devices] Checking presense... ")
                val ch = mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)
                sleep(0, logicBalanceTimeNS)

                val sense1 = switchboard.isChannelPresent()
                val sense1Result = presenceTest.action(sense1, if (sense1) "Device detected" else "Not detected", System.currentTimeMillis())
                if(!sense1) {
                    ui.setID(device.deviceNum, "--- N/A ---")
                }
                addResult(device, sense1Result)
                switchboard.device(ch, OFF)
            }
        }
        activeChannel = ChannelMode.OFF
        switchboard.channel(ChannelMode.OFF)
    }

    private fun testVoltage() {
        switchboard.power(PowerMode.OFF)
        switchboard.disableAll()
        for (channel in CHANNELS) {
            activeChannel = channel
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (deviceNumToChannel(i) != activeChannel)
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                    continue

                statusLogger.info("[${i + 1}/$devices] Testing power from VBUS... ")
                val ch = mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)

                var list = mutableListOf<Float>()
                switchboard.power(PowerMode.BATTERY)
                val startTime: Long = System.nanoTime()
                for(i in 0..100) {
                    list.add(getIoutVoltage())
                }
                val endTime: Long = System.nanoTime()
                val listStr = list.fold("") {acc, i -> "$acc, $i" }
                logger.info("=== Voltage ===")
                logger.info("Elapsed: ${endTime - startTime} ns")
                logger.info(listStr)

                switchboard.device(ch, OFF)
            }
        }
    }

    private fun testEnd() {
        disableChannels()
        switchboard.disableAll()
        switchboard.power(PowerMode.OFF)
        serialManager.closeAllPorts()
        testOnlyDevices = setOf()
        startTest = false
        val testEnd = System.currentTimeMillis()
        statusLogger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    private fun disableChannels() {
        activeChannel = ChannelMode.OFF
        switchboard.channel(ChannelMode.OFF)
    }

    private fun shouldSkipDevice(deviceNum: Int) = testOnlyDevices.isNotEmpty() && !testOnlyDevices.contains(deviceNum)

    private fun isDeviceInTesting(device: DeviceTest): Boolean = (device.testStatus != TestStatus.ERROR && device.testStatus != TestStatus.DISCONNECTED)

    fun getFailedDevices(): List<Int> {
        return deviceTests.filter { it.testStatus == TestStatus.ERROR }.map { it.deviceNum }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if (result.status == TestStatus.ERROR)
            logger.severe("[${device.deviceNum + 1}/$devices] $result")
        else
            logger.info("[${device.deviceNum + 1}/$devices] $result")
        if(result.status == TestStatus.DISCONNECTED)
            device.testStatus = result.status
        ui.setStatus(device.deviceNum, device.testStatus)
    }

    private fun mapDeviceToSwitchboard(num: Int): Int {
        //  A,  B   CH
        //  0,  5 -> 0
        //  1,  6 -> 1
        //  2,  7 -> 2
        //  3,  8 -> 3
        //  4,  9 -> 4
        // 10, 15 -> 5
        // 11, 16 -> 6
        // 12, 17 -> 7
        // 13, 18 -> 8
        // 14, 19 -> 9
        return when {
            (num < 5) -> num
            (num < 10) -> num - 5
            (num < 15) -> num - 5
            (num < 20) -> num - 10
            else -> -1
        }
    }

    private fun unmapDeviceFromSwitchboard(num: Int): Int {
        return when(activeChannel) {
            ChannelMode.A -> if(num < 5) num else num + 5
            ChannelMode.B -> if (num < 5) num + 5 else num + 10
            else -> 0
        }
    }

    private fun deviceNumToChannel(num: Int): ChannelMode {
        // A  B  A  B
        // 0  5 10 15
        // 1  6 11 16
        // 2  7 12 17
        // 3  8 13 18
        // 4  9 14 19
        return when {
            (num < 5) -> ChannelMode.A
            (num < 10) -> ChannelMode.B
            (num < 15) -> ChannelMode.A
            (num < 20) -> ChannelMode.B
            else -> ChannelMode.OFF
        }
    }
}
