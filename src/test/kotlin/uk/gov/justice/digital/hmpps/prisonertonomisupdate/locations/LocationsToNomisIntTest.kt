package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension.Companion.locationsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue

internal const val DPS_ID = "57718979-573c-433a-9e51-2d83f887c11c"
internal const val PARENT_ID = "12345678-573c-433a-9e51-2d83f887c11c"
internal const val NOMIS_ID = 1234567L

class LocationsToNomisIntTest : SqsIntegrationTestBase() {

  private val locationApiResponse = """
    {
      "id": "$DPS_ID",
      "prisonId": "MDI",
      "code": "001",
      "pathHierarchy": "A-1-001",
      "locationType": "CELL",
      "active": true,
      "localName": "Wing A",
      "comments": "Not to be used",
      "capacity": {
        "maxCapacity": 2,
        "workingCapacity": 2,
        "certifiedNormalAccommodation": 1
      },
      "certification": {
        "certified": true,
        "certifiedNormalAccommodation": 1,
        "capacityOfCertifiedCell": 3
      },
      "certifiedCell": true,
      "attributes": [ "FEMALE_SEMI", "NON_SMOKER_CELL" ],
      "usage": [
        { "usageType": "APPOINTMENT", "capacity": 3, "sequence": 1  },
        { "usageType": "VISIT",       "capacity": 3, "sequence": 2  },
        { "usageType": "MOVEMENT",    "sequence": 3  },
        { "usageType": "OCCURRENCE",  "sequence": 4  }
      ],
      "orderWithinParentLocation": 1,
      "topLevelId": "abcdef01-573c-433a-9e51-2d83f887c11c",
      "parentId": "$PARENT_ID",
      "key": "MDI-A-1-001",
      "status": "ACTIVE",
      "isResidential": true,
      "internalMovementAllowed": false,
      "lastModifiedBy": "me",
      "lastModifiedDate": "2024-05-25T01:02:03"
    }
  """.trimIndent()

  private fun locationApiResponseDeactivated(permanentlyDeactivated: Boolean = false) = """
    {
      "id": "$DPS_ID",
      "prisonId": "MDI",
      "code": "001",
      "pathHierarchy": "A-1-001",
      "locationType": "CELL",
      "active": false,
      "orderWithinParentLocation": 1,
      "topLevelId": "abcdef01-573c-433a-9e51-2d83f887c11c",
      "key": "MDI-A-1-001",
      "status": "INACTIVE",
      "isResidential": true,
      "deactivatedDate": "2024-02-01",
      "deactivatedReason": "MOTHBALLED",
      "proposedReactivationDate": "2024-02-14",
      "permanentlyDeactivated": $permanentlyDeactivated,
      "lastModifiedBy": "me",
      "lastModifiedDate": "2024-05-25T01:02:03"
    }
  """.trimIndent()

  private val locationMappingResponse = """
    {
      "dpsLocationId": "$DPS_ID",
      "nomisLocationId": $NOMIS_ID,
      "mappingType": "LOCATION_CREATED"
    }
  """.trimIndent()

  private val parentMappingResponse = """
    {
      "dpsLocationId": "$PARENT_ID",
      "nomisLocationId": 12345678,
      "mappingType": "LOCATION_CREATED"
    }
  """.trimIndent()

  @Nested
  inner class Create {
    @Nested
    inner class WhenLocationHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        nomisApi.stubLocationCreate("""{ "locationId": $NOMIS_ID }""")
        mappingServer.stubGetMappingGivenDpsLocationIdWithError(DPS_ID, 404)
        mappingServer.stubGetMappingGivenDpsLocationId(PARENT_ID, parentMappingResponse)
        mappingServer.stubCreateLocation()
        publishLocationDomainEvent("location.inside.prison.created")
      }

      @Test
      fun `will callback back to location service to get more details`() {
        waitForCreateProcessingToBeComplete()

        locationsApi.verify(getRequestedFor(urlEqualTo("/sync/id/$DPS_ID?includeHistory=false")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("location-create-success"),
          org.mockito.kotlin.check {
            assertThat(it["dpsLocationId"]).isEqualTo(DPS_ID)
            assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_ID.toString())
            assertThat(it["key"]).isEqualTo("MDI-A-1-001")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the location`() {
        waitForCreateProcessingToBeComplete()

        nomisApi.verify(
          postRequestedFor(urlEqualTo("/locations"))
            .withRequestBody(
              equalToJson(
                """
                {
                  "certified" : true,
                  "locationType" : "CELL",
                  "prisonId" : "MDI",
                  "locationCode" : "001",
                  "description" : "MDI-A-1-001",
                  "parentLocationId" : 12345678,
                  "operationalCapacity" : 2,
                  "cnaCapacity" : 1,
                  "userDescription" : "Wing A",
                  "unitType" : null,
                  "capacity" : 2,
                  "listSequence" : 1,
                  "comment" : "Not to be used",
                  "tracking" : false,
                  "active" : true,
                  "profiles" : [
                    { "profileType" : "SUP_LVL_TYPE", "profileCode" : "S" },
                    { "profileType" : "HOU_UNIT_ATT", "profileCode" : "NSMC" }
                  ],
                  "usages" : [
                    { "internalLocationUsageType": "APP",      "capacity": 3, "sequence": 1 },
                    { "internalLocationUsageType": "VISIT",    "capacity": 3, "sequence": 2 },
                    { "internalLocationUsageType": "MOVEMENT", "capacity" : null, "sequence": 3 },
                    { "internalLocationUsageType": "OCCUR",    "capacity" : null, "sequence": 4 }
                  ]
                }
                """.trimIndent(),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping`() {
        waitForCreateProcessingToBeComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/locations"))
              .withRequestBody(matchingJsonPath("dpsLocationId", equalTo(DPS_ID)))
              .withRequestBody(matchingJsonPath("nomisLocationId", equalTo(NOMIS_ID.toString()))),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class ValidationErrors {

      @Test
      fun `attribute is invalid`() {
        val locationApiResponse = """
          {
            "id": "$DPS_ID",
            "prisonId": "MDI",
            "code": "001",
            "pathHierarchy": "A-1-001",
            "locationType": "CELL",
            "active": true,
            "topLevelId": "abcdef01-573c-433a-9e51-2d83f887c11c",
            "parentId": "$PARENT_ID",
            "key": "MDI-A-1-001",
            "isResidential": true,
            "attributes": [ "INVALID" ]
          }
        """.trimIndent()

        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        publishLocationDomainEvent("location.inside.prison.created")

        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("location-create-failed"),
            org.mockito.kotlin.check {
              assertThat(it["dpsLocationId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisLocationId"]).isNull()
              assertThat(it["key"]).isEqualTo("MDI-A-1-001")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `usage is invalid`() {
        val locationApiResponse = """
          {
            "id": "$DPS_ID",
            "prisonId": "MDI",
            "code": "001",
            "pathHierarchy": "A-1-001",
            "locationType": "CELL",
            "active": true,
            "topLevelId": "abcdef01-573c-433a-9e51-2d83f887c11c",
            "parentId": "$PARENT_ID",
            "key": "MDI-A-1-001",
            "isResidential": true,
            "usage": [{ "usageType": "INVALID", "capacity": 3, "sequence": 1 }]
          }
        """.trimIndent()

        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        publishLocationDomainEvent("location.inside.prison.created")

        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("location-create-failed"),
            org.mockito.kotlin.check {
              assertThat(it["dpsLocationId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisLocationId"]).isNull()
              assertThat(it["key"]).isEqualTo("MDI-A-1-001")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForLocation {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        publishLocationDomainEvent("location.inside.prison.created")
      }

      @Test
      fun `will not create an location in NOMIS`() {
        waitForCreateProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("location-create-duplicate"),
          org.mockito.kotlin.check {
            assertThat(it["dpsLocationId"]).isEqualTo(DPS_ID)
          },
          isNull(),
        )

        nomisApi.verify(
          0,
          postRequestedFor(urlEqualTo("/locations")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenDpsLocationIdWithError(DPS_ID, 404)
        mappingServer.stubGetMappingGivenDpsLocationId(PARENT_ID, parentMappingResponse)
        mappingServer.stubCreateLocationWithErrorFollowedBySlowSuccess()
        nomisApi.stubLocationCreate("""{ "locationId": $NOMIS_ID }""")
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        publishLocationDomainEvent("location.inside.prison.created")

        await untilCallTo { locationsApi.getCountFor("/sync/id/$DPS_ID?includeHistory=false") } matches { it == 1 }
        await untilCallTo { nomisApi.postCountFor("/locations") } matches { it == 1 }
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
        nomisApi.verify(
          1,
          postRequestedFor(urlEqualTo("/locations")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS location is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/locations"))
              .withRequestBody(matchingJsonPath("dpsLocationId", equalTo(DPS_ID)))
              .withRequestBody(matchingJsonPath("nomisLocationId", equalTo(NOMIS_ID.toString()))),
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
  inner class Update {
    @Nested
    inner class WhenLocationHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        mappingServer.stubGetMappingGivenDpsLocationId(PARENT_ID, parentMappingResponse)
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID")
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/capacity?ignoreOperationalCapacity=false")
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/certification")
        publishLocationDomainEvent("location.inside.prison.amended")
      }

      @Test
      fun `will callback back to location service to get more details`() {
        await untilAsserted {
          locationsApi.verify(getRequestedFor(urlEqualTo("/sync/id/$DPS_ID?includeHistory=false")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("location-amend-success"),
            org.mockito.kotlin.check {
              assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
              assertThat(it["key"]).isEqualTo("MDI-A-1-001")
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to update the location`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID")))
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/capacity?ignoreOperationalCapacity=false")))
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/certification")))
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocationWithErrorFollowedBySlowSuccess(
            id = DPS_ID,
            includeHistory = false,
            response = locationApiResponse,
          )
          mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
          mappingServer.stubGetMappingGivenDpsLocationId(PARENT_ID, parentMappingResponse)
          nomisApi.stubLocationUpdate("/locations/$NOMIS_ID")
          nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/capacity?ignoreOperationalCapacity=false")
          nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/certification")
          publishLocationDomainEvent("location.inside.prison.amended")
        }

        @Test
        fun `will callback back to location service twice to get more details`() {
          await untilAsserted {
            locationsApi.verify(2, getRequestedFor(urlEqualTo("/sync/id/$DPS_ID?includeHistory=false")))
            verify(telemetryClient).trackEvent(Mockito.eq("location-amend-success"), any(), isNull())
          }
        }

        @Test
        fun `will eventually update the location in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/locations/$NOMIS_ID")))
            verify(telemetryClient).trackEvent(Mockito.eq("location-amend-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("location-amend-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenServiceKeepsFailing {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocation(id = DPS_ID, false, response = locationApiResponse)
          mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
          mappingServer.stubGetMappingGivenDpsLocationId(PARENT_ID, parentMappingResponse)
          nomisApi.stubLocationUpdateWithError("/locations/$NOMIS_ID", 400)
          publishLocationDomainEvent("location.inside.prison.amended")
        }

        @Test
        fun `will callback back to location service 3 times before given up`() {
          await untilAsserted {
            locationsApi.verify(3, getRequestedFor(urlEqualTo("/sync/id/$DPS_ID?includeHistory=false")))
          }
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("location-amend-failed"),
              org.mockito.kotlin.check {
                assertThat(it["dpsId"]).isEqualTo(DPS_ID)
                assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will add message to dead letter queue`() {
          await untilCallTo {
            awsSqsLocationDlqClient!!.countAllMessagesOnQueue(locationDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }
  }

  @Nested
  inner class Deactivate {
    @Nested
    inner class WhenLocationHasBeenDeactivatedInDPS {
      @BeforeEach
      fun setUp() {
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponseDeactivated())
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/deactivate")
        publishLocationDomainEvent("location.inside.prison.deactivated")
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("location-deactivate-success"),
            org.mockito.kotlin.check {
              assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api correctly to deactivate the location`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate"))
              .withRequestBody(matchingJsonPath("reasonCode", equalTo("I")))
              .withRequestBody(matchingJsonPath("reactivateDate", equalTo("2024-02-14")))
              .withRequestBody(matchingJsonPath("deactivateDate", equalTo("2024-02-01")))
              .withRequestBody(matchingJsonPath("force", equalTo("true"))),
          )
        }
      }
    }

    @Nested
    inner class WhenLocationHasBeenPermanentlyDeactivatedInDPS {
      @BeforeEach
      fun setUp() {
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponseDeactivated(permanentlyDeactivated = true))
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/deactivate")
        publishLocationDomainEvent("location.inside.prison.deactivated")
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("location-deactivate-success"),
            org.mockito.kotlin.check {
              assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api correctly to deactivate the location`() {
        await untilAsserted {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate"))
              .withRequestBody(matchingJsonPath("reasonCode", equalTo("I")))
              .withRequestBody(matchingJsonPath("reactivateDate", equalTo("2024-02-14")))
              .withRequestBody(matchingJsonPath("deactivateDate", equalTo("2024-02-01")))
              .withRequestBody(matchingJsonPath("force", equalTo("true"))),
          )
        }
      }
    }

    @Nested
    inner class Exceptions {

      @Nested
      inner class WhenServiceFailsOnce {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
          mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
          nomisApi.stubLocationUpdateWithErrorFollowedBySlowSuccess("/locations/$NOMIS_ID/deactivate")
          publishLocationDomainEvent("location.inside.prison.deactivated")
        }

        @Test
        fun `will eventually deactivate the location in NOMIS`() {
          await untilAsserted {
            nomisApi.verify(2, putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate")))
            verify(telemetryClient).trackEvent(Mockito.eq("location-deactivate-failed"), any(), isNull())
            verify(telemetryClient).trackEvent(Mockito.eq("location-deactivate-success"), any(), isNull())
          }
        }
      }

      @Nested
      inner class WhenNomisFails {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
          mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
          nomisApi.stubLocationUpdateWithError("/locations/$NOMIS_ID/deactivate", 503)
          publishLocationDomainEvent("location.inside.prison.deactivated")
        }

        @Test
        fun `will create failure telemetry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("location-deactivate-failed"),
              org.mockito.kotlin.check {
                assertThat(it["dpsId"]).isEqualTo(DPS_ID)
                assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingNotFound {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
          mappingServer.stubGetMappingGivenDpsLocationIdWithError(DPS_ID, 404)
          nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/deactivate")
          publishLocationDomainEvent("location.inside.prison.deactivated")
        }

        @Test
        fun `will create failure telemetry and not call Nomis`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("location-deactivate-failed"),
              org.mockito.kotlin.check {
                assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              },
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate")),
          )
        }
      }

      @Nested
      inner class WhenLocationApiFails {

        @BeforeEach
        fun setUp() {
          locationsApi.stubGetLocationWithError(DPS_ID, false, 404)
          mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
          nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/deactivate")
          publishLocationDomainEvent("location.inside.prison.deactivated")
        }

        @Test
        fun `will create failure telemetry and not call Nomis`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              Mockito.eq("location-deactivate-failed"),
              org.mockito.kotlin.check {
                assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              },
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate")),
          )
        }
      }
    }
  }

  @Nested
  inner class Reactivate {
    @Nested
    inner class WhenLocationHasBeenReactivatedInDPS {
      @BeforeEach
      fun setUp() {
        locationsApi.stubGetLocation(DPS_ID, false, locationApiResponse)
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/reactivate")
        publishLocationDomainEvent("location.inside.prison.reactivated")
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("location-reactivate-success"),
            org.mockito.kotlin.check {
              assertThat(it["dpsId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisId"]).isEqualTo(NOMIS_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to reactivate the location`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/reactivate")))
        }
      }
    }
  }

  @Nested
  inner class DeleteLocation {
    @Nested
    inner class WhenLocationHasJustBeenDeletedByLocationService {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetMappingGivenDpsLocationId(DPS_ID, locationMappingResponse)
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/deactivate")
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/capacity?ignoreOperationalCapacity=false")
        nomisApi.stubLocationUpdate("/locations/$NOMIS_ID/certification")
        mappingServer.stubDeleteLocationMapping(DPS_ID)
        publishLocationDomainEvent("location.inside.prison.deleted")
      }

      @Test
      fun `will delete the location in NOMIS`() {
        await untilAsserted {
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/deactivate")))
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/capacity?ignoreOperationalCapacity=false")))
          nomisApi.verify(putRequestedFor(urlEqualTo("/locations/$NOMIS_ID/certification")))
        }
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            Mockito.eq("location-delete-success"),
            org.mockito.kotlin.check {
              assertThat(it["dpsLocationId"]).isEqualTo(DPS_ID)
              assertThat(it["nomisLocationId"]).isEqualTo(NOMIS_ID.toString())
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

  private fun locationMessagePayload(id: String, eventType: String) = """{"eventType":"$eventType", "additionalInformation": {"id":"$id", "key":"MDI-A-1-001"}, "version": "1.0", "description": "description", "occurredAt": "2024-04-02T10:58:01.531693442+01:00"}"""
}
