package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapMovementMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate
import java.util.*

@SpringAPIServiceTest
@Import(TapMappingApiService::class, TapMappingApiMockServer::class, RetryApiService::class)
class TapMappingApiServiceTest {
  @Autowired
  private lateinit var apiService: TapMappingApiService

  @Autowired
  private lateinit var mappingApi: TapMappingApiMockServer

  @Nested
  inner class CreateApplicationMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTapApplicationMapping()

      apiService.createTapApplicationMapping(
        tapApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapApplicationMapping()

      apiService.createTapApplicationMapping(
        tapApplicationMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisApplicationId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsAuthorisationId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED"))),
      )
    }

    @Test
    fun `should return error for 409 conflict`() = runTest {
      val dpsMovementApplicationId = UUID.randomUUID()
      mappingApi.stubCreateTapApplicationMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapApplicationMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisApplicationId = 1L,
              dpsAuthorisationId = dpsMovementApplicationId,
              mappingType = TapApplicationMappingDto.MappingType.NOMIS_CREATED,
            ),
            duplicate = TapApplicationMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisApplicationId = 2L,
              dpsAuthorisationId = dpsMovementApplicationId,
              mappingType = TapApplicationMappingDto.MappingType.NOMIS_CREATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      assertThrows<DuplicateMappingException> {
        apiService.createTapApplicationMapping(
          tapApplicationMapping(),
        )
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisApplicationId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisApplicationId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapApplicationMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapApplicationMapping(
          tapApplicationMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetApplicationMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapApplicationMapping(dpsId = dpsId)

      apiService.getTapApplicationMapping(dpsId)

      mappingApi.verify(
        WireMock.getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass dpsId`() = runTest {
      mappingApi.stubGetTapApplicationMapping(dpsId = dpsId)

      apiService.getTapApplicationMapping(dpsId)

      mappingApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/mapping/taps/application/dps-id/$dpsId")),
      )
    }

    @Test
    internal fun `should parse returned mapping`() = runTest {
      mappingApi.stubGetTapApplicationMapping(dpsId = dpsId)

      with(apiService.getTapApplicationMapping(dpsId)!!) {
        assertThat(prisonerNumber).isEqualTo("A1234BC")
        assertThat(nomisApplicationId).isEqualTo(1L)
        assertThat(dpsAuthorisationId).isEqualTo(dpsId)
      }
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapApplicationMapping(dpsId = dpsId, status = HttpStatus.NOT_FOUND)

      apiService.getTapApplicationMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapApplicationMapping(dpsId = dpsId, status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapApplicationMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateTapScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTapScheduleMapping()

      apiService.createTapScheduleMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapScheduleMapping()

      apiService.createTapScheduleMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        postRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("some address")))
          .withRequestBody(matchingJsonPath("eventTime", WireMock.containing("${LocalDate.now()}")))
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
      mappingApi.stubCreateTapScheduleMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 1L,
              dpsOccurrenceId = dpsScheduledMovementId,
              mappingType = TapScheduleMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
              eventTime = "",
            ),
            duplicate = TapScheduleMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisEventId = 2L,
              dpsOccurrenceId = dpsScheduledMovementId,
              mappingType = TapScheduleMappingDto.MappingType.NOMIS_CREATED,
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
        apiService.createTapScheduleMapping(
          tapScheduleMapping(),
        )
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisEventId"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisEventId"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapScheduleMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapScheduleMapping(
          tapScheduleMapping(),
        )
      }
    }
  }

  @Nested
  inner class UpdateTapScheduleMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubUpdateTapScheduleMapping()

      apiService.updateTapScheduledMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        WireMock.putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubUpdateTapScheduleMapping()

      apiService.updateTapScheduledMapping(
        tapScheduleMapping(),
      )

      mappingApi.verify(
        WireMock.putRequestedFor(anyUrl())
          .withRequestBody(matchingJsonPath("prisonerNumber", equalTo("A1234BC")))
          .withRequestBody(matchingJsonPath("bookingId", equalTo("12345")))
          .withRequestBody(matchingJsonPath("nomisEventId", equalTo("1")))
          .withRequestBody(matchingJsonPath("dpsOccurrenceId", not(absent())))
          .withRequestBody(matchingJsonPath("mappingType", equalTo("MIGRATED")))
          .withRequestBody(matchingJsonPath("dpsAddressText", equalTo("some address")))
          .withRequestBody(matchingJsonPath("eventTime", WireMock.containing("${LocalDate.now()}")))
          .withRequestBody(matchingJsonPath("nomisAddressId", equalTo("54321")))
          .withRequestBody(matchingJsonPath("nomisAddressOwnerClass", equalTo("OFF")))
          .withRequestBody(matchingJsonPath("dpsUprn", equalTo("654")))
          .withRequestBody(matchingJsonPath("dpsDescription", absent()))
          .withRequestBody(matchingJsonPath("dpsPostcode", equalTo("SW1A 1AA"))),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubUpdateTapScheduleMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.updateTapScheduledMapping(
          tapScheduleMapping(),
        )
      }
    }
  }

  @Nested
  inner class GetATapScheduleMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapScheduleMapping(dpsId = dpsId)

      apiService.getTapScheduleMapping(dpsId)

      mappingApi.verify(
        WireMock.getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapScheduleMapping(dpsId = dpsId, status = HttpStatus.NOT_FOUND)

      apiService.getTapScheduleMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapScheduleMapping(dpsId = dpsId, status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapScheduleMapping(dpsId)
      }
    }
  }

  @Nested
  inner class CreateTapMappings {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubCreateTapMovementMapping()

      apiService.createTapMovementMapping(tapMovementMapping())

      mappingApi.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `should pass data to service`() = runTest {
      mappingApi.stubCreateTapMovementMapping()

      apiService.createTapMovementMapping(tapMovementMapping())

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
      val dpsMovementId = UUID.randomUUID()
      mappingApi.stubCreateTapMovementMappingConflict(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            existing = TapMovementMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 1,
              dpsMovementId = dpsMovementId,
              mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
              // TODO add address mapping details
              nomisAddressId = 0,
              nomisAddressOwnerClass = "",
              dpsAddressText = "",
            ),
            duplicate = TapMovementMappingDto(
              prisonerNumber = "A1234BC",
              bookingId = 12345L,
              nomisMovementSeq = 2,
              dpsMovementId = dpsMovementId,
              mappingType = TapMovementMappingDto.MappingType.NOMIS_CREATED,
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
        apiService.createTapMovementMapping(tapMovementMapping())
      }.error.apply {
        assertThat(moreInfo.existing!!["nomisMovementSeq"]).isEqualTo(1)
        assertThat(moreInfo.duplicate["nomisMovementSeq"]).isEqualTo(2)
      }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubCreateTapMovementMapping(status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.createTapMovementMapping(tapMovementMapping())
      }
    }
  }

  @Nested
  inner class GetTapMovementMappings {
    private val dpsId = UUID.randomUUID()

    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapMovementMapping(dpsId = dpsId)

      apiService.getTapMovementMapping(dpsId)

      mappingApi.verify(
        WireMock.getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should return null if not found`() = runTest {
      mappingApi.stubGetTapMovementMapping(dpsId = dpsId, status = HttpStatus.NOT_FOUND)

      apiService.getTapMovementMapping(dpsId)
        .also { assertThat(it).isNull() }
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapMovementMapping(dpsId = dpsId, status = HttpStatus.INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMovementMapping(dpsId)
      }
    }
  }

  @Nested
  inner class GetMappingIds {
    @Test
    internal fun `should pass oath2 token to service`() = runTest {
      mappingApi.stubGetTapMappingIds(prisonerNumber = "A1234BC")

      apiService.getTapMappingIds("A1234BC")

      mappingApi.verify(
        WireMock.getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should send prisoner in url`() = runTest {
      mappingApi.stubGetTapMappingIds(prisonerNumber = "A1234BC")

      apiService.getTapMappingIds("A1234BC")

      mappingApi.verify(
        WireMock.getRequestedFor(WireMock.urlPathEqualTo("/mapping/taps/A1234BC/ids")),
      )
    }

    @Test
    fun `should throw if API calls fail`() = runTest {
      mappingApi.stubGetTapMappingIds(
        prisonerNumber = "A1234BC",
        status = HttpStatus.INTERNAL_SERVER_ERROR,
      )

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getTapMappingIds("A1234BC")
      }
    }
  }
}
