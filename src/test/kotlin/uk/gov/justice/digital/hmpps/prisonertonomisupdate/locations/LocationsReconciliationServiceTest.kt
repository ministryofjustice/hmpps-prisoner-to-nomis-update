package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Capacity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Certification
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.NonResidentialUsageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AmendmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UsageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
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
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(NOMIS_LOCATION_ID)).thenReturn(locationMappingDto)
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse())
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, true)).thenReturn(dpsResponse(DPS_LOCATION_ID))

    assertThat(locationsReconciliationService.checkMatch(locationMappingDto)).isNull()
  }

  @Test
  fun `will report mismatch where locations have a different comment`() = runTest {
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(NOMIS_LOCATION_ID)).thenReturn(locationMappingDto)
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse("comment1"))
    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, true)).thenReturn(dpsResponse(DPS_LOCATION_ID, "comment3"))

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
            comment = "comment1",
            active = true,
            attributes = 2,
            usages = 1,
            history = 1,
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
            comment = "comment3",
            active = true,
            attributes = 2,
            usages = 1,
            history = 1,
          ),
        ),
      )
  }

  @Test
  fun `will report mismatch where locations have a different history count`() = runTest {
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(NOMIS_LOCATION_ID)).thenReturn(locationMappingDto)
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(nomisResponse())

    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, true)).thenReturn(
      dpsResponse(DPS_LOCATION_ID).apply {
        (changeHistory as MutableList).add(
          ChangeHistory(
            amendedDate = "2023-09-25T11:12:45",
            amendedBy = "STEVE_ADM",
            attribute = "NEW attribute",
            oldValue = "old",
            newValue = "new",
          ),
        )
      },
    )

    with(locationsReconciliationService.checkMatch(locationMappingDto)!!) {
      assertThat(nomisLocation?.history).isEqualTo(1)
      assertThat(dpsLocation?.history).isEqualTo(2)
    }
  }

  @Test
  fun `will report mismatch where locations have a different history item`() = runTest {
    whenever(locationsMappingService.getMappingGivenNomisIdOrNull(NOMIS_LOCATION_ID)).thenReturn(locationMappingDto)
    whenever(nomisApiService.getLocationDetails(NOMIS_LOCATION_ID)).thenReturn(
      nomisResponse().apply {
        (amendments as MutableList).add(
          AmendmentResponse(
            amendDateTime = "2023-09-25T11:12:45",
            columnName = "Baseline CNA",
            oldValue = "5",
            newValue = "6",
            amendedBy = "STEVE_ADM",
          ),
        )
      },
    )

    whenever(locationsApiService.getLocation(DPS_LOCATION_ID, true)).thenReturn(
      dpsResponse(DPS_LOCATION_ID).apply {
        (changeHistory as MutableList).add(
          ChangeHistory(
            amendedDate = "2023-09-25T11:12:45",
            amendedBy = "STEVE_ADM",
            attribute = "NEW dps attribute",
            oldValue = "old",
            newValue = "new",
          ),
        )
      },
    )

    with(locationsReconciliationService.checkMatch(locationMappingDto)!!) {
      assertThat(nomisLocation?.history).isEqualTo(2)
      assertThat(dpsLocation?.history).isEqualTo(2)
    }
  }

  private fun nomisResponse(comment: String? = null) = LocationResponse(
    locationId = NOMIS_LOCATION_ID,
    comment = comment,
    locationType = "LAND",
    locationCode = "3",
    description = DPS_KEY,
    parentLocationId = PARENT_NOMIS_LOCATION_ID,
    parentKey = "MDI-C",
    userDescription = "Wing C, landing 3",
    prisonId = "MDI",
    listSequence = 4,
    unitType = LocationResponse.UnitType.REC,
    capacity = 14,
    operationalCapacity = 12,
    cnaCapacity = 13,
    certified = true,
    createUsername = "TJONES_ADM",
    createDatetime = "2023-09-25T11:12:45",
    active = true,
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
        amendDateTime = "2023-09-25T11:12:45",
        columnName = "Accommodation Type",
        oldValue = "41",
        newValue = "42",
        amendedBy = "STEVE_ADM",
      ),
      // not counted in DPS as it is a duplicate:
      AmendmentResponse(
        amendDateTime = "2023-09-25T11:12:45",
        columnName = "Accommodation Type",
        oldValue = "41",
        newValue = "42",
        amendedBy = "STEVE_ADM",
      ),
      // not counted in DPS as old and new are the same
      AmendmentResponse(
        amendDateTime = "2023-09-25T11:12:45",
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
    capacity = Capacity(
      workingCapacity = 12,
      maxCapacity = 14,
    ),
    certification = Certification(
      certified = true,
      capacityOfCertifiedCell = 13,
    ),
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
        amendedDate = "2023-09-25T11:12:45",
        amendedBy = "STEVE_ADM",
        attribute = "Location Type",
        oldValue = "41",
        newValue = "42",
      ),
    ),
    //    deactivatedDate = ,
    //    deactivatedReason = ,
    //    reactivatedDate = ,
    parentId = UUID.fromString(PARENT_DPS_LOCATION_ID),
    lastModifiedBy = "me",
    lastModifiedDate = "2024-05-25",
  )
}
