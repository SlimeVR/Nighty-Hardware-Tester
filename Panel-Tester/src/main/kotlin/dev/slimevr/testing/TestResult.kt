package dev.slimevr.testing

data class TestResult(
    var testName: String,
    var status: TestStatus,
    var timeStart: Long,
    var timeEnd: Long,
    var endValue: String,
    var log: String
) {
    override fun toString(): String {
        return "$testName: $endValue (${status.name}): $log"
    }
}
