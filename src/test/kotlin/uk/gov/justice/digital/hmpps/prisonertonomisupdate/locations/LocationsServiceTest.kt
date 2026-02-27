package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.groups.Tuple
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
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

    callCreateService(newLocation().copy(active = true))
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
        assertThat(it.active).isTrue()
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

  @Test
  fun `Amend maps to nomis correctly`() = runTest {
    whenever(
      locationsMappingService.getMappingGivenDpsId(DPS_LOCATION_ID),
    ).thenReturn(
      LocationMappingDto(
        dpsLocationId = DPS_LOCATION_ID,
        nomisLocationId = NOMIS_LOCATION_ID,
        mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
      ),
    )
    whenever(
      locationsMappingService.getMappingGivenDpsId(PARENT_DPS_LOCATION_ID),
    ).thenReturn(
      LocationMappingDto(
        dpsLocationId = PARENT_DPS_LOCATION_ID,
        nomisLocationId = PARENT_NOMIS_LOCATION_ID,
        mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
      ),
    )

    callAmendService()
    verify(nomisApiService).updateLocation(
      eq(NOMIS_LOCATION_ID),
      check {
        assertThat(it.locationType).isEqualTo(UpdateLocationRequest.LocationType.CELL)
        assertThat(it.locationCode).isEqualTo("003")
        assertThat(it.description).isEqualTo(DPS_KEY)
        assertThat(it.parentLocationId).isEqualTo(PARENT_NOMIS_LOCATION_ID)
        assertThat(it.userDescription).isEqualTo("description")
        assertThat(it.unitType).isEqualTo(UpdateLocationRequest.UnitType.NA)
        assertThat(it.listSequence).isEqualTo(4)
        assertThat(it.comment).isEqualTo("comments")
        assertThat(it.active).isFalse()
        assertThat(it.profiles).extracting("profileType", "profileCode").containsExactly(
          Tuple.tuple(ProfileRequest.ProfileType.HOU_UNIT_ATT, "GC"),
          Tuple.tuple(ProfileRequest.ProfileType.HOU_USED_FOR, "7"),
        )
        assertThat(it.usages).extracting("internalLocationUsageType", "sequence", "capacity").containsExactly(
          Tuple.tuple(UsageRequest.InternalLocationUsageType.MOVEMENT, 3, 11),
        )
      },
    )
    verify(nomisApiService).updateLocationCapacity(
      eq(NOMIS_LOCATION_ID),
      check {
        assertThat(it.capacity).isEqualTo(14)
        assertThat(it.operationalCapacity).isEqualTo(12)
      },
      eq(false),
    )
    verify(nomisApiService).updateLocationCertification(
      eq(NOMIS_LOCATION_ID),
      check {
        assertThat(it.certified).isTrue()
        assertThat(it.cnaCapacity).isEqualTo(13)
      },
    )
  }

  @Test
  fun `Amend passes ignoreOperationalCapacity flag`() = runTest {
    whenever(
      locationsMappingService.getMappingGivenDpsId(DPS_LOCATION_ID),
    ).thenReturn(
      LocationMappingDto(
        dpsLocationId = DPS_LOCATION_ID,
        nomisLocationId = NOMIS_LOCATION_ID,
        mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
      ),
    )

    callAmendService(newLocation().copy(ignoreWorkingCapacity = true, parentId = null))

    verify(nomisApiService).updateLocationCapacity(
      eq(NOMIS_LOCATION_ID),
      any(),
      eq(true),
    )
  }

  private suspend fun callCreateService(newLocation: LegacyLocation = newLocation()) {
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID)).thenReturn(newLocation)
    whenever(nomisApiService.createLocation(any())).thenReturn(LocationIdResponse(NOMIS_LOCATION_ID))

    val location = LocationDomainEvent(
      "TYPE",
      "version",
      "description",
      LocationAdditionalInformation(id = DPS_LOCATION_ID, key = DPS_KEY),
    )
    locationsService.createLocation(location)
  }

  private suspend fun callAmendService(newLocation: LegacyLocation = newLocation()) {
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID)).thenReturn(newLocation)

    val location = LocationDomainEvent(
      "TYPE",
      "version",
      "description",
      LocationAdditionalInformation(id = DPS_LOCATION_ID, key = DPS_KEY),
    )
    locationsService.amendLocation(location)
  }

  private fun newLocation() = LegacyLocation(
    id = UUID.fromString(DPS_LOCATION_ID),
    prisonId = "LEI",
    code = "003",
    pathHierarchy = "B-3-003",
    locationType = LegacyLocation.LocationType.CELL,
    active = false,
    key = DPS_KEY,
    residentialHousingType = LegacyLocation.ResidentialHousingType.NORMAL_ACCOMMODATION,
    localName = "description",
    comments = "comments",
    capacity = Capacity(
      workingCapacity = 12,
      maxCapacity = 14,
      certifiedNormalAccommodation = 13,
    ),
    certification = Certification(
      certified = true,
      certifiedNormalAccommodation = 13,
      capacityOfCertifiedCell = 13,
    ),
    certifiedCell = true,
    attributes = listOf(
      LegacyLocation.Attributes.GATED_CELL,
      LegacyLocation.Attributes.VULNERABLE_PRISONER_UNIT,
    ),
    usage = listOf(
      NonResidentialUsageDto(
        usageType = NonResidentialUsageDto.UsageType.MOVEMENT,
        sequence = 3,
        capacity = 11,
      ),
    ),
    orderWithinParentLocation = 4,
    parentId = UUID.fromString(PARENT_DPS_LOCATION_ID),
    lastModifiedBy = "me",
    lastModifiedDate = LocalDateTime.parse("2024-05-25T02:03:04"),
  )
}
