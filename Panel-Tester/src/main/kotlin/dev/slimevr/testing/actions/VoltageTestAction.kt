package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

class VoltageTestAction(
    val testName: String,
    val minValue: Float,
    val maxValue: Float
): TestAction<Float> {

    fun fail(value: Float) : Boolean = (value < minValue || value > maxValue)
    fun pass(value: Float): Boolean = !fail(value)

    override fun action(testedValue: Float, log: String, startTime: Long): TestResult {
        val status = if (fail(testedValue)) TestStatus.ERROR else TestStatus.PASS
        return TestResult(testName, status, startTime, System.currentTimeMillis(), testedValue.toString(), log)
    }
}
