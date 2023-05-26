package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.logging.Logger
import java.util.regex.Pattern

/**
 * Executes console command and tests output for patterns.
 * The action will block until the process launched exists or
 * timeout reached, even if the process will end with success or
 * failure earlier.
 */
class ExecuteCommandAction(
    testName: String,
    successPatterns: Array<Pattern>,
    failurePatterns: Array<Pattern>,
    command: String,
    private val timeout: Long
) : MatchingAction(testName, successPatterns, failurePatterns) {

    private val logger: Logger = Logger.getLogger("ExecuteCommandAction")

    private val processBuilder: ProcessBuilder =
        ProcessBuilder(command.split(" ")).redirectErrorStream(true).redirectOutput(
            ProcessBuilder.Redirect.PIPE
        )

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val fullLog = mutableListOf<String>()
        if (log.isNotBlank())
            fullLog.add(log)
        var logStart = fullLog.size
        logger.info("Starting process: " + processBuilder.command().joinToString(" "))
        val process = processBuilder.start()
        val endTime = System.currentTimeMillis() + timeout
        val inputReader = process.inputReader()
        var testResult = TestStatus.TESTING
        var matchedString = ""
        var destroyed = false
        do {
            try {
                when (val line = inputReader.readLine()) {
                    null -> break
                    else -> if (line.isNotBlank()) fullLog.add(line)
                }
            } catch(ex: IOException) {
                ex.message?.let { fullLog.add(it) }
            }
            fullLog.subList(logStart, fullLog.size).forEach {
                logger.info(it)
                val result = matchString(it)
                if ((result == MatchResult.SUCCESS) && (testResult != TestStatus.ERROR)) {
                    matchedString = it
                    testResult = TestStatus.PASS
                } else if (result == MatchResult.FAILURE) {
                    testResult = TestStatus.ERROR
                }
            }
            if (!destroyed && timeout > 0 && endTime < System.currentTimeMillis()) {
                process.destroyForcibly()
                fullLog.add("Forcibly destroying process after ${timeout / 1000}s")
                testResult = TestStatus.ERROR
                destroyed = true
            }
            logStart = fullLog.size
        } while(process.isAlive)
        //fullLog.add("Process ended with ${process.exitValue()} exit code")
        // If we didn't pass the test yet, it's a failure
        if (testResult == TestStatus.TESTING) {
            testResult = if (successPatterns.isEmpty() && failurePatterns.isEmpty())// && process.exitValue() == 0)
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
            fullLog.joinToString("\n"))
    }
}
