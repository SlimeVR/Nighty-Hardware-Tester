package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

class FailedAction(
    private val testName: String
) {
    fun action(log: String, startTime: Long): TestResult {
        return TestResult(testName, TestStatus.ERROR, startTime, System.currentTimeMillis(), testName, log)
    }
}
