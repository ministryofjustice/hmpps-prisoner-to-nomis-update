package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.github.tomakehurst.wiremock.client.WireMock
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension.Companion.locationsApi

private const val LOCATION_ID = "2475f250-434a-4257-afe7-b911f1773a4d"

@SpringAPIServiceTest
@Import(LocationsApiService::class, LocationsConfiguration::class)
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
        "capacityOfCertifiedCell": 1
      },
      "attributes": ["LOCATE_FLAT", "ELIGIBLE"],
      "orderWithinParentLocation": 1,
      "topLevelId": "57718979-573c-433a-9e51-2d83f887c11c",
      "parentId": "57718979-573c-433a-9e51-2d83f887c11c",
      "key": "MDI-A-1-001",
      "status": "ACTIVE",
      "isResidential": true
    }
  """.trimIndent()

  @Nested
  @DisplayName("GET /locations/{id}")
  inner class GetLocation {
    @BeforeEach
    internal fun setUp() {
      locationsApi.stubGetLocation(LOCATION_ID, false, response)
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      locationsApiService.getLocation(LOCATION_ID)

      locationsApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/locations/$LOCATION_ID?includeHistory=false"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will parse data for a detail field`(): Unit = runTest {
      val location = locationsApiService.getLocation(LOCATION_ID)

      assertThat(location.pathHierarchy).isEqualTo("A-1-001")
      assertThat(location.attributes).containsExactlyInAnyOrder(Location.Attributes.LOCATE_FLAT, Location.Attributes.ELIGIBLE)
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
  }
}
