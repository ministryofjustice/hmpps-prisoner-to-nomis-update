package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.AlertCodeSummary
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class AlertsDpsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val alertsDpsApi = AlertsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
    alertsDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    alertsDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    alertsDpsApi.stop()
  }
}

class AlertsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8095
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

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(AlertsDpsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }

  fun stubGetAlert(alert: Alert = dpsAlert()) {
    stubFor(
      get(urlMatching("/alerts/.*"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(alert)
            .withStatus(200),
        ),
    )
  }
}

fun dpsAlert(): Alert = Alert(
  alertUuid = UUID.randomUUID(),
  prisonNumber = "A1234AA",
  alertCode = AlertCodeSummary(
    alertTypeCode = "A",
    code = "ABC",
    description = "Alert code description",
    listSequence = 3,
    isActive = true,
  ),
  description = "Alert description",
  authorisedBy = "A. Nurse, An Agency",
  activeFrom = LocalDate.parse("2021-09-27"),
  activeTo = LocalDate.parse("2022-07-15"),
  isActive = true,
  comments = emptyList(),
  createdAt = LocalDateTime.parse("2024-02-28T13:56:10"),
  createdBy = "USER1234",
  createdByDisplayName = "Firstname Lastname",
  lastModifiedAt = LocalDateTime.parse("2024-02-28T13:56:10"),
  lastModifiedBy = "USER1234",
  lastModifiedByDisplayName = "Firstname Lastname",
)
