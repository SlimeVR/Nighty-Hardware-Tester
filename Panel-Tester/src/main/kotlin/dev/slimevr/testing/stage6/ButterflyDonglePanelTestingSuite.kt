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
import dev.slimevr.hardware.swd.ProgrammerService
import dev.slimevr.logger.LogManager
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.actions.SerialMatchingAction
import dev.slimevr.testing.actions.VoltageTestAction
import dev.slimevr.testing.actions.SuccessAction
import dev.slimevr.testing.actions.ExecuteCommandAction
import dev.slimevr.testing.actions.FailedAction
import dev.slimevr.testing.stage5.DongleDeviceTest
import dev.slimevr.ui.stage6.TesterButterflyDongleUI
import java.util.logging.Level
import java.util.logging.Logger
import java.rmi.UnexpectedException
import kotlin.math.round

class ButterflyDonglePanelTestingSuite(
    val board: SwitchboardStage6,
    private val dbs: List<TestingDatabase>,
    private val ui: TesterButterflyDongleUI,
    private val deviceLoggers: Array<Logger>,
    val logger: Logger
    ) : Thread("Testing suit thread"), USBNotify {

    private val powerBalanceTimeMS = 100L
    private val serialEnumTimeMS = 2000L
    private val serialEnumTimeoutMS = 5000L
    private val serialOpenTimeMS = 500L
    private val flashTimeoutMS = 60 * 1000L // 60s
    private val FLASH_RETRIES = 2
    private val SERIAL_RETRIES = 3
    private val PING_RETRIES = 5

    private val devices = 20
    private val SELF_TEST_ASSERT = true
    private val FLASH_DEVICES = true
    private val SUBMIT_RESULTS = false
    private val RETEST_RETRY = true // XXX

    private val actionEmptyBusFrom3V = VoltageTestAction("Reference 3V3", 3.25f, 3.35f) // 3v3
    private val actionEmptyBusFrom5V = VoltageTestAction("Empty USB power", 0f, 0.1f) // 0v

    private val actionBusFrom3V = VoltageTestAction("3V3 power", 2.45f, 3.35f) // 3v3 may have some drop due to current limiting resistor
    private val actionPowerGood = SuccessAction("PG status")
    private val actionBusFrom5Vdefault = VoltageTestAction("USB power", 1.7f, 1.9f) // Default 1.8v for pre-flashed
    private val actionBusFrom5Vnormal = VoltageTestAction("USB power", 3.2f, 3.4f) // 3v3 for configured LDO

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
    //private val dmesgWatcher = USBDmesgWatcher(this)
    private val actionSerialFound = SuccessAction("Search USB")
    private val failSerialOpened = FailedAction("Fail open serial port")
    private val failSendInfoCommand = FailedAction("Fail send INFO command")
    private val actionFirmwareVersion = SuccessAction("Firmware")
    private val failSendPingCommand = FailedAction("Fail send PING command")
    private val actionRadioPong = SuccessAction("Radio")

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
        //dmesgWatcher.start()
        try {
            selfTest()
        } catch (ex: Throwable) {
            logger.log(Level.SEVERE, "Self-test failed", ex)
            LogManager.exceptionsLogger.log(Level.SEVERE, "Self-test error ${ex.message}", ex)
            return
        }
        isReady = true
        //logger.info("Testing suit started~")
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
                    testEnd()
                    synchronized(this) {
                        isTesting = false
                    }
                    // Do not test, do not commit
                    continue
                }
                testVoltage2()
                enumerateAndTestDevices()
                checkTestResults()
                commitTestResults()
                showTestErrors()
                testEnd()
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

    private fun testEnd() {
        if(dongle.serialPort != null)
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

        // logger.info("USB connected:")
        // SerialPort.getCommPorts().forEach {
        //     logger.info("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
        // }

        searchForSelfUsbDevices()

        // Disable SWD to prevent pins burnout
        board.disableAll()
        sleep(powerBalanceTimeMS)

        testSelfVoltages()
    }

    val infoResultSuccess = arrayOf("Channel frequency:.*".toRegex())
    val infoResultFail = arrayOf("Unknown command".toRegex())

    private fun searchForSelfUsbDevices() {
        if (!lookupSelfUsbDevices() && SELF_TEST_ASSERT) {
            throw UnexpectedException("Tester USB devices enumeration failed")
        }
        if(dongle.serialPort == null)
            return
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
            ui.setID(i, "")
            deviceLoggers[i].info("[${i + 1}] Self-Test")
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
            val fAdc = board.readADC(i)
            val sAdc = String.format("%.3f", fAdc)
            if(actionEmptyBusFrom5V.fail(fAdc)) {
                logger.warning("[${i + 1}] Empty target voltage out of range (${actionEmptyBusFrom5V.minValue}, ${actionEmptyBusFrom5V.maxValue}): $fAdc")
                deviceLoggers[i].severe("[${i + 1}] Empty bus: ${sAdc}v")
                ui.setStatus(i, TestStatus.ERROR)
                success = false
            } else {
                deviceLoggers[i].config("[${i + 1}] Empty bus: ${sAdc}v")
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
            val fAdc = board.readADC(i)
            val sAdc = String.format("%.3f", fAdc)
            if(actionEmptyBusFrom3V.fail(fAdc)) {
                logger.warning("[${i + 1}] 3V3 bus voltage out of range (${actionEmptyBusFrom3V.minValue}, ${actionEmptyBusFrom3V.maxValue}): $fAdc")
                deviceLoggers[i].severe("[${i + 1}] 3V3 bus: ${sAdc}v")
                ui.setStatus(i, TestStatus.ERROR)
                success = false
            } else {
                deviceLoggers[i].config("[${i + 1}] 3V3 bus: ${sAdc}v")
            }
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
        if(dongle.serialPort != null)
            serialManager.markAsKnown(dongle.serialPort!!)

        if (testOnlyDevices.isNotEmpty()) {
            // Re-test selected devices
            val listStr = testOnlyDevices.joinToString(transform = { (it + 1).toString() })
            logger.info("Retesting device $listStr")
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
        logger.info("Dmesg: $addr = $tty")
        // TODO Catch devices attaching and disconnections
        // WARNING: Main Dongle or SWD programmer port disconnected means test failure

        val device = devicePortLookup(addr) ?: return
        ui.setUSB(device.deviceNum, tty)
    }

    fun testVoltage() {
        // Test 3V3
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            device.logger.info("[${device.deviceNum + 1}] Power")
            board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.V3V3, true)
            showStatus(device, TestStatus.NOT_UPDATED)
            sleep(10) // Minimize surge current
        }
        sleep(powerBalanceTimeMS)

        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            val vbus = board.readADC(device.deviceNum)
            val vBusResult = actionBusFrom3V.action(vbus, "", System.currentTimeMillis())
            addResult(device, vBusResult)

            board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.V3V3, false)
        }

        // Test USB 5V
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.USB, true)
            showStatus(device, TestStatus.NOT_UPDATED)
            sleep(10) // Minimize surge current
        }
        sleep(powerBalanceTimeMS)

        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue

            // Detect over-current event
            val powerFailure = board.is5vFaultAsserted(device.deviceNum)
            val vbus = board.readADC(device.deviceNum)
            // Bus voltage can be either 1.8v default or 3.3v if configured once
            val is1v8bus = !actionBusFrom5Vdefault.fail(vbus)
            val actionBusFrom5V = if(is1v8bus) actionBusFrom5Vdefault else actionBusFrom5Vnormal
            val vBusResult = actionBusFrom5V.action(vbus, "", System.currentTimeMillis())

            val powerFlagResult = actionPowerGood.action(
                !powerFailure,
                if (powerFailure) "VUSB: Over-current" else "Power OK",
                System.currentTimeMillis()
            )
            powerFlagResult.setOkOnPass()
            addResult(device, vBusResult)
            addResult(device, powerFlagResult)

            board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.USB, false)
        }

        board.disableGPIO()
    }

    private val DEBUG_FLASHING_ERRORS = false
    private val DEBUG_FLASHING = false

    private val openocdLogFilter = { log: MutableList<String> ->
        val startIndex = log.indexOfFirst { it.startsWith("Open On-Chip Debugger", ignoreCase = true) }
        if(startIndex != -1) {
            log.removeAt(startIndex)
            if(log.size > startIndex && log[startIndex].startsWith("Licensed under", ignoreCase = true))
                log.removeAt(startIndex)
            if(log.size > startIndex + 1 && log[startIndex].startsWith("For bug reports", ignoreCase = true)) {
                log.removeAt(startIndex)
                log.removeAt(startIndex)
            }
            if(log.size > startIndex && log[startIndex].startsWith("debug_level", ignoreCase = true))
                log.removeAt(startIndex)
        }
    }

    fun flashDevices() {
        if(FLASH_DEVICES) {
            logger.info("Flashing")
        } else {
            logger.warning("Flashing skipped")
            return
        }

        board.swd(true)

        for (i in 0 until devices) {
            val device = deviceTests[i]
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue

            board.power(i, SwitchboardStage6.GpioPinMode.V3V3, true)
            sleep(10) // Minimize surge current
        }

        // Wait SWD programmers boot and enumerate
        sleep(serialEnumTimeMS / 2)

        val swd1 = ProgrammerService("SWD1", serial = probeSerials[0], threadsCount = 5)
        val swd2 = ProgrammerService("SWD2", serial = probeSerials[1], threadsCount = 5)

        val toRegEx = {s: String -> ".*$s.*".toRegex(RegexOption.IGNORE_CASE)}
        val successStrings = listOf("Verified OK").map(toRegEx).toTypedArray()
        val failStrings = listOf("unable to find a matching CMSIS-DAP device",
            "Error connecting DP", "Programming Failed", "Verify Failed", "Unable to reset target").map(toRegEx).toTypedArray()

        val imageFile = if(testEraseFlash) emptyFile else firmwareFile

        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (!isDeviceInTesting(device)) {
                if(device.testStatus == TestStatus.ERROR)
                    LogManager.global.warning("[${device.deviceNum + 1}] Skipped due to previous error")
            } else if (!device.flashingRequired) {
                device.logger.info("[${device.deviceNum + 1}] Skip flash")
                LogManager.global.info("[${device.deviceNum + 1}] Skipping already flashed device")
                continue
            } else {
                val swdIdx = board.mapDeviceToSwdDevice(device.deviceNum)
                if(swdIdx == -1)
                    throw ArrayIndexOutOfBoundsException()
                val swd =  if (swdIdx == 0) swd1 else swd2
                val itf = board.mapDeviceToSwdInterface(device.deviceNum)
                if(itf == -1)
                    throw ArrayIndexOutOfBoundsException()
                val gdbPort = 3333 + device.deviceNum * 2
                val task: () -> TestStatus = {
                    device.logger.info("[${device.deviceNum + 1}] Flashing")
                    showStatus(device, TestStatus.NOT_UPDATED)
                    device.testStatus = TestStatus.TESTING
                    val startTime = System.currentTimeMillis()
                    val flashAction = ExecuteCommandAction(
                        "Flash firmware",
                        successStrings, failStrings,
                        "sh flash.sh ${swd.serial} $itf $imageFile $gdbPort",
                        timeout = flashTimeoutMS,
                        logFilter = openocdLogFilter
                    )
                    val flashResult = flashAction.action("", "", startTime)
                    flashResult.setOkOnPass()
                    if (DEBUG_FLASHING || (DEBUG_FLASHING_ERRORS && flashResult.status == TestStatus.ERROR)) {
                        logger.warning(flashResult.log)
                    }
                    addResult(device, flashResult)

                    if(testEraseFlash && flashResult.status != TestStatus.ERROR) {
                        showStatus(device, TestStatus.RETESTED)
                    }
                    flashResult.status
                }
                swd.add(task)
            }
        }

        data class SWDThread(val swd: ProgrammerService) {
            val thread = Thread {
                swd.runAll()
                swd.await()
                if(FLASH_RETRIES > 0) {
                    for (i in 1..FLASH_RETRIES) {
                        var wait = swd.reAddFailed()
                        if (wait) {
                            logger.info("Flashing retry $i")
                            swd.runAll()
                            swd.await()
                        }
                    }
                }
                swd.clear()
            }
        }
        val swdThreads = listOf(SWDThread(swd1), SWDThread(swd2))
        swdThreads.forEach { it.thread.start() }
        swdThreads.forEach { it.thread.join() }

        board.swd(false)
        board.disableGPIO() // Power off
    }

    fun testVoltage2() {
        // Test USB 5V
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.USB, true)
            showStatus(device, TestStatus.NOT_UPDATED)
            sleep(10) // Minimize surge current
        }
        sleep(powerBalanceTimeMS)

        // Test bus voltage after flash
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue

            // Detect over-current event
            val powerFailure = board.is5vFaultAsserted(device.deviceNum)
            val vbus = board.readADC(device.deviceNum)
            val vBusResult = actionBusFrom5Vnormal.action(vbus, "", System.currentTimeMillis())

            val powerFlagResult = actionPowerGood.action(
                !powerFailure,
                if (powerFailure) "VUSB: Over-current" else "Power OK",
                System.currentTimeMillis()
            )
            powerFlagResult.setOkOnPass()
            addResult(device, vBusResult)
            addResult(device, powerFlagResult)

            if(device.testStatus == TestStatus.ERROR) {
                board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.USB, false)
            } else {
                // Keep USB power
            }
        }
    }

    fun enumerateAndTestDevices() {
        for(i in 1..SERIAL_RETRIES) {
            if(i > 1) {
                // Power cycle
                board.disableGPIO()
                sleep(powerBalanceTimeMS)

                // Power up USB 5V
                for (device in deviceTests) {
                    if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                        continue
                    board.power(device.deviceNum, SwitchboardStage6.GpioPinMode.USB, true)
                    showStatus(device, TestStatus.NOT_UPDATED)
                    sleep(10) // Minimize surge current
                }
                sleep(powerBalanceTimeMS)
            }
            if (!enumerateSerialDevices(i))
                break
            if (!hasUnfailedDevicesOnChannel())
                return

            logger.info("Testing devices...")
            for (device in deviceTests) {
                if (shouldSkipDevice(device.deviceNum))
                    continue
                if (!isDeviceInTesting(device) || device.serialPort == null) {
                    if (device.testStatus == TestStatus.ERROR || device.serialPort == null) {
                        LogManager.global.warning("[${device.deviceNum + 1}] Skipped due to previous error")
                    }
                    continue
                }
                testDevice(device, device.serialPort!!, i)
            }
            clearSerial()
        }
        //board.disableGPIO() // XXX
    }

    private fun enumerateSerialDevices(retry: Int): Boolean {
        var portsCount = 0
        for (device in deviceTests) {
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            if(device.deviceId.isEmpty()) {
                showStatus(device, TestStatus.NOT_UPDATED)
                portsCount++
            }
        }
        if (portsCount == 0)
            return false

        logger.info("Waiting for serial devices...")
        val startTime = System.currentTimeMillis()
        sleep(serialEnumTimeMS) // Devices booting + Host is enumerating USB devices

        var foundSerials = 0
        var ports: List<SerialPort>
        //val knownPorts = mutableListOf<SerialPort>()
        val endWait = System.currentTimeMillis() + serialEnumTimeoutMS
        //var portsFound = 0
        do {
            ports = serialManager.findNewPorts()
            //if(portsFound != ports.size) {
            //  logger.warning("Found $portsFound ports")
            //  portsFound = ports.size
            //}
            if (ports.size == portsCount)
                break
            sleep(200)
        } while (System.currentTimeMillis() < endWait)

        //logger.info("USB connected:")
        //SerialPort.getCommPorts().forEach {
        //    logger.info("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
        //}

        //sleep(500)
        ports.forEach {
            //logger.warning("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
            val device = devicePortLookup(it.portLocation) ?: return@forEach
            device.serialPort = it
            //knownPorts.add(it)
            serialManager.markAsKnown(it)
            foundSerials++
            //logger.info("[${device.deviceNum + 1}/$devices] Device used: ${it.descriptivePortName} ${it.systemPortPath}")
            ui.setUSB(device.deviceNum, it.systemPortName)
        }
        deviceTests.forEach {
            if (shouldSkipDevice(it.deviceNum) || !isDeviceInTesting(it))
                return@forEach
            val connectTest = if (it.serialPort == null) {
                actionSerialFound.action(false, "Serial port not found", startTime)
            } else {
                val port = it.serialPort!!
                actionSerialFound.action(true, "Serial port found: ${port.descriptivePortName} ${port.systemPortPath}", startTime)
            }
            connectTest.setOkOnPass()
            addResult(it, connectTest)
            if(connectTest.status == TestStatus.ERROR) {
                if(retry < SERIAL_RETRIES) {
                    it.logger.warning("Retry...$retry")
                    LogManager.global.warning("[${it.deviceNum + 1}] Port not found. Retry...$retry")
                    it.testStatus = TestStatus.TESTING
                    showStatus(it, TestStatus.PORT_ERROR)
                }
            }
        }
        logger.info("Found $foundSerials/$portsCount serial devices")
        //knownPorts.forEach { serialManager.removePort(it) }
        return true
    }

    fun testDevice(device: DeviceTest, port: SerialPort, retry: Int) {
        //device.logger.info("[${device.deviceNum + 1}] Access ${port.systemPortName}")
        showStatus(device, TestStatus.NOT_UPDATED)
        val startTime = System.currentTimeMillis()
        if(serialManager.openPort(port, device)) {
            //device.logger.info("[${device.deviceNum + 1}] Port opened")
            sleep(serialOpenTimeMS)

            device.flushSerialIO = false // Prevent Rpi3 freeze

            //device.logger.info("[${device.deviceNum + 1}] Sending info command...")
            if (device.sendSerialCommand("info")) {
                getDeviceInfo(device, retry)

                if(device.serialDisconnected) {
                    if(retry < SERIAL_RETRIES) {
                        serialManager.closePort(port)
                        device.logger.warning("Retry...$retry")
                        LogManager.global.warning("[${device.deviceNum + 1}] Port disconnected. Retry...$retry")
                        device.testStatus = TestStatus.TESTING
                        showStatus(device, TestStatus.PORT_ERROR)
                        return
                    }
                }
                serialManager.closePort(port)
            } else {
                // Failed to send command means either device is disconnected
                // or native exception (example SerialPortTimeoutException) that
                // means port is not operable and we need to reopen it again
                serialManager.closePort(port)
                if(retry < SERIAL_RETRIES) {
                    device.logger.warning("Retry...$retry")
                    LogManager.global.warning("[${device.deviceNum + 1}] Can't send serial command. Retry...$retry")
                    device.testStatus = TestStatus.TESTING
                    showStatus(device, TestStatus.PORT_ERROR)
                    return
                }
                val result = failSendInfoCommand.action("Serial command error", startTime)
                addResult(device, result)
            }
        } else {
            serialManager.closePort(port)
            if(retry < SERIAL_RETRIES) {
                device.logger.warning("Retry...$retry")
                LogManager.global.warning("[${device.deviceNum + 1}] Can't open port. Retry...$retry")
                device.testStatus = TestStatus.TESTING
                showStatus(device, TestStatus.PORT_ERROR)
                return
            }
            val flashResult = failSerialOpened.action("Can't open port. Error ${port.lastErrorCode}", startTime)
            addResult(device, flashResult)
        }
    }

    fun getDeviceInfo(device: DeviceTest, retry: Int) {
        device.logger.info("[${device.deviceNum + 1}] Testing")
        // Wait for the command output and disconnect
        val getInfoResult = SerialMatchingAction("Device info", infoResultSuccess, infoResultFail, device, 2000) // TODO: Minimize logs (trim lines before the command)
        val startTime = System.currentTimeMillis()
        val infoResult = getInfoResult.action("", "", startTime)
        infoResult.setOkOnPass()
        addResult(device, infoResult)
        if (infoResult.status == TestStatus.PASS) {
            // Data lookup
            //logger.warning(log)
            val buildMatch = infoResult.matchLog("Commit: ([a-zA-Z0-9-.]+)")
            val buildHash = buildMatch?.get(1) ?: ""
            val buildHashOk = buildHash.contains(FIRMWARE_HASH)
            val buildResult = actionFirmwareVersion.action(buildHashOk, buildMatch?.get(0) ?: "Build commit hash not found", startTime)
            if(buildResult.status == TestStatus.PASS)
                buildResult.endValue = buildHash
            if(buildHashOk)
                device.flashingRequired = false
            addResult(device, buildResult)

            device.deviceId = infoResult.matchLog("Device address: ([a-zA-Z0-9:]+)")?.get(1)?.uppercase() ?: ""
            ui.setID(device.deviceNum, device.deviceId)

            // Ping the dongle
            checkDonglePing(device, retry)
        } else {
            if(retry < SERIAL_RETRIES) {
                device.logger.warning("Retry...$retry")
                LogManager.global.warning("[${device.deviceNum + 1}] Serial response failed. Retry...$retry")
                device.testStatus = TestStatus.TESTING
                showStatus(device, TestStatus.PORT_ERROR)
                return // to external retry loop
            }
        }
    }

    private val pongResultSuccess = arrayOf("PONG packet received .*".toRegex())
    private val pongResultFail = arrayOf("Unknown command".toRegex())
    private val PONG_RSSI_MIN = 30

    private fun checkDonglePing(device: DeviceTest, retry: Int) {
        val radioChannel = dongle.channel - 2400
        retryPing@ for(i in 1..PING_RETRIES) {
            val startTime = System.currentTimeMillis()
            if (device.sendSerialCommand("ping ${dongle.deviceId} $radioChannel")) {
                // Dongle scanning channels on startup, PING task is delayed
                val waitPongResult = SerialMatchingAction("Ping", pongResultSuccess, pongResultFail, device, 15000)
                val pongResult = waitPongResult.action("", "", startTime)
                if(device.serialDisconnected) {
                    if(retry < SERIAL_RETRIES) {
                        showStatus(device, TestStatus.PORT_ERROR)
                        device.deviceId = "" // Force to enumerate
                        return // to external retry loop
                    }
                    addResult(device, pongResult)
                }
                if(pongResult.endValue.lowercase().startsWith("timeout")) {
                    // Dongle can miss ping packet, so it is OK to retry again
                    if(i < PING_RETRIES) {
                        //device.logger.warning("Retry...$retry")
                        //LogManager.global.warning("[${device.deviceNum + 1}] No pong response. Retry...$i")
                        showStatus(device, TestStatus.PORT_ERROR)
                        continue@retryPing
                    }
                    addResult(device, pongResult)
                }
                pongResult.setOkOnPass()
                if(pongResult.status == TestStatus.PASS) {
                    val match = pongResult.matchLog("PONG packet received from ([a-zA-Z0-9:]+), RSSI ([0-9]+)")
                    val pongAddress = match?.get(1) ?: ""
                    val pongRssi = match?.get(2)?.toInt() ?: 255
                    val pongOk = pongAddress.contentEquals(device.deviceId) && (pongRssi < PONG_RSSI_MIN)
                    val result = actionRadioPong.action(pongOk, match?.get(0) ?: "No pong packet", startTime)
                    result.endValue = if(match != null) "RSSI $pongRssi" else "No pong packet"
                    // Address mismatch may be due to requests collision
                    // Low RSSI may be due to radio interference
                    // Both is OK, just retry again
                    if(result.status == TestStatus.ERROR) {
                        if(i < PING_RETRIES) {
                            //device.logger.warning("Retry...$retry")
                            //LogManager.global.warning("[${device.deviceNum + 1}] Failed pong response. Retry...$i")
                            showStatus(device, TestStatus.ERROR)
                            continue@retryPing
                        }
                    }
                    addResult(device, pongResult)
                    addResult(device, result)
                } else {
                    addResult(device, pongResult)
                }
                break@retryPing
            } else {
                // Failed to send command means either device is disconnected
                // or native exception (example SerialPortTimeoutException) that
                // means port is not operable and we need to reopen it again
                if(device.serialDisconnected) {
                    if(retry < SERIAL_RETRIES) {
                        device.deviceId = "" // Force to enumerate
                        device.testStatus = TestStatus.TESTING
                        showStatus(device, TestStatus.PORT_ERROR)
                        return // to external retry loop
                    }
                }
                if(retry < SERIAL_RETRIES) {
                    device.logger.warning("Retry...$retry")
                    LogManager.global.warning("[${device.deviceNum + 1}] Can't send serial command. Retry...$retry")
                    device.deviceId = "" // Force to enumerate
                    device.testStatus = TestStatus.TESTING
                    showStatus(device, TestStatus.PORT_ERROR)
                    return // to external retry loop
                }
                val result = failSendPingCommand.action("Serial command error", startTime)
                addResult(device, result)
            }
        }
    }

    private fun clearSerial() {
        serialManager.removePort(dongle.serialPort!!)
        serialManager.closeAllPorts()
        serialManager.markAsKnown(dongle.serialPort!!)
        for (device in deviceTests) {
            device.serialPort = null
            ui.setUSB(device.deviceNum, "")
        }
    }

    private fun checkTestResults(): Boolean {
        var ok = true

        // For devices without ID generate panel location info
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
                    LogManager.global.warning(panelName)
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
            //val deviceId = if(device.deviceId.isEmpty()) "" else " ${device.deviceId}:"
            //val msgPrefix = "[${device.deviceNum + 1}]$deviceId"
            when(device.testStatus) {
                TestStatus.PASS -> {
                    deviceLoggers[device.deviceNum].config("[${device.deviceNum + 1}] TEST PASS")
                }
                TestStatus.ERROR -> {
                    deviceLoggers[device.deviceNum].severe("[${device.deviceNum + 1}] FAILED")
                    for (test in device.testsList) {
                        if (test.status == TestStatus.ERROR) {
                            var msg = "[${device.deviceNum + 1}] $test"
                            if(test.log.isNotEmpty())
                                msg = msg + "\n" + test.log
                            LogManager.global.severe(msg)
                            //logger.severe("[${device.deviceNum + 1}] ${test.testName}: ${test.endValue}")
                        }
                    }
                }
                TestStatus.DISCONNECTED -> {} // Skip
                else -> {
                    logger.severe("[${device.deviceNum + 1}] Unexpected status {$device.testStatus}")
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

    private fun hasUnfailedDevicesOnChannel(): Boolean {
        for (d in 0 until devices) {
            if (shouldSkipDevice(d) || !isDeviceInTesting(deviceTests[d]))
                continue
            if (deviceTests[d].testStatus != TestStatus.ERROR)
                return true
        }
        return false
    }

    private fun shouldSkipDevice(deviceNum: Int) = testOnlyDevices.isNotEmpty() && !testOnlyDevices.contains(deviceNum)

    private fun isDeviceInTesting(device: DeviceTest): Boolean = (device.testStatus != TestStatus.ERROR && device.testStatus != TestStatus.DISCONNECTED)

    fun getFailedDevices(): List<Int> {
        return deviceTests.filter { it.testStatus == TestStatus.ERROR }.map { it.deviceNum }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        deviceLoggers[device.deviceNum].log(
            if (result.status == TestStatus.ERROR) Level.SEVERE else Level.CONFIG,
            "[${device.deviceNum + 1}] $result")
        if(result.status == TestStatus.DISCONNECTED)
            device.testStatus = result.status
        ui.setStatus(device.deviceNum, device.testStatus)
    }

    private fun showStatus(device: DeviceTest, status: TestStatus) {
        ui.setStatus(device.deviceNum, status)
    }
}
