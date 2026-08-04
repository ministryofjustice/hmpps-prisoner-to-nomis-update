package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePropertyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class PropertyNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubPostProperty(
    propertyId: Long,
    bookingId: Long,
    response: CreatePropertyResponse = CreatePropertyResponse(
      propertyContainerId = propertyId,
      bookingId = bookingId,
    ),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/property-containers")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubPutProperty(propertyId: Long, status: Int = 200) {
    nomisApi.stubFor(
      put(urlEqualTo("/property-containers/$propertyId")).willReturn(status(status)),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
