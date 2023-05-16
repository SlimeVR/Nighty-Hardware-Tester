package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

class VoltageTestAction(
    val testName: String,
    val minValue: Float,
    val maxValue: Float
): TestAction<Float> {

    override fun action(testedValue: Float, log: String, startTime: Long): TestResult {
        if(testedValue < minValue || testedValue > maxValue) {
            return TestResult(testName, TestStatus.ERROR, startTime, System.currentTimeMillis(), testedValue.toString(), log)
        } else {
            return TestResult(testName, TestStatus.PASS, startTime, System.currentTimeMillis(), testedValue.toString(), log)
        }
    }
}
