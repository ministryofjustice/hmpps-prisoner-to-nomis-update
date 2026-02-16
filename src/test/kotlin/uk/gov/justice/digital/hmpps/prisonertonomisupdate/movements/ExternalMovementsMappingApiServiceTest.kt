package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate
import java.util.*

@SpringAPIServiceTest
@Import(ExternalMovementsMappingApiService::class, ExternalMovementsMappingApiMockServer::class, RetryApiService::class)
class ExternalMovementsMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: ExternalMovementsMappingApiService

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

  @Nested
  inner class CreateApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

      apiService.createApplicationMapping(
        temporaryAbsenceApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsMovementApplicationId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsMovementApplicationId = UUID.randomUUID()
      mappingApi.stubCreateTemporaryAbsenceApplicationMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TemporaryAbsenceApplicationSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationId = 1L,
              dpsMovementApplicationId = dpsMovementApplicationId,
              mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TemporaryAbsenceApplicationSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementApplicationId = 2L,
              dpsMovementApplicationId = dpsMovementApplicationId,
              mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createApplicationMapping(
          temporaryAbsenceApplicationMapping(),
        )
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisMovementApplicationId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisMovementApplicationId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTemporaryAbsenceApplicationMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createApplicationMapping(
          temporaryAbsenceApplicationMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetApplicationMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId)

      apiService.getApplicationMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass dpsId`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId)

      apiService.getApplicationMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(urlPathEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")),
      )
    }

    @Test
    internal fun `should parse returned mapping`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId)

      with(apiService.getApplicationMapping(dpsId)!!) {
        assertThat(prisonerNumber).isEqualTo("A1234BC")
        assertThat(nomisMovementApplicationId).isEqualTo(1L)
        assertThat(dpsMovementApplicationId).isEqualTo(dpsId)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = NOT_FOUND)

      apiService.getApplicationMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getApplicationMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping()

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping()

      apiService.createScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("some address")))
          .withRequestBody(matchingJsonPath("eventTime", containing("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("54321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsUprn", equalTo("654")))
          .withRequestBody(matchingJsonPath("dpsDescription", absent()))
          .withRequestBody(matchingJsonPath("dpsPostcode", equalTo("SW1A 1AA"))),

      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsScheduledMovementId = UUID.randomUUID()
      mappingApi.stubCreateScheduledMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsOccurrenceId = dpsScheduledMovementId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
            duplicate = ScheduledMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsOccurrenceId = dpsScheduledMovementId,
              mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
        )
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisEventId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisEventId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
        )
      }
    }
  }

  @Nested
  inner class UpdateScheduledMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping()

      apiService.updateScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping()

      apiService.updateScheduledMovementMapping(
        temporaryAbsenceScheduledMovementMapping(),
      )

      mappingApi.verify(
        putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("some address")))
          .withRequestBody(matchingJsonPath("eventTime", containing("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("54321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsUprn", equalTo("654")))
          .withRequestBody(matchingJsonPath("dpsDescription", absent()))
          .withRequestBody(matchingJsonPath("dpsPostcode", equalTo("SW1A 1AA"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubUpdateScheduledMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateScheduledMovementMapping(
          temporaryAbsenceScheduledMovementMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetScheduledMovementMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsId)

      apiService.getScheduledMovementMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsId, status = NOT_FOUND)

      apiService.getScheduledMovementMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsId, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getScheduledMovementMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateExternalMovementMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateExternalMovementMapping()

      apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateExternalMovementMapping()

      apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisMovementSeq", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsMovementId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
        // TODO check address mapping details
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsExternalMovementId = UUID.randomUUID()
      mappingApi.stubCreateExternalMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 1,
              dpsMovementId = dpsExternalMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
            ),
            duplicate = ExternalMovementSyncMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 2,
              dpsMovementId = dpsExternalMovementId,
              mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisMovementSeq"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisMovementSeq"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateExternalMovementMapping(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createExternalMovementMapping(temporaryAbsenceExternalMovementMapping())
      }
    }
  }

  @Nested
  inner class GetExternalMovementMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsId)

      apiService.getExternalMovementMapping(dpsId)

      mappingApi.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsId, status = NOT_FOUND)

      apiService.getExternalMovementMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsId, status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getExternalMovementMapping(dpsId)
      }
    }
  }
}
