package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.verification.LoggedRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Component
class ProfileDetailsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutProfileDetails(
    offenderNo: String = "A1234AA",
    created: Boolean = true,
    bookingId: Long = 12345,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(UpsertProfileDetailsResponse(created, bookingId))),
      ),
    )
  }

  fun stubPutProfileDetails(errorStatus: HttpStatus) {
    nomisApi.stubFor(
      put(urlMatching("/prisoners/.*/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(errorStatus.value())
          .withBody(objectMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
      ),
    )
  }

  fun findAllProfileDetailsRequests(): List<LoggedRequest> = nomisApi.findAll(putRequestedFor(urlPathEqualTo("/prisoners/A1234AA/profile-details")))

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
