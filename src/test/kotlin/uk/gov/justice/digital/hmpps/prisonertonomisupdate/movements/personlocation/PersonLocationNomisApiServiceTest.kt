package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(PersonLocationNomisApiService::class, PersonLocationNomisApiMockServer::class)
class PersonLocationNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationNomisApiService

  @Autowired
  private lateinit var personLocationNomisApiMockServer: PersonLocationNomisApiMockServer

  @Nested
  inner class GetOffenderPersonLocationsTest {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      personLocationNomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")

      personLocationNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      personLocationNomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")

      personLocationNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/movements/A1234BC/person-locations")),
      )
    }

    @Test
    fun `will return offender transfer movements`() = runTest {
      personLocationNomisApiMockServer.stubGetOffenderPersonLocations(offenderNo = "A1234BC")

      val result = apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")!!

      assertThat(result.bookings).hasSize(1)
      assertThat(result.bookings[0].booking.bookingId).isEqualTo(12345)
      assertThat(result.bookings[0].movements).hasSize(2)
      assertThat(result.bookings[0].movements[0].movementReason.code).isEqualTo("INT")
      assertThat(result.bookings[0].movements[1].to!!.code).isEqualTo("LEEDCC")
    }

    @Test
    fun `will return null when offender does not exist`() = runTest {
      personLocationNomisApiMockServer.stubGetOffenderPersonLocations(status = NOT_FOUND)

      assertThat(apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      personLocationNomisApiMockServer.stubGetOffenderPersonLocations(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOffenderPersonLocationsOrNull(offenderNo = "A1234BC")
      }
    }
  }
}
