package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.util.logging.Logger

open class MatchingAction(
    protected val testName: String,
    protected val successPatterns: Array<Regex>,
    protected val failurePatterns: Array<Regex>
) : TestAction<String> {

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val res = matchString(testedValue)
        return TestResult(
            testName, if (res.isSuccess()) TestStatus.PASS else TestStatus.ERROR,
            startTime, System.currentTimeMillis(), testedValue, log
        )
    }

    fun matchString(string: String): MatchResult {
        for (pattern in failurePatterns) {
            if (string.contains(pattern))
                return MatchResult.FAILURE
        }
        for (pattern in successPatterns) {
            if (string.contains(pattern))
                return MatchResult.SUCCESS
        }
        return MatchResult.NOT_FOUND
    }

    enum class MatchResult {
        SUCCESS,
        FAILURE,
        NOT_FOUND;

        fun isSuccess() = this == SUCCESS

        fun isFailure() = this == FAILURE

        fun isMatched() = this != NOT_FOUND
    }
}
