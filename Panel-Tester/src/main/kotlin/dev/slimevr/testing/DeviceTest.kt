package dev.slimevr.testing

import java.util.UUID

class DeviceTest {

    var deviceId = UUID.randomUUID().toString()
    var testStatus = TestStatus.DISCONNECTED
    val testsList = ArrayList<TestResult>()
    val startTime = System.currentTimeMillis()
    var endTime = -1L
}
