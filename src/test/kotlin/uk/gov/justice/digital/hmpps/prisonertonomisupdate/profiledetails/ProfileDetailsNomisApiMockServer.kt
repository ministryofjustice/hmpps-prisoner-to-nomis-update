package uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class ProfileDetailsNomisApiMockServer(private val jsonMapper: JsonMapper) {
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
          .withBody(jsonMapper.writeValueAsString(UpsertProfileDetailsResponse(created, bookingId))),
      ),
    )
  }

  fun stubPutProfileDetails(errorStatus: HttpStatus) {
    nomisApi.stubFor(
      put(urlMatching("/prisoners/.*/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(errorStatus.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
      ),
    )
  }

  fun stubGetProfileDetails(
    offenderNo: String,
    response: PrisonerProfileDetailsResponse = profileDetailsResponse(offenderNo),
    fixedDelay: Int = 30,
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response))
          .withFixedDelay(fixedDelay),
      ),
    )
  }

  fun stubGetProfileDetails(offenderNo: String, errorStatus: HttpStatus) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(errorStatus.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = errorStatus, userMessage = "some error"))),
      ),
    )
  }

  fun stubGetProfileDetailsAfterRetry(
    offenderNo: String,
    response: PrisonerProfileDetailsResponse = profileDetailsResponse(offenderNo),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details"))
        .inScenario("Works on retry")
        .willReturn(
          aResponse()
            .withFault(Fault.CONNECTION_RESET_BY_PEER),
        ).willSetStateTo("Failed first call"),
    )

    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/profile-details"))
        .inScenario("Works on retry")
        .whenScenarioStateIs("Failed first call")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun profileDetailsResponse(offenderNo: String, bookings: List<BookingProfileDetailsResponse> = listOf(booking())) = PrisonerProfileDetailsResponse(
  offenderNo = offenderNo,
  bookings = bookings,
)

fun booking(profileDetails: List<ProfileDetailsResponse> = listOf(profileDetails())) = BookingProfileDetailsResponse(
  bookingId = 1,
  startDateTime = LocalDateTime.parse("2024-02-03T12:34:56"),
  latestBooking = true,
  sequence = 1,
  profileDetails = profileDetails,
)

fun profileDetails(type: String = "MARITAL", code: String? = "M") = ProfileDetailsResponse(
  type = type,
  code = code,
  createDateTime = LocalDateTime.now(),
  createdBy = "A_USER",
  modifiedDateTime = LocalDateTime.now(),
  modifiedBy = "ANOTHER_USER",
  auditModuleName = "NOMIS",
)
