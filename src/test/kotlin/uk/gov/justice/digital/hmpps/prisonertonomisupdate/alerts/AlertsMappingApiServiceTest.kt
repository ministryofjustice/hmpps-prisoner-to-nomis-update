package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.AlertMappingDto
import java.util.UUID

@SpringAPIServiceTest
@Import(AlertsMappingApiService::class, AlertsConfiguration::class, AlertsMappingApiMockServer::class)
class AlertsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsMappingApiService

  @Autowired
  private lateinit var alertsMappingApiMockServer: AlertsMappingApiMockServer

  @Nested
  inner class GetByDpsId {
    private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubGetByDpsId(dpsAlertId)

      apiService.getOrNullByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass NOMIS ids to service`() = runTest {
      alertsMappingApiMockServer.stubGetByDpsId(dpsAlertId)

      apiService.getOrNullByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")),
      )
    }

    @Test
    fun `will return dpsAlertId when mapping exists`() = runTest {
      alertsMappingApiMockServer.stubGetByDpsId(
        dpsAlertId = dpsAlertId,
        mapping = AlertMappingDto(
          dpsAlertId = dpsAlertId,
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          mappingType = AlertMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getOrNullByDpsId(dpsAlertId)

      assertThat(mapping?.nomisBookingId).isEqualTo(123456)
      assertThat(mapping?.nomisAlertSequence).isEqualTo(1)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      alertsMappingApiMockServer.stubGetByDpsId(NOT_FOUND)

      assertThat(apiService.getOrNullByDpsId(dpsAlertId)).isNull()
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      alertsMappingApiMockServer.stubGetByDpsId(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getOrNullByDpsId(dpsAlertId)
      }
    }
  }

  @Nested
  inner class PostMapping {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        AlertMappingDto(
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          dpsAlertId = UUID.randomUUID().toString(),
          mappingType = AlertMappingDto.MappingType.DPS_CREATED,
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubPostMapping()

      apiService.createMapping(
        AlertMappingDto(
          nomisBookingId = 123456,
          nomisAlertSequence = 1,
          dpsAlertId = dpsAlertId,
          mappingType = AlertMappingDto.MappingType.DPS_CREATED,
        ),
      )

      alertsMappingApiMockServer.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("nomisBookingId", equalTo("123456")))
          .withRequestBody(matchingJsonPath("nomisAlertSequence", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsAlertId", equalTo(dpsAlertId)))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("DPS_CREATED"))),
      )
    }
  }

  @Nested
  inner class DeleteMapping {
    private val dpsAlertId = UUID.randomUUID().toString()

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsMappingApiMockServer.stubDeleteByDpsId(dpsAlertId)

      apiService.deleteByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass ids to service`() = runTest {
      val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"
      alertsMappingApiMockServer.stubDeleteByDpsId(dpsAlertId)

      apiService.deleteByDpsId(dpsAlertId)

      alertsMappingApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/alerts/dps-alert-id/$dpsAlertId")),
      )
    }
  }
}
