package dev.slimevr.database

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.time.Instant
import java.util.logging.Logger

class RemoteTestingDatabase(
    val rpc_url: String,
    val rpc_password: String,
    val testerName: String,
    val testType: String,
    val globalLogger: Logger
) : TestingDatabase {

    val HTTP_RETRIES = 3

    val httpClient = HttpClient.newHttpClient()
    val httpRequestBuilder = HttpRequest.newBuilder(URI.create(rpc_url))
        .timeout(Duration.ofMinutes(1))
        .header("Content-Type", "application/json")
        .header("Authorization", rpc_password)

    override fun sendTestData(device: DeviceTest): String {
        if(device.deviceId.isBlank())
            return "No ID, not sent"
        val writer = ObjectMapper().writer().with(SerializationFeature.INDENT_OUTPUT)
        val body = ApiRequestBody("insert_test_report", toReport(device))
        val json = writer.writeValueAsString(body)
        val request = httpRequestBuilder.POST(BodyPublishers.ofString(json)).timeout(Duration.ofSeconds(10)).build()
        var response: HttpResponse<String>? = null
        for(i in 1..HTTP_RETRIES) {
            try {
                response = httpClient.send(request, BodyHandlers.ofString())
                break
            } catch(ex: HttpTimeoutException) {
                if(i < HTTP_RETRIES) {
                    globalLogger.warning("HTTP Timeout: ${ex.message}. Retry... $i")
                    continue
                }
                throw ex
            }
        }
        return "${response!!.statusCode()}: ${response.body()}"
    }

    private fun toReport(device: DeviceTest) = TestReport(
        device.deviceId,
        testType,
        toTestValues(device),
        testerName,
        if (device.testStatus == TestStatus.PASS) "succeeded" else "failed",
        Instant.ofEpochMilli(device.startTime).toString(),
        Instant.ofEpochMilli(device.endTime).toString()
    )

    private fun toTestValues(device: DeviceTest) = device.testsList.map {
        TestReportValue(
            it.testName,
            it.testName,
            it.endValue.ifEmpty { "<empty>" },
            it.log,
            it.status != TestStatus.PASS,
            Instant.ofEpochMilli(it.timeStart).toString(),
            Instant.ofEpochMilli(it.timeEnd).toString()
        )
    }

    data class ApiRequestBody(
        val method: String,
        val params: TestReport
    )

    data class TestReport(
        val id: String,
        val type: String,
        val values: List<TestReportValue>,
        val tester: String,
        val state: String,
        val startedAt: String,
        val endedAt: String
    )

    data class TestReportValue(
        val step: String,
        val condition: String,
        val value: String,
        val logs: String?,
        val failed: Boolean,
        val startedAt: String,
        val endedAt: String
    )
}
