package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonPersonReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Component
class PrisonPersonNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutPhysicalAttributes(
    offenderNo: String = "A1234AA",
    created: Boolean = true,
    bookingId: Long = 12345,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/prisoners/$offenderNo/physical-attributes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(UpsertPhysicalAttributesResponse(created, bookingId))),
      ),
    )
  }

  fun stubPutPhysicalAttributes(errorStatus: HttpStatus) {
    nomisApi.stubFor(
      put(urlMatching("/prisoners/.*/physical-attributes")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(errorStatus.value())
          .withBody(objectMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
      ),
    )
  }

  fun stubGetReconciliation(
    offenderNo: String = "A1234AA",
    height: Int? = 180,
    weight: Int? = 80,
  ) = nomisApi.stubFor(
    get(urlEqualTo("/prisoners/$offenderNo/prison-person/reconciliation")).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(HttpStatus.OK.value())
        .withBody(objectMapper.writeValueAsString(PrisonPersonReconciliationResponse(offenderNo, height, weight))),
    ),
  )

  fun stubGetReconciliation(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/prisoners/\\w+/prison-person/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }
  fun stubGetReconciliationWithRetry(
    offenderNo: String = "A1234AA",
    height: Int? = 180,
    weight: Int? = 80,
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/prison-person/reconciliation"))
        .inScenario("Retry Prison Person")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ).willSetStateTo("Prison Person first call failed"),
    )

    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/prison-person/reconciliation"))
        .inScenario("Retry Prison Person")
        .whenScenarioStateIs("Prison Person first call failed")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(PrisonPersonReconciliationResponse(offenderNo, height, weight))),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
