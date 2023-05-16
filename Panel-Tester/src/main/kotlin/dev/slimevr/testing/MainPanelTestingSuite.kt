package dev.slimevr.testing

import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.actions.*
import dev.slimevr.ui.TesterUI
import java.util.logging.Logger

class MainPanelTestingSuite(
    val switchboard: Switchboard,
    val adcProvider: ADCProvider,
    val testingDatabases: List<TestingDatabase>,
    val testerUi: TesterUI,
    val devices: Int
) : Thread("Testing suit thread") {

    /**
     * Full log and end report goes here
     */
    private val logger: Logger = Logger.getLogger("Testing Suit")

    /**
     * Current testing status (stage, etc) goes here
     */
    private var statusLogger: Logger = Logger.getLogger("Status logger")

    val powerBalanceTimeMS = 100L
    var serialBootTimeMS = 300L
    val bootTimeMS = 1500L
    val resetTimeMS = 300L

    val serialManager = SerialManager()

    val actionTestVBUS_REF = VoltageTestAction("VBUS reference", 4.2f, 5.1f)
    val actionTestVBUS_VCC = VoltageTestAction("VCC voltage from VBUS power", 3.3f, 5.1f)
    val actionTestVBUS_3v3 = VoltageTestAction("3v3 voltage from VBUS power", 3.0f, 3.5f)
    val actionTestVBUS_Bat = VoltageTestAction("Bat voltage from VBUS power", 4.0f, 4.5f)

    val actionTestBAT_REF = VoltageTestAction("Bat reference", 4.2f, 5.1f)
    val actionTestBAT_VCC = VoltageTestAction("VCC voltage from Bat power", 3.3f, 5.1f)
    val actionTestBAT_3v3 = VoltageTestAction("3v3 voltage from Bat power", 3.0f, 3.5f)
    val actionTestBAT_VBUS = VoltageTestAction("VBUS voltage from Bat power", -1.0f, 1f)

    val serialFound = SuccessAction("Find serial port")
    val serialOpened = SuccessAction("Open serial port")

    val firmwareFile = ""

    val deviceTests = mutableListOf<DeviceTest>()
    var testStart = 0L
    val flashingRequired = BooleanArray(devices).apply { fill(true) }

    override fun run() {
        logger.info("Testing suit started~")
        while (true) {
            waitTestStart()
            testVBUSVoltages()
            testBATVoltages()
            if (!enumerateSerialDevices()) {
                testEnd()
                continue
            }
            // At this stage all devices should be enabled and only reboot via pins is allowed
            readDeviceIDs()
            // TODO Add check if flashing required
            flashDevices()
            openSerialPorts()
            testI2C()
            testIMU()
            checkTestResultsAndReport()
            finishTestAndCommit()
            testEnd()
        }
    }

    private fun testI2C() {
        statusLogger.info("Testing I2C...")
        logger.info("Rebooting all devices...")
        reboot()
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                val testI2C = SerialMatchingAction(
                    "Test I2C",
                    arrayOf("[INFO ] [BNO080Sensor:0] Connected to BNO085 on 0x4a"),
                    arrayOf("ERR", "FATAL"),
                    device,
                    15000
                )
                val result = testI2C.action("", "", System.currentTimeMillis())
                addResult(device, result)
            }
        }
    }

    private fun testIMU() {
        statusLogger.info("Testing IMU...")
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                val testIMU = SerialMatchingAction(
                    "Test IMU",
                    arrayOf("Sensor 1 sent some data, looks working."),
                    arrayOf("Sensor 1 didn't send any data yet!", "ERR", "FATAL"),
                    device,
                    15000
                )
                val result = testIMU.action("", "", System.currentTimeMillis())
                addResult(device, result)
            }
        }
    }

    private fun openSerialPorts() {
        statusLogger.info("Opening serial ports...")
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                val result = serialOpened.action(
                    serialManager.openPort(device.serialPort!!, device),
                    "Error: ${device.serialPort!!.lastErrorCode}",
                    System.currentTimeMillis()
                )
                addResult(device, result)
            }
        }
    }

    private fun reboot() {
        switchboard.resetMode(true)
        sleep(resetTimeMS)
        switchboard.resetMode(false)
        sleep(bootTimeMS)
    }

    private fun flash() {
        switchboard.flashMode(true)
        sleep(powerBalanceTimeMS)
        switchboard.resetMode(true)
        sleep(resetTimeMS)
        switchboard.resetMode(false)
        switchboard.flashMode(false)
    }

    private fun flashDevices() {
        // TODO Flash in parallel
        flash()
        statusLogger.info("Flashing devices...")
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else if (!flashingRequired[device.deviceNum]) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping already flashed device")
            } else {
                val flashAction = ExecuteCommandAction(
                    "Flash firmware", emptyArray(), emptyArray(),
                    "/usr/bin/python3 /home/pi/.platformio/packages/tool-esptoolpy/esptool.py "
                        + "--before no_reset --after no_reset --chip esp8266 "
                        + "--port ${device.serialPort!!.systemPortPath} "
                        + "--baud 115200 write_flash -fm qio 0x0000 \"${firmwareFile}\"", -1
                )
                val flashResult = flashAction.action("", "", System.currentTimeMillis())
                addResult(device, flashResult)
            }
        }
    }

    private fun readDeviceIDs() {
        flash()
        statusLogger.info("Reading MAC addresses...")
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                val macAction = ExecuteCommandAction(
                    "Read MAC address", arrayOf("MAC: "), emptyArray(),
                    "esptool --port ${device.serialPort!!.systemPortPath} read_mac", 60000
                )
                val macResult = macAction.action("", "", System.currentTimeMillis())
                addResult(device, macResult)
                if (macResult.status == TestStatus.PASS) {
                    device.deviceId = macResult.endValue.substring(5)
                    device.commitDevice = true
                }
            }
        }

    }

    private fun testEnd() {
        switchboard.disableAll()
        switchboard.powerOff()
        switchboard.resetMode(false)
        switchboard.flashMode(false)
        serialManager.closeAllPorts()
        val testEnd = System.currentTimeMillis()
        statusLogger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    private fun enumerateSerialDevices(): Boolean {
        switchboard.disableAll()
        switchboard.powerVbus()
        statusLogger.info("Searching for serial devices...")
        var foundSerials = 0
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to power error")
            } else {
                val testStart = System.currentTimeMillis()
                switchboard.enableDevice(device.deviceNum)
                for (i in 1..10) {
                    sleep(serialBootTimeMS)
                    val newPorts = serialManager.findNewPorts()
                    if (newPorts.size > 1) {
                        logger.severe("[${device.deviceNum + 1}/$devices] More than one new serial device detected")
                        logger.severe(
                            "Tester can not proceed from here and the testing will end immediately, "
                                + "no results will be saved"
                        )
                        return false
                    } else if (newPorts.size == 1) {
                        device.serialPort = newPorts.first()
                        serialManager.markAsKnown(device.serialPort!!)
                        break
                    }
                }
                if (device.serialPort == null) {
                    val connectTest = serialFound.action(false, "", testStart)
                    addResult(device, connectTest)
                    continue
                } else {
                    val connectTest = serialFound.action(true, "", testStart)
                    addResult(device, connectTest)
                    foundSerials++
                }
            }
        }
        statusLogger.info("Found $foundSerials serial devices")
        return true
    }

    private fun checkTestResultsAndReport(): Boolean {
        var failed = false
        for (device in deviceTests) {
            if (device.testStatus == TestStatus.ERROR) {
                failed = true
                logger.severe("[${device.deviceNum + 1}/$devices] Test failed:")
                for (test in device.testsList) {
                    if (test.status == TestStatus.ERROR) {
                        logger.severe(test.toString())
                    }
                }
            } else {
                device.testStatus = TestStatus.PASS
                testerUi.setStatus(device.deviceNum, TestStatus.PASS)
            }
            device.serialPort?.let { serialManager.closePort(it) }
        }
        return failed
    }

    private fun finishTestAndCommit() {
        logger.info("Committing the test results to database...")
        // TODO write data to databases that are not created yet
    }

    private fun waitTestStart() {
        testerUi.statusLogNandler.clear()
        switchboard.disableAll()
        switchboard.powerOff()
        testerUi.clear()
        deviceTests.clear()
        for (i in 0 until devices) {
            deviceTests.add(DeviceTest(i))
        }
        statusLogger.info("Ready to start the test")
        while (!switchboard.isButtonPressed()) {
            sleep(10)
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
