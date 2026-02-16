package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAlertsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class AlertsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubPostAlert(
    offenderNo: String = "A1234AK",
    alert: CreateAlertResponse = createAlertResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/prisoners/$offenderNo/alerts")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun stubResynchroniseAlerts(
    offenderNo: String = "A1234AK",
    alerts: List<CreateAlertResponse> = listOf(createAlertResponse()),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/prisoners/$offenderNo/alerts/resynchronise")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(alerts)),
      ),
    )
  }

  fun stubPutAlert(
    bookingId: Long = 12345678,
    alertSequence: Long = 3,
    alert: AlertResponse = alertResponse(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun stubDeleteAlert(
    bookingId: Long = 12345678,
    alertSequence: Long = 3,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/prisoners/booking-id/$bookingId/alerts/$alertSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubGetAlertsForReconciliation(offenderNo: String, response: PrisonerAlertsResponse = PrisonerAlertsResponse(latestBookingAlerts = emptyList())) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/alerts/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubCreateAlertCode() {
    nomisApi.stubFor(
      post(urlEqualTo("/alerts/codes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubUpdateAlertCode(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/codes/$code")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeactivateAlertCode(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/codes/$code/deactivate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubReactivateAlertCode(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/codes/$code/reactivate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubCreateAlertType() {
    nomisApi.stubFor(
      post(urlEqualTo("/alerts/types")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value()),
      ),
    )
  }

  fun stubUpdateAlertType(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/types/$code")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeactivateAlertType(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/types/$code/deactivate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubReactivateAlertType(code: String) {
    nomisApi.stubFor(
      put(urlEqualTo("/alerts/types/$code/reactivate")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
fun createAlertResponse(bookingId: Long = 12345, alertSequence: Long = 1) = CreateAlertResponse(
  bookingId = bookingId,
  alertSequence = alertSequence,
  alertCode = CodeDescription("HPI", ""),
  type = CodeDescription("X", ""),
)

fun alertResponse() = AlertResponse(
  bookingId = 12345678,
  alertSequence = 3,
  bookingSequence = 1,
  alertCode = CodeDescription("XA", "TACT"),
  type = CodeDescription("X", "Security"),
  date = LocalDate.now(),
  isActive = true,
  isVerified = false,
  audit = NomisAudit(
    createDatetime = LocalDateTime.now(),
    createUsername = "Q1251T",
  ),
)

fun alertCode(code: String) = CodeDescription(code = code, description = "Description for $code")
