package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class ActivitiesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val activitiesApi = ActivitiesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    activitiesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    activitiesApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    activitiesApi.stop()
  }
}

class ActivitiesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8086
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  private fun stubGet(url: String, response: String) =
    stubFor(
      get(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )

  private fun stubGetWithError(url: String, status: Int = 500) =
    stubFor(
      get(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "error": "some error"
              }
            """.trimIndent(),
          )
          .withStatus(status),
      ),
    )

  fun stubGetSchedule(id: Long, response: String) {
    stubGet("/schedules/$id", response)
  }

  fun stubGetScheduleWithError(id: Long, status: Int = 500) {
    stubGetWithError("/schedules/$id", status)
  }

  fun stubGetActivity(id: Long, response: String) {
    stubGet("/activities/$id", response)
  }

  fun stubGetActivityWithError(id: Long, status: Int = 500) {
    stubGetWithError("/activities/$id", status)
  }

  fun stubGetAllocation(id: Long, response: String) {
    stubGet("/allocations/id/$id", response)
  }

  fun stubGetAllocationWithError(id: Long, status: Int = 500) {
    stubGetWithError("/allocations/id/$id", status)
  }

  fun stubGetAttendanceSync(id: Long, response: String) {
    stubGet("/synchronisation/attendance/$id", response)
  }

  fun stubGetAttendanceSyncWithError(id: Long, status: Int = 500) {
    stubGetWithError("/synchronisation/attendance/$id", status)
  }

  fun stubGetScheduledInstance(id: Long, response: String) {
    stubGet("/scheduled-instances/$id", response)
  }

  fun stubGetScheduledInstanceWithError(id: Long, status: Int = 500) {
    stubGetWithError("/scheduled-instances/$id", status)
  }

  fun getCountFor(url: String) = this.findAll(getRequestedFor(urlEqualTo(url))).count()

  fun stubAllocationReconciliation(prisonId: String, response: String) =
    stubGet("/synchronisation/reconciliation/allocations/$prisonId", response)

  fun stubAllocationReconciliationWithError(prisonId: String, status: Int = 500) {
    stubGetWithError("/synchronisation/reconciliation/allocations/$prisonId", status)
  }
}
