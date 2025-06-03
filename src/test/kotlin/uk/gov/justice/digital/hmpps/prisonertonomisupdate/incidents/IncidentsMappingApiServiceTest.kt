package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto

@SpringAPIServiceTest
@Import(IncidentsMappingApiService::class, IncidentsConfiguration::class, IncidentsMappingApiMockServer::class)
class IncidentsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: IncidentsMappingApiService

  @Autowired
  private lateinit var mockServer: IncidentsMappingApiMockServer

  @Nested
  inner class Incident {

    @Nested
    inner class GetByDpsIncidentIdOrNull {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsIncidentIdOrNull(dpsIncidentId = "1234-567")

        apiService.getByDpsIncidentIdOrNull(dpsIncidentId = "1234-567")

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsIncidentIdOrNull()

        apiService.getByDpsIncidentIdOrNull(dpsIncidentId = "1234-567")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/incidents/dps-incident-id/1234-567")),
        )
      }

      @Test
      fun `will return nomisIncidentId when mapping exists`() = runTest {
        mockServer.stubGetByDpsIncidentIdOrNull(
          dpsIncidentId = "1234-567",
          mapping = IncidentMappingDto(
            dpsIncidentId = "1234-567",
            nomisIncidentId = 1234567,
            mappingType = IncidentMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsIncidentIdOrNull(dpsIncidentId = "1234-567")

        assertThat(mapping?.nomisIncidentId).isEqualTo(1234567)
      }

      @Test
      fun `will return null if mapping does not exist`() = runTest {
        mockServer.stubGetByDpsIncidentIdOrNull(
          dpsIncidentId = "1234-567",
          mapping = null,
        )

        assertThat(apiService.getByDpsIncidentIdOrNull(dpsIncidentId = "1234-567")).isNull()
      }
    }

    @Nested
    inner class GetByDpsIncidentId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubGetByDpsIncidentId(dpsIncidentId = "1234-567")

        apiService.getByDpsIncidentId(dpsIncidentId = "1234-567")

        mockServer.verify(
          getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubGetByDpsIncidentId(dpsIncidentId = "1234-567")

        apiService.getByDpsIncidentId(dpsIncidentId = "1234-567")

        mockServer.verify(
          getRequestedFor(urlPathEqualTo("/mapping/incidents/dps-incident-id/1234-567")),
        )
      }

      @Test
      fun `will return dpsIncidentId`() = runTest {
        mockServer.stubGetByDpsIncidentId(
          dpsIncidentId = "1234-567",
          mapping = IncidentMappingDto(
            dpsIncidentId = "1234-567",
            nomisIncidentId = 1234567,
            mappingType = IncidentMappingDto.MappingType.MIGRATED,
          ),
        )

        val mapping = apiService.getByDpsIncidentId(dpsIncidentId = "1234-567")

        assertThat(mapping.nomisIncidentId).isEqualTo(1234567)
      }
    }

    @Nested
    inner class CreateIncidentMapping {
      @Test
      internal fun `will pass oath2 token to create mapping endpoint`() = runTest {
        mockServer.stubCreateIncidentMapping()

        apiService.createIncidentMapping(
          IncidentMappingDto(
            mappingType = IncidentMappingDto.MappingType.DPS_CREATED,
            nomisIncidentId = 1234567,
            dpsIncidentId = "1234-567",
          ),
        )

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/mapping/incidents"))
            .withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will throw error when 409 conflict`() = runTest {
        val nomisIncidentId = 2234567890
        val dpsIncidentId = "2234567890"
        val existingNomisIncidentId = 3234567890

        mockServer.stubCreateIncidentMapping(
          error = DuplicateMappingErrorResponse(
            moreInfo = DuplicateErrorContentObject(
              duplicate = IncidentMappingDto(
                dpsIncidentId = dpsIncidentId,
                nomisIncidentId = nomisIncidentId,
                mappingType = IncidentMappingDto.MappingType.DPS_CREATED,
              ),
              existing = IncidentMappingDto(
                dpsIncidentId = dpsIncidentId,
                nomisIncidentId = existingNomisIncidentId,
                mappingType = IncidentMappingDto.MappingType.DPS_CREATED,
              ),
            ),
            errorCode = 1409,
            status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
            userMessage = "Duplicate mapping",
          ),
        )

        val error = assertThrows<DuplicateMappingException> {
          apiService.createIncidentMapping(
            IncidentMappingDto(
              mappingType = IncidentMappingDto.MappingType.NOMIS_CREATED,
              nomisIncidentId = nomisIncidentId,
              dpsIncidentId = dpsIncidentId,
            ),
          )
        }.error

        assertThat(error.moreInfo.existing!!["dpsIncidentId"]).isEqualTo(dpsIncidentId)
        assertThat(error.moreInfo.existing["nomisIncidentId"]).isEqualTo(existingNomisIncidentId)
        assertThat(error.moreInfo.duplicate["dpsIncidentId"]).isEqualTo(dpsIncidentId)
        assertThat(error.moreInfo.duplicate["nomisIncidentId"]).isEqualTo(nomisIncidentId)
      }
    }

    @Nested
    inner class DeleteByDpsIncidentId {
      @Test
      internal fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteByDpsIncidentId(dpsIncidentId = "1234-567")

        apiService.deleteByDpsIncidentId(dpsIncidentId = "1234-567")

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      internal fun `will pass DPS id to service`() = runTest {
        mockServer.stubDeleteByDpsIncidentId(dpsIncidentId = "1234-567")

        apiService.deleteByDpsIncidentId(dpsIncidentId = "1234-567")

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/mapping/incidents/dps-incident-id/1234-567")),
        )
      }
    }
  }
}
