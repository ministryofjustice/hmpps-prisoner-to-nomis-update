package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto

@SpringAPIServiceTest
@Import(VisitSlotsMappingService::class, VisitSlotsMappingApiMockServer::class)
class VisitSlotsMappingApiServiceTest {

  @Autowired
  private lateinit var apiService: VisitSlotsMappingService

  @Autowired
  private lateinit var mockServer: VisitSlotsMappingApiMockServer

  @Nested
  inner class GetTimeSlotByDpsIdOrNull {
    val dpsTimeSlotId = "12345"

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
        mapping = VisitTimeSlotMappingDto(
          dpsId = "123456",
          nomisPrisonId = "MDI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 1,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
      )

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass DPS id to service`() = runTest {
      mockServer.stubGetTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
        mapping = VisitTimeSlotMappingDto(
          dpsId = dpsTimeSlotId,
          nomisPrisonId = "MDI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 1,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      apiService.getTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
      )

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots/dps-id/$dpsTimeSlotId")),
      )
    }

    @Test
    fun `will return dpsId when mapping exists`() = runTest {
      mockServer.stubGetTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
        mapping = VisitTimeSlotMappingDto(
          dpsId = dpsTimeSlotId,
          nomisPrisonId = "MDI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 1,
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
        ),
      )

      val mapping = apiService.getTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
      )

      assertThat(mapping?.dpsId).isEqualTo(dpsTimeSlotId)
    }

    @Test
    fun `will return null if mapping does not exist`() = runTest {
      mockServer.stubGetTimeSlotByDpsIdOrNull(
        dpsId = dpsTimeSlotId,
        mapping = null,
      )

      assertThat(
        apiService.getTimeSlotByDpsIdOrNull(
          dpsId = dpsTimeSlotId,
        ),
      ).isNull()
    }
  }

  @Nested
  inner class CreateTimeSlotMapping {
    @Test
    fun `will pass oath2 token to migrate endpoint`() = runTest {
      mockServer.stubCreateTimeSlotMapping()

      apiService.createTimeSlotMapping(
        VisitTimeSlotMappingDto(
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
        ),
      )

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/mapping/visit-slots/time-slots")).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return success when OK response`() = runTest {
      mockServer.stubCreateTimeSlotMapping()

      apiService.createTimeSlotMapping(
        VisitTimeSlotMappingDto(
          mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
          label = "2020-01-01T10:00",
          dpsId = "1233",
          nomisPrisonId = "WWI",
          nomisDayOfWeek = "MON",
          nomisSlotSequence = 2,
        ),
      )
    }

    @Test
    fun `will return error when 409 conflict`() = runTest {
      val dpsId = "1234"
      val existingDpsId = "5678"

      mockServer.stubCreateTimeSlotMapping(
        error = DuplicateMappingErrorResponse(
          moreInfo = DuplicateErrorContentObject(
            duplicate = VisitTimeSlotMappingDto(
              dpsId = dpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            ),
            existing = VisitTimeSlotMappingDto(
              dpsId = existingDpsId,
              nomisPrisonId = "WWI",
              nomisDayOfWeek = "MON",
              nomisSlotSequence = 2,
              mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            ),
          ),
          errorCode = 1409,
          status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
          userMessage = "Duplicate mapping",
        ),
      )

      val error = assertThrows<DuplicateMappingException> {
        apiService.createTimeSlotMapping(
          VisitTimeSlotMappingDto(
            mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
            label = "2020-01-01T10:00",
            dpsId = "1233",
            nomisPrisonId = "WWI",
            nomisDayOfWeek = "MON",
            nomisSlotSequence = 2,
          ),
        )
      }.error

      assertThat(error.moreInfo.existing!!["dpsId"]).isEqualTo(existingDpsId)
      assertThat(error.moreInfo.existing["nomisSlotSequence"]).isEqualTo(2)
      assertThat(error.moreInfo.duplicate["dpsId"]).isEqualTo(dpsId)
      assertThat(error.moreInfo.duplicate["nomisSlotSequence"]).isEqualTo(2)
    }
  }
}
