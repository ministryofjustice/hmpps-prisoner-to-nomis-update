package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class AlertsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPostAlert(
    offenderNo: String = "A1234AK",
    alert: CreateAlertResponse = CreateAlertResponse(
      bookingId = 12345,
      alertSequence = 1,
      alertCode = CodeDescription("HPI", ""),
      type = CodeDescription("X", ""),
    ),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/prisoners/$offenderNo/alerts")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(alert)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
