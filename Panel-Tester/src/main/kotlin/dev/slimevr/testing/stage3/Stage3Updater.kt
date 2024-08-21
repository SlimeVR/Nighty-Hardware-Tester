package dev.slimevr.testing.stage3

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.actions.ExecuteCommandAction
import dev.slimevr.testing.actions.SerialMatchingAction
import dev.slimevr.testing.actions.SuccessAction
import dev.slimevr.ui.stage2.Stage2UI
import dev.slimevr.ui.updater.Stage3UpdaterUI
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.logging.Logger

class Stage3Updater(
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi: Stage3UpdaterUI,
    private val logger: Logger,
    private val deviceLoggers: Array<Logger?>,
    private val wifiSSID: String,
    private val wifiPass: String
): Thread("Stage 3 updater thread") {

    private val getTestCommand = SuccessAction("GET TEST command")
    private val factoryResetCommand = SuccessAction("FRST command")
    private val setWiFiCommand = SuccessAction("SET WIFI command")
    private val wifiConnectionParseFailed = SuccessAction("WiFi Connection parsing failed")
    private val serialManager = SerialManager()

    private var committedSuccessfulDeviceIds = mutableListOf<String>()

    private val FIRMWARE_BUILD = 17

    private val emptyDevices = BooleanArray(deviceLoggers.size) { true }

    override fun run() {
        try {
            val fw = FileReader("updatedStage3Boards.txt")
            fw.forEachLine {
                committedSuccessfulDeviceIds.add(it)
                if (committedSuccessfulDeviceIds.size > 30)
                    committedSuccessfulDeviceIds.removeAt(0)
            }
            logger.info("Loaded ${committedSuccessfulDeviceIds.size} old boards")
        } catch (ex: IOException) {
            logger.info("Old boards not loaded (${ex.message})")
        }
        logger.info("Ready, connect devices")

        while(true) {
            sleep(25)
            val ports = serialManager.findNewPorts()
            if (ports.isEmpty()) {
                continue
            }
            ports.forEach { p ->
                serialManager.markAsKnown(p)
                Thread {
                    doUpdate(p)
                }.start()
            }
        }
    }

    private fun portToDeviceNum(port: SerialPort): Int {
        // for windows we just pick first empty device
        for (i in emptyDevices.indices) {
            if(emptyDevices[i]) {
                emptyDevices[i] = false
                return i
            }
        }
        return -1
    }

    private fun releaseDevice(port: SerialPort, deviceNum: Int) {
        emptyDevices[deviceNum] = true
    }

    private fun doUpdate(port: SerialPort) {
        val deviceNum = portToDeviceNum(port)
        if(deviceNum < 0) {
            serialManager.removePort(port)
            logger.severe("Can't map port to a device: ${port.systemPortPath}")
            return
        }
        logger.info("Device ${deviceNum + 1} connected to ${port.systemPortPath}")
        deviceLoggers[deviceNum]?.info("[${deviceNum + 1}] Updating on port ${port.systemPortPath}...")
        val device = DeviceTest(deviceNum, deviceLoggers[deviceNum] ?: Logger.getLogger("devices"))
        device.serialPort = port
        if(serialManager.openPort(port, device)) {
            deviceLoggers[deviceNum]?.info("Port opened")
            //waitBootSequence(device)
            getTest(device)
            if(!device.flashingRequired) {
                deviceLoggers[deviceNum]?.info("[${device.deviceNum + 1}] Already current version, flashing not required")
            } else {
                connectToWiFi(device)
                waitWiFiConnection(device)
                flashUpdate(device)
                waitBootSequence(device)
            }
            factoryReset(device)
            waitBootSequence(device)
            if(device.deviceId == "*****" || device.deviceId.isBlank()) {
                getTest(device, true)
            }
            checkTestResults(device)
            commitTestResults(device)
            reportTestResults(device)
        } else {
            testerUi.setStatus(deviceNum, TestStatus.PORT_ERROR)
            deviceLoggers[deviceNum]?.severe("[${device.deviceNum + 1}] Can't open port. Error ${port.lastErrorCode}")
            sleep(500L)
        }
        deviceLoggers[deviceNum]?.info("[${device.deviceNum + 1}] Update finished, disconnect the device!")
        logger.info("[${device.deviceNum + 1}] Update finished, disconnect the device!")
        while(!device.serialDisconnected && serialManager.isPortConnected(port)) {//while(!device.serialDisconnected && serialManager.areKnownPortsConnected()) {
            sleep(25)
        }
        port.closePort()
        serialManager.removePort(port)
        releaseDevice(port, deviceNum)
        testerUi.clear(deviceNum)
        logger.info("[${device.deviceNum + 1}] Device disconnected, ready for the next!")
    }

    private fun checkTestResults(device: DeviceTest): Boolean {
        var failed = false
        device.endTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            failed = true
        } else if(!device.flashingRequired) {
            testerUi.setStatus(device.deviceNum, TestStatus.NOT_UPDATED)
        } else {
            device.testStatus = TestStatus.PASS
            testerUi.setStatus(device.deviceNum, TestStatus.PASS)
        }
        return failed
    }

    private fun reportTestResults(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.severe("[${device.deviceNum + 1}] ${device.deviceId}: Test failed")
            for (test in device.testsList) {
                if (test.status == TestStatus.ERROR) {
                    deviceLoggers[device.deviceNum]?.severe(test.toString() + "\n" + test.log)
                }
            }
        } else {
            deviceLoggers[device.deviceNum]?.severe("[${device.deviceNum + 1}] ${device.deviceId}: Test success")
        }
    }

    private fun commitTestResults(device: DeviceTest) {
        deviceLoggers[device.deviceNum]?.info("Committing the test results to database...")
        if(device.deviceId.isBlank()) {
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Skipping, no ID")
            return
        }
        if(device.testStatus == TestStatus.PASS) {
            if (committedSuccessfulDeviceIds.contains(device.deviceId)) {
                deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Skipping, recently committed as ${device.deviceId}")
                testerUi.setStatus(device.deviceNum, TestStatus.RETESTED)
                return
            }
            committedSuccessfulDeviceIds.add(device.deviceId)
            if (committedSuccessfulDeviceIds.size > 30)
                committedSuccessfulDeviceIds.removeAt(0)
        }
        for(db in testingDatabases) {
            val response = db.sendTestData(device)
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] ${device.deviceId}: $response")
        }
        if(device.testStatus == TestStatus.PASS) {
            try {
                val fw = FileWriter("updatedStage3Boards.txt", true)
                fw.append(device.deviceId).append('\n')
                fw.close()
            } catch (ex: IOException) {
                deviceLoggers[device.deviceNum]?.info("Tested boards file write error: ${ex.message}")
            }
        }
    }

    private fun connectToWiFi(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Connecting to WiFi $wifiSSID...")
            val startTime = System.currentTimeMillis()
            if (!device.sendSerialCommand("SET WIFI \"$wifiSSID\" \"$wifiPass\"")) {
                val result = setWiFiCommand.action(false, "Serial command error", startTime)
                addResult(device, result)
            } else {
                val result = setWiFiCommand.action(true, "Serial command sent", startTime)
                addResult(device, result)
            }
        }
    }

    private fun factoryReset(device: DeviceTest) {
        deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Factory reset...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            sleep(500)
            val response = device.sendSerialCommand("FRST")
            val result = factoryResetCommand.action(response, if (response) "Serial command success"  else "Serial command error", startTime)
            addResult(device, result)
        }
    }

    private fun getTest(device: DeviceTest, recheck: Boolean = false) {
        deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Getting info...")
        val startTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            sleep(500)
            if (!device.sendSerialCommand("GET TEST")) {
                val result = getTestCommand.action(false, "Serial command error", startTime)
                addResult(device, result)
            } else {
                val getTestResult = SerialMatchingAction(
                    "Read firmware state",
                    arrayOf(
                        """.*\[TEST] Board:.*""".toRegex()
                    ),
                    arrayOf(
                        ".*ERR.*".toRegex(),
                        ".*FATAL.*".toRegex()
                    ),
                    device,
                    5000
                )
                val result = getTestResult.action("", "", startTime)
                addResult(device, result)
                if (result.status == TestStatus.PASS) {
                    deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] End value: ${result.endValue}")
                    val match = """.*build: (\d+),.*mac: ([a-zA-Z0-9:*]+),.*""".toRegex()
                        .matchAt(result.endValue, 0)
                    if (match != null) {
                        deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Build: ${match.groupValues[1]}, mac: ${match.groupValues[2]}")
                        device.deviceId = match.groupValues[2].lowercase()
                        testerUi.setID(device.deviceNum, device.deviceId)
                        if (!recheck && match.groupValues[1].toInt() == FIRMWARE_BUILD) {
                            device.flashingRequired = false
                        }
                    }
                }
            }

        }
    }

    private fun flashUpdate(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            val startTime = System.currentTimeMillis()
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Flashing...")
            val flashAction = ExecuteCommandAction(
                "Flash firmware", arrayOf(
                    ".*`pio` exited with non-zero exit code.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*Result: OK.*".toRegex(RegexOption.IGNORE_CASE)
                ), arrayOf(
                    ".*Errno.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*error.*".toRegex(RegexOption.IGNORE_CASE)
                ),
                "${System.getenv("UPDATER_PIO_PATH")} run -t upload --upload-port ${device.ipAddress}", -1
                , File(System.getenv("UPDATER_FIRMWARE_PATH")),
                deviceLoggers[device.deviceNum]
            )
            val flashResult = flashAction.action("", "", startTime)
            addResult(device, flashResult)
        }
    }

    private fun waitBootSequence(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Waiting for boot...")
            val startTime = System.currentTimeMillis()
            val testI2C = SerialMatchingAction(
                "Boot test",
                arrayOf(
                    """\[INFO ] \[SlimeVR] SlimeVR .* starting up...""".toRegex()
                ),
                arrayOf(
                    "ERR".toRegex(),
                    "FATAL".toRegex()
                ),
                device,
                30000
            )
            val result = testI2C.action("", "", startTime)
            addResult(device, result)
        }
    }

    private fun waitWiFiConnection(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            deviceLoggers[device.deviceNum]?.warning("[${device.deviceNum + 1}] Skipped due to previous error")
        } else {
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Waiting to connect to wifi...")
            val startTime = System.currentTimeMillis()
            val testI2C = SerialMatchingAction(
                "WiFi Connected",
                arrayOf(
                    """\[INFO ] \[WiFiHandler] Connected successfully to SSID .*""".toRegex()
                ),
                arrayOf(
                    "ERR".toRegex(),
                    "FATAL".toRegex(),
                    "\"Can't connect".toRegex()
                ),
                device,
                30000
            )
            val result = testI2C.action("", "", startTime)
            addResult(device, result)
            if (result.status == TestStatus.PASS) {
                deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] Connected: ${result.endValue}")
                val match = """.*SSID '([^']+)', ip address ([0-9]+\.[0-9]+\.[0-9]+\.[0-9]+)""".toRegex()
                    .matchAt(result.endValue, 0)
                if (match != null) {
                    deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] SSID: ${match.groupValues[1]}, IP: ${match.groupValues[2]}")
                    device.ipAddress = match.groupValues[2]
                    return
                }
                val result = wifiConnectionParseFailed.action(false, result.endValue, startTime)
                addResult(device, result)
            }
        }
    }

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if(result.status == TestStatus.ERROR)
            deviceLoggers[device.deviceNum]?.severe("[${device.deviceNum + 1}] $result")
        else
            deviceLoggers[device.deviceNum]?.info("[${device.deviceNum + 1}] $result")
        testerUi.setStatus(device.deviceNum, device.testStatus)
    }
}
