package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.util.regex.Pattern

open class MatchingAction(
    protected val testName: String,
    protected val successPatterns: Array<Pattern>,
    protected val failurePatterns: Array<Pattern>
): TestAction<String> {

    override fun action(testedValue: String, log: String, startTime: Long): dev.slimevr.testing.TestResult {
        val res = matchString(testedValue)
        return TestResult(testName, if(res.isSuccess()) TestStatus.PASS else TestStatus.ERROR,
            startTime, System.currentTimeMillis(), testedValue, log)
    }

    fun matchString(string: String): MatchResult {
        for(pattern in failurePatterns) {
            if(pattern.matcher(string).matches())
                return MatchResult.FAILURE
        }
        for(pattern in successPatterns) {
            if(pattern.matcher(string).matches())
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
