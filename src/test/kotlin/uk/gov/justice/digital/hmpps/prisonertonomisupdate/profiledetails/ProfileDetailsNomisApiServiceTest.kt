package uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.havingExactly
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.tuple
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(ProfileDetailsNomisApiService::class, ProfileDetailsNomisApiMockServer::class)
class ProfileDetailsNomisApiServiceTest {
  @Autowired
  private lateinit var nomisApiService: ProfileDetailsNomisApiService

  @Autowired
  private lateinit var nomisApi: ProfileDetailsNomisApiMockServer

  @Nested
  inner class UpsertProfileDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubPutProfileDetails(offenderNo = "A1234KT")

      nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = "SMALL")

      nomisApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass details to service`() = runTest {
      nomisApi.stubPutProfileDetails(offenderNo = "A1234KT")

      nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = "SMALL")

      nomisApi.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/profile-details"))
          .withRequestBody(matchingJsonPath("$.profileType", equalTo("BUILD")))
          .withRequestBody(matchingJsonPath("$.profileCode", equalTo("SMALL"))),
      )
    }

    @Test
    internal fun `will not pass null profile code to service`() = runTest {
      nomisApi.stubPutProfileDetails(offenderNo = "A1234KT")

      nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = null)

      nomisApi.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/profile-details"))
          .withRequestBody(matchingJsonPath("$.profileType", equalTo("BUILD")))
          .withRequestBody(matchingJsonPath("$.profileCode", absent())),
      )
    }

    @Test
    fun `will return created and booking ID`() = runTest {
      nomisApi.stubPutProfileDetails(offenderNo = "A1234KT")

      val response =
        nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = "SMALL")

      assertThat(response.bookingId).isEqualTo(12345)
      assertThat(response.created).isTrue()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubPutProfileDetails(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = "SMALL")
      }
    }
  }

  @Nested
  inner class GetProfileDetails {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApi.stubGetProfileDetails(offenderNo = "A1234KT")

      nomisApiService.getProfileDetails(
        offenderNo = "A1234KT",
        profileTypes = ContactPersonProfileType.all(),
        latestBookingOnly = true,
      )

      nomisApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass parameters to the service`() = runTest {
      nomisApi.stubGetProfileDetails(offenderNo = "A1234KT")

      nomisApiService.getProfileDetails(
        offenderNo = "A1234KT",
        profileTypes = ContactPersonProfileType.all(),
        latestBookingOnly = true,
      )

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/profile-details"))
          .withQueryParam("profileTypes", havingExactly("MARITAL", "CHILD"))
          .withQueryParam("latestBookingOnly", equalTo("true")),
      )
    }

    @Test
    fun `will return profile details`() = runTest {
      nomisApi.stubGetProfileDetails(
        offenderNo = "A1234KT",
        response = profileDetailsResponse(
          "A1234KT",
          listOf(
            booking(
              listOf(
                profileDetails(type = "MARITAL", code = "M"),
                profileDetails(type = "CHILD", code = "3"),
              ),
            ),
          ),
        ),
      )

      val profileDetailsResponse =
        nomisApiService.getProfileDetails(
          offenderNo = "A1234KT",
          profileTypes = ContactPersonProfileType.all(),
          latestBookingOnly = true,
        )

      with(profileDetailsResponse!!) {
        assertThat(offenderNo).isEqualTo("A1234KT")
        assertThat(bookings)
          .extracting("bookingId", "startDateTime", "latestBooking")
          .containsExactly(tuple(1L, LocalDateTime.parse("2024-02-03T12:34:56"), true))
        assertThat(bookings[0].profileDetails)
          .extracting("type", "code", "createdBy", "modifiedBy", "auditModuleName")
          .containsExactly(
            tuple("MARITAL", "M", "A_USER", "ANOTHER_USER", "NOMIS"),
            tuple("CHILD", "3", "A_USER", "ANOTHER_USER", "NOMIS"),
          )
        assertThat(bookings[0].profileDetails[0].createDateTime.toLocalDate()).isEqualTo(LocalDate.now())
        assertThat(bookings[0].profileDetails[0].modifiedDateTime!!.toLocalDate()).isEqualTo(LocalDate.now())
      }
    }

    @Test
    fun `will return null when not found`() = runTest {
      nomisApi.stubGetProfileDetails(offenderNo = "A1234KT", errorStatus = NOT_FOUND)

      assertThat(
        nomisApiService.getProfileDetails(
          offenderNo = "A1234KT",
          profileTypes = ContactPersonProfileType.all(),
          latestBookingOnly = true,
        ),
      ).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApi.stubGetProfileDetails(offenderNo = "A1234KT", errorStatus = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getProfileDetails(
          offenderNo = "A1234KT",
          profileTypes = ContactPersonProfileType.all(),
          latestBookingOnly = true,
        )
      }
    }
  }
}
