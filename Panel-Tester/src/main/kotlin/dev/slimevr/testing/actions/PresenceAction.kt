package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

class PresenceAction(
    private val testName: String
): TestAction<Boolean> {
    override fun action(testedValue: Boolean, log: String, startTime: Long): TestResult {
        return TestResult(testName, if (testedValue) TestStatus.PASS else TestStatus.DISCONNECTED, startTime, System.currentTimeMillis(), testedValue.toString(), log)
    }
}
