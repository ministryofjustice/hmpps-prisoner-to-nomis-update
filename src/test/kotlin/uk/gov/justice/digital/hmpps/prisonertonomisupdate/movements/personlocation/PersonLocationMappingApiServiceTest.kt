package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationBookingMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonLocationMovementMappingDto
import java.util.*

@SpringAPIServiceTest
@Import(PersonLocationMappingApiService::class, PersonLocationMappingApiMockServer::class, PersonLocationConfiguration::class)
class PersonLocationMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationMappingApiService

  @Autowired
  private lateinit var mappingApi: PersonLocationMappingApiMockServer

  @Nested
  inner class GetMappings {
    @Test
    internal fun `should pass oauth2 token to service`() = runTest {
      mappingApi.stubGetPersonLocationMappings()

      apiService.getMappings(listOf(12345L))

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass booking ids to service`() = runTest {
      mappingApi.stubGetPersonLocationMappings()

      apiService.getMappings(listOf(12345L, 12346L))

      mappingApi.verify(
        postRequestedFor(urlPathEqualTo("/mapping/person-location/nomis-booking-ids/get-list"))
          .withRequestBody(equalToJson("[12345, 12346]")),
      )
    }

    @Test
    internal fun `should parse response`() = runTest {
      val dpsCustodialSeriesId = UUID.randomUUID()
      val dpsExternalMovementId = UUID.randomUUID()
      val mappings = listOf(
        PersonLocationBookingMappingDto(
          bookingId = 12345L,
          dpsCustodialSeriesId = dpsCustodialSeriesId,
          movements = listOf(
            PersonLocationMovementMappingDto(
              nomisMovementSeq = 1,
              dpsExternalMovementId = dpsExternalMovementId,
            ),
          ),
        ),
      )
      mappingApi.stubGetPersonLocationMappings(response = mappings)

      assertThat(apiService.getMappings(listOf(12345L))).isEqualTo(mappings)
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetPersonLocationMappings(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getMappings(listOf(12345L))
      }
    }
  }
}
