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
