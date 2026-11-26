package dev.slimevr.testing

data class TestResult(
    var testName: String,
    var status: TestStatus,
    var timeStart: Long,
    var timeEnd: Long,
    var endValue: String,
    var log: String
) {
    companion object {
        const val OK: String = "OK"
    }

    /**
     * Replace output with simple OK in PASS
     * <p>
     * Minimize visual noise in logs. Especially for SerialMatchingAction results.
     *
     */
    fun setOkOnPass() {
        if(status == TestStatus.PASS)
            endValue = OK
    }

    override fun toString(): String {
        return if(endValue.contentEquals(OK))
            "$testName (${status.name})"
        else
            "$testName: $endValue (${status.name})"
    }

    fun matchLog(pattern: String): List<String>? {
        return pattern.toRegex().find(log)?.groupValues
    }
}
