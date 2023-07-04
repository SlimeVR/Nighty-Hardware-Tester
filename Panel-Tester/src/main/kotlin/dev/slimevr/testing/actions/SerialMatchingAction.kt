package dev.slimevr.testing.actions

import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestResult
import dev.slimevr.testing.TestStatus
import java.lang.Thread.sleep
import java.util.logging.Logger

class SerialMatchingAction(
    testName: String,
    successPatterns: Array<Regex>,
    failurePatterns: Array<Regex>,
    private val device: DeviceTest,
    private val timeout: Long
) : MatchingAction(testName, successPatterns, failurePatterns) {

    var logger: Logger = Logger.getLogger("Serial Matching Action")

    override fun action(testedValue: String, log: String, startTime: Long): TestResult {
        val serialLogStart = device.serialLogRead
        val timeoutTo = System.currentTimeMillis() + timeout
        while (timeout < 0 || timeoutTo > System.currentTimeMillis()) {
            with(device) {
                synchronized(serialLog) {
                    if (serialDisconnected) {
                        return TestResult(
                            testName,
                            TestStatus.ERROR,
                            startTime,
                            System.currentTimeMillis(),
                            "Serial disconnected",
                            serialLog.subList(serialLogStart, serialLogRead).joinToString("\n")
                        )
                    }
                    if (serialLog.size > serialLogRead) {
                        serialLog.subList(serialLogRead, serialLog.size).forEachIndexed { index, s ->
                            val result = matchString(s)
                            //logger.info(s)
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
                    }
                }
                sleep(10)
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
