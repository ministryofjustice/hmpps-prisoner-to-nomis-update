package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

private const val DPS_ID = "57718979-573c-433a-9e51-2d83f887c11c"
private const val NOMIS_ID = 1234567L

class LocationsToNomisIntTest : SqsIntegrationTestBase() {

  val locationApiResponse = """
    {
      "id": "$DPS_ID",
      "prisonId": "MDI",
      "code": "001",
      "pathHierarchy": "A-1-001",
      "locationType": "CELL",
      "active": true,
      "description": "Wing A",
      "comments": "Not to be used",
      "capacity": {
        "capacity": 2,
        "operationalCapacity": 2
      },
      "certification": {
        "certified": true,
        "capacityOfCertifiedCell": 1
      },
      "orderWithinParentLocation": 1,
      "topLevelId": "57718979-573c-433a-9e51-2d83f887c11c",
      "parentId": "57718979-573c-433a-9e51-2d83f887c11c",
      "key": "MDI-A-1-001",
      "isResidential": true
    }
  """.trimIndent()

  val locationMappingResponse = """
    {
      "dpsLocationId": "$DPS_ID",
      "nomisLocationId": $NOMIS_ID,
      "mappingType": "LOCATION_CREATED"
    }
  """.trimIndent()

  @Nested
  inner class Create {
    @Nested
    inner class WhenLocationHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        LocationsApiExtension.locationsApi.stubGetLocation(DPS_ID, locationApiResponse)
        NomisApiExtension.nomisApi.stubLocationCreate("""{ "locationId": $NOMIS_ID }""")
        MappingExtension.mappingServer.stubGetMappingGivenDpsLocationIdWithError(DPS_ID, 404)
        MappingExtension.mappingServer.stubCreateLocation()
        publishLocationDomainEvent("location.inside.prison.created")
      }

      @Test
      fun `will callback back to location service to get more details`() {
        waitForCreateProcessingToBeComplete()

        LocationsApiExtension.locationsApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/locations/$DPS_ID")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("location-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsId"]).isEqualTo("$DPS_ID")
            Assertions.assertThat(it["key"]).isEqualTo("MDI-A-1-001")
            Assertions.assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the location`() {
        waitForCreateProcessingToBeComplete()

        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/locations")))
      }

      @Test
      fun `will create a mapping`() {
        waitForCreateProcessingToBeComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
              WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/locations"))
                  .withRequestBody(WireMock.matchingJsonPath("dpsLocationId", WireMock.equalTo(DPS_ID)))
                  .withRequestBody(WireMock.matchingJsonPath("nomisLocationId", WireMock.equalTo(NOMIS_ID.toString()))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForLocation {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        publishLocationDomainEvent("location.inside.prison.created")
      }

      @Test
      fun `will not create an location in NOMIS`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("location-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsId"]).isEqualTo(DPS_ID)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/locations")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenDpsLocationIdWithError(DPS_ID, 404)
        MappingExtension.mappingServer.stubCreateLocationWithErrorFollowedBySlowSuccess()
        NomisApiExtension.nomisApi.stubLocationCreate("""{ "locationId": $NOMIS_ID }""")
        LocationsApiExtension.locationsApi.stubGetLocation(DPS_ID, locationApiResponse)
        publishLocationDomainEvent("location.inside.prison.created")

        await untilCallTo { LocationsApiExtension.locationsApi.getCountFor("/locations/$DPS_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/locations") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS location once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("location-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/locations")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS location is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/locations"))
              .withRequestBody(WireMock.matchingJsonPath("dpsLocationId", WireMock.equalTo(DPS_ID)))
              .withRequestBody(WireMock.matchingJsonPath("nomisLocationId", WireMock.equalTo(NOMIS_ID.toString()))),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("location-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    private fun waitForCreateProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteLocation {
    @Nested
    inner class WhenLocationHasJustBeenDeletedByLocationService {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        NomisApiExtension.nomisApi.stubLocationDelete(NOMIS_ID)
        MappingExtension.mappingServer.stubDeleteLocationMapping(DPS_ID)
        publishLocationDomainEvent("location.inside.prison.deleted")
      }

      @Test
      fun `will delete the location in NOMIS`() {
        await untilAsserted {
          NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/locations/$NOMIS_ID")))
        }
      }

      @Test
      fun `will delete the mapping`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/locations/dps/$DPS_ID")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("location-delete-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              Assertions.assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishLocationDomainEvent(eventType: String) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(locationMessagePayload(DPS_ID, eventType))
        .messageAttributes(
          mapOf("eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build()),
        ).build(),
    ).get()
  }

  fun locationMessagePayload(id: String, eventType: String) =
    """{"eventType":"$eventType", "additionalInformation": {"id":"$id", "key":"MDI-A-1-001"}, "version": "1.0", "description": "description", "occurredAt": "2024-02-01T17:09:56.0"}"""
}
