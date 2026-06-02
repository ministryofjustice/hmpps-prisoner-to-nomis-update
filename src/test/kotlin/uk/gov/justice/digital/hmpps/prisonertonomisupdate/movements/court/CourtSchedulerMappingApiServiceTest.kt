package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import java.util.*

@SpringAPIServiceTest
@Import(CourtSchedulerMappingApiService::class, CourtSchedulerMappingApiMockServer::class, CourtSchedulerConfiguration::class)
class CourtSchedulerMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: CourtSchedulerMappingApiService

  @Autowired
  private lateinit var mappingApi: CourtSchedulerMappingApiMockServer

  @Nested
  inner class GetPrisonerMappingIds {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds()

      apiService.getCourtSchedulerPrisonMappingIds("A1234BC")

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return mappings`() = runTest {
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds()

      with(apiService.getCourtSchedulerPrisonMappingIds("A1234BC")) {
        assertThat(schedules[0].nomisEventId).isEqualTo(1)
        assertThat(movements[0].nomisMovementSeq).isEqualTo(3)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetCourtSchedulerPrisonerMappingIds("A1234BC", status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtSchedulerPrisonMappingIds("A1234BC")
      }
    }
  }

  @Nested
  inner class GetCourtScheduleMapping {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetCourtScheduleMapping(dpsId = dpsId)

      apiService.getCourtScheduleMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)

      apiService.getCourtScheduleMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetCourtScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getCourtScheduleMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateCourtScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping()

      apiService.createCourtScheduleMapping(courtScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping()

      apiService.createCourtScheduleMapping(courtScheduleMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsScheduledMovementId = UUID.randomUUID()
      mappingApi.stubCreateCourtScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = CourtScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsCourtAppearanceId = dpsScheduledMovementId,
              mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = CourtScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsCourtAppearanceId = dpsScheduledMovementId,
              mappingType = CourtScheduleMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createCourtScheduleMapping(courtScheduleMapping())
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisEventId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisEventId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateCourtScheduleMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createCourtScheduleMapping(courtScheduleMapping())
      }
    }
  }
}
