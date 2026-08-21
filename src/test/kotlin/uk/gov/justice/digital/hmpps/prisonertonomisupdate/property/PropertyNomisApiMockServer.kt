package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePropertyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerGetResponse
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

  fun stubGetProperty(propertyId: Long, response: PropertyContainerGetResponse) {
    nomisApi.stubFor(
      get(urlEqualTo("/property-containers/$propertyId")).willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPropertyError(propertyId: Long, status: Int = 500) {
    nomisApi.stubFor(
      get(urlEqualTo("/property-containers/$propertyId")).willReturn(status(status)),
    )
  }

  fun stubGetPropertyIdsInRange(
    fromId: Long = 0L,
    toId: Long = 20L,
    status: Int = 200,
  ) {
    val content: List<Long> = (fromId + 1..(if (toId > 0) toId else fromId)).toList()
    nomisApi.stubFor(
      get(urlPathEqualTo("/property-containers/ids-in-range"))
        .withQueryParam("fromId", equalTo(fromId.toString()))
        .withQueryParam("toId", equalTo((if (toId > 0) toId else Long.MAX_VALUE).toString()))
        .willReturn(if (status == 200) okJson(jsonMapper.writeValueAsString(content)) else status(status)),
    )
  }

  fun stubGetPropertyIdRanges(pageSize: Long = 10, totalElements: Long = 20) {
    val content: List<Long> = (1..totalElements / pageSize).map { it * pageSize }
    nomisApi.stubFor(
      get(urlPathEqualTo("/property-containers/id-ranges"))
        .willReturn(okJson(jsonMapper.writeValueAsString(content))),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
