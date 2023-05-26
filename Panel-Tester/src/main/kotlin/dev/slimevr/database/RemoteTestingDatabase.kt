package dev.slimevr.database

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import dev.slimevr.testing.DeviceTest
import dev.slimevr.testing.TestStatus
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date

class RemoteTestingDatabase(
    val rpc_url: String,
    val rpc_password: String,
    val testerName: String,
    val testType: String
) : TestingDatabase {

    var dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")

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
        val request = httpRequestBuilder.POST(BodyPublishers.ofString(json)).build()
        val response = httpClient.send(request, BodyHandlers.ofString())
        return response.body()
    }

    private fun toReport(device: DeviceTest) = TestReport(
        device.deviceId,
        testType,
        toTestValues(device),
        testerName,
        dateFormat.format(Date(device.startTime)),
        dateFormat.format(Date(device.endTime))
    )

    private fun toTestValues(device: DeviceTest) = device.testsList.map {
        TestReportValue(
            it.testName,
            it.testName,
            it.endValue,
            it.log,
            it.status != TestStatus.PASS,
            dateFormat.format(Date(it.timeStart)),
            dateFormat.format(Date(it.timeEnd))
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
