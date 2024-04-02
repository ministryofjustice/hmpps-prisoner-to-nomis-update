package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.util.UUID

private const val DPS_LOCATION_ID = "abcdef01-2345-6789-abcd-ef0123456789"
private const val PARENT_DPS_LOCATION_ID = "12345678-2345-6789-abcd-ef0123456789"
private const val DPS_KEY = "LEI-B-3-003"
private const val NOMIS_LOCATION_ID = 4567L
private const val PARENT_NOMIS_LOCATION_ID = 1234L

@OptIn(ExperimentalCoroutinesApi::class)
internal class LocationsServiceTest {

  private val locationsApiService: LocationsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val locationsMappingService: LocationsMappingService = mock()
  private val locationsUpdateQueueService: LocationsRetryQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val objectMapper: ObjectMapper = mock()

  private val locationsService = LocationsService(
    locationsApiService,
    nomisApiService,
    locationsMappingService,
    locationsUpdateQueueService,
    telemetryClient,
    objectMapper,
  )

  @Test
  fun `Create maps to nomis correctly`() = runTest {
    whenever(
      locationsMappingService.getMappingGivenDpsId(PARENT_DPS_LOCATION_ID),
    ).thenReturn(
      LocationMappingDto(
        dpsLocationId = PARENT_DPS_LOCATION_ID,
        nomisLocationId = PARENT_NOMIS_LOCATION_ID,
        mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
      ),
    )

    callService()
    verify(nomisApiService).createLocation(
      check {
        assertThat(it.locationType).isEqualTo(CreateLocationRequest.LocationType.CELL)
        assertThat(it.prisonId).isEqualTo("LEI")
        assertThat(it.locationCode).isEqualTo("003")
        assertThat(it.description).isEqualTo(DPS_KEY)
        assertThat(it.parentLocationId).isEqualTo(PARENT_NOMIS_LOCATION_ID)
        assertThat(it.userDescription).isEqualTo("description")
        assertThat(it.unitType).isEqualTo(CreateLocationRequest.UnitType.NA)
        assertThat(it.listSequence).isEqualTo(4)
        assertThat(it.comment).isEqualTo("comments")
        assertThat(it.certified).isTrue()
        assertThat(it.cnaCapacity).isEqualTo(13)
        assertThat(it.capacity).isEqualTo(14)
        assertThat(it.operationalCapacity).isEqualTo(12)
        assertThat(it.profiles).extracting("profileType", "profileCode").containsExactly(
          Tuple.tuple(ProfileRequest.ProfileType.HOU_UNIT_ATT, "GC"),
          Tuple.tuple(ProfileRequest.ProfileType.HOU_USED_FOR, "7"),
        )
        assertThat(it.usages).extracting("internalLocationUsageType", "sequence", "capacity").containsExactly(
          Tuple.tuple(UsageRequest.InternalLocationUsageType.MOVEMENT, 3, 11),
        )
      },
    )
  }

  private suspend fun callService() {
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID)).thenReturn(
      newLocation(),
    )
    whenever(nomisApiService.createLocation(any())).thenReturn(LocationIdResponse(NOMIS_LOCATION_ID))

    val location = LocationDomainEvent(
      "TYPE",
      "version",
      "description",
      LocationAdditionalInformation(id = DPS_LOCATION_ID, key = DPS_KEY),
    )
    locationsService.createLocation(location)
  }

  private fun newLocation(): Location {
    return Location(
      id = UUID.fromString(DPS_LOCATION_ID),
      prisonId = "LEI",
      code = "003",
      pathHierarchy = "B-3-003",
      locationType = Location.LocationType.CELL,
      active = true,
      topLevelId = UUID.fromString(PARENT_DPS_LOCATION_ID),
      key = DPS_KEY,
      isResidential = true,
      residentialHousingType = Location.ResidentialHousingType.NORMAL_ACCOMMODATION,
      localName = "description",
      comments = "comments",
      capacity = Capacity(
        workingCapacity = 12,
        maxCapacity = 14,
      ),
      certification = Certification(
        certified = true,
        capacityOfCertifiedCell = 13,
      ),
      attributes = listOf(
        Location.Attributes.GATED_CELL,
        Location.Attributes.VULNERABLE_PRISONER_UNIT,
      ),
      usage = listOf(
        NonResidentialUsageDto(
          usageType = NonResidentialUsageDto.UsageType.MOVEMENT,
          sequence = 3,
          capacity = 11,
        ),
      ),
      orderWithinParentLocation = 4,
      //    deactivatedDate = ,
      //    deactivatedReason = ,
      //    reactivatedDate = ,
      parentId = UUID.fromString(PARENT_DPS_LOCATION_ID),
      //      parentLocation = ,
      //      childLocations = ,
    )
  }
}
