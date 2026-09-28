package dev.slimevr.testing.stage5

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.SwitchboardStage5
import dev.slimevr.hardware.SwitchboardStage5.Companion.PowerMode
import dev.slimevr.hardware.SwitchboardStage5.Companion.ChannelMode
import dev.slimevr.hardware.SwitchboardStage5.Companion.LedColor
import dev.slimevr.hardware.serial.ButterflyTrackerPortChecker
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.hardware.swd.ProgrammerService
import dev.slimevr.hardware.usb.USBDmesgWatcher
import dev.slimevr.hardware.usb.USBNotify
import dev.slimevr.testing.*
import dev.slimevr.testing.actions.ExecuteCommandAction
import dev.slimevr.testing.actions.FailedAction
import dev.slimevr.testing.actions.PresenceAction
import dev.slimevr.testing.actions.SerialMatchingAction
import dev.slimevr.testing.actions.SuccessAction
import dev.slimevr.testing.actions.VoltageTestAction
import dev.slimevr.ui.stage5.*
import java.rmi.UnexpectedException
import java.util.logging.Level
import java.util.logging.Logger
import java.io.File
import java.nio.file.Path

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
) : Thread("Testing suit thread"), USBNotify {

    companion object {
        val CHANNELS: List<ChannelMode> = listOf(ChannelMode.A, ChannelMode.B)
        const val ON: Boolean = SwitchboardStage5.ON
        const val OFF: Boolean = SwitchboardStage5.OFF
        const val ALL: Int = SwitchboardStage5.ALL
    }

    private val powerBalanceTimeMS = 100L
    private val serialEnumTimeMS = 2000L
    private val serialOpenTimeMS = 100L
    private val SERIAL_RETRIES = 3
    private val PING_RETRIES = 5

    private val actionPowerGood = SuccessAction("Power good")
    //private val actionVBusRef = VoltageTestAction("VBUS reference", 4.8f, 5.5f) // 5V
    private val actionVBatRef = VoltageTestAction("VBat reference", 3.15f, 3.45f) // 3v3
    private val actionVoutUsb = VoltageTestAction("Vout voltage from USB power", 4.4f, 5.3f) // Vdd = 5V - Shottkey drop (0.2..0.4v)
    private val actionVoutBat = VoltageTestAction("Vout voltage from BAT power", 3.15f, 3.45f) // Vdd = Vbat
    private val actionBusFromBat = VoltageTestAction("Target voltage from Bat power", 2.9f, 3.2f) // 3v0
    private val actionBusFromUsb = VoltageTestAction("Target voltage from USB power", 2.9f, 3.2f) // 3v0
    private val actionIoutBlank = VoltageTestAction("Current draw", 0f, 20f) // empty 3.5mA (flashed 7.5mA, TX 12mA)

    private val actionPresence = PresenceAction("Presence")
    private val actionSerialFound = SuccessAction("Search serial port")
    private val failSerialOpened = FailedAction("Fail open serial port")
    private val failSendInfoCommand = FailedAction("Fail send INFO command")
    private val actionFirmwareVersion = SuccessAction("Firmware version check")
    private val actionGetIMU = SuccessAction("Test IMU")
    private val failSendPingCommand = FailedAction("Fail send PING command")
    private val actionRadioPong = SuccessAction("Test Radio")

    private val firmwareFile = System.getenv("TESTER_FIRMWARE_FILE")
    private val FIRMWARE_HASH = "e7484d4f9ec5" // TODO
    private val emptyFile = "blank.hex"
    var eraseFlash = false

    private val serialManager = SerialManager(ButterflyTrackerPortChecker(), sendReset = false)
    private val dmesgWatcher = USBDmesgWatcher(this)

    private var dongle = DongleDeviceTest()
    private var hubLocationPrefix = "1-1.1" // Default location
    private var channelHubLocations = arrayOf("1.4", "1.3", "1.1.1", "1.1.4", "1.1.2", "1.1.3", "1.2.2", "1.2.1", "1.2.3", "1.2.4")
    private var probeHubLocations = arrayOf("2", "3")
    private var probeSerials = mutableListOf("unknown", "unknown")

    private val deviceTests = mutableListOf<DeviceTest>()
    private var testStart = 0L
    private var isTesting = false
    private val committedSuccessfulDeviceIds = mutableListOf<String>()
    private var startTest: Boolean = false
    private var testOnlyDevices = setOf<Int>()
    private var isReady = false
    private var testRepeat = false

    init {
        logger.setLevel(Level.CONFIG)
        Logger.getLogger("devices").setLevel(Level.WARNING)
        Logger.getLogger("dongle").setLevel(Level.WARNING)
    }

    override fun run() {
        try {
            selfTest()
        } catch (exception: Throwable) {
            switchboard.led(LedColor.RED)
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
                testPresence()
                testVoltage()
                flashDevices()
                if(eraseFlash) {
                    testEnd()
                    continue
                }
                enumerateAndTestDevices()

                // TODO
                //checkTestResults()
                //commitTestResults()
                //reportTestResults()
                testEnd()
            } catch (exception: Throwable) {
                logger.log(Level.SEVERE, "Tester error", exception)
            }
            synchronized(this) {
                isTesting = false
            }
            /*if (!testRepeat) {
                var failed = getFailedDevices()
                if (failed.isNotEmpty()) {
                    logger.info("Repeating test for ${failed.size} devices...")
                    testRepeat = true
                    startTest(*failed.toIntArray())
                }
            } else {
                testRepeat = false
            }*/
        }
    }

    fun isReady() = isReady

    private fun waitTestStart() {
        switchboard.disableAll()
        statusLogger.info("=== Ready ===")
        while (!switchboard.isButtonPressed() && !startTest) {
            sleep(10)
        }
        startTest = false
    }

    fun startTest(vararg devices: Int) {
        synchronized(this) {
            if (isTesting)
                return
        }
        // TODO one device doesn't work somewhere, maybe hardware flash/reset error
        testOnlyDevices = setOf(*devices.toTypedArray())
        startTest = true
    }

    private fun testEnd() {
        switchboard.disableAll()
        serialManager.removePort(dongle.serialPort!!)
        serialManager.closeAllPorts()
        testOnlyDevices = setOf()
        startTest = false
        eraseFlash = false
        val testEnd = System.currentTimeMillis()
        statusLogger.info("Done in ${(testEnd - testStart) / 1000}s")
        sleep(300)
    }

    /**
     * Current from VBat power source in mAmpers
     *
     * 1R shunt = 1V@1A with x100 gain => 100V@1A 1V=10mA
     */
    private fun getBatCurrent(): Float = 10f * getIoutVoltage()
    private fun getIoutVoltage(): Float = adc.ADS1X15_1.getVoltage(0u)
    /** VBat target test-point */
    private fun getBatVoltage(): Float = adc.ADS1X15_1.getVoltage(1u)
    /** 3V targer test-point */
    private fun getBusVoltage(): Float = adc.ADS1X15_1.getVoltage(2u)
    /** VDD targer test-point */
    private fun getOutVoltage(): Float = adc.ADS1X15_1.getVoltage(3u)

    private fun selfTest() {
        logger.info("=== Testing suite self-test ===")
        statusLogger.info("Self-test")
        switchboard.led(LedColor.PURPLE)
        searchForSelfUsbDevices()
        testSelfVoltages()
        switchboard.led(LedColor.GREEN)
    }

    private fun testSelfVoltages() {
        switchboard.disableAll()
        sleep(powerBalanceTimeMS)
        if (switchboard.isChannelPresent()) {
            logger.severe("Channel sense is abnormaly asserted")
        }
        if (switchboard.isPowerFaultTriggered()) {
            logger.severe("Power fault is abnormaly asserted")
        }
        val emptyIdx = findEmptyChannel()
        if (emptyIdx >= 0) {
            val channel = switchboard.deviceNumToChannel(emptyIdx)
            val ch = switchboard.mapDeviceToSwitchboard(emptyIdx)

            logger.config("Check slot ${channel}${ch + 1} supply voltages")

            switchboard.channel(channel)
            switchboard.device(ch, ON)
            switchboard.power(PowerMode.USB)
            sleep(powerBalanceTimeMS)

            val vbus = getBusVoltage()
            val faultVbus = switchboard.isPowerFaultTriggered()
            logger.log(
                if (faultVbus) Level.WARNING else Level.CONFIG,
                "Target voltage: $vbus" + if (faultVbus) " !!Over-current!!" else ""
            )

            switchboard.power(PowerMode.BATTERY)
            sleep(powerBalanceTimeMS)

            val vbat = getBatVoltage()
            val faultVbat = switchboard.isPowerFaultTriggered()
            val vbatErr = actionVBatRef.fail(vbat) || faultVbat
            logger.log(
                if (vbatErr) Level.WARNING else Level.CONFIG,
                "Battery voltage (${actionVBatRef.minValue}, ${actionVBatRef.maxValue}): $vbat" +
                    if (faultVbat) " !!Over-current!!" else ""
            )

            switchboard.channel(ChannelMode.OFF)
            switchboard.device(ch, OFF)
            switchboard.power(PowerMode.OFF)
            sleep(powerBalanceTimeMS)
        } else {
            logger.warning("Disconnected ADC voltages")
            logger.config("Target voltage: ${getBusVoltage()}")
            logger.config("Battery voltage: ${getBatVoltage()}")
            if (switchboard.isPowerFaultTriggered()) {
                logger.severe("Power fault is abnormaly asserted")
            }
        }
        logger.config("Output voltage: ${getOutVoltage()}")
        logger.config("Iout voltage: ${getIoutVoltage()}")
        switchboard.disableAll()
    }

    val infoResultSuccess = arrayOf("Channel frequency:.*".toRegex())
    val infoResultFail = arrayOf("Unknown command".toRegex())

    private fun searchForSelfUsbDevices() {
        SerialPort.getCommPorts().filter { p ->
            p.systemPortPath.startsWith("/dev/ttyACM")
        }.forEach { p ->
            val name = p.descriptivePortName.lowercase()
            if (name.contains("slimenrf receiver")) {
                dongle.serialPort = p
            } else if (name.contains("multiprobe")) {
                hubLocationPrefix = p.portLocation.dropLast(1)
                //logger.warning("HUB Prefix: $hubLocationPrefix")
                val locationSuffix = p.portLocation.drop(hubLocationPrefix.length)
                val probeIdx: Int = probeHubLocations.indexOfFirst { s -> s.contentEquals(locationSuffix) }
                if(probeIdx != -1)
                    probeSerials[probeIdx] = p.serialNumber
            }
        }

        val probesOk: Boolean = (probeSerials.count{ s -> s.contentEquals("unknown") } == 0)
        val dongleOk = (dongle.serialPort != null)
        logger.log(
            if (probesOk && dongleOk) Level.CONFIG else Level.SEVERE,
                "Butterfly Dongle usb@${dongle.serialPort?.portLocation ?: "unknown"}" +
                    ", SWD probes serial: ${probeSerials[0]}, ${probeSerials[1]}"
        )
        if (!probesOk || !dongleOk)
            throw UnexpectedException("Tester USB devices enumeration failed")

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
            throw UnexpectedException("USB connection with Dongle failed")
        }
    }

    private fun findEmptyChannel(): Int {
        switchboard.disableAll()
        for (channel in CHANNELS) {
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (switchboard.deviceNumToChannel(i) != switchboard.channel())
                    continue
                val ch = switchboard.mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)
                sleep(1)
                val sense = switchboard.isChannelPresent()
                if (!sense) {
                    switchboard.device(ch, OFF)
                    switchboard.channel(ChannelMode.OFF)
                    return i
                }
                switchboard.device(ch, OFF)
            }
        }
        switchboard.channel(ChannelMode.OFF)
        return -1
    }

    private fun testStart() {
        switchboard.led(LedColor.OFF)
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
                deviceTests[it] = DeviceTest(it)
                statusLogger.info("Retesting device ${it + 1}")
                testStart = System.currentTimeMillis()
            }
            return // Do not clear the state
        }

        ui.statusLogHandler.clear()
        ui.clearAll()
        deviceTests.clear()
        for (i in 0 until devices) {
            val device = DeviceTest(i)
            deviceTests.add(device)
        }
        if(!eraseFlash) {
            statusLogger.info("Testing...")
        } else {
            statusLogger.info("ERASING")
        }
        testStart = System.currentTimeMillis()
        for (i in 0 until devices) {
            ui.setStatus(i, TestStatus.TESTING)
            ui.setID(i, "")
        }
    }

    private fun testPresence() {
        switchboard.disableAll()

        statusLogger.info("Presence")
        for (channel in CHANNELS) {
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (switchboard.deviceNumToChannel(i) != switchboard.channel())
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum))
                    continue
                val ch = switchboard.mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)
                sleep(1)

                val senseStartTime = System.currentTimeMillis()
                val sense1 = switchboard.isChannelPresent()
                val sense1Result = actionPresence.action(sense1, if (sense1) "Device detected" else "Device not detected", senseStartTime)
                if(!sense1) {
                    ui.setID(device.deviceNum, "--- N/A ---")
                }
                addResult(device, sense1Result)
                switchboard.device(ch, OFF)
            }
        }
        switchboard.channel(ChannelMode.OFF)
    }

    private fun testVoltage() {
        switchboard.disableAll()
        if(eraseFlash)
            return
        for (channel in CHANNELS) {
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (switchboard.deviceNumToChannel(i) != switchboard.channel())
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                    continue

                statusLogger.info("[${i + 1}/$devices] Power")
                val ch = switchboard.mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)

                showStatus(device, TestStatus.NOT_UPDATED)

                switchboard.resetPowerFault()
                switchboard.power(PowerMode.BATTERY)
                sleep(powerBalanceTimeMS)

                val batPgStartTime = System.currentTimeMillis()
                val batPg = switchboard.isPowerFaultTriggered()
                val batPgResult = actionPowerGood.action(
                    !batPg,
                    if (batPg) "VBAT_PG: Over-current detected" else "Power OK", batPgStartTime
                )
                val vBatResult = actionVBatRef.action(getBatVoltage(), "", System.currentTimeMillis())
                val vOutFromBatResult = actionVoutBat.action(getOutVoltage(), "", System.currentTimeMillis())
                val vBusFromBatResult = actionBusFromBat.action(getBusVoltage(), "", System.currentTimeMillis())
                val vIoutResult = actionIoutBlank.action(getBatCurrent(), "", System.currentTimeMillis())
                addResult(device, batPgResult)
                addResult(device, vBatResult)
                addResult(device, vOutFromBatResult)
                addResult(device, vBusFromBatResult)
                addResult(device, vIoutResult)

                switchboard.resetPowerFault()
                switchboard.power(PowerMode.USB)
                sleep(powerBalanceTimeMS)

                val busPgStartTime = System.currentTimeMillis()
                val busPg = switchboard.isPowerFaultTriggered()
                val busPgResult = actionPowerGood.action(
                    !busPg,
                    if (busPg) "VBUS_PG: Over-current detected" else "Power OK", busPgStartTime
                )
                val vOutFromUsbResult = actionVoutUsb.action(getOutVoltage(), "", System.currentTimeMillis())
                val vBusFromUsbResult = actionBusFromUsb.action(getBusVoltage(), "", System.currentTimeMillis())
                addResult(device, busPgResult)
                addResult(device, vOutFromUsbResult)
                addResult(device, vBusFromUsbResult)

                switchboard.power(PowerMode.OFF)
                switchboard.device(ch, OFF)
            }
        }
    }

    private fun flashDevices() {
        statusLogger.info("Flashing")

        switchboard.disableAll()
        //sleep(1000) // Wait power drained
        switchboard.channel(ChannelMode.BOTH)
        switchboard.power(PowerMode.BATTERY)
        // Sequently power on to avoid huge surge
        for(i in 0 until 10) {
            switchboard.device(i, ON)
            sleep(powerBalanceTimeMS / 2)
        }

        val swd1 = ProgrammerService("SWD1", "openocd1.cfg") // 7D5D611A6D349089
        val swd2 = ProgrammerService("SWD2", "openocd2.cfg") // 2D6CE9DC57B556E3

        val toRegEx = {s: String -> ".*$s.*".toRegex(RegexOption.IGNORE_CASE)}
        val successStrings = listOf("Verified OK").map(toRegEx).toTypedArray()
        val failStrings = listOf("unable to find a matching CMSIS-DAP device",
            "Error connecting DP", "Programming Failed", "Verify Failed", "Unable to reset target").map(toRegEx).toTypedArray()
        val timeout = 60 * 1000L // 60s timeout
        var deviceList = deviceTests.toMutableList()

        //val imageFile = File(if(eraseFlash) emptyFile else firmwareFile).path!!
        val imageFile = if(eraseFlash) emptyFile else firmwareFile

        for (device in deviceList) {
            if (shouldSkipDevice(device.deviceNum))
                continue
            if (!isDeviceInTesting(device)) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
            } else if (!device.flashingRequired) {
                logger.info("[${device.deviceNum + 1}/$devices] Skipping already flashed device")
            } else {
                val ch = switchboard.mapDeviceToSwitchboard(device.deviceNum)
                if (!switchboard.isDeviceOn(ch)) {
                    switchboard.device(ch, ON)
                    sleep(powerBalanceTimeMS)
                }
                val swd = if (ch < 5) swd1 else swd2
                val itf = switchboard.mapDeviceToSwdInterface(device.deviceNum)
                val gdbPort = 3333 + device.deviceNum * 2
                val task: () -> TestStatus = {
                    statusLogger.info("[${device.deviceNum + 1}/$devices] Flashing")
                    showStatus(device, TestStatus.NOT_UPDATED)
                    device.testStatus = TestStatus.TESTING
                    val startTime = System.currentTimeMillis()
                    val flashAction = ExecuteCommandAction(
                        "Flash firmware",
                        successStrings, failStrings,
                        "sh flash.sh ${swd.config} $itf $imageFile $gdbPort",
                        timeout
                    )
                    val flashResult = flashAction.action("", "", startTime)
                    //if (flashResult.status == TestStatus.ERROR) {
                    //  logger.warning(flashResult.log)
                    //}
                    addResult(device, flashResult)

                    if(eraseFlash && flashResult.status != TestStatus.ERROR) {
                        showStatus(device, TestStatus.RETESTED)
                    }
                    // Testing
                    //if(flashResult.status != TestStatus.ERROR) {
                    //    showStatus(device, TestStatus.PASS)
                    //}

                    flashResult.status
                }
                swd.add(task)
            }
        }
        data class SWDThread(val swd: ProgrammerService) {
            val thread = Thread {
                swd.runAll()
                swd.await()
                for (i in 0 until 2) {
                    var wait = swd.reAddFailed()
                    if (wait) {
                        statusLogger.info("Flashing retry ${i + 1}")
                        swd.runAll()
                        swd.await()
                    }
                }
                swd.clear()
            }
        }
        val swdThreads = listOf(SWDThread(swd1), SWDThread(swd2))
        swdThreads.forEach { it.thread.start() }
        swdThreads.forEach { it.thread.join() }
        switchboard.disableAll()
    }

    private fun enumerateAndTestDevices() {
        switchboard.power(PowerMode.USB)
        switchboard.channel(ChannelMode.A)
        for(i in 1..SERIAL_RETRIES) {
            if (!enumerateSerialDevices(i))
                break
            testDevices(i)
            clearSerial()
        }
        // Cycle the power to properly handle device change on usb ports
        switchboard.disableAll()
        sleep(powerBalanceTimeMS)
        switchboard.power(PowerMode.USB)
        switchboard.channel(ChannelMode.B)
        for(i in 1..SERIAL_RETRIES) {
            if (!enumerateSerialDevices(i))
                break
            testDevices(i)
            clearSerial()
        }

        // TODO: Ping devices from the dongle?

        // TODO: Measure current draw in sleep

        switchboard.disableAll()
    }

    fun Int.toHexString(): String = String.format("%02x", this)

    private fun devicePortLookup(portLocation: String, activeChannel: ChannelMode): DeviceTest? {
        if (!portLocation.startsWith(hubLocationPrefix))
            return null
        val locationSuffix: String = portLocation.drop(hubLocationPrefix.length)
        var deviceNum = channelHubLocations.indexOfFirst { it.contentEquals(locationSuffix) }
        //logger.config("$hubLocationPrefix + $locationSuffix = $deviceNum")
        if (deviceNum == -1)
            return null
        if (deviceNum >= 5)
            deviceNum += 5
        if (activeChannel == ChannelMode.B)
            deviceNum += 5
        return deviceTests[deviceNum]
    }

    // Dmesg callback
    override fun setUSB(addr: String, tty: String) {
        val device = devicePortLookup(addr, switchboard.channel()) ?: return
        ui.setUSB(device.deviceNum, tty)
    }

    private fun enumerateSerialDevices(retry: Int): Boolean {
        if (!hasUnfailedDevicesOnChannel())
            return false

        if(retry != 0) {
            // Cycle the power on retry
            switchboard.device(ALL, OFF)
            sleep(serialEnumTimeMS / 2)
        }
        var portsCount = 0
        for (device in deviceTests) {
            if (switchboard.deviceNumToChannel(device.deviceNum) != switchboard.channel())
                continue
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            if(device.deviceId.isEmpty()) {
                // Enable device to enumerate the port and get info
                val ch = switchboard.mapDeviceToSwitchboard(device.deviceNum)
                switchboard.device(ch, ON)
                showStatus(device, TestStatus.NOT_UPDATED)
                sleep(powerBalanceTimeMS / 2)
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
        val knownPorts = mutableListOf<SerialPort>()
        val endWait = System.currentTimeMillis() + 5000
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

        //SerialPort.getCommPorts().forEach {
        //    logger.warning("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
        //}

        //sleep(500)
        ports.forEach {
            //logger.warning("${it.portLocation} | ${it.systemPortName} | ${it.vendorID.toHexString()}:${it.productID.toHexString()}:${it.serialNumber} | ${it.descriptivePortName}")
            val device = devicePortLookup(it.portLocation, switchboard.channel()) ?: return@forEach
            device.serialPort = it
            knownPorts.add(it)
            serialManager.markAsKnown(it)
            foundSerials++
            //logger.info("[${device.deviceNum + 1}/$devices] Device used: ${it.descriptivePortName} ${it.systemPortPath}")
            ui.setUSB(device.deviceNum, it.systemPortName)
        }
        deviceTests.forEach {
            if (switchboard.deviceNumToChannel(it.deviceNum) != switchboard.channel())
                return@forEach
            if (shouldSkipDevice(it.deviceNum) || !isDeviceInTesting(it))
                return@forEach
            val connectTest = if (it.serialPort == null) {
                if(!switchboard.isDeviceOn(switchboard.mapDeviceToSwitchboard(it.deviceNum)))
                    return@forEach // Skip in the case of partial enumeration
                actionSerialFound.action(false, "Serial port not found", startTime)
            } else {
                val port = it.serialPort!!
                actionSerialFound.action(true, "Serial port found: ${port.descriptivePortName} ${port.systemPortPath}", startTime)
            }
            addResult(it, connectTest)
            if(connectTest.status == TestStatus.ERROR) {
                if(retry < SERIAL_RETRIES) {
                    logger.warning("[${it.deviceNum + 1}/$devices] Port not found. Retry...$retry")
                    it.testStatus = TestStatus.TESTING
                    showStatus(it, TestStatus.PORT_ERROR)
                }
            }
        }
        statusLogger.info("Found $foundSerials/$portsCount serial devices")
        knownPorts.forEach { serialManager.removePort(it) }
        return true
    }

    private fun testDevices(retry: Int) {
        if (!hasUnfailedDevicesOnChannel())
            return

        statusLogger.info("Testing devices...")
        for (device in deviceTests) {
            if (switchboard.deviceNumToChannel(device.deviceNum) != switchboard.channel())
                continue
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            if (!switchboard.isDeviceOn(switchboard.mapDeviceToSwitchboard(device.deviceNum)))
                continue
            if (device.testStatus == TestStatus.ERROR || device.serialPort == null) {
                logger.warning("[${device.deviceNum + 1}/$devices] Skipped due to previous error")
                continue
            }
            testDevice(device, device.serialPort!!, retry)
        }
    }

    fun testDevice(device: DeviceTest, port: SerialPort, retry: Int) {
        //statusLogger.info("[${device.deviceNum + 1}/$devices] Access ${port.systemPortName}")
        showStatus(device, TestStatus.NOT_UPDATED)
        val startTime = System.currentTimeMillis()
        if(serialManager.openPort(port, device)) {
            //statusLogger.info("[${device.deviceNum + 1}/$devices] Port opened")
            sleep(serialOpenTimeMS)

            //statusLogger.info("[${device.deviceNum + 1}] Sending info command...")
            if (device.sendSerialCommand("info")) {
                getDeviceInfo(device, retry)

                if(device.serialDisconnected) {
                    if(retry < SERIAL_RETRIES) {
                        logger.warning("[${device.deviceNum + 1}/$devices] Port disconnected. Retry...$retry")
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
                if(retry < SERIAL_RETRIES) {
                    logger.warning("[${device.deviceNum + 1}/$devices] Can't send serial command. Retry...$retry")
                    device.testStatus = TestStatus.TESTING
                    showStatus(device, TestStatus.PORT_ERROR)
                    return
                }
                serialManager.closePort(port)
                val result = failSendInfoCommand.action("Serial command error", startTime)
                addResult(device, result)
            }
        } else {
            serialManager.closePort(port)
            if(retry < SERIAL_RETRIES) {
                logger.warning("[${device.deviceNum + 1}/$devices] Can't open port. Retry...$retry")
                device.testStatus = TestStatus.TESTING
                showStatus(device, TestStatus.PORT_ERROR)
                return
            }
            val flashResult = failSerialOpened.action("Can't open port. Error ${port.lastErrorCode}", startTime)
            addResult(device, flashResult)
        }
    }

    fun getDeviceInfo(device: DeviceTest, retry: Int) {
        statusLogger.info("[${device.deviceNum + 1}] Testing...")
        // Wait for the command output and disconnect
        val getInfoResult = SerialMatchingAction("Read device info", infoResultSuccess, infoResultFail, device, 2000)
        val startTime = System.currentTimeMillis()
        val infoResult = getInfoResult.action("", "", startTime)
        addResult(device, infoResult)
        if (infoResult.status == TestStatus.PASS) {
            // Data lookup
            //logger.warning(log)
            val buildMatch = infoResult.matchLog("Commit: ([a-zA-Z0-9]+)")
            val buildHash = buildMatch?.get(1) ?: ""
            val buildHashOk = buildHash.startsWith(FIRMWARE_HASH)
            val buildResult = actionFirmwareVersion.action(buildHashOk, buildMatch?.get(0) ?: "Build commit hash not found", startTime)
            if(buildHashOk)
                device.flashingRequired = false
            addResult(device, buildResult)

            device.deviceId = infoResult.matchLog("Device address: ([a-zA-Z0-9:]+)")?.get(1)?.uppercase() ?: ""
            ui.setID(device.deviceNum, device.deviceId)

            val imuMatch = infoResult.matchLog("IMU: ([a-zA-Z0-9-/]+)")
            val imuName = imuMatch?.get(1) ?: "None"
            val failList = listOf("none", "not searching")
            val imuOk = !failList.contains(imuName.lowercase())
            val imuResult = actionGetIMU.action(imuOk, imuMatch?.get(0) ?: "IMU not found", startTime)
            addResult(device, imuResult)

            // TODO Request IMU self-test?
            // Note: IMU detection by SoC is enough for general testing

            // TODO: Test Mag is detected
            // Should be provided by INFO response

            // TODO Request Mag self-test?
            // Note: Mag detection by SoC is enough for general testing

            // Ping the dongle
            checkDonglePing(device, retry)
        } else {
            if(retry < SERIAL_RETRIES) {
                logger.warning("[${device.deviceNum + 1}/$devices] Serial response failed. Retry...$retry")
                device.testStatus = TestStatus.TESTING
                showStatus(device, TestStatus.PORT_ERROR)
                return // to external retry loop
            }
        }
    }

    private val pongResultSuccess = arrayOf("PONG packet received .*".toRegex())
    private val pongResultFail = arrayOf("Unknown command".toRegex())
    private val PONG_RSSI_MIN = 50

    private fun checkDonglePing(device: DeviceTest, retry: Int) {
        val radioChannel = dongle.channel - 2400
        retryPing@ for(i in 1..PING_RETRIES) {
            val startTime = System.currentTimeMillis()
            if (device.sendSerialCommand("ping ${dongle.deviceId} $radioChannel")) {
                val waitPongResult = SerialMatchingAction("Read pong info", pongResultSuccess, pongResultFail, device, 500)
                val pongResult = waitPongResult.action("", "", startTime)
                if(device.serialDisconnected) {
                    return // to external retry loop
                }
                if(pongResult.endValue.lowercase().startsWith("timeout")) {
                    // Dongle can miss ping packet, so it is OK to retry again
                    if(i < PING_RETRIES) {
                        //logger.warning("[${device.deviceNum + 1}/$devices] No pong response. Retry...$i")
                        continue@retryPing
                    }
                }
                val match = pongResult.matchLog("PONG packet received from ([a-zA-Z0-9:]+), RSSI ([0-9]+)")
                val pongAddress = match?.get(1) ?: ""
                val pongRssi = match?.get(2)?.toInt() ?: 255
                val pongOk = pongAddress.contentEquals(device.deviceId) && (pongRssi < PONG_RSSI_MIN)
                val result = actionRadioPong.action(pongOk, match?.get(0) ?: "No pong packet", startTime)
                // Address mismatch may be due to requests collision
                // Low RSSI may be due to radio interference
                // Both is OK, just retry again
                if(result.status == TestStatus.ERROR) {
                    if(i < PING_RETRIES) {
                        //logger.warning("[${device.deviceNum + 1}/$devices] Failed pong response. Retry...$i")
                        continue@retryPing
                    }
                }
                // Record success or the latest error
                addResult(device, pongResult)
                addResult(device, result)
            } else {
                // Failed to send command means either device is disconnected
                // or native exception (example SerialPortTimeoutException) that
                // means port is not operable and we need to reopen it again
                if(device.serialDisconnected) {
                    return // to external retry loop
                }
                if(retry < SERIAL_RETRIES) {
                    logger.warning("[${device.deviceNum + 1}/$devices] Can't send serial command. Retry...$retry")
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

    private fun hasUnfailedDevicesOnChannel(): Boolean {
        for (d in 0 until devices) {
            if (switchboard.channel() != switchboard.deviceNumToChannel(d))
                continue
            if (shouldSkipDevice(d) || !isDeviceInTesting(deviceTests[d]))
                continue
            if (deviceTests[d].testStatus != TestStatus.ERROR)
                return true
        }
        /*for (device in deviceTests) {
            if (switchboard.channel() != switchboard.deviceNumToChannel(device.deviceNum))
                continue
            if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                continue
            if (device.testStatus != TestStatus.ERROR)
                return true
        }*/
        return false
    }

    /*private fun testCurrentDraw() {
        switchboard.power(PowerMode.OFF)
        switchboard.disableAll()
        for (channel in CHANNELS) {
            switchboard.channel(channel)
            for (i in 0 until devices) {
                if (switchboard.deviceNumToChannel(i) != switchboard.channel())
                    continue
                val device = deviceTests[i]
                if (shouldSkipDevice(device.deviceNum) || !isDeviceInTesting(device))
                    continue

                statusLogger.info("[${i + 1}/$devices] Testing power from VBUS... ")
                val ch = switchboard.mapDeviceToSwitchboard(i)
                switchboard.device(ch, ON)

                var list = mutableListOf<Float>()
                switchboard.power(PowerMode.BATTERY)
                //sleep(powerBalanceTimeMS)
                val startTime: Long = System.nanoTime()
                for (i in 0..200) {
                    list.add(getBatCurrent())
                }
                switchboard.power(PowerMode.OFF)
                val endTime: Long = System.nanoTime()
                val listStr = list.fold("") { acc, i -> "$acc, $i" }
                logger.info("=== Voltage ===")
                logger.info("Elapsed: ${endTime - startTime} ns")
                logger.info(listStr)

                switchboard.device(ch, OFF)
            }
        }
    }*/

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
            logger.config("[${device.deviceNum + 1}/$devices] $result")
        if(result.status == TestStatus.DISCONNECTED)
            device.testStatus = result.status
        ui.setStatus(device.deviceNum, device.testStatus)
    }

    private fun showStatus(device: DeviceTest, status: TestStatus) {
        ui.setStatus(device.deviceNum, status)
    }
}
