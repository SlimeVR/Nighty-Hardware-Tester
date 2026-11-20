package dev.slimevr.testing.stage6

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.database.TestedDevicesLocalCache
import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.SwitchboardStage6
import dev.slimevr.hardware.spi.MCP3008
import dev.slimevr.hardware.i2c.MCP23017
import dev.slimevr.hardware.i2c.*
import dev.slimevr.hardware.serial.ButterflyTrackerPortFilter
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.hardware.usb.USBDmesgWatcher
import dev.slimevr.hardware.usb.USBNotify
import dev.slimevr.logger.LogManager
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.actions.SerialMatchingAction
import dev.slimevr.testing.actions.VoltageTestAction
import dev.slimevr.testing.stage5.DongleDeviceTest
import dev.slimevr.ui.stage6.TesterButterflyDongleUI
import java.util.logging.Level
import java.util.logging.Logger
import java.rmi.UnexpectedException

class ButterflyDonglePanelTestingSuite(
    val board: SwitchboardStage6,
    private val dbs: List<TestingDatabase>,
    private val ui: TesterButterflyDongleUI,
    private val deviceLoggers: Array<Logger>,
    val logger: Logger
    ) : Thread("Testing suit thread"), USBNotify {


    private val powerBalanceTimeMS = 100L
    private val serialEnumTimeMS = 2000L

    private val devices = 20
    private val SUBMIT_RESULTS = false
    private val RETEST_RETRY = false

    private val actionBusFrom3V = VoltageTestAction("Bus voltage from 3V3 power", 3.25f, 3.35f) // 3v3
    private val actionEmptyBusFrom5V = VoltageTestAction("Empty voltage from USB power", 0f, 0.1f) // 0v

    private var hubLocationPrefix = "1-1.1" // Default location
    private var hubCommonLocationPrefix = "1-1"
    private var probeHubLocations = arrayOf("1", "2")
    private var channelHubLocations = arrayOf("1.3", "1.4", "1.2", "1.7", "1.1", "1.6", "1.5", "2.4", "2.3", "2.7", "2.2", "2.6", "2.1", "2.5", "3.3", "3.4", "3.2", "3.6", "3.1", "3.5")
    private var probeSerials = mutableListOf("unknown", "unknown")

    private val firmwareFile = System.getenv("TESTER_FIRMWARE_FILE")
    private val FIRMWARE_HASH = System.getenv("TESTER_FIRMWARE_COMMIT") // Git hash
    private val emptyFile = "blank.hex"
    private val EMPTY_ID_PREFIX = "00000000:"

    private val serialManager = SerialManager(ButterflyTrackerPortFilter(), sendReset = false)
    private val dmesgWatcher = USBDmesgWatcher(this)

    private var dongle = DongleDeviceTest()
    private val deviceTests = mutableListOf<DeviceTest>()
    private val committedList = TestedDevicesLocalCache("testedList.txt", logger, logger)
    private var testStart = 0L
    private var isTesting = false
    private var startTest: Boolean = false
    private var testOnlyDevices = setOf<Int>()
    private var testOnlyDevicesReflash: Boolean = true
    private var testEraseFlash: Boolean = false
    private var isReady = false
    private var testRepeat = false

    init {
        logger.setLevel(Level.CONFIG)
        for(deviceLogger in deviceLoggers)
            deviceLogger.setLevel(Level.CONFIG)
        Logger.getLogger("devices").setLevel(Level.WARNING)
        Logger.getLogger("dongle").setLevel(Level.WARNING)
    }

    override fun run() {
        committedList.load()
        dmesgWatcher.start()
        try {
            selfTest()
        } catch (ex: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", ex)
            LogManager.exceptionsLogger.log(Level.SEVERE, "Self-test error ${ex.message}", ex)
            return
        }
        isReady = true
        logger.info("Testing suit started~")
        while (true) {
            try {
                waitTestStart()
                // if (!lookupSelfUsbDevices()) {
                //     continue
                // }
                testStart()
            } catch (ex: Throwable) {
                logger.log(Level.SEVERE, "Standby error, can't continue", ex)
                LogManager.exceptionsLogger.log(Level.SEVERE, "Standby error ${ex.message}", ex)
                return
            }
            synchronized(this) {
                isTesting = true
            }
            try {
                testVoltage()
                flashDevices()
                if(testEraseFlash) {
                    testEnd(TestStatus.NOT_UPDATED)
                    synchronized(this) {
                        isTesting = false
                    }
                    // Do not test, do not commit
                    continue
                }
                enumerateAndTestDevices()
                val ok = checkTestResults()
                commitTestResults()
                showTestErrors()
                testEnd(if(ok) TestStatus.PASS else TestStatus.ERROR)
            } catch (exception: Throwable) {
                logger.log(Level.SEVERE, "Tester error", exception)
            }
            synchronized(this) {
                isTesting = false
            }
            repeatTestForFailedDevices()
        }
    }

    fun isReady() = isReady

    private fun waitTestStart() {
        //board.disableAll() // TODO
        logger.info("=== Ready ===")
        while (!startTest) {
            sleep(10)
        }
        startTest = false
    }

    fun startTest(vararg devices: Int, reflash: Boolean = true, eraseFlash: Boolean = false) {
        synchronized(this) {
            if (isTesting)
                return
        }
        // TODO one device doesn't work somewhere, maybe hardware flash/reset error
        testOnlyDevices = setOf(*devices.toTypedArray())
        testOnlyDevicesReflash = reflash
        testEraseFlash = eraseFlash
        testRepeat = devices.isNotEmpty()
        startTest = true
    }

    fun repeatTestForFailedDevices() {
        if (RETEST_RETRY && !testRepeat) {
            var failed = getFailedDevices()
            if (failed.isNotEmpty()) {
                logger.info("Repeating test for ${failed.size} devices...")
                testRepeat = true
                startTest(*failed.toIntArray(), reflash = false)
            }
        } else {
            testRepeat = false
        }
    }

    private fun testEnd(status: TestStatus) {
        serialManager.removePort(dongle.serialPort!!)
        serialManager.closeAllPorts()
        sleep(500)

        //board.disableAll()

        testOnlyDevices = setOf()
        startTest = false
        testEraseFlash = false
        val testEnd = System.currentTimeMillis()
        logger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    fun Int.toHexString(): String = String.format("%02x", this)

    private fun selfTest() {
        logger.info("=== Self-test ===")
        //board.power(14, SwitchboardStage6.GpioPinMode.USB, true)
        board.swd(true)
        sleep(serialEnumTimeMS)

        logger.info("USB connected:")
        SerialPort.getCommPorts().forEach {
            logger.info("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
        }

        searchForSelfUsbDevices()

        board.disableAll()
        sleep(powerBalanceTimeMS)

        testSelfVoltages()
    }

    val infoResultSuccess = arrayOf("Channel frequency:.*".toRegex())
    val infoResultFail = arrayOf("Unknown command".toRegex())

    private fun searchForSelfUsbDevices() {
        if (!lookupSelfUsbDevices()) {
            throw UnexpectedException("Tester USB devices enumeration failed")
        }

        if (serialManager.openPort(dongle.serialPort!!, dongle)) {
            sleep(10)
            val startTime = System.currentTimeMillis()
            if (!dongle.sendSerialCommand("info")) {
                serialManager.closePort(dongle.serialPort!!)
                throw UnexpectedException("USB communication with Dongle failed")
            } else {
                // Wait for the command output and disconnect
                val getInfoResult = SerialMatchingAction("Read address", infoResultSuccess, infoResultFail, dongle, 500)
                // Address and channel lookup
                with(getInfoResult.action("", "", startTime)) {
                    serialManager.closePort(dongle.serialPort!!)
                    if (status == TestStatus.ERROR)
                        throw UnexpectedException("USB communication with Dongle error")
                    //logger.warning(log)
                    val buildDate = matchLog("Build: ([ 0-9:-]+)")?.get(1)
                        ?: throw UnexpectedException("Can't parse Dongle build date")
                    val buildHash = matchLog("Commit: ([a-zA-Z0-9]+)")?.get(1)
                        ?: throw UnexpectedException("Can't parse Dongle build hash")
                    logger.info("Dongle firmware: $buildDate ($buildHash)")
                    dongle.deviceId = matchLog("Device address: ([a-zA-Z0-9:]+)")?.get(1)?.uppercase()
                        ?: throw UnexpectedException("Can't parse Dongle addresss")
                    dongle.channel = matchLog("Channel frequency: ([0-9]+)")?.get(1)?.toInt()
                        ?: throw UnexpectedException("Can't parse Dongle channel")
                    logger.config("Dongle address: ${dongle.deviceId}, channel: ${dongle.channel}")
                }
            }
        } else {
            serialManager.closePort(dongle.serialPort!!)
            // TODO: Implement retry
            throw UnexpectedException("USB connection with Dongle failed")
        }
    }

    private fun lookupSelfUsbDevices(): Boolean {
        probeSerials[0] = "unknown"
        probeSerials[1] = "unknown"
        dongle.serialPort = null
        val usbList = SerialPort.getCommPorts().filter { p ->
            p.systemPortPath.startsWith("/dev/ttyACM")
        }.toList()
        // Look for SWD devices first
        usbList.forEach { p ->
            val name = p.descriptivePortName.lowercase()
            if (name.contains("multiprobe")) {
                hubLocationPrefix = p.portLocation.dropLast(1)
                //logger.warning("HUB Prefix: $hubLocationPrefix")
                val locationSuffix = p.portLocation.drop(hubLocationPrefix.length)
                val probeIdx: Int = probeHubLocations.indexOfFirst { s -> s.contentEquals(locationSuffix) }
                if(probeIdx != -1)
                    probeSerials[probeIdx] = p.serialNumber
            }
        }
        hubCommonLocationPrefix = hubLocationPrefix.dropLast(2) // Common hub is one level up
        // Look for the Dongle next to SWD
        usbList.forEach { p ->
            val name = p.descriptivePortName.lowercase()
            if (name.contains("slimenrf receiver") || name.contains("slimevr butterfly dongle")) {
                if(p.portLocation.startsWith(hubLocationPrefix)) {
                    dongle.serialPort = p
                }
            }
        }
        var probesOk: Boolean = (probeSerials.count{ s -> s.contentEquals("unknown") } == 0)
        probesOk = probesOk or (probeSerials.count{ s -> s.contentEquals("unknown") } == 1) // XXX Continue with single SWD
        val dongleOk = (dongle.serialPort != null)
        logger.log(
            if (probesOk && dongleOk) Level.CONFIG else Level.SEVERE,
                "Butterfly Dongle usb@${dongle.serialPort?.portLocation ?: "unknown"}" +
                    ", SWD probes serial: ${probeSerials[0]}, ${probeSerials[1]}"
            )
        return probesOk && dongleOk
    }

    private fun testSelfVoltages() {
        var success = true
        // Test 5V
        for (i in 0 until devices) {
            deviceLoggers[i].info("[${i + 1}] Self-test")
            board.power(i, SwitchboardStage6.GpioPinMode.USB, true)
            sleep(10) // Minimize surge current
        }
        for (i in 0 until devices) {
            val f = board.is5vFaultAsserted(i)
            if(f) {
                logger.severe("[${i + 1}] Power fault is abnormaly asserted")
                deviceLoggers[i].severe("[${i + 1}] Power Fault")
                ui.setStatus(i, TestStatus.ERROR)
                success = false
            }
            val v = board.readADC(i)
            if(actionEmptyBusFrom5V.fail(v)) {
                logger.severe("[${i + 1}] Empty target voltage out of range (${actionEmptyBusFrom5V.minValue}, ${actionEmptyBusFrom5V.maxValue}): $v")
                deviceLoggers[i].severe("[${i + 1}] Empty bus: ${v}v")
                ui.setStatus(i, TestStatus.ERROR)
                success = false
            } else {
                logger.config("[${i + 1}] Empty target voltage: $v")
            }
        }
        board.disableGPIO()
        sleep(powerBalanceTimeMS)

        // Test 3V3
        for (i in 0 until devices) {
            board.power(i, SwitchboardStage6.GpioPinMode.V3V3, true)
            sleep(10) // Minimize surge current
        }
        for (i in 0 until devices) {
            val v = board.readADC(i)
            if(actionBusFrom3V.fail(v)) {
                logger.warning("[${i + 1}] 3V3 bus voltage out of range (${actionBusFrom3V.minValue}, ${actionBusFrom3V.maxValue}): $v")
                deviceLoggers[i].severe("[${i + 1}] 3V3 bus: ${v}v")
                ui.setStatus(i, TestStatus.ERROR)
                success = false
            } else {
                logger.config("[${i + 1}] 3V3 bus voltage: $v")
            }
            deviceLoggers[i].info("[${i + 1}] == Done ==")
        }
        board.disableGPIO()
        sleep(powerBalanceTimeMS)

        if(success)
            logger.config("Voltage self test: PASS")
        else
            logger.severe("Voltage self test: FAILED")
    }

    private fun testStart() {
        // Ignore dongle port for new enumerations
        serialManager.markAsKnown(dongle.serialPort!!)

        if (testOnlyDevices.isNotEmpty()) {
            // Re-test selected devices
            testOnlyDevices.forEach {
                ui.setStatus(it, TestStatus.TESTING)
                ui.setID(it, "")
                if (deviceTests.isEmpty()) {
                    for (i in 0 until devices) {
                        val device = DeviceTest(i)
                        deviceTests.add(device)
                    }
                }
                // Do not recreate device, to prevent reflashing
                //deviceTests[it] = DeviceTest(it)
                deviceTests[it].testStatus = TestStatus.TESTING
                deviceTests[it].deviceId = ""
                //ui.clear(it)
                logger.info("Retesting device ${it + 1}")
                testStart = System.currentTimeMillis()
            }
            return // Do not clear the state
        }

        ui.fullLogHandler.clear()
        ui.clearAll()
        deviceTests.clear()
        for (i in 0 until devices) {
            val device = DeviceTest(i, deviceLoggers[i])
            deviceTests.add(device)
        }
        if(!testEraseFlash) {
            logger.info("Testing...")
        } else {
            logger.info("!! ERASING !!")
        }
        testStart = System.currentTimeMillis()
        for (i in 0 until devices) {
            ui.setStatus(i, TestStatus.TESTING)
            ui.setID(i, "")
        }
    }

    private fun devicePortLookup(portLocation: String): DeviceTest? {
        if (!portLocation.startsWith(hubCommonLocationPrefix))
            return null
        val locationSuffix: String = portLocation.drop(hubLocationPrefix.length)
        var deviceNum = channelHubLocations.indexOfFirst { it.contentEquals(locationSuffix) }
        //logger.config("$hubLocationPrefix + $locationSuffix = $deviceNum")
        if (deviceNum == -1)
            return null
        return deviceTests[deviceNum]
    }

    // Dmesg callback
    override fun setUSB(addr: String, tty: String) {
        logger.warning("USB: $addr = $tty")
        // TODO Catch devices attaching and disconnections
        // WARNING: Main Dongle or SWD programmer port disconnected means test failure
    }

    fun testVoltage() {
        // TODO
        for (i in 0 until devices) {
            board.power(i, SwitchboardStage6.GpioPinMode.V3V3, true)
        }
    }

    fun flashDevices() {
        // TODO
    }

    fun enumerateAndTestDevices() {
        // TODO
    }

    private fun checkTestResults(): Boolean {
        var ok = true

        val panelName: String by lazy {
            deviceTests.joinToString(transform = {
            "%02d:".format(it.deviceNum + 1) + when {
                it.testStatus == TestStatus.DISCONNECTED -> "NA"
                it.deviceId.isBlank() -> "ERR"
                else -> it.deviceId
                }
            })
        }
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || device.testStatus == TestStatus.DISCONNECTED)
                continue
            device.endTime = System.currentTimeMillis()
            if (device.testStatus == TestStatus.ERROR) {
                if(device.deviceId.isBlank()) {
                    // Add a placeholder for failed devices
                    device.addTestResult(TestResult("Panel info", TestStatus.PASS, device.endTime, device.endTime, panelName, ""))
                    device.deviceId = "$EMPTY_ID_PREFIX${"%02d".format(device.deviceNum + 1)}"
                    ui.setID(device.deviceNum, device.deviceId)
                    deviceLoggers[device.deviceNum].warning(panelName)
                }
                ok = false
            } else if (device.testStatus != TestStatus.RETESTED) {
                device.testStatus = TestStatus.PASS
                ui.setStatus(device.deviceNum, TestStatus.PASS)
            }
            device.serialPort?.let { serialManager.closePort(it) }
        }
        return ok
    }

    private fun showTestErrors() {
        LogManager.global.info("== Test results ===")
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            val deviceId = if(device.deviceId.isEmpty()) "" else " ${device.deviceId}:"
            val msgPrefix = "[${device.deviceNum + 1}]$deviceId"
            when(device.testStatus) {
                TestStatus.PASS -> {
                    deviceLoggers[device.deviceNum].config("$msgPrefix Test success")
                }
                TestStatus.ERROR -> {
                    deviceLoggers[device.deviceNum].severe("$msgPrefix Test failed")
                    for (test in device.testsList) {
                        if (test.status == TestStatus.ERROR) {
                            LogManager.global.severe(test.toString() + "\n" + test.log)
                            deviceLoggers[device.deviceNum].severe("${test.testName}: ${test.endValue}")
                        }
                    }
                }
                TestStatus.DISCONNECTED -> {} // Skip
                else -> {
                    deviceLoggers[device.deviceNum].severe("$msgPrefix: Unexpected status {$device.testStatus}")
                }
            }
        }
    }

    private fun commitTestResults() {
        if(SUBMIT_RESULTS) {
            logger.info("Committing the test results to database...")
        } else {
            logger.warning("Skip committing test results")
            return
        }
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || device.testStatus == TestStatus.DISCONNECTED)
                continue
            val msgPrefix = "[${device.deviceNum + 1}]"
            if (device.deviceId.isBlank()) {
                deviceLoggers[device.deviceNum].info("$msgPrefix Skipping, no ID")
                continue
            }
            if(committedList.has(device.deviceId)) {
                deviceLoggers[device.deviceNum].info("$msgPrefix Skipping, recently committed as ${device.deviceId}")
                ui.setStatus(device.deviceNum, TestStatus.RETESTED)
                continue
            }
            for (db in dbs) {
                val response = db.sendTestData(device)
                deviceLoggers[device.deviceNum].config("$msgPrefix ${device.deviceId}: $response")
            }
            if (device.deviceId.startsWith(EMPTY_ID_PREFIX))
                continue // Do not cache
            if (device.testStatus == TestStatus.PASS) {
                committedList.append(device.deviceId)
            }
        }
    }

    private fun shouldSkipDevice(deviceNum: Int) = testOnlyDevices.isNotEmpty() && !testOnlyDevices.contains(deviceNum)

    private fun isDeviceInTesting(device: DeviceTest): Boolean = (device.testStatus != TestStatus.ERROR && device.testStatus != TestStatus.DISCONNECTED)

    fun getFailedDevices(): List<Int> {
        return deviceTests.filter { it.testStatus == TestStatus.ERROR }.map { it.deviceNum }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if (result.status == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum].severe("[${device.deviceNum + 1}/$devices] $result")
        } else {
            deviceLoggers[device.deviceNum].config("[${device.deviceNum + 1}/$devices] $result")
        }
        if(result.status == TestStatus.DISCONNECTED)
            device.testStatus = result.status
        ui.setStatus(device.deviceNum, device.testStatus)
    }

    private fun showStatus(device: DeviceTest, status: TestStatus) {
        ui.setStatus(device.deviceNum, status)
    }
}
