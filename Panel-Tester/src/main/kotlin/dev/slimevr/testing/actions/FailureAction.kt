package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

class FailureAction(
    private val testName: String
): TestAction<Boolean> {
    override fun action(testedValue: Boolean, log: String, startTime: Long): TestResult {
        return TestResult(testName, TestStatus.ERROR, startTime, System.currentTimeMillis(), testedValue.toString(), log)
    }
}
