package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult

interface TestAction<T> {

    fun action(testedValue: T, log: String, startTime: Long): TestResult
}
