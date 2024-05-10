package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.Alert
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.model.AlertCodeSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.min

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

  fun stubGetActiveAlertsForPrisoner(offenderNo: String, count: Int = 1) {
    stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PageImpl(
                (1..min(count, 1000)).map { dpsAlert() },
                Pageable.ofSize(1000),
                count.toLong(),
              ),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetActiveAlertsForPrisoner(offenderNo: String, vararg alerts: Alert) {
    stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              PageImpl(
                alerts.toList(),
                Pageable.ofSize(1000),
                alerts.size.toLong(),
              ),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetActiveAlertsForPrisoner(offenderNo: String, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/alerts"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }
}

fun dpsAlert(): Alert = Alert(
  alertUuid = UUID.randomUUID(),
  prisonNumber = "A1234AA",
  alertCode = dpsAlertCode("A"),
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

fun dpsAlertCode(code: String) = AlertCodeSummary(
  alertTypeCode = "A",
  code = code,
  description = "Alert code description",
  listSequence = 3,
  isActive = true,
)
