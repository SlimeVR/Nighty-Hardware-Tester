package dev.slimevr.testing

import java.util.UUID

class DeviceTest(
    var deviceNum: Int
) {

    var deviceId = UUID.randomUUID().toString()
    var testStatus = TestStatus.TESTING
    val testsList = mutableListOf<TestResult>()
    val startTime = System.currentTimeMillis()
    var endTime = -1L

    /**
     * If the device test should be saved to the database
     * at the end of the testing process
     */
    var commitDevice = false

    fun addTestResult(result: TestResult) {
        testsList.add(result)
        if(result.status == TestStatus.ERROR) {
            testStatus = TestStatus.ERROR
        }
    }

    fun endTest() {
        endTime = System.currentTimeMillis()
    }
}
