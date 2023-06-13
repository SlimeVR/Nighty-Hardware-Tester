package dev.slimevr.testing

import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.actions.*
import dev.slimevr.ui.TesterUI
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.log

class MainPanelTestingSuite(
    private val switchboard: Switchboard,
    private val adcProvider: ADCProvider,
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi: TesterUI,
    private val devices: Int,
    private val logger: Logger,
    private var statusLogger: Logger
) : Thread("Testing suit thread") {

    private val powerBalanceTimeMS = 1500L
    private val flashResetPinsMS = 1500L
    private var serialBootTimeMS = 200L
    private val bootTimeMS = 1500L
    private val resetTimeMS = 1500L
    private val retries = 3
    private val FIRMWARE_BUILD = 15

    private val serialManager = SerialManager()

    private val actionTestVBUS_REF = VoltageTestAction("VBUS reference", 4.6f, 5.3f)
    private val actionTestVBUS_VCC = VoltageTestAction("VCC voltage from VBUS power", 3.3f, 5.3f)
    private val actionTestVBUS_3v3 = VoltageTestAction("3v3 voltage from VBUS power", 2.9f, 3.2f)
    private val actionTestVBUS_Bat = VoltageTestAction("Bat voltage from VBUS power", 4.0f, 4.5f)
    private val actionTestVBUS_CHRG = VoltageTestAction("Chrg LED voltage from VBUS power", 0.0f, 1f)
    private val actionTestVBUS_FULL = VoltageTestAction("Full LED voltage from VBUS power", 0.0f, 1f)

    private val actionTestBAT_REF = VoltageTestAction("Bat reference", 4.6f, 5.3f)
    private val actionTestBAT_VCC = VoltageTestAction("VCC voltage from Bat power", 3.3f, 5.3f)
    private val actionTestBAT_3v3 = VoltageTestAction("3v3 voltage from Bat power", 2.9f, 3.5f)
    private val actionTestBAT_VBUS = VoltageTestAction("VBUS voltage from Bat power", -0.5f, 0.5f)
    private val actionTestBAT_CHRG = VoltageTestAction("Chrg LED voltage from Bat power", -1.0f, 1f)
    private val actionTestBAT_FULL = VoltageTestAction("Full LED voltage from Bat power", -1.0f, 1f)

    private val serialFound = SuccessAction("Find serial port")
    private val serialOpened = SuccessAction("Open serial port")
    private val testIMUCommandFailed = SuccessAction("Test IMU")

    private val firmwareFile = "/home/pi/slimevr-tracker-esp/.pio/build/esp12e/firmware.bin"

    private val deviceTests = mutableListOf<DeviceTest>()
    private var testStart = 0L
    private var committedSuccessfulDeviceIds = mutableListOf<String>()

    override fun run() {
        try {
            selfTest()
        } catch(exception: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", exception)
            return
        }
        logger.info("Testing suit started~")
        while (true) {
            try {
                waitTestStart()
                testStart()
            } catch(exception: Throwable) {
                logger.log(Level.SEVERE, "Standby error, can't continue", exception)
                return
            }
            try {
                switchboard.disableAll()
                switchboard.powerVbus()
                for(i in 0..9)
                    switchboard.enableDevice(i)
                waitForButton()
//                testVBUSVoltages()
//                testBATVoltages()
//                checkFirmware()
//                if (enumerateSerialDevices()) {
//                    readDeviceIDs()
//                    flashDevices()
////                    testDevices()
//                }
//                checkTestResults()
//                commitTestResults()
//                reportTestResults()
                testEnd()
            } catch(exception: Throwable) {
                logger.log(Level.SEVERE, "Tester error", exception)
            }
            waitTestStart()
        }
    }

    private fun checkFirmware() {
        switchboard.powerOff()
        switchboard.disableAll()
        sleep(powerBalanceTimeMS)
        switchboard.powerVbus()
        statusLogger.info("Checking firmware status...")
        for(device in deviceTests) {
            // TODO : Run in parallel?
            val startTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR) {// || device.serialPort == null) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                statusLogger.info("[${device.deviceNum + 1}/$devices] Fetching data...")
                switchboard.enableDevice(device.deviceNum)
                if(findSerialPort(device, startTime)) {
                    //for (i in 1..5) {
                        if (serialManager.openPort(device.serialPort!!, device)) {
                            statusLogger.info("[${device.deviceNum + 1}/$devices] Port ${device.serialPort!!.systemPortPath} open")
                            if (device.sendSerialCommand("GET TEST")) {
                                statusLogger.info("[${device.deviceNum + 1}/$devices] GET TEST command sent")
                                val testIMU = SerialMatchingAction(
                                    "Read firmware state",
                                    arrayOf(
                                        """.*mac: [A-Za-z0-9]{2}:[A-Za-z0-9]{2}:[A-Za-z0-9]{2}:[A-Za-z0-9]{2}:[A-Za-z0-9]{2}:[A-Za-z0-9]{2}.*""".toRegex()
                                    ),
                                    arrayOf(
                                        ".*ERR.*".toRegex(),
                                        ".*FATAL.*".toRegex()
                                    ),
                                    device,
                                    3000
                                )
                                val result = testIMU.action("", "", startTime)
                                // Check mac, firmware and hardware version, check IMU too?
                                //logger.info("[${device.deviceNum + 1}/$devices] $result:\n${result.log}")
                                if (result.status == TestStatus.PASS) {
                                    logger.info("[${device.deviceNum + 1}/$devices] End value: ${result.endValue}")
                                    val match = """.*build: (\d+),.*mac: ([a-zA-Z0-9:]+),.*""".toRegex()
                                        .matchAt(result.endValue, 0)
                                    if (match != null) {
                                        logger.info("[${device.deviceNum + 1}/$devices] Build: ${match.groupValues[1]}, mac: ${match.groupValues[2]}")
                                        if (match.groupValues[1].toInt() == FIRMWARE_BUILD) {
                                            device.deviceId = match.groupValues[2].lowercase()
                                            testerUi.setID(device.deviceNum, device.deviceId)
                                            device.flashingRequired = false
                                            logger.info("[${device.deviceNum + 1}/$devices] No flashing required")
                                        }
                                    }
                                }
                            }
                        }
                    serialManager.closePort(device.serialPort!!)
                }
                switchboard.disableDevice(device.deviceNum)
            }
        }
    }

    private fun selfTest() {
        switchboard.disableAll()
        switchboard.powerOff()
        sleep(powerBalanceTimeMS)
        logger.info("=== Testing suite self-test: ===")
        logger.info("VBUS voltage: ${adcProvider.getVBUSVoltage()}")
        logger.info("BAT voltage: ${adcProvider.getBatVoltage()}")
        logger.info("3v3 voltage: ${adcProvider.get3v3Voltage()}")
        logger.info("VCC voltage: ${adcProvider.getVCCVoltage()}")
        logger.info("Chrg voltage: ${adcProvider.getChrgVoltage()}")
        logger.info("Full voltage: ${adcProvider.getFullVoltage()}")
//        logger.info("=== Enabling all... ===")
//        for(i in 0..9)
//            switchboard.enableDevice(i)
//        switchboard.powerVbus()
//        sleep(bootTimeMS)
//        logger.info("VBUS voltage: ${adcProvider.getVBUSVoltage()}")
//        logger.info("BAT voltage: ${adcProvider.getBatVoltage()}")
//        logger.info("3v3 voltage: ${adcProvider.get3v3Voltage()}")
//        logger.info("VCC voltage: ${adcProvider.getVCCVoltage()}")
//        logger.info("Chrg voltage: ${adcProvider.getChrgVoltage()}")
//        logger.info("Full voltage: ${adcProvider.getFullVoltage()}")
//        switchboard.disableAll()
//        switchboard.powerOff()
//        sleep(bootTimeMS)
    }

    private fun testDevices() {
        statusLogger.info("Testing devices...")
        serialManager.closeAllPorts()
        switchboard.disableAll()
        switchboard.powerVbus()
        sleep(bootTimeMS)
        for(device in deviceTests) {
            val startTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                statusLogger.info("[${device.deviceNum + 1}/$devices] Testing ${device.deviceId}")
                switchboard.enableDevice(device.deviceNum)
                sleep(serialBootTimeMS)
                if(findSerialPort(device, startTime)) {
                    // todo open
                    testI2C(device)
                    testIMU(device)
                }
                serialManager.closeAllPorts()
                switchboard.disableAll()
            }
        }
    }

    private fun testI2C(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}/$devices] Testing I2C...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
            logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
        } else {
            val testI2C = SerialMatchingAction(
                "Test I2C",
                arrayOf(
                    """\[INFO ] \[BNO080Sensor:0] Connected to BNO085 on 0x4a""".toRegex()
                ),
                arrayOf(
                    "ERR".toRegex(),
                    "FATAL".toRegex()
                ),
                device,
                15000
            )
            val result = testI2C.action("", "", startTime)
            addResult(device, result)
        }
    }

    private fun testIMU(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}/$devices] Testing IMU...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
            logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
        } else {
            val result: TestResult
            if (!device.sendSerialCommand("GET TEST")) {
                result = testIMUCommandFailed.action(false, "Serial command error", startTime)
            } else {
                val testIMU = SerialMatchingAction(
                    "Test IMU",
                    arrayOf(""".*Sensor 1 sent some data, looks working\..*""".toRegex(RegexOption.IGNORE_CASE)),
                    arrayOf(
                        ".*Sensor 1 didn't send any data yet!.*".toRegex(RegexOption.IGNORE_CASE),
                        ".*ERR.*".toRegex(RegexOption.IGNORE_CASE),
                        ".*FATAL.*".toRegex(RegexOption.IGNORE_CASE)
                    ),
                    device,
                    15000
                )
                result = testIMU.action("", "", startTime)
            }
            addResult(device, result)
        }
    }

    private fun findSerialPort(device: DeviceTest, startTime: Long): Boolean {
        if (device.testStatus == TestStatus.ERROR) {
            logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            return false
        } else {
            for (i in 1..10) {
                sleep(serialBootTimeMS)
                val newPorts = serialManager.findNewPorts()
                if (newPorts.size > 1) {
                    logger.severe("[${device.deviceNum + 1}/$devices] More than one new serial device detected")
                    logger.severe(
                        "Tester can not proceed from here and the testing will end immediately, "
                            + "no results will be saved. Found ports:"
                    )
                    newPorts.forEach {
                        logger.severe("${it.portDescription} : ${it.descriptivePortName} : ${it.systemPortPath} : ${it.portLocation}")
                    }
                    for (otherDevice in deviceTests) {
                        otherDevice.addTestResult(serialFound.action(false, "More than one serial port found: ${newPorts.joinToString { p -> p.descriptivePortName + " " + p.systemPortPath }}", startTime))
                    }
                    throw IOException("Too many opened serial ports")
                } else if (newPorts.size == 1) {
                    device.serialPort = newPorts.first()
                    serialManager.markAsKnown(device.serialPort!!)
                    logger.info("[${device.deviceNum + 1}/$devices] Device used: ${device.serialPort!!.descriptivePortName} ${device.serialPort!!.systemPortPath}")
                    break
                } else {
                    sleep(500)
                }
            }
            if (device.serialPort == null) {
                val connectTest = serialFound.action(false, "No serial ports found", startTime)
                addResult(device, connectTest)
                return false
            } else {
                val connectTest = serialFound.action(true, "Serial port found: ${device.serialPort!!.descriptivePortName} ${device.serialPort!!.systemPortPath}", startTime)
                addResult(device, connectTest)
                return true
            }
        }


    }

    private fun reboot() {
        switchboard.resetMode(true)
        sleep(flashResetPinsMS)
        switchboard.resetMode(false)
        sleep(bootTimeMS)
    }

    private fun flash() {
        switchboard.flashMode(true)
        sleep(powerBalanceTimeMS)
        switchboard.resetMode(true)
        sleep(flashResetPinsMS)
        switchboard.resetMode(false)
        sleep(flashResetPinsMS)
        switchboard.flashMode(false)
        sleep(resetTimeMS)
    }

    private fun flashDevices() {
        statusLogger.info("Flashing devices...")
        flash()
        val flashThreads = mutableListOf<Thread>()
        for (device in deviceTests) {
            val startTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else if (!device.flashingRequired) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping already flashed device")
            } else {
                flashThreads.add(Thread {
                    val flashAction = ExecuteCommandAction(
                        "Flash firmware", arrayOf(
                            ".*Hash of data verified.*".toRegex(RegexOption.IGNORE_CASE)
                        ), arrayOf(
                            ".*Errno.*".toRegex(RegexOption.IGNORE_CASE),
                            ".*error.*".toRegex(RegexOption.IGNORE_CASE)
                        ),
                        "/usr/bin/python3 /home/pi/.platformio/packages/tool-esptoolpy/esptool.py "
                            + "--before no_reset --after no_reset --chip esp8266 "
                            + "--port ${device.serialPort!!.systemPortPath} "
                            + "--baud 3000000 write_flash -fm qio 0x0000 $firmwareFile", -1
                    )
                    val flashResult = flashAction.action("", "", startTime)
                    addResult(device, flashResult)
                })
            }
        }
        flashThreads.forEach { it.start() }
        flashThreads.forEach { it.join() }
    }

    private fun readDeviceIDs() {
        statusLogger.info("Reading MAC addresses...")
        flash()
        for (device in deviceTests) {
            if(device.deviceId.isNotBlank()) {
                logger.info("[${device.deviceNum + 1}/$devices] Has firmware, loaded ${device.deviceId} address")
                testerUi.setID(device.deviceNum, device.deviceId)
                device.commitDevice = true
            } else {
                val startTime = System.currentTimeMillis()
                if (device.testStatus == TestStatus.ERROR || device.serialPort == null) {
                    logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
                } else {
                    val macAction = ExecuteCommandAction(
                        "Read MAC address",
                        arrayOf(".*MAC: .*".toRegex(RegexOption.IGNORE_CASE)),
                        emptyArray(),
                        "esptool --before no_reset --after no_reset --port ${device.serialPort!!.systemPortPath} read_mac",
                        10000
                    )
                    val macResult = macAction.action("", "", startTime)
                    addResult(device, macResult)
                    if (macResult.status == TestStatus.PASS) {
                        device.deviceId = macResult.endValue.substring(5)
                        testerUi.setID(device.deviceNum, device.deviceId)
                        device.commitDevice = true
                    }
                }
            }
        }
    }

    private fun testEnd() {
        switchboard.disableAll()
        switchboard.powerOff()
        serialManager.closeAllPorts()
        val testEnd = System.currentTimeMillis()
        statusLogger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    private fun enumerateSerialDevices(): Boolean {
        statusLogger.info("Searching for serial devices...")
        switchboard.powerOff()
        switchboard.disableAll()
        sleep(powerBalanceTimeMS)
        switchboard.powerVbus()
        var foundSerials = 0
        for (device in deviceTests) {
            val startTime = System.currentTimeMillis()
            switchboard.enableDevice(device.deviceNum)
            if(findSerialPort(device, startTime)) {
                foundSerials++
//                break
            } else {
                //switchboard.disableDevice(device.deviceNum)
            }
        }
        statusLogger.info("Found $foundSerials serial devices")
        return true
    }

    private fun checkTestResults(): Boolean {
        var failed = false
        for (device in deviceTests) {
            device.endTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR) {
                failed = true
            } else {
                device.testStatus = TestStatus.PASS
                testerUi.setStatus(device.deviceNum, TestStatus.PASS)
            }
            device.serialPort?.let { serialManager.closePort(it) }
        }
        return failed
    }

    private fun reportTestResults() {
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR) {
                logger.severe("[${device.deviceNum + 1}/$devices] ${device.deviceId}: Test failed")
                for (test in device.testsList) {
                    if (test.status == TestStatus.ERROR) {
                        logger.severe(test.toString() + "\n" + test.log)
                    }
                }
            } else {
                logger.severe("[${device.deviceNum + 1}/$devices] ${device.deviceId}: Test success")
            }
        }
    }

    private fun commitTestResults() {
        logger.info("Committing the test results to database...")
        for(device in deviceTests) {
            if(device.deviceId.isBlank()) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping, no ID")
                continue
            }
            if(committedSuccessfulDeviceIds.contains(device.deviceId)) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping, recently committed as ${device.deviceId}")
                continue
            }
            committedSuccessfulDeviceIds.add(device.deviceId)
            if(committedSuccessfulDeviceIds.size > 30)
                committedSuccessfulDeviceIds.removeAt(0)
            for(db in testingDatabases) {
                val response = db.sendTestData(device)
                logger.info("[${device.deviceNum + 1}/$devices] ${device.deviceId}: $response")
            }
        }
    }

    private fun waitTestStart() {
        switchboard.disableAll()
        switchboard.powerOff()
        statusLogger.info("Ready to start the test")
        while (!switchboard.isButtonPressed()) {
            sleep(10)
        }
    }

    private fun waitForButton() {
        logger.info("=== Press the button to continue ===")
        sleep(1000)
        while (!switchboard.isButtonPressed()) {
            sleep(10)
        }
    }

    private fun testStart() {
        testerUi.statusLogHandler.clear()
        testerUi.clear()
        deviceTests.clear()
        for (i in 0 until devices) {
            val device = DeviceTest(i)
            deviceTests.add(device)
        }
        statusLogger.info("We goin'~")
        testStart = System.currentTimeMillis()
        for (i in 0 until devices) {
            testerUi.setStatus(i, TestStatus.TESTING)
            testerUi.setID(i, "")
        }
    }

    private fun testVBUSVoltages() {
        switchboard.powerOff()
        switchboard.disableAll()
        switchboard.powerVbus()
        sleep(powerBalanceTimeMS)

        for (i in 0 until devices) {
            val device = deviceTests[i]
            statusLogger.info("[${i + 1}/$devices] Testing power from VBUS... ")
            switchboard.enableDevice(i)
            sleep(powerBalanceTimeMS)

            val vbus = actionTestVBUS_REF.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
            addResult(device, vbus)

            val vcc = actionTestVBUS_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
            addResult(device, vcc)

            val v33 = actionTestVBUS_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
            addResult(device, v33)

            val bat = actionTestVBUS_Bat.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
            addResult(device, bat)

//            val chrg = actionTestVBUS_CHRG.action(adcProvider.getChrgVoltage(), "", System.currentTimeMillis())
//            addResult(device, chrg)
//
//            val full = actionTestVBUS_FULL.action(adcProvider.getFullVoltage(), "", System.currentTimeMillis())
//            addResult(device, full)

            switchboard.disableAll()
        }
    }

    private fun testBATVoltages() {
        switchboard.powerOff()
        switchboard.disableAll()
        switchboard.powerBattery()
        sleep(powerBalanceTimeMS)

        for (i in 0 until devices) {
            val device = deviceTests[i]
            statusLogger.info("[${i + 1}/$devices] Testing power from Battery... ")
            switchboard.enableDevice(i)
            sleep(powerBalanceTimeMS)

            val bat = actionTestBAT_REF.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
            addResult(device, bat)

            val vbus = actionTestBAT_VBUS.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
            addResult(device, vbus)

            val vcc = actionTestBAT_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
            addResult(device, vcc)

            val v33 = actionTestBAT_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
            addResult(device, v33)

//            val chrg = actionTestBAT_CHRG.action(adcProvider.getChrgVoltage(), "", System.currentTimeMillis())
//            addResult(device, chrg)
//
//            val full = actionTestBAT_FULL.action(adcProvider.getFullVoltage(), "", System.currentTimeMillis())
//            addResult(device, full)

            switchboard.disableAll()
        }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if(result.status == TestStatus.ERROR)
            logger.severe("[${device.deviceNum + 1}/$devices] $result")
        else
            logger.info("[${device.deviceNum + 1}/$devices] $result")
        testerUi.setStatus(device.deviceNum, device.testStatus)
    }
}
