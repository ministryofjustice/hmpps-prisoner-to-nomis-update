package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.LocationsMappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerGetResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto.CurrentLocationType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val OFFENDER_NO = "A5678BZ"
const val BOOKING_ID = 12345L
const val NOMIS_ID = 1234567890L
const val DPS_ID = "be1ee367-8cfa-4499-942b-3938d3750000"
const val NOMIS_LOCATION_ID = 1234L
const val DPS_LOCATION_ID = "be1ee367-8cfa-4499-942b-222211110000"

@SpringAPIServiceTest
@Import(
  PropertyReconciliationService::class,
  PropertyNomisApiService::class,
  PropertyDpsApiService::class,
  PropertyNomisApiMockServer::class,
  PropertyMappingApiMockServer::class,
  NomisApiService::class,
  PropertyDpsApiMockServer::class,
  PropertyMappingService::class,
  LocationsMappingService::class,
  RetryApiService::class,
  PropertyConfiguration::class,
)
class PropertyReconciliationServiceTest {

  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var propertyNomisApi: PropertyNomisApiMockServer

  @Autowired
  private lateinit var propertyMappingApi: PropertyMappingApiMockServer

  @Autowired
  private lateinit var service: PropertyReconciliationService

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
  }

  @Nested
  inner class CheckMatch {
    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubPropertyRetrieval()
      assertThat(service.checkProperty(NOMIS_ID)).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `will report mismatches`() = runTest {
      stubPropertyRetrieval(
        nomisProperty().copy(prisonId = "OTHER", sealMark = "seal-mark-nomis"),
        dpsProperty(),
      )
      assertThat(service.checkProperty(NOMIS_ID)?.differences).isEqualTo(
        listOf(
          Difference(field = "prisonId", dps = "SWI", nomis = "OTHER"),
          Difference(field = "sealMark", dps = "seal-mark", nomis = "seal-mark-nomis"),
        ),
      )
      verify(telemetryClient).trackEvent(
        "property-reports-reconciliation-mismatch",
        mapOf(
          "dpsId" to DPS_ID,
          "nomisId" to NOMIS_ID.toString(),
          "differences" to "prisonId: dps=SWI, nomis=OTHER, sealMark: dps=seal-mark, nomis=seal-mark-nomis",
        ),
        null,
      )
    }

    @Test
    fun `will report mismatch when no mapping`() = runTest {
      stubPropertyRetrieval()

      propertyMappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)

      assertThat(service.checkProperty(NOMIS_ID)?.differences).isEqualTo(
        listOf(
          Difference(
            field = "nomis-only",
            dps = null,
            nomis = PropertyContainerFields(
              id = NOMIS_ID.toString(),
              offenderNo = OFFENDER_NO,
              location = DPS_LOCATION_ID,
              prison = "SWI",
              active = true,
              sealMark = "seal-mark",
              containerCode = PropertyContainerCode.BULK.name,
              proposedDisposalDate = LocalDate.now().plusDays(60),
              expiryDate = null,
            ),
          ),
        ),
      )
      verify(telemetryClient).trackEvent(
        "property-reports-reconciliation-no-mapping",
        mapOf(
          "nomisId" to NOMIS_ID.toString(),
        ),
        null,
      )
    }

    @Test
    fun `will report mismatch when no DPS record`() = runTest {
      stubPropertyRetrieval()

      propertyDpsApi.stubGetProperty(DPS_ID, status = 404)

      assertThat(service.checkProperty(NOMIS_ID)?.differences).isEqualTo(
        listOf(
          Difference(
            field = "nomis-only",
            dps = null,
            nomis = PropertyContainerFields(
              id = NOMIS_ID.toString(),
              offenderNo = OFFENDER_NO,
              location = DPS_LOCATION_ID,
              prison = "SWI",
              active = true,
              sealMark = "seal-mark",
              containerCode = PropertyContainerCode.BULK.name,
              proposedDisposalDate = LocalDate.now().plusDays(60),
              expiryDate = null,
            ),
          ),
        ),
      )
      verify(telemetryClient).trackEvent(
        "property-reports-reconciliation-no-dps",
        mapOf(
          "nomisId" to NOMIS_ID.toString(),
          "dpsId" to DPS_ID,
        ),
        null,
      )
    }
  }

  private fun stubPropertyRetrieval(nomisProperty: PropertyContainerGetResponse = nomisProperty(), dpsProperty: PropertyContainerDto = dpsProperty()) {
    propertyNomisApi.stubGetProperty(NOMIS_ID, nomisProperty)
    propertyDpsApi.stubGetProperty(DPS_ID, dpsProperty.copy(id = UUID.fromString(DPS_ID)))
    propertyMappingApi.stubGetByNomisId(
      NOMIS_ID,
      PropertyContainerMappingDto(DPS_ID, NOMIS_ID, BOOKING_ID, OFFENDER_NO, PropertyContainerMappingDto.MappingType.MIGRATED),
    )
    mappingServer.stubGetMappingGivenNomisLocationIdUsingExternalApi(
      NOMIS_LOCATION_ID,
      """{
            "dpsLocationId": "$DPS_LOCATION_ID",
            "nomisLocationId": $NOMIS_LOCATION_ID,
            "mappingType": "MIGRATED"
          }
          """,
    )
  }
}

fun dpsProperty(): PropertyContainerDto = PropertyContainerDto(
  id = UUID.fromString(DPS_ID),
  prisonerNumber = OFFENDER_NO,
  prisonId = "SWI",
  containerType = PropertyContainerDto.ContainerType.STANDARD,
  currentStatus = PropertyContainerDto.CurrentStatus.STORED,
  createDateTime = LocalDateTime.now(),
  createdByUserId = "ME",
  currentSealNumber = "seal-mark",
  currentLocation = UUID.fromString(DPS_LOCATION_ID),
  currentLocationType = CurrentLocationType.INTERNAL,
  proposedDisposalDate = LocalDate.now().plusDays(60),
  removalOutcome = null,
  removalDate = null,
)

fun nomisProperty() = PropertyContainerGetResponse(
  containerId = NOMIS_ID,
  offenderNo = OFFENDER_NO,
  bookingId = BOOKING_ID,
  prisonId = "SWI",
  active = true,
  containerCode = PropertyContainerCode.BULK,
  createdDateTime = LocalDateTime.now(),
  createdBy = "ME",
  internalLocationId = NOMIS_LOCATION_ID,
  sealMark = "seal-mark",
  expiryDate = null,
  proposedDisposalDate = LocalDate.now().plusDays(60),
)
