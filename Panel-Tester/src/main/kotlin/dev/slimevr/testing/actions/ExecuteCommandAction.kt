package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus

/**
 * Executes console command and tests output for patterns.
 * The action will block until the process launched exists or
 * timeout reached, even if the process will end with success or
 * failure earlier.
 */
class ExecuteCommandAction(
    testName: String,
    successPatterns: Array<String>,
    failurePatterns: Array<String>,
    command: String,
    private val timeout: Long
) : MatchingAction(testName, successPatterns, failurePatterns) {

    private val processBuilder: ProcessBuilder =
        ProcessBuilder(command.split(" ")).redirectError(ProcessBuilder.Redirect.PIPE).redirectOutput(
            ProcessBuilder.Redirect.PIPE
        )

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val fullLog = mutableListOf<String>()
        if (log.isNotBlank())
            fullLog.add(log)
        var logStart = fullLog.size
        val process = processBuilder.start()
        val endTime = System.currentTimeMillis() + timeout
        val errorReader = process.errorReader()
        val inputReader = process.inputReader()
        var testResult = TestStatus.TESTING
        var matchedString = ""
        while (process.isAlive) {
            when (val line = errorReader.readLine()) {
                null -> break
                else -> fullLog.add(line)
            }
            when (val line = inputReader.readLine()) {
                null -> break
                else -> fullLog.add(line)
            }
            fullLog.subList(logStart, fullLog.size).forEach {
                val result = matchString(it)
                if ((result == MatchResult.SUCCESS) && (testResult != TestStatus.ERROR)) {
                    matchedString = it
                    testResult = TestStatus.PASS
                } else if (result == MatchResult.FAILURE) {
                    testResult = TestStatus.ERROR
                }
            }
            if (timeout > 0 && endTime < System.currentTimeMillis()) {
                process.destroyForcibly()
                fullLog.add("Forcibly destroying process after ${timeout / 1000}s")
                testResult = TestStatus.ERROR
                break
            }
            logStart = fullLog.size
        }
        // If we didn't pass the test yet, it's a failure
        if (testResult == TestStatus.TESTING) {
            testResult = if (successPatterns.isEmpty() && failurePatterns.isEmpty() && process.exitValue() == 0)
                TestStatus.PASS
            else
                TestStatus.ERROR
        }
        return TestResult(
            testName,
            testResult,
            startTime,
            System.currentTimeMillis(),
            matchedString,
            fullLog.joinToString { "\n" })
    }
}
