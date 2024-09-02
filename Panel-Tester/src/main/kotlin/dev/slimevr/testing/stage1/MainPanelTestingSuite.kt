package dev.slimevr.testing.stage1

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.Switchboard
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.hardware.usb.USBDmesgWatcher
import dev.slimevr.hardware.usb.USBNotify
import dev.slimevr.testing.*
import dev.slimevr.testing.actions.*
import dev.slimevr.ui.TesterUI
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.logging.Level
import java.util.logging.Logger

class MainPanelTestingSuite(
    private val switchboard: Switchboard,
    private val adcProvider: ADCProvider,
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi: TesterUI,
    private val devices: Int,
    private val logger: Logger,
    private var statusLogger: Logger
) : Thread("Testing suit thread"), USBNotify {

    private val powerBalanceTimeMS = 100L
    private val flashResetPinsMS = 200L
    private val serialBootTimeMS = 200L
    private val bootTimeMS = 1500L
    private val resetTimeMS = 300L
    private val FIRMWARE_BUILD = 17
    private val TEST_CHRG_DIODE_VOLTAGES = false
    private val RETRIES = 5

    private val serialManager = SerialManager()

    private val actionTestVBUS_REF = VoltageTestAction("VBUS reference", 4.6f, 5.5f)
    private val actionTestVBUS_VCC = VoltageTestAction("VCC voltage from VBUS power", 3.3f, 5.5f)
    private val actionTestVBUS_3v3 = VoltageTestAction("3v3 voltage from VBUS power", 2.9f, 3.2f)
    private val actionTestVBUS_Bat = VoltageTestAction("Bat voltage from VBUS power", 3.2f, 4.3f)
    private val actionTestVBUS_CHRG = VoltageTestAction("Chrg LED voltage from VBUS power", 0.0f, 1f)
    private val actionTestVBUS_FULL = VoltageTestAction("Full LED voltage from VBUS power", 0.0f, 1f)

    private val actionTestBAT_REF = VoltageTestAction("Bat reference", 4.6f, 5.5f)
    private val actionTestBAT_VCC = VoltageTestAction("VCC voltage from Bat power", 3.3f, 5.5f)
    private val actionTestBAT_3v3 = VoltageTestAction("3v3 voltage from Bat power", 2.9f, 3.5f)
    private val actionTestBAT_VBUS = VoltageTestAction("VBUS voltage from Bat power", -0.5f, 0.7f)
    private val actionTestBAT_CHRG = VoltageTestAction("Chrg LED voltage from Bat power", -1.0f, 1f)
    private val actionTestBAT_FULL = VoltageTestAction("Full LED voltage from Bat power", -1.0f, 1f)

    private val serialFound = SuccessAction("Find serial port")
    private val serialOpened = SuccessAction("Open serial port")
    private val testIMUCommandFailed = SuccessAction("Test IMU")
    private val getTestCommandFailed = SuccessAction("GET TEST command")
    private val firmwareVersionFailed = SuccessAction("Firmware version check")

    private val firmwareFile = System.getenv("TESTER_FIRMWARE_FILE")

    private val usbPortsMap = mapOf(
        Pair("1-1.3.2", 0), Pair("1-1.4.2", 1), Pair("1-1.2.1", 2), Pair("1-1.4.1", 3), Pair("1-1.3.1", 4),
        Pair("1-1.2.2", 5), Pair("1-1.3.3", 6), Pair("1-1.2.3", 7), Pair("1-1.2.4", 8), Pair("1-1.3.4", 9)
    )

    private val deviceMap = arrayOf(0, 2, 4, 6, 8, 0, 2, 4, 6, 8, 1, 3, 5, 7, 9, 1, 3, 5, 7, 9)
    private val switchboardMap = arrayOf(0, 10, 1, 11, 2, 12, 3, 13, 4, 14)

    private val deviceTests = mutableListOf<DeviceTest>()
    private var testStart = 0L
    private var isTesting = false
    private val committedSuccessfulDeviceIds = mutableListOf<String>()
    private var startTest: Boolean = false
    private var btnPressed: Boolean = false
    private var testOnlyDevices = setOf<Int>()
    private val dmesgWatcher = USBDmesgWatcher(this)
    private val usbMap = mutableMapOf<String, Int>()
    private var isReady = false
    private var activeChannel: Int = 0
    private var testRepeat = false

    override fun run() {
        try {
            val fw = FileReader("testedBoards.txt")
            fw.forEachLine {
                committedSuccessfulDeviceIds.add(it)
                if (committedSuccessfulDeviceIds.size > 30)
                    committedSuccessfulDeviceIds.removeAt(0)
            }
            logger.info("Loaded ${committedSuccessfulDeviceIds.size} old boards")
        } catch (ex: IOException) {
            logger.info("Old boards not loaded (${ex.message})")
        }
        try {
            selfTest()
        } catch (exception: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", exception)
            return
        }
        isReady = true
        dmesgWatcher.start()
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
                testVBUSVoltages()
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
                reportTestResults()
                testEnd()
            } catch (exception: Throwable) {
                logger.log(Level.SEVERE, "Tester error", exception)
            }
            synchronized(this) {
                isTesting = false
            }
            if(!testRepeat) {
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

    override fun setUSB(addr: String, tty: String) {
        val device = unmapDeviceFromSwitchboard(usbPortsMap[addr] ?: return)
        if (tty.isBlank()) {
            testerUi.setUSB(device, tty)
            usbMap.remove(tty)
        } else {
            testerUi.setUSB(device, "/dev/$tty")
            usbMap[tty] = device
        }
    }

    private fun setChannelA() {
        serialManager.closeAllPorts()
        usbMap.clear()
        for (device in deviceTests)
            testerUi.setUSB(device.deviceNum, "")
        activeChannel = 1
        switchboard.enableChannelA()
        sleep(powerBalanceTimeMS)
    }

    private fun setChannelB() {
        serialManager.closeAllPorts()
        usbMap.clear()
        for (device in deviceTests)
            testerUi.setUSB(device.deviceNum, "")
        activeChannel = 2
        switchboard.enableChannelB()
        sleep(powerBalanceTimeMS)
    }

    private fun disableChannels() {
        activeChannel = 0
        switchboard.disableChannels()
    }

    private fun testI2C(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}] Testing I2C...")
        val startTime = System.currentTimeMillis()
        val testI2C = SerialMatchingAction(
            "Test I2C",
            arrayOf(
                """\[INFO ] \[BNO080Sensor:0] Connected to BNO085 on 0x4a""".toRegex()
            ),
            arrayOf(
                "ERR".toRegex(),
                "FATAL".toRegex(),
                "Connected to BNO085 on 0x4b".toRegex()
            ),
            device,
            30000
        )
        val result = testI2C.action("", "", startTime)
        addResult(device, result)
    }

    private fun testIMU(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}] Getting info...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            logger.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            val testIMU = SerialMatchingAction(
                "Test IMU",
                arrayOf(""".*Sensor\[0] sent some data, looks working\..*""".toRegex(RegexOption.IGNORE_CASE)),
                arrayOf(
                    ".*Sensor 1 didn't send any data yet!.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*Sensor\\[0] didn't send any data yet!.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*ERR.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*FATAL.*".toRegex(RegexOption.IGNORE_CASE)
                ),
                device,
                1000
            )
            var result = testIMU.action("", "", startTime)
            addResult(device, result)
        }
    }

    private fun getTest(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}] Getting info...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            logger.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            sleep(200)
            for(i in 0 until 5) {
                if (!device.sendSerialCommand("GET TEST")) {
                    val result = getTestCommandFailed.action(false, "Serial command error", startTime)
                    addResult(device, result)
                    break
                } else {
                    val getTestResult = SerialMatchingAction(
                        "Read firmware state",
                        arrayOf(
                            """.*\[TEST] Board:.*, wifi state: \d.*""".toRegex()
                        ),
                        arrayOf(
                            ".*ERR.*".toRegex(),
                            ".*FATAL.*".toRegex()
                        ),
                        device,
                        200
                    )
                    val result = getTestResult.action("", "", startTime)
                    if(result.status == TestStatus.ERROR && i != 4)
                        continue
                    addResult(device, result)
                    if (result.status == TestStatus.PASS) {
                        statusLogger.info("[${device.deviceNum + 1}] End value: ${result.endValue}")
                        val match = """.*build: (\d+),.*mac: ([a-zA-Z0-9:]+),.*""".toRegex()
                            .matchAt(result.endValue, 0)
                        if (match != null) {
                            statusLogger.info("[${device.deviceNum + 1}] Build: ${match.groupValues[1]}, mac: ${match.groupValues[2]}")
                            if (match.groupValues[1].toInt() == FIRMWARE_BUILD) {
                                device.deviceId = match.groupValues[2].lowercase()
                                testerUi.setID(device.deviceNum, device.deviceId)
                                device.flashingRequired = false
                                return
                            }
                        }
                        val result = firmwareVersionFailed.action(false, result.endValue, startTime)
                        addResult(device, result)
                    }
                }
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
    }

    private fun shouldSkipDevice(deviceNum: Int) = testOnlyDevices.isNotEmpty() && !testOnlyDevices.contains(deviceNum)

    private fun testDevices() {
        if(!hasUnfailedDevicesOnChannel())
            return
        statusLogger.info("Testing devices...")
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (deviceNumToChannel(device.deviceNum) != activeChannel)
                continue
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else {
                statusLogger.info("[${device.deviceNum + 1}/$devices] Testing ${device.deviceId}")
                if(serialManager.openPort(device.serialPort!!, device)) {
                    statusLogger.info("[${device.deviceNum + 1}/$devices] Port opened")
                    //waitBootSequence(device)
                    // Not doing I2C test because it requires catching the boot seq
                    //testI2C(device)
                    getTest(device)
                    testIMU(device)
                } else {
                    val flashResult = serialOpened.action(false, " Can't open port. Error ${device.serialPort!!.lastErrorCode}", System.currentTimeMillis())
                    addResult(device, flashResult)
                }

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
        if(!hasUnfailedDevicesOnChannel())
            return
        statusLogger.info("Flashing devices...")
        //flash()
        val flashThreads = mutableListOf<Thread>()
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (deviceNumToChannel(device.deviceNum) != activeChannel)
                continue
            val startTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null || device.deviceId.isBlank()) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else if (!device.flashingRequired) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping already flashed device")
            } else {
                flashThreads.add(Thread {
                    statusLogger.info("[${device.deviceNum + 1}/$devices] Flashing...")
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
        if(!hasUnfailedDevicesOnChannel())
            return
        statusLogger.info("Reading MAC addresses...")
        flash()
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (deviceNumToChannel(device.deviceNum) != activeChannel)
                continue
            if (device.deviceId.isNotBlank()) {
                logger.info("[${device.deviceNum + 1}/$devices] Has firmware, loaded ${device.deviceId} address")
                testerUi.setID(device.deviceNum, device.deviceId)
                device.commitDevice = true
            } else {
                val startTime = System.currentTimeMillis()
                if (device.testStatus == TestStatus.ERROR || device.serialPort == null) {
                    logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
                } else {
                    statusLogger.info("[${device.deviceNum + 1}/$devices] Reading MAC address")
                    for (retry in 1..RETRIES) {
                        val macAction = ExecuteCommandAction(
                            "Read MAC address",
                            arrayOf(".*MAC: .*".toRegex(RegexOption.IGNORE_CASE)),
                            emptyArray(),
                            "esptool --before no_reset --after no_reset --port ${device.serialPort!!.systemPortPath} read_mac",
                            500
                        )
                        val macResult = macAction.action("", "", startTime)
                        if (retry != RETRIES && macResult.status == TestStatus.ERROR) {
                            logger.warning("[${device.deviceNum + 1}/$devices] Retrying $retry...")
                            switchboard.flashMode(true)
                            sleep(powerBalanceTimeMS)
                            switchboard.disableDevice(mapDeviceToSwitchboard(device.deviceNum))
                            sleep(flashResetPinsMS)
                            switchboard.enableDevice(mapDeviceToSwitchboard(device.deviceNum))
                            sleep(flashResetPinsMS)
                            switchboard.flashMode(false)
                            sleep(resetTimeMS)
                            continue
                        }
                        addResult(device, macResult)
                        if (macResult.status == TestStatus.PASS) {
                            device.deviceId = macResult.endValue.substring(5)
                            testerUi.setID(device.deviceNum, device.deviceId)
                            device.commitDevice = true
                            // We just flashed it successfully, don't flash it again
                            if (committedSuccessfulDeviceIds.contains(device.deviceId))
                                device.flashingRequired = false
                            break
                        }
                    }
                }
            }
        }
    }

    fun getFailedDevices(): List<Int> {
        return deviceTests.filter { it.testStatus == TestStatus.ERROR }.map { it.deviceNum }
    }

    fun hasUnfailedDevicesOnChannel(): Boolean {
        if(activeChannel == 1) {
            for(d in 0..4) {
                if(!shouldSkipDevice(d) && deviceTests[d].testStatus != TestStatus.ERROR)
                    return true
            }
            for(d in 10..14) {
                if(!shouldSkipDevice(d) && deviceTests[d].testStatus != TestStatus.ERROR)
                    return true
            }
        } else if(activeChannel == 2) {
            for(d in 5..9) {
                if(!shouldSkipDevice(d) && deviceTests[d].testStatus != TestStatus.ERROR)
                    return true
            }
            for(d in 15..19) {
                if(!shouldSkipDevice(d) && deviceTests[d].testStatus != TestStatus.ERROR)
                    return true
            }
        }
        return false
    }

    fun transposeDevices() {
        val tmp = mutableMapOf<Int, DeviceTest>()
        deviceTests.forEach {
            val newIndex = transposeDeviceIndex(it.deviceNum)
            it.deviceNum = newIndex
            tmp[newIndex] = it
        }
        deviceTests.clear()
        for (i in 0..9) {
            tmp[i]?.let {
                deviceTests.add(it)
                testerUi.setStatus(it.deviceNum, it.testStatus)
                testerUi.setID(it.deviceNum, it.deviceId)
            }
        }
    }

    private fun testEnd() {
        disableChannels()
        switchboard.disableAll()
        switchboard.powerOff()
        serialManager.closeAllPorts()
        testOnlyDevices = setOf()
        startTest = false
        val testEnd = System.currentTimeMillis()
        statusLogger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    private fun enumerateSerialDevices(): Boolean {
        if(!hasUnfailedDevicesOnChannel())
            return false
        statusLogger.info("Waiting for serial devices...")
        switchboard.powerOff()
        switchboard.disableAll()
        sleep(500L)
        switchboard.powerVbus()
        val startTime = System.currentTimeMillis()
        var portsCount = 0
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (deviceNumToChannel(device.deviceNum) != activeChannel)
                continue
            portsCount++
            switchboard.enableDevice(mapDeviceToSwitchboard(device.deviceNum))
        }
        if(portsCount == 0)
            return false
        sleep(serialBootTimeMS)
        statusLogger.info("VBUS Voltage: " + adcProvider.getVBUSVoltage())
        var foundSerials = 0
        var ports: List<SerialPort>
        val endWait = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < endWait) {
            ports = serialManager.findNewPorts()
            if (ports.size == portsCount)
                break
        }
        sleep(500)
        ports = serialManager.findNewPorts()
        ports.forEach {
            val deviceNum = usbMap[it.systemPortPath.substring(5)]
            if (deviceNum != null) {
                val device = deviceTests[deviceNum]
                device.serialPort = it
                serialManager.markAsKnown(device.serialPort!!)
                foundSerials++
                logger.info("[${device.deviceNum + 1}/$devices] Device used: ${device.serialPort!!.descriptivePortName} ${device.serialPort!!.systemPortPath}")
            }
        }
        deviceTests.forEach {
            if (deviceNumToChannel(it.deviceNum) != activeChannel)
                return@forEach
            if (shouldSkipDevice(it.deviceNum))
                return@forEach
            if (it.serialPort == null) {
                val connectTest = serialFound.action(false, "Serial port not found for the device", startTime)
                addResult(it, connectTest)
            } else {
                val connectTest = serialFound.action(
                    true,
                    "Serial port found: ${it.serialPort!!.descriptivePortName} ${it.serialPort!!.systemPortPath}",
                    startTime
                )
                addResult(it, connectTest)
            }
        }
        statusLogger.info("Found $foundSerials serial devices")
        return true
    }

    private fun checkTestResults(): Boolean {
        var failed = false
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            device.endTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR) {
                failed = true
            } else if (device.testStatus != TestStatus.RETESTED) {
                device.testStatus = TestStatus.PASS
                testerUi.setStatus(device.deviceNum, TestStatus.PASS)
            }
            device.serialPort?.let { serialManager.closePort(it) }
        }
        return failed
    }

    private fun reportTestResults() {
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (device.testStatus == TestStatus.ERROR) {
                logger.severe("[${device.deviceNum + 1}/$devices] ${device.deviceId}: Test failed")
                for (test in device.testsList) {
                    if (test.status == TestStatus.ERROR) {
                        logger.severe(test.toString() + "\n" + test.log)
                        statusLogger.severe("[${device.deviceNum + 1}] ${test.testName}: ${test.endValue}")
                    }
                }
            } else {
                logger.info("[${device.deviceNum + 1}/$devices] ${device.deviceId}: Test success")
            }
        }
    }

    private fun commitTestResults() {
        logger.info("Committing the test results to database...")
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (device.deviceId.isBlank()) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping, no ID")
                continue
            }
            if (committedSuccessfulDeviceIds.contains(device.deviceId)) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping, recently committed as ${device.deviceId}")
                testerUi.setStatus(device.deviceNum, TestStatus.RETESTED)
                continue
            }
            for (db in testingDatabases) {
                val response = db.sendTestData(device)
                logger.info("[${device.deviceNum + 1}/$devices] ${device.deviceId}: $response")
            }
            if (device.testStatus == TestStatus.PASS) {
                committedSuccessfulDeviceIds.add(device.deviceId)
                if (committedSuccessfulDeviceIds.size > 30)
                    committedSuccessfulDeviceIds.removeAt(0)
                try {
                    val fw = FileWriter("testedBoards.txt", true)
                    fw.append(device.deviceId).append('\n')
                    fw.close()
                } catch (ex: IOException) {
                    statusLogger.info("Tested boards file write error: ${ex.message}")
                }
            }
        }
    }

    private fun waitTestStart() {
        disableChannels()
        switchboard.disableAll()
        switchboard.powerOff()
        statusLogger.info("Ready to start the test")
        while (!switchboard.isButtonPressed() && !startTest) {
            sleep(10)
        }
        startTest = false
    }

    private fun waitForButton() {
        logger.info("=== Press the button to continue ===")
        sleep(1000)
        while (!switchboard.isButtonPressed() && !btnPressed) {
            sleep(10)
        }
        btnPressed = false
    }

    private fun testStart() {
        if (testOnlyDevices.isNotEmpty()) {
            testOnlyDevices.forEach {
                testerUi.setStatus(it, TestStatus.TESTING)
                testerUi.setID(it, "")
                if (deviceTests.size < 10) {
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

        for (channel in 0 until 2) {
            if (channel == 0)
                setChannelA()
            else
                setChannelB()
            for (i in 0 until devices) {
                if (deviceNumToChannel(i) != activeChannel)
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum))
                    continue
                statusLogger.info("[${i + 1}/$devices] Testing power from VBUS... ")
                switchboard.enableDevice(mapDeviceToSwitchboard(i))
                sleep(powerBalanceTimeMS)

                val vbus = actionTestVBUS_REF.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
                addResult(device, vbus)

                val vcc = actionTestVBUS_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
                addResult(device, vcc)

                val v33 = actionTestVBUS_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
                addResult(device, v33)

                val bat = actionTestVBUS_Bat.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
                addResult(device, bat)

                if (TEST_CHRG_DIODE_VOLTAGES) {
                    val chrg = actionTestVBUS_CHRG.action(adcProvider.getChrgVoltage(), "", System.currentTimeMillis())
                    addResult(device, chrg)

                    val full = actionTestVBUS_FULL.action(adcProvider.getFullVoltage(), "", System.currentTimeMillis())
                    addResult(device, full)
                }

                switchboard.disableAll()
            }
        }
    }

    private fun testBATVoltages() {
        switchboard.powerOff()
        switchboard.disableAll()
        switchboard.powerBattery()
        sleep(powerBalanceTimeMS)

        for (channel in 0 until 2) {
            if (channel == 0)
                setChannelA()
            else
                setChannelB()
            for (i in 0 until devices) {
                if (deviceNumToChannel(i) != activeChannel)
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum))
                    continue
                statusLogger.info("[${i + 1}/$devices] Testing power from Battery... ")
                switchboard.enableDevice(mapDeviceToSwitchboard(i))
                sleep(powerBalanceTimeMS)

                val bat = actionTestBAT_REF.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
                addResult(device, bat)

                val vbus = actionTestBAT_VBUS.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
                addResult(device, vbus)

                val vcc = actionTestBAT_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
                addResult(device, vcc)

                val v33 = actionTestBAT_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
                addResult(device, v33)

                if (TEST_CHRG_DIODE_VOLTAGES) {
                    val chrg = actionTestBAT_CHRG.action(adcProvider.getChrgVoltage(), "", System.currentTimeMillis())
                    addResult(device, chrg)

                    val full = actionTestBAT_FULL.action(adcProvider.getFullVoltage(), "", System.currentTimeMillis())
                    addResult(device, full)
                }

                switchboard.disableAll()
            }
        }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if (result.status == TestStatus.ERROR)
            logger.severe("[${device.deviceNum + 1}/$devices] $result")
        else
            logger.info("[${device.deviceNum + 1}/$devices] $result")
        testerUi.setStatus(device.deviceNum, device.testStatus)
    }

    private fun transposeDeviceIndex(num: Int): Int {
        return 19 - num
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
