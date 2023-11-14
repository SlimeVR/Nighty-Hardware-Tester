package dev.slimevr.testing.stage2

import com.fazecast.jSerialComm.SerialPort
import dev.slimevr.database.TestingDatabase
import dev.slimevr.hardware.serial.SerialManager
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import dev.slimevr.testing.actions.SerialMatchingAction
import dev.slimevr.testing.actions.SuccessAction
import dev.slimevr.ui.stage2.Stage2UI
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException
import java.util.logging.Logger

class Stage2TestingSuite(
    private val testingDatabases: List<TestingDatabase>,
    private val testerUi: Stage2UI,
    private val logger: Logger,
    private var statusLogger: Logger
): Thread("Stage 2 testing suite thread") {

    private val getTestCommandFailed = SuccessAction("GET TEST command")
    private val firmwareVersionFailed = SuccessAction("Firmware version check")
    private val serialManager = SerialManager()

    private var committedSuccessfulDeviceIds = mutableListOf<String>()

    private val FIRMWARE_BUILD = 17

    override fun run() {
        try {
            val fw = FileReader("testedBoards.txt")
            fw.forEachLine {
                committedSuccessfulDeviceIds.add(it)
                if (committedSuccessfulDeviceIds.size > 30)
                    committedSuccessfulDeviceIds.removeAt(0)
            }
            statusLogger.info("Loaded ${committedSuccessfulDeviceIds.size} old boards")
        } catch (ex: IOException) {
            statusLogger.info("Old boards not loaded (${ex.message})")
        }
        while(true) {
            statusLogger.info("Waiting for the next device to connect...")
            while(true) {
                val ports = serialManager.findNewPorts()
                if(ports.isEmpty()) {
                    sleep(10)
                    continue
                } else if(ports.size > 1) {
                    statusLogger.severe("More than 1 port connected: ${ports.joinToString()}")
                    sleep(5000)
                    continue
                }
                doTest(ports.first())
                break
            }
            testerUi.statusLogHandler.clear()
            testerUi.fullLogHandler.clear()
            testerUi.setID(0, "")
            testerUi.setStatus(0, TestStatus.DISCONNECTED)
            sleep(500)
        }
    }

    private fun doTest(port: SerialPort) {
        statusLogger.info("Testing port ${port.systemPortPath}...")
        serialManager.markAsKnown(port)
        val device = DeviceTest(0)
        device.serialPort = port
        if(serialManager.openPort(port, device)) {
            statusLogger.info("Port opened")
            //waitBootSequence(device)
            testI2C(device)
            getTest(device)
            testIMU(device)
            checkTestResults(device)
            commitTestResults(device)
            reportTestResults(device)
        } else {
            statusLogger.severe("Can't open port. Error ${port.lastErrorCode}")
            sleep(500L)
        }
        statusLogger.info("Testing finished, disconnect the device!")
        while(serialManager.areKnownPortsConnected()) {
            sleep(10)
        }
        serialManager.closeAllPorts()
    }

    private fun checkTestResults(device: DeviceTest): Boolean {
        var failed = false
        device.endTime = System.currentTimeMillis()
        if (device.testStatus == TestStatus.ERROR) {
            failed = true
        } else {
            device.testStatus = TestStatus.PASS
            testerUi.setStatus(device.deviceNum, TestStatus.PASS)
        }
        device.serialPort?.let { serialManager.closePort(it) }
        return failed
    }

    private fun reportTestResults(device: DeviceTest) {
        if (device.testStatus == TestStatus.ERROR) {
            statusLogger.severe("[${device.deviceNum + 1}] ${device.deviceId}: Test failed")
            for (test in device.testsList) {
                if (test.status == TestStatus.ERROR) {
                    statusLogger.severe(test.toString() + "\n" + test.log)
                }
            }
        } else {
            statusLogger.severe("[${device.deviceNum + 1}] ${device.deviceId}: Test success")
        }
    }

    private fun commitTestResults(device: DeviceTest) {
        statusLogger.info("Committing the test results to database...")
        if(device.deviceId.isBlank()) {
            statusLogger.info("[${device.deviceNum + 1}] Skipping, no ID")
            return
        }
        if(committedSuccessfulDeviceIds.contains(device.deviceId)) {
            statusLogger.info("[${device.deviceNum + 1}] Skipping, recently committed as ${device.deviceId}")
            testerUi.setStatus(device.deviceNum, TestStatus.RETESTED)
            return
        }
        committedSuccessfulDeviceIds.add(device.deviceId)
        if(committedSuccessfulDeviceIds.size > 30)
            committedSuccessfulDeviceIds.removeAt(0)
        for(db in testingDatabases) {
            val response = db.sendTestData(device)
            statusLogger.info("[${device.deviceNum + 1}] ${device.deviceId}: $response")
        }
        try {
            val fw = FileWriter("testedBoards.txt", true)
            fw.append(device.deviceId).append('\n')
            fw.close()
        } catch(ex: IOException) {
            statusLogger.info("Tested boards file write error: ${ex.message}")
        }
    }

    private fun waitBootSequence(device: DeviceTest) {
        statusLogger.info("[${device.deviceNum + 1}] Waiting for boot...")
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
                arrayOf(""".*Sensor 1 sent some data, looks working\..*""".toRegex(RegexOption.IGNORE_CASE)),
                arrayOf(
                    ".*Sensor 1 didn't send any data yet!.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*ERR.*".toRegex(RegexOption.IGNORE_CASE),
                    ".*FATAL.*".toRegex(RegexOption.IGNORE_CASE)
                ),
                device,
                15000
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
            sleep(500)
            if (!device.sendSerialCommand("GET TEST")) {
                val result = getTestCommandFailed.action(false, "Serial command error", startTime)
                addResult(device, result)
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
                    5000
                )
                val result = getTestResult.action("", "", startTime)
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

    private fun addResult(device: DeviceTest, result: TestResult) {
        device.addTestResult(result)
        if(result.status == TestStatus.ERROR)
            statusLogger.severe("[${device.deviceNum + 1}] $result")
        else
            statusLogger.info("[${device.deviceNum + 1}] $result")
        testerUi.setStatus(device.deviceNum, device.testStatus)
    }
}
