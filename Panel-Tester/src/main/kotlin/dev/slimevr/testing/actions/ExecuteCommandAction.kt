package dev.slimevr.testing.actions

import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.io.File
import java.io.IOException
import java.lang.StringBuilder
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Executes console command and tests output for patterns.
 * The action will block until the process launched exists or
 * timeout reached, even if the process will end with success or
 * failure earlier.
 */
class ExecuteCommandAction(
    testName: String,
    successPatterns: Array<Regex>,
    failurePatterns: Array<Regex>,
    command: String,
    private val timeout: Long,
    directory: File? = null,
    private val logger: Logger? = null
) : MatchingAction(testName, successPatterns, failurePatterns) {

    private val processBuilder: ProcessBuilder =
        ProcessBuilder(command.split(" ")).redirectErrorStream(true).redirectOutput(
            ProcessBuilder.Redirect.PIPE
        ).directory(directory ?: File("").absoluteFile)

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val fullLog = mutableListOf<String>()
        if (log.isNotBlank())
            fullLog.add(log)
        var logStart = fullLog.size
        logger?.info("Starting process: " + processBuilder.command().joinToString(" "))
        val process = try {
            processBuilder.start()
        } catch(ex: Exception) {
            logger?.log(Level.SEVERE, "Can't start process", ex)
            return TestResult(
                testName,
                TestStatus.ERROR,
                startTime,
                System.currentTimeMillis(),
                ex.toString(),
                fullLog.joinToString("\n"))
        }
        val endTime = System.currentTimeMillis() + timeout
        val inputReader = process.inputReader()
        var testResult = TestStatus.TESTING
        var matchedString = ""
        var destroyed = false
        var currentLine = StringBuilder()
        do {
            try {
                while(inputReader.ready()) {
                    when (val ch = inputReader.read()) {
                        -1 -> break
                        '\n'.code -> {
                            if (currentLine.isNotBlank()) {
                                fullLog.add(currentLine.toString())
                                currentLine.setLength(0)
                            }
                        }
                        else -> currentLine.appendCodePoint(ch)
                    }
                }
            } catch(ex: IOException) {
                ex.message?.let { fullLog.add(it) }
                break
            }
            fullLog.subList(logStart, fullLog.size).forEach {
                logger?.info(it)
                val result = matchString(it)
                if ((result == MatchResult.SUCCESS) && (testResult != TestStatus.ERROR)) {
                    matchedString = it
                    testResult = TestStatus.PASS
                } else if (result == MatchResult.FAILURE) {
                    matchedString = it
                    testResult = TestStatus.ERROR
                }
            }
            if (!destroyed && timeout > 0 && endTime < System.currentTimeMillis()) {
                process.destroyForcibly()
                matchedString = "Timeout ${timeout / 1000}s"
                fullLog.add("Forcibly destroying process after ${timeout / 1000}s")
                testResult = TestStatus.ERROR
                destroyed = true
            }
            logStart = fullLog.size
        } while(process.isAlive)
        if(currentLine.isNotBlank())
            fullLog.add(currentLine.toString())
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
