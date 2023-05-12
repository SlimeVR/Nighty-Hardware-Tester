package dev.slimevr.testing

data class TestResult(
    var testName: String,
    var status: TestStatus,
    var timeStart: Long,
    var timeEnd: Long,
    var endValue: String,
    var log: String
)
