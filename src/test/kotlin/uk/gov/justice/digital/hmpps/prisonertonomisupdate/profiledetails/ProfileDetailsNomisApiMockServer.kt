package uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class ProfileDetailsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutProfileDetails(
    offenderNo: String = "A1234BC",
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

  fun stubGetProfileDetails(
    offenderNo: String,
    response: PrisonerProfileDetailsResponse = PrisonerProfileDetailsResponse(
      offenderNo = offenderNo,
      bookings = listOf(
        BookingProfileDetailsResponse(
          bookingId = 1,
          startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
          latestBooking = true,
          profileDetails = listOf(
            ProfileDetailsResponse(
              type = "MARITAL",
              code = "M",
              createDateTime = LocalDateTime.now(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
            ProfileDetailsResponse(
              type = "CHILD",
              code = "3",
              createDateTime = LocalDateTime.now(),
              createdBy = "A_USER",
              modifiedDateTime = LocalDateTime.now(),
              modifiedBy = "ANOTHER_USER",
              auditModuleName = "NOMIS",
            ),
          ),
        ),
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetProfileDetails(offenderNo: String, errorStatus: HttpStatus) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
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
