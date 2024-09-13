package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.physicalattributes

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertPhysicalAttributesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Component
class PhysicalAttributesNomisApiMockServer(private val objectMapper: ObjectMapper) {
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

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
