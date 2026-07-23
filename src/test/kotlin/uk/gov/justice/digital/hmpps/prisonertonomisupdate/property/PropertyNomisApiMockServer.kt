package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
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
    property: CreatePropertyResponse = CreatePropertyResponse(
      propertyContainerId = propertyId,
    ),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/property-containers")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(property)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun propertyResponse(propertyId: Long) = CreatePropertyResponse(
  propertyContainerId = propertyId,
  bookingId = 12345678,
)
