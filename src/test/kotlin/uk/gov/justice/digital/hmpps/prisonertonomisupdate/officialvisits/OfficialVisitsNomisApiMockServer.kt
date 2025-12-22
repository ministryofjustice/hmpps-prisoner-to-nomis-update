package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PageMetadata
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelVisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class OfficialVisitsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    fun pageVisitIdResponse(content: List<VisitIdResponse>, totalElements: Long = content.size.toLong(), pageSize: Int = 20, pageNumber: Int = 1): PagedModelVisitIdResponse = PagedModelVisitIdResponse(
      content = content,
      page = PageMetadata(
        propertySize = pageSize.toLong(),
        number = pageNumber.toLong(),
        totalElements = totalElements,
        totalPages = Math.ceilDiv(totalElements, pageSize),
      ),
    )
  }
  fun stubGetOfficialVisitIds(
    pageNumber: Int = 0,
    pageSize: Int = 1,
    totalElements: Long = content.size.toLong(),
    content: List<VisitIdResponse>,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/official-visits/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(objectMapper.writeValueAsString(pageVisitIdResponse(content, pageSize = pageSize, pageNumber = pageNumber, totalElements = totalElements))),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
