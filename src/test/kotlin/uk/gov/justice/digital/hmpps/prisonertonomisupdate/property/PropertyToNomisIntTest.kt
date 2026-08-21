package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.PropertyDpsApiExtension.Companion.propertyDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

class PropertyToNomisIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var propertyNomisApiMockServer: PropertyNomisApiMockServer

  @Autowired
  private lateinit var propertyMappingApiMockServer: PropertyMappingApiMockServer

  @Nested
  @DisplayName("prison-property.container.created")
  inner class PropertyCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Property create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishPropertyDomainEvent("prison-property.container.created", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the create`() {
        verify(telemetryClient).trackEvent(
          eq("property-create-ignored"),
          check {
            assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
            assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
            assertThat(it).containsEntry("source", "NOMIS")
          },
          isNull(),
        )
      }

      @Test
      fun `will not try to create the Property in NOMIS`() {
        propertyNomisApiMockServer.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Property create")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setup() {
          propertyNomisApiMockServer.stubPostProperty(NOMIS_ID, BOOKING_ID)
          propertyDpsApi.stubGetProperty(DPS_ID, dpsProperty(UUID.fromString(DPS_ID), UUID.fromString(DPS_LOCATION_ID)))
          propertyMappingApiMockServer.stubPostMapping()
          mappingServer.stubGetMappingGivenDpsLocationId(
            DPS_LOCATION_ID,
            """{
            "dpsLocationId": "$DPS_LOCATION_ID",
            "nomisLocationId": $NOMIS_LOCATION_ID,
            "mappingType": "MIGRATED"
          }
          """,
          )
          publishPropertyDomainEvent("prison-property.container.created")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will correctly create the Property in NOMIS`() {
          propertyNomisApiMockServer.verify(
            1,
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("$.offenderNo", OFFENDER_NO)
              .withRequestBodyJsonPath("$.prisonId", "MDI")
              .withRequestBodyJsonPath("$.active", true)
              .withRequestBodyJsonPath("$.sealMark", "SEAL1234")
              .withRequestBodyJsonPath("$.containerCode", "VALU")
              .withRequestBodyJsonPath("$.internalLocationId", NOMIS_LOCATION_ID)
              .withRequestBodyJsonPath("$.proposedDisposalDate", "2026-03-04"),
          )
        }

        @Test
        fun `will create the mapping in the mapping service`() {
          propertyMappingApiMockServer.verify(1, postRequestedFor(anyUrl()))
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("property-create-success"),
            check {
              assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
              assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
              assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
              assertThat(it).containsEntry("mappingType", "DPS_CREATED")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class ErrorScenarios {

        @BeforeEach
        fun setup() {
          propertyNomisApiMockServer.stubPostProperty(NOMIS_ID, BOOKING_ID)
          propertyDpsApi.stubGetProperty(DPS_ID, dpsProperty(UUID.fromString(DPS_ID), UUID.fromString(DPS_LOCATION_ID)))
          mappingServer.stubGetMappingGivenDpsLocationId(
            DPS_LOCATION_ID,
            """{
            "dpsLocationId": "$DPS_LOCATION_ID",
            "nomisLocationId": $NOMIS_LOCATION_ID,
            "mappingType": "MIGRATED"
          }
          """,
          )
        }

        @Test
        fun `mapping temporary error`() {
          propertyMappingApiMockServer.stubPostMappingFollowedBySuccess(HttpStatus.SERVICE_UNAVAILABLE)
          publishPropertyDomainEvent("prison-property.container.created")
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("property-create-mapping-retry"),
              check {
                assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
                assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
                assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("property-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
                assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
                assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
                assertThat(it).containsEntry("reason", "503 Service Unavailable from POST http://localhost:8084/mapping/property")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("property-mapping-create-success"),
              check {
                assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
                assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
                assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            propertyMappingApiMockServer.verify(2, postRequestedFor(anyUrl()))
          }
        }

        @Test
        fun `mapping permanent error`() {
          propertyMappingApiMockServer.stubPostMapping(HttpStatus.SERVICE_UNAVAILABLE)
          publishPropertyDomainEvent("prison-property.container.created")
          await untilAsserted {
            propertyMappingApiMockServer.verify(3, postRequestedFor(anyUrl()))
            verify(telemetryClient).trackEvent(
              eq("property-create-mapping-retry"),
              check {
                assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
                assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
                assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              },
              isNull(),
            )
            verify(telemetryClient).trackEvent(
              eq("property-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
                assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
                assertThat(it).containsEntry("locationId", "$NOMIS_LOCATION_ID")
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                assertThat(it).containsEntry("bookingId", "$BOOKING_ID")
                assertThat(it).containsEntry("mappingType", "DPS_CREATED")
                assertThat(it).containsEntry("reason", "503 Service Unavailable from POST http://localhost:8084/mapping/property")
              },
              isNull(),
            )
          }
          verify(telemetryClient, never()).trackEvent(
            eq("property-mapping-create-success"),
            anyMap(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("prison-property.container.updated")
  inner class PropertyUpdated {
    @Nested
    @DisplayName("when NOMIS is the origin of a Property update")
    inner class WhenNomisUpdated {
      @BeforeEach
      fun setup() {
        publishPropertyDomainEvent("prison-property.container.updated", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("property-update-ignored"),
          check {
            assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
            assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
            assertThat(it).containsEntry("source", "NOMIS")
          },
          isNull(),
        )
      }

      @Test
      fun `will not try to update the Property in NOMIS`() {
        propertyNomisApiMockServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when no Nomis-mapped field was updated")
    inner class WhenNoRelevantFieldUpdated {
      @BeforeEach
      fun setup() {
        publishPropertyDomainEvent("prison-property.container.updated", source = "DPS", changedFields = listOf("otherField"))
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("property-update-ignored"),
          check {
            assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
            assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
            assertThat(it).containsEntry("source", "DPS")
            assertThat(it).containsEntry("changedFields", "[otherField]")
          },
          isNull(),
        )
      }

      @Test
      fun `will not try to update the Property in NOMIS`() {
        propertyNomisApiMockServer.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Property update")
    inner class WhenDpsUpdated {
      @BeforeEach
      fun setup() {
        propertyMappingApiMockServer.stubGetByDpsId(
          DPS_ID,
          PropertyContainerMappingDto(
            nomisPropertyContainerId = NOMIS_ID,
            dpsPropertyContainerId = DPS_ID,
            bookingId = BOOKING_ID,
            offenderNo = OFFENDER_NO,
            mappingType = PropertyContainerMappingDto.MappingType.DPS_CREATED,
          ),
        )
        mappingServer.stubGetMappingGivenDpsLocationId(
          DPS_LOCATION_ID,
          """{
            "dpsLocationId": "$DPS_LOCATION_ID",
            "nomisLocationId": $NOMIS_LOCATION_ID,
            "mappingType": "MIGRATED"
          }
          """,
        )
        propertyNomisApiMockServer.stubPutProperty(NOMIS_ID)
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setup() {
          propertyDpsApi.stubGetProperty(DPS_ID, dpsProperty(UUID.fromString(DPS_ID), UUID.fromString(DPS_LOCATION_ID), null))

          publishPropertyDomainEvent("prison-property.container.updated", changedFields = listOf("sealNumber"))
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will correctly update the Property in NOMIS`() {
          propertyNomisApiMockServer.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("$.sealMark", "SEAL1234")
              .withRequestBodyJsonPath("$.containerCode", "VALU")
              .withRequestBodyJsonPath("$.internalLocationId", NOMIS_LOCATION_ID)
              .withRequestBodyJsonPath("$.proposedDisposalDate", "2026-03-04")
              .withRequestBodyJsonPath("$.active", "true")
              .withRequestBodyJsonPath("$.expiryDate", "2026-01-02"),
          )
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("property-update-success"),
            check {
              assertThat(it).containsEntry("dpsPropertyContainerId", DPS_ID)
              assertThat(it).containsEntry("nomisPropertyContainerId", "$NOMIS_ID")
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("source", "DPS")
              assertThat(it).containsEntry("changedFields", "[sealNumber]")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPathPropertyRemoved {
        @BeforeEach
        fun setup() {
          propertyDpsApi.stubGetProperty(DPS_ID, dpsProperty(UUID.fromString(DPS_ID), UUID.fromString(DPS_LOCATION_ID), PropertyContainerDto.RemovalOutcome.DISPOSED))

          publishPropertyDomainEvent("prison-property.container.updated", changedFields = listOf("sealNumber"))
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will correctly update the Property in NOMIS`() {
          propertyNomisApiMockServer.verify(
            putRequestedFor(anyUrl())
              .withRequestBody(
                equalToJson(
                  """{
                     "internalLocationId": null,
                     "containerCode": "VALU",
                     "active":false,
                     "sealMark":"SEAL1234",
                     "proposedDisposalDate":"2026-03-04",
                     "expiryDate":"2026-01-02"
                  }
                  """,
                ),
              ),
          )
        }
      }
    }
  }

  private fun publishPropertyDomainEvent(
    eventType: String,
    offenderNo: String = OFFENDER_NO,
    dpsId: String = DPS_ID,
    source: String = "DPS",
    changedFields: List<String>? = null,
  ) {
    val event = PropertyDomainEvent(
      eventType = eventType,
      version = "1",
      description = "A prisoner property container was changed in DPS",
      detailUrl = null,
      occurredAt = "2026-07-01T00:00:00Z",
      prisonerNumber = offenderNo,
      source = source,
      additionalInformation = PropertyDomainAdditionalInformation(
        dpsId = dpsId,
        nomisPropertyContainerId = null,
        changedFields = changedFields,
      ),
    )
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(jsonMapper.writeValueAsString(event))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        )
        .build(),
    ).get()
  }
}
