package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingDto
import java.util.UUID

@SpringAPIServiceTest
@Import(TransferSchedulerMappingApiService::class, TransferSchedulerMappingApiMockServer::class, TransferSchedulerConfiguration::class)
class TransferSchedulerMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: TransferSchedulerMappingApiService

  @Autowired
  private lateinit var mappingApi: TransferSchedulerMappingApiMockServer

  @Nested
  inner class GetPrisonerMappingIds {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds()

      apiService.getMappings("A1234BC")

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mappings`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds()

      with(apiService.getMappings("A1234BC")) {
        assertThat(schedules[0].nomisEventId).isEqualTo(1)
        assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTransferSchedulerPrisonerMappingIds(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getMappings("A1234BC")
      }
    }
  }

  @Nested
  inner class GetTransferScheduleMapping {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(dpsId = dpsId)

      apiService.getTransferScheduleMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)

      apiService.getTransferScheduleMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTransferScheduleMapping(dpsId)
      }
    }
  }

  @Nested
  inner class DeleteTransferScheduleMapping {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubDeleteTransferScheduleMapping(dpsId = dpsId)

      apiService.deleteTransferScheduleMapping(dpsId)

      mappingApi.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call delete endpoint`() = runTest {
      mappingApi.stubDeleteTransferScheduleMapping(dpsId = dpsId)

      apiService.deleteTransferScheduleMapping(dpsId)

      mappingApi.verify(
        deleteRequestedFor(urlPathEqualTo("/mapping/transfer-scheduler/schedule/dps-id/$dpsId")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubDeleteTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.deleteTransferScheduleMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateTransferScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping()

      apiService.createTransferScheduleMapping(transferScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping()

      apiService.createTransferScheduleMapping(transferScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsTransferScheduleId = UUID.randomUUID()
      mappingApi.stubCreateTransferScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TransferScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsTransferScheduleId = dpsTransferScheduleId,
              mappingType = TransferScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TransferScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsTransferScheduleId = dpsTransferScheduleId,
              mappingType = TransferScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createTransferScheduleMapping(transferScheduleMapping())
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisEventId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisEventId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTransferScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTransferScheduleMapping(transferScheduleMapping())
      }
    }
  }
}
