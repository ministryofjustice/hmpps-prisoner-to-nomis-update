package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AmendmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDateTime
import java.util.UUID

private const val DPS_LOCATION_ID = "abcdef01-2345-6789-abcd-ef0123456789"
private const val PARENT_DPS_LOCATION_ID = "12345678-2345-6789-abcd-ef0123456789"
private const val DPS_KEY = "MDI-B-3"
private const val NOMIS_LOCATION_ID = 4567L
private const val PARENT_NOMIS_LOCATION_ID = 1234L

class LocationsReconciliationServiceTest {

  private val locationsApiService: LocationsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val locationsMappingService: LocationsMappingService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val locationsReconciliationService =
    LocationsReconciliationService(telemetryClient, nomisApiService, locationsApiService, locationsMappingService, 10)

  private val locationMappingDto = LocationMappingDto(
    dpsLocationId = DPS_LOCATION_ID,
    nomisLocationId = NOMIS_LOCATION_ID,
    mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
  )

  @Test
  fun `will not report mismatch where details match`() = runTest {
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse())
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, true)).thenReturn(dpsResponse(DPS_LOCATION_ID))

    assertThat(locationsReconciliationService.checkMatch(locationMappingDto)).isNull()
  }

  @Test
  fun `will report mismatch where locations have a different sequence number`() = runTest {
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse(listSequence = 3))
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, false)).thenReturn(dpsResponse(DPS_LOCATION_ID))
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(PARENT_NOMIS_LOCATION_ID))
      .thenReturn(
        LocationMappingDto(
          dpsLocationId = PARENT_DPS_LOCATION_ID,
          nomisLocationId = PARENT_NOMIS_LOCATION_ID,
          mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
        ),
      )

    assertThat(locationsReconciliationService.checkMatch(locationMappingDto))
      .isEqualTo(
        MismatchLocation(
          nomisId = NOMIS_LOCATION_ID,
          dpsId = DPS_LOCATION_ID,
          nomisLocation = LocationReportDetail(
            code = "3",
            key = DPS_KEY,
            housingType = "REC",
            localName = "Wing C, landing 3",
            maxCapacity = 14,
            operationalCapacity = 12,
            certified = true,
            cnaCapacity = 13,
            comment = null,
            active = true,
            attributes = 2,
            usages = 1,
            history = 1,
            internalMovementAllowed = false,
          ),
          dpsLocation = LocationReportDetail(
            code = "3",
            key = DPS_KEY,
            housingType = "NORMAL_ACCOMMODATION",
            localName = "Wing C, landing 3",
            maxCapacity = 14,
            operationalCapacity = 12,
            certified = true,
            cnaCapacity = 13,
            comment = null,
            active = true,
            attributes = 2,
            usages = 1,
            history = 1,
            internalMovementAllowed = false,
          ),
        ),
      )
  }

  @Test
  fun `will report mismatch where locations have a different tracking flag`() = runTest {
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID))
      .thenReturn(
        nomisResponse(tracking = true).copy(unitType = null),
      )
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, false))
      .thenReturn(
        dpsResponse(DPS_LOCATION_ID).copy(
          residentialHousingType = null,
          locationType = LegacyLocation.LocationType.TRAINING_AREA,
          internalMovementAllowed = false,
        ),
      )
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(PARENT_NOMIS_LOCATION_ID))
      .thenReturn(
        LocationMappingDto(
          dpsLocationId = PARENT_DPS_LOCATION_ID,
          nomisLocationId = PARENT_NOMIS_LOCATION_ID,
          mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
        ),
      )

    val result = locationsReconciliationService.checkMatch(locationMappingDto)
    assertThat(result?.nomisLocation?.internalMovementAllowed).isTrue()
    assertThat(result?.dpsLocation?.internalMovementAllowed).isFalse()
  }

  @Test
  fun `will report mismatch where only nomis has a parent`() = runTest {
    assertThat(
      locationsReconciliationService.doesNotMatch(
        nomisResponse(),
        dpsResponse(DPS_LOCATION_ID).copy(parentId = null),
        "nomis-parent-UUID",
      ),
    ).isEqualTo("Parent mismatch")
  }

  @Test
  fun `will report mismatch where only DPS has a parent`() = runTest {
    assertThat(
      locationsReconciliationService.doesNotMatch(
        nomisResponse().copy(parentLocationId = null),
        dpsResponse(DPS_LOCATION_ID),
        null,
      ),
    ).isEqualTo("Parent mismatch")
  }

  @Test
  fun `will report mismatch where locations have a different parent`() = runTest {
    assertThat(
      locationsReconciliationService.doesNotMatch(
        nomisResponse().copy(parentLocationId = 54321),
        dpsResponse(DPS_LOCATION_ID).copy(parentId = UUID.fromString("00000000-2345-6789-abcd-000011112222")),
        "nomis-parent-UUID",
      ),
    ).isEqualTo("Parent mismatch")
  }

  @Test
  fun `will NOT report mismatch where location is permanently deactivated`() = runTest {
    assertThat(
      locationsReconciliationService.doesNotMatch(
        nomisResponse().copy(comment = "different"),
        dpsResponse(DPS_LOCATION_ID).copy(permanentlyDeactivated = true),
        "nomis-parent-UUID",
      ),
    ).isNull()
  }

  @Test
  fun `will continue after a GET mapping error`() = runTest {
    whenever(nomisApiService.getLocations(0, 10)).thenReturn(
      PageImpl(
        listOf(LocationIdResponse(999), LocationIdResponse(NOMIS_LOCATION_ID)),
        Pageable.ofSize(10),
        2,
      ),
    )
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(999))
      .thenThrow(RuntimeException("test error"))
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(NOMIS_LOCATION_ID))
      .thenReturn(LocationMappingDto(DPS_LOCATION_ID, NOMIS_LOCATION_ID, LocationMappingDto.MappingType.LOCATION_CREATED))

    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse().copy(listSequence = 9999))
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, false)).thenReturn(dpsResponse(DPS_LOCATION_ID))

    // Total of 2 => no missing dps records
    whenever(locationsApiService.getLocations(0, 1)).thenReturn(
      PageImpl(
        listOf(dpsResponse(DPS_LOCATION_ID), dpsResponse(DPS_LOCATION_ID)),
        Pageable.ofSize(10),
        2,
      ),
    )

    val results = locationsReconciliationService.generateReconciliationReport(2)
    assertThat(results).hasSize(1)

    verify(telemetryClient, times(1)).trackEvent(
      eq("locations-reports-reconciliation-retrieval-error"),
      check {
        assertThat(it).containsEntry("nomis-location-id", "999")
      },
      isNull(),
    )
    verify(telemetryClient, times(1)).trackEvent(
      eq("locations-reports-reconciliation-mismatch"),
      anyMap(),
      isNull(),
    )
  }

  private fun nomisResponse(listSequence: Int? = 4, comment: String? = null, tracking: Boolean = false) = LocationResponse(
    locationId = NOMIS_LOCATION_ID,
    comment = comment,
    locationType = "LAND",
    locationCode = "3",
    description = DPS_KEY,
    parentLocationId = PARENT_NOMIS_LOCATION_ID,
    parentKey = "MDI-C",
    userDescription = "Wing C, landing 3",
    prisonId = "MDI",
    listSequence = listSequence,
    unitType = LocationResponse.UnitType.REC,
    capacity = 14,
    operationalCapacity = 12,
    cnaCapacity = 13,
    certified = true,
    createUsername = "TJONES_ADM",
    createDatetime = LocalDateTime.parse("2023-09-25T11:12:45"),
    active = true,
    tracking = tracking,
    profiles = listOf(
      ProfileRequest(ProfileRequest.ProfileType.SUP_LVL_TYPE, "C"),
      ProfileRequest(ProfileRequest.ProfileType.SUP_LVL_TYPE, "D"),
      ProfileRequest(ProfileRequest.ProfileType.NON_ASSO_TYP, "not-migrated-to-dps"),
    ),
    usages = listOf(
      UsageRequest(UsageRequest.InternalLocationUsageType.OCCUR, 42, 5),
    ),
    amendments = mutableListOf(
      AmendmentResponse(
        amendDateTime = LocalDateTime.parse("2023-09-25T11:12:45"),
        columnName = "Accommodation Type",
        oldValue = "41",
        newValue = "42",
        amendedBy = "STEVE_ADM",
      ),
      // not counted in DPS as it is a duplicate:
      AmendmentResponse(
        amendDateTime = LocalDateTime.parse("2023-09-25T11:12:45"),
        columnName = "Accommodation Type",
        oldValue = "41",
        newValue = "42",
        amendedBy = "STEVE_ADM",
      ),
      // not counted in DPS as old and new are the same
      AmendmentResponse(
        amendDateTime = LocalDateTime.parse("2023-09-25T11:12:45"),
        columnName = "Accommodation Type",
        oldValue = "same",
        newValue = "same",
        amendedBy = "STEVE_ADM",
      ),
    ),
  )

  private fun dpsResponse(id: String, comment: String? = null) = LegacyLocation(
    id = UUID.fromString(id),
    prisonId = "MDI",
    code = "3",
    pathHierarchy = "B-3",
    locationType = LegacyLocation.LocationType.LANDING,
    active = true,
    orderWithinParentLocation = 4,
    key = DPS_KEY,
    residentialHousingType = LegacyLocation.ResidentialHousingType.NORMAL_ACCOMMODATION,
    localName = "Wing C, landing 3",
    comments = comment,
    internalMovementAllowed = false,
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
      LegacyLocation.Attributes.CAT_C,
      LegacyLocation.Attributes.CAT_D,
    ),
    usage = listOf(
      NonResidentialUsageDto(
        usageType = NonResidentialUsageDto.UsageType.OCCURRENCE,
        sequence = 5,
        capacity = 42,
      ),
    ),
    changeHistory = mutableListOf(
      ChangeHistory(
        amendedDate = LocalDateTime.parse("2023-09-25T11:12:45"),
        amendedBy = "STEVE_ADM",
        attribute = "Location Type",
      ),
    ),
    parentId = UUID.fromString(PARENT_DPS_LOCATION_ID),
    lastModifiedBy = "me",
    lastModifiedDate = LocalDateTime.parse("2024-05-25T11:10:05"),
  )
}
