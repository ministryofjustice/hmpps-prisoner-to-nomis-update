package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.PatchNonResidentialLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension.Companion.locationsApi
import java.util.UUID

private const val LOCATION_ID = "2475f250-434a-4257-afe7-b911f1773a4d"

@SpringAPIServiceTest
@Import(LocationsApiService::class, LocationsConfiguration::class, RetryApiService::class)
internal class LocationsApiServiceTest {

  @Autowired
  private lateinit var locationsApiService: LocationsApiService

  val response = """
    {
      "id": "$LOCATION_ID",
      "prisonId": "MDI",
      "code": "001",
      "pathHierarchy": "A-1-001",
      "locationType": "CELL",
      "residentialHousingType": "NORMAL_ACCOMMODATION",
      "active": true,
      "localName": "Wing A",
      "comments": "Not to be used",
      "capacity": {
        "maxCapacity": 2,
        "workingCapacity": 2
      },
      "certification": {
        "certified": true,
        "certifiedNormalAccommodation": 1,
        "capacityOfCertifiedCell": 3
      },
      "attributes": ["LOCATE_FLAT", "ELIGIBLE"],
      "orderWithinParentLocation": 1,
      "parentId": "57718979-573c-433a-9e51-2d83f887c11c",
      "key": "MDI-A-1-001",
      "lastModifiedBy": "me",
      "lastModifiedDate": "2024-05-25T01:02:03"
    }
  """.trimIndent()

  @Nested
  @DisplayName("GET /sync/id/{id}")
  inner class GetLocation {
    @BeforeEach
    internal fun setUp() {
      locationsApi.stubGetLocation(LOCATION_ID, false, response)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      locationsApiService.getLocation(LOCATION_ID)

      locationsApi.verify(
        getRequestedFor(urlEqualTo("/sync/id/$LOCATION_ID?includeHistory=false"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will parse data for a detail field`(): Unit = runTest {
      val location = locationsApiService.getLocation(LOCATION_ID)

      assertThat(location.pathHierarchy).isEqualTo("A-1-001")
      assertThat(location.attributes).containsExactlyInAnyOrder(LegacyLocation.Attributes.LOCATE_FLAT, LegacyLocation.Attributes.ELIGIBLE)
    }

    @Test
    fun `when location is not found an exception is thrown`() = runTest {
      locationsApi.stubGetLocationWithError(LOCATION_ID, false, status = 404)

      assertThrows<WebClientResponseException.NotFound> {
        locationsApiService.getLocation(LOCATION_ID)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      locationsApi.stubGetLocationWithError(LOCATION_ID, false, status = 503)

      assertThrows<WebClientResponseException.ServiceUnavailable> {
        locationsApiService.getLocation(LOCATION_ID)
      }
    }

    @Test
    fun `when a timeout occurs the call is retried`() = runTest {
      locationsApi.stubGetLocationSlowThenQuick(LOCATION_ID, response)

      val location = locationsApiService.getLocation(LOCATION_ID)

      assertThat(location.pathHierarchy).isEqualTo("A-1-001")
    }
  }

  @Nested
  @DisplayName("PATCH /locations/non-residential/{id}")
  inner class PatchNonResidentialLocation {
    val uuid: UUID = UUID.fromString(LOCATION_ID)
    val p = PatchNonResidentialLocationRequest(
      code = "code",
      locationType = PatchNonResidentialLocationRequest.LocationType.CLASSROOM,
    )

    @BeforeEach
    internal fun setUp() {
      locationsApi.stubPatchNonResidentialLocation(uuid)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      locationsApiService.patchNonResidentialLocation(LOCATION_ID, p)

      locationsApi.verify(
        patchRequestedFor(urlEqualTo("/locations/non-residential/$LOCATION_ID"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `when location is not found an exception is thrown`() = runTest {
      locationsApi.stubPatchNonResidentialLocationWithError(uuid, status = 404)

      assertThrows<WebClientResponseException.NotFound> {
        locationsApiService.patchNonResidentialLocation(LOCATION_ID, p)
      }
    }

    @Test
    fun `when any bad response is received an exception is thrown`() = runTest {
      locationsApi.stubPatchNonResidentialLocationWithError(uuid, status = 503)

      assertThrows<WebClientResponseException.ServiceUnavailable> {
        locationsApiService.patchNonResidentialLocation(LOCATION_ID, p)
      }
    }
  }
}
