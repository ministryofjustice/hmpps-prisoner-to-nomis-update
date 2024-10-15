package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent.Component.FACTOR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent.Component.PLAN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CSIPNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutCSIP(
    csipResponse: UpsertCSIPResponse = upsertCSIPResponse(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/csip")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(objectMapper.writeValueAsString(csipResponse)),
      ),
    )
  }

  fun stubDeleteCSIP(
    nomisCSIPReportId: Long = 12345678,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/csip/$nomisCSIPReportId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun upsertCSIPResponse(nomisCSIPReportId: Long = 12345) = UpsertCSIPResponse(
  nomisCSIPReportId = nomisCSIPReportId,
  offenderNo = "A1234BC",
  components = listOf(
    CSIPComponent(FACTOR, 111, "8cdadcf3-b003-4116-9956-c99bd8df6111"),
    CSIPComponent(PLAN, 222, "8cdadcf3-b003-4116-9956-c99bd8df6333"),
  ),
)
