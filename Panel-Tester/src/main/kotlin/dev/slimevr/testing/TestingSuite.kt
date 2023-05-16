package dev.slimevr.testing

import dev.slimevr.database.TestingDatabase
import dev.slimevr.testing.actions.VoltageTestAction
import dev.slimevr.ui.TesterUI
import java.util.logging.Logger

class TestingSuite(
    val switchboard: Switchboard,
    val adcProvider: ADCProvider,
    val testingDatabases: List<TestingDatabase>,
    val testerUi: TesterUI,
    val devices: Int
): Thread("Testing suit thread") {

    /**
     * Full log and end report goes here
     */
    private val logger: Logger = Logger.getLogger("Testing Suit")

    /**
     * Current testing status (stage, etc) goes here
     */
    private var statusLogger: Logger = Logger.getLogger("Status logger")

    val powerBalanceTimeMS = 100L
    val bootTimeMS = 1500L

    val actionTestVBUS_REF = VoltageTestAction("VBUS reference", 4.2f, 5.1f)
    val actionTestVBUS_VCC = VoltageTestAction("VCC voltage from VBUS power", 3.3f, 5.1f)
    val actionTestVBUS_3v3 = VoltageTestAction("3v3 voltage from VBUS power", 3.0f, 3.5f)
    val actionTestVBUS_Bat = VoltageTestAction("Bat voltage from VBUS power", 4.0f, 4.5f)

    val actionTestBAT_REF = VoltageTestAction("Bat reference", 4.2f, 5.1f)
    val actionTestBAT_VCC = VoltageTestAction("VCC voltage from Bat power", 3.3f, 5.1f)
    val actionTestBAT_3v3 = VoltageTestAction("3v3 voltage from Bat power", 3.0f, 3.5f)
    val actionTestBAT_VBUS = VoltageTestAction("VBUS voltage from Bat power", -1.0f, 1f)

    val deviceTests = mutableListOf<DeviceTest>()
    var testStart = 0L
    var testEnd = 0L

    override fun run() {
        logger.info("Testing suit started~")
        while(true) {
            waitTestStart()
            testVBUSVoltages()
            testBATVoltages()

            checkTestResultsAndReport()
            finishTestAndCommit()
        }
    }

    private fun checkTestResultsAndReport(): Boolean {
        var failed = false
        for(device in deviceTests) {
            if(device.testStatus == TestStatus.ERROR) {
                failed = true
                logger.severe("[${device.deviceNum+1}/$devices] Test failed:")
                for(test in device.testsList) {
                    if(test.status == TestStatus.ERROR) {
                        logger.severe(test.toString())
                    }
                }
            }
        }
        return failed
    }

    private fun finishTestAndCommit() {
        logger.info("Committing the test results to database...")
        testEnd = System.currentTimeMillis()
        // TODO
    }

    private fun waitTestStart() {
        testerUi.statusLogNandler.clear()
        switchboard.disableAll()
        switchboard.powerOff()
        testerUi.clear()
        deviceTests.clear()
        for(i in 0 until devices) {
            deviceTests.add(DeviceTest(i))
        }
        statusLogger.info("Ready to start the test")
        while(!switchboard.isButtonPressed()) {
            sleep(10)
        }
        statusLogger.info("We goin'~")
        testStart = System.currentTimeMillis()
        for(i in 0 until devices) {
            testerUi.setStatus(i, TestStatus.TESTING)
        }
    }

    private fun testVBUSVoltages() {
        switchboard.powerOff()
        switchboard.disableAll()
        switchboard.powerVbus()
        for(i in 0 until devices) {
            val deviceTest = deviceTests[i]
            statusLogger.info("[${i+1}/$devices] Testing power from VBUS... ")
            switchboard.enableDevice(i)
            sleep(powerBalanceTimeMS)
            val vbus = actionTestVBUS_REF.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $vbus")
            deviceTest.addTestResult(vbus)

            val vcc = actionTestVBUS_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $vcc")
            deviceTest.addTestResult(vcc)

            val v33 = actionTestVBUS_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $v33")
            deviceTest.addTestResult(v33)

            val bat = actionTestVBUS_Bat.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $bat")
            deviceTest.addTestResult(bat)

            switchboard.disableAll()
            testerUi.setStatus(i, deviceTest.testStatus)
        }
    }

    private fun testBATVoltages() {
        switchboard.powerOff()
        switchboard.disableAll()
        switchboard.powerBattery()
        for(i in 0 until devices) {
            val deviceTest = deviceTests[i]
            statusLogger.info("[${i+1}/$devices] Testing power from Battery... ")
            switchboard.enableDevice(i)
            sleep(powerBalanceTimeMS)

            val bat = actionTestBAT_REF.action(adcProvider.getBatVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $bat")
            deviceTest.addTestResult(bat)

            val vbus = actionTestBAT_VBUS.action(adcProvider.getVBUSVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] $vbus")
            deviceTest.addTestResult(vbus)

            val vcc = actionTestBAT_VCC.action(adcProvider.getVCCVoltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] {$vcc}")
            deviceTest.addTestResult(vcc)

            val v33 = actionTestBAT_3v3.action(adcProvider.get3v3Voltage(), "", System.currentTimeMillis())
            logger.info("[${i+1}/$devices] {$v33}")
            deviceTest.addTestResult(v33)

            switchboard.disableAll()
            testerUi.setStatus(i, deviceTest.testStatus)
        }
    }
}
