package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson.PrisonPersonConfiguration

@SpringAPIServiceTest
@Import(ProfileDetailsNomisApiService::class, PrisonPersonConfiguration::class, ProfileDetailsNomisApiMockServer::class)
class ProfileDetailsNomisApiServiceTest {
  @Autowired
  @Qualifier("prisonPersonProfileDetailsNomisApiService")
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

      val response = nomisApiService.upsertProfileDetails(offenderNo = "A1234KT", profileType = "BUILD", profileCode = "SMALL")

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
}
