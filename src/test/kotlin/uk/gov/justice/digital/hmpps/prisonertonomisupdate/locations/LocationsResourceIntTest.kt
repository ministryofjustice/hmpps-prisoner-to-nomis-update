package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@ExtendWith(MockitoExtension::class)
class LocationsResourceIntTest(
  @Autowired private val locationsReconciliationService: LocationsReconciliationService,
) : IntegrationTestBase() {

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  private fun generateUUID(it: Int) = "de91dfa7-821f-4552-a427-111111${it.toString().padStart(6, '0')}"

  private fun locationsNomisPagedResponse(
    totalElements: Int = 10,
    numberOfElements: Int = 10,
    pageSize: Int = 10,
    pageNumber: Int = 0,
  ): String {
    val locationId = (1..numberOfElements)
      .map { it + (pageNumber * pageSize) }
    val content = locationId
      .map { """{ "locationId": $it }""" }
      .joinToString { it }
    return pagedResponse(content, pageSize, pageNumber, totalElements, locationId.size)
  }

  private fun locationsDpsPagedResponse(
    totalElements: Int = 10,
    numberOfElements: Int = 10,
    pageSize: Int = 10,
    pageNumber: Int = 0,
  ): String {
    val content =
      (1..numberOfElements)
        .map { it + (pageNumber * pageSize) }
        .map { locationDpsJson("001", id = generateUUID(it)) }
        .joinToString { it }
    return pagedResponse(content, pageSize, pageNumber, totalElements, numberOfElements)
  }

  private fun pagedResponse(
    content: String,
    pageSize: Int,
    pageNumber: Int,
    totalElements: Int,
    pageElements: Int,
  ) = """
  {
      "content": [
          $content
      ],
      "pageable": {
          "sort": {
              "empty": false,
              "sorted": true,
              "unsorted": false
          },
          "offset": 0,
          "pageSize": $pageSize,
          "pageNumber": $pageNumber,
          "paged": true,
          "unpaged": false
      },
      "first": true,
      "last": false,
      "totalPages": ${totalElements / pageSize + 1},
      "totalElements": $totalElements,
      "size": $pageSize,
      "number": $pageNumber,
      "sort": {
          "empty": false,
          "sorted": true,
          "unsorted": false
      },
      "numberOfElements": $pageElements,
      "empty": false
  }                
  """.trimIndent()

  private fun locationNomisResponse(id: Long): String = locationNomisJson(id)

  private fun locationNomisJson(id: Long): String = """
  {
    "locationId" : $id,
    "certified" : true,
    "locationType" : "LAND",
    "prisonId" : "MDI",
    "unitType": "NA",
    "locationCode" : "001",
    "description" : "MDI-A-1-001",
    "capacity" : 2,
    "operationalCapacity" : 2,
    "cnaCapacity" : 1,
    "userDescription" : "Landing A",
    "listSequence" : 1,
    "createDatetime" : "2021-08-10T10:00:00",
    "createUsername" : "user",
    "tracking" : true,
    "active" : true
  }
  """.trimIndent()

  private fun locationApiResponse(code: String, id: String) = locationDpsJson(code, id)

  private fun locationDpsJson(code: String, id: String) = """
  {
    "id": "$id",
    "prisonId": "MDI",
    "code": "$code",
    "pathHierarchy": "A-1",
    "locationType": "LANDING",
    "active": true,
    "localName": "Landing A",
    "capacity": {
      "maxCapacity": 2,
      "workingCapacity": 2
    },
    "certification": {
      "certified": true,
      "certifiedNormalAccommodation": 1,
      "capacityOfCertifiedCell": 3
    },
    "orderWithinParentLocation": 1,
    "topLevelId": "abcdef01-573c-433a-9e51-2d83f887c11c",
    "key": "MDI-A-1-001",
    "status": "ACTIVE",
    "residentialHousingType": "NORMAL_ACCOMMODATION",
    "internalMovementAllowed": true,
    "lastModifiedBy": "me",
    "lastModifiedDate": "2024-05-25T01:02:03"
  }
  """.trimIndent()

  @DisplayName("Locations reconciliation report")
  @Nested
  inner class GenerateLocationReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)

      val numberOfLocations = 34
      nomisApi.stubGetLocationsInitialCount(locationsNomisPagedResponse(totalElements = numberOfLocations, pageSize = 1))
      nomisApi.stubGetLocationsPage(0, 10, locationsNomisPagedResponse(numberOfLocations, 10, pageNumber = 0, pageSize = 10))
      nomisApi.stubGetLocationsPage(1, 10, locationsNomisPagedResponse(numberOfLocations, 10, pageNumber = 1, pageSize = 10))
      nomisApi.stubGetLocationsPage(2, 10, locationsNomisPagedResponse(numberOfLocations, 10, pageNumber = 2, pageSize = 10))
      nomisApi.stubGetLocationsPage(3, 10, locationsNomisPagedResponse(numberOfLocations, 4, pageNumber = 3, pageSize = 10))

      locationsApi.stubGetLocationsPage(0, 1, locationsDpsPagedResponse(37, 37)) // some extra in DPS
      locationsApi.stubGetLocationsPage(0, 10, locationsDpsPagedResponse(37, 10, 10, 0))
      locationsApi.stubGetLocationsPage(1, 10, locationsDpsPagedResponse(37, 10, 10, 1))
      locationsApi.stubGetLocationsPage(2, 10, locationsDpsPagedResponse(37, 10, 10, 2))
      locationsApi.stubGetLocationsPage(3, 10, locationsDpsPagedResponse(37, 7, 10, 3))

      (1..numberOfLocations).forEach {
        val nomisId = it.toLong()
        val dpsId = generateUUID(it)
        nomisApi.stubGetLocation(nomisId, locationNomisResponse(nomisId))
        locationsApi.stubGetLocation(dpsId, false, locationApiResponse(if (it % 10 == 0) "OTHER" else "001", dpsId)) // every 10th location has an 'OTHER' code in DPS
        mappingServer.stubGetMappingGivenNomisLocationId(
          nomisId,
          """{
            "dpsLocationId": "$dpsId",
            "nomisLocationId": $nomisId,
            "mappingType": "MIGRATED"
          }
          """.trimIndent(),
        )
      }
    }

    @Test
    fun `will output report requested telemetry`() = runTest {
      locationsReconciliationService.generateReconciliationReport()

      verify(telemetryClient).trackEvent(
        eq("locations-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("locations-nomis-total", "34")
        },
        isNull(),
      )

      awaitReportFinished()
    }

    @Test
    fun `should execute batches of prisoners`() = runTest {
      // given "reports.locations.reconciliation.page-size=10"

      locationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()
      nomisApi.verify(
        WireMock.getRequestedFor(urlPathEqualTo("/locations/ids"))
          .withQueryParam("size", WireMock.equalTo("1")),
      )
      nomisApi.verify(
        // 34 prisoners will be spread over 4 pages of 10 prisoners each
        4,
        WireMock.getRequestedFor(urlPathEqualTo("/locations/ids"))
          .withQueryParam("size", WireMock.equalTo("10")),
      )
    }

    @Test
    fun `should emit a mismatched custom event for each mismatch along with a summary`() = runTest {
      locationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("locations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "6")
          assertThat(it).containsEntry(
            "10,${generateUUID(10)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "20,${generateUUID(20)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "30,${generateUUID(30)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "null,${generateUUID(35)}",
            "nomis=null, dps=LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "null,${generateUUID(36)}",
            "nomis=null, dps=LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "null,${generateUUID(37)}",
            "nomis=null, dps=LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
        },
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("locations-reports-reconciliation-mismatch"),
        telemetryCaptor.capture(),
        isNull(),
      )

      verify(telemetryClient, times(3)).trackEvent(
        eq("locations-reports-reconciliation-dps-only"),
        telemetryCaptor.capture(),
        isNull(),
      )

      with(telemetryCaptor.allValues[0]) {
        assertThat(this).containsEntry("nomisId", "10")
        assertThat(this).containsEntry(
          "nomis",
          "LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)",
        )
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
      with(telemetryCaptor.allValues[1]) {
        assertThat(this).containsEntry("nomisId", "20")
        assertThat(this).containsEntry(
          "nomis",
          "LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)",
        )
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
      with(telemetryCaptor.allValues[2]) {
        assertThat(this).containsEntry("nomisId", "30")
        assertThat(this).containsEntry(
          "nomis",
          "LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)",
        )
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
      with(telemetryCaptor.allValues[3]) {
        assertThat(this).containsEntry("dpsId", "de91dfa7-821f-4552-a427-111111000035")
        assertThat(this).containsEntry("key", "MDI-A-1")
        assertThat(this).doesNotContainKey("nomis")
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
      with(telemetryCaptor.allValues[4]) {
        assertThat(this).containsEntry("dpsId", "de91dfa7-821f-4552-a427-111111000036")
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
      with(telemetryCaptor.allValues[5]) {
        assertThat(this).containsEntry("dpsId", "de91dfa7-821f-4552-a427-111111000037")
        assertThat(this).containsEntry(
          "dps",
          "LocationReportDetail(code=001, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
        )
      }
    }

    @Test
    fun `will attempt to complete a report even if some of the checks fail`() = runTest {
      nomisApi.stubGetLocationWithError(2, 500)
      locationsApi.stubGetLocationWithError(generateUUID(20), false, 500)

      locationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("locations-reports-reconciliation-mismatch-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("locations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "5")
          assertThat(it).containsEntry(
            "10,${generateUUID(10)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "30,${generateUUID(30)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
        },
        isNull(),
      )
    }

    @Test
    fun `when initial prison count fails the whole report fails`() = runTest {
      nomisApi.stubGetLocationsPageWithError(0, 500)

      assertThrows<RuntimeException> {
        locationsReconciliationService.generateReconciliationReport()
      }
    }

    @Test
    fun `will attempt to complete a report even if whole pages of the Nomis and DPS checks fail`() = runTest {
      nomisApi.stubGetLocationsPageWithError(2, 500)
      locationsApi.stubGetLocationsPageWithError(2, 10)

      locationsReconciliationService.generateReconciliationReport()

      awaitReportFinished()

      verify(telemetryClient, times(2)).trackEvent(
        eq("locations-reports-reconciliation-mismatch-page-error"),
        any(),
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("locations-reports-reconciliation-success"),
        check {
          assertThat(it).containsEntry("mismatch-count", "5")
          assertThat(it).containsEntry(
            "10,${generateUUID(10)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
          assertThat(it).containsEntry(
            "20,${generateUUID(20)}",
            "nomis=LocationReportDetail(code=001, key=MDI-A-1-001, housingType=NA, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=0, usages=null, history=0, internalMovementAllowed=true)," +
              " dps=LocationReportDetail(code=OTHER, key=MDI-A-1, housingType=NORMAL_ACCOMMODATION, localName=Landing A, comment=null, operationalCapacity=2, maxCapacity=2, certified=true, cnaCapacity=1, active=true, attributes=null, usages=null, history=null, internalMovementAllowed=true)",
          )
        },
        isNull(),
      )
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("locations-reports-reconciliation-success"), any(), isNull()) }
  }
}
