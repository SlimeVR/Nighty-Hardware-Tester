package dev.slimevr.testing.actions

import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.lang.Thread.sleep
import java.util.logging.Logger
import java.util.regex.Pattern

class SerialMatchingAction(
    testName: String,
    successPatterns: Array<Pattern>,
    failurePatterns: Array<Pattern>,
    private val device: DeviceTest,
    private val timeout: Long
) : MatchingAction(testName, successPatterns, failurePatterns) {

    var logger: Logger = Logger.getLogger("Serial Matching Action")

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val serialLogStart = device.serialLogRead
        while (timeout < 0 || startTime + timeout > System.currentTimeMillis()) {
            with(device) {
                synchronized(serialLog) {
                    if (serialLog.size > serialLogRead) {
                        serialLog.subList(serialLogRead, serialLog.size).forEachIndexed { index, s ->
                            val result = matchString(s)
                            logger.info("$s: $result")
                            if (result != MatchResult.NOT_FOUND) {
                                serialLogRead += index + 1
                                return TestResult(
                                    testName,
                                    if (result.isSuccess()) TestStatus.PASS else TestStatus.ERROR,
                                    startTime,
                                    System.currentTimeMillis(),
                                    s,
                                    serialLog.subList(serialLogStart, serialLogRead).joinToString("\n")
                                )
                            }
                        }
                        serialLogRead = serialLog.size
                    } else {
                        sleep(10)
                    }
                }
            }
        }
        synchronized(device.serialLog) {
            return TestResult(
                testName, TestStatus.ERROR, startTime, System.currentTimeMillis(), "Timeout ${timeout / 1000}s",
                device.serialLog.subList(serialLogStart, device.serialLogRead).joinToString("\n")
            )
        }
    }
}
