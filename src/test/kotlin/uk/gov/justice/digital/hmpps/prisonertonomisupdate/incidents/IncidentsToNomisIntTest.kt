package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

class IncidentsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: IncidentsNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: IncidentsMappingApiMockServer

  private val dpsApi = IncidentsDpsApiExtension.Companion.incidentsDpsApi

  @Nested
  inner class Incident {
    @Nested
    @DisplayName("incident.report.created")
    inner class IncidentCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an incident create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateIncidentDomainEvent(dpsId = "12345", nomisId = 1234L, source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an incident create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 7654321L
          private val incidentId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentIdOrNull(dpsIncidentId = dpsId.toString(), null)
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,
              ),
            )
            nomisApi.stubCreateIncident(nomisId)
            mappingApi.stubCreateIncidentMapping()
            publishCreateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("incident-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the incident created`() {
            verify(telemetryClient).trackEvent(
              eq("incident-create-success"),
              check {
                assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
                assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get incident details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/incident-reports/$dpsId/with-details")))
          }

          @Test
          fun `will create the incident in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/incidents/$nomisId")))
          }

          @Test
          fun `the created incident will contain details of the DPS incident`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("location", "ASI")
                .withRequestBodyJsonPath("statusCode", "AWAN")
                .withRequestBodyJsonPath("typeCode", "ATT_ESC_E")
                .withRequestBodyJsonPath("incidentDateTime", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("reportedDateTime", "2021-07-07T10:35:17")
                .withRequestBodyJsonPath("reportedBy", "FSTAFF_GEN")
                .withRequestBodyJsonPath("title", "There was an incident in the exercise yard")
                .withRequestBodyJsonPath("description", "Fred and Jimmy were fighting outside."),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/incidents"))
                .withRequestBodyJsonPath("dpsIncidentId", "$dpsId")
                .withRequestBodyJsonPath("nomisIncidentId", nomisId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 1234L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentIdOrNull(dpsIncidentId = dpsId.toString(), null)
            dpsApi.stubGetIncident(dpsIncident().copy(id = dpsId))
            mappingApi.stubCreateIncidentMappingFollowedBySuccess()
            nomisApi.stubCreateIncident(nomisId)
            publishCreateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("incident-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("incident-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the incident in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("incident-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, putRequestedFor(urlEqualTo("/incidents/$nomisId")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 7654321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentIdOrNull(dpsIncidentId = dpsId.toString(), null)
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,
              ),
            )
            nomisApi.stubCreateIncident(incidentId = nomisId)
            mappingApi.stubCreateIncidentMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = IncidentMappingDto(
                    dpsIncidentId = dpsId.toString(),
                    nomisIncidentId = 999999,
                    mappingType = IncidentMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = IncidentMappingDto(
                    dpsIncidentId = dpsId.toString(),
                    nomisIncidentId = nomisId,
                    mappingType = IncidentMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-incident-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the incident in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-incident-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, putRequestedFor(urlEqualTo("/incidents/$nomisId")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("incident.report.amended")
    inner class IncidentUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an incident update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateIncidentDomainEvent(dpsId = "12345", nomisId = 1234L, source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an incident update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 12345L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentId(
              dpsIncidentId = dpsId.toString(),
              IncidentMappingDto(
                dpsIncidentId = dpsId.toString(),
                nomisIncidentId = nomisId,
                mappingType = IncidentMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,
              ),
            )
            nomisApi.stubCreateIncident(incidentId = nomisId)
            publishUpdateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("incident-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the  updated`() {
            verify(telemetryClient).trackEvent(
              eq("incident-update-success"),
              check {
                assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
                assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get incident details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/incident-reports/$dpsId/with-details")))
          }

          @Test
          fun `will update the incident in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/incidents/$nomisId")))
          }

          @Test
          fun `the updated incident will contain details of the DPS incident`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("location", "ASI")
                .withRequestBodyJsonPath("statusCode", "AWAN")
                .withRequestBodyJsonPath("typeCode", "ATT_ESC_E")
                .withRequestBodyJsonPath("incidentDateTime", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("reportedDateTime", "2021-07-07T10:35:17")
                .withRequestBodyJsonPath("reportedBy", "FSTAFF_GEN")
                .withRequestBodyJsonPath("title", "There was an incident in the exercise yard")
                .withRequestBodyJsonPath("description", "Fred and Jimmy were fighting outside."),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("incident.report.deleted")
    inner class IncidentDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an incident delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteIncidentDomainEvent(dpsId = "12345", nomisId = 123L, source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an incident delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentIdOrNull(
              dpsIncidentId = dpsId.toString(),
              IncidentMappingDto(
                dpsIncidentId = dpsId.toString(),
                nomisIncidentId = nomisId,
                mappingType = IncidentMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteIncident(nomisId)
            mappingApi.stubDeleteByDpsIncidentId(dpsIncidentId = dpsId.toString())
            publishDeleteIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("incident-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the incident deleted`() {
            verify(telemetryClient).trackEvent(
              eq("incident-delete-success"),
              check {
                assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
                assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the incident in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/incidents/$nomisId")))
          }

          @Test
          fun `will delete the incident mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/incidents/dps-incident-id/$dpsId")))
          }
        }

        @Nested
        @DisplayName("Incident mapping already deleted")
        inner class MappingMissing {
          private val dpsId = UUID.randomUUID()
          private val nomisId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsIncidentIdOrNull(dpsIncidentId = dpsId.toString(), null)
            publishDeleteIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the incident deleted`() {
            verify(telemetryClient).trackEvent(
              eq("incident-delete-skipped"),
              check {
                assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
                assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateIncidentDomainEvent(dpsId: String, nomisId: Long, source: String = "DPS") {
      with("incident.report.created") {
        publishDomainEvent(
          eventType = this,
          payload = incidentMessagePayload(dpsIncidentId = dpsId, nomisIncidentId = nomisId, eventType = this, source = source),
        )
      }
    }

    private fun publishUpdateIncidentDomainEvent(dpsId: String, nomisId: Long, source: String = "DPS") {
      with("incident.report.amended") {
        publishDomainEvent(
          eventType = this,
          payload = incidentMessagePayload(dpsIncidentId = dpsId, nomisIncidentId = nomisId, eventType = this, source = source),
        )
      }
    }

    private fun publishDeleteIncidentDomainEvent(dpsId: String, nomisId: Long, source: String = "DPS") {
      with("incident.report.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = incidentMessagePayload(
            eventType = this,
            source = source,
            dpsIncidentId = dpsId,
            nomisIncidentId = nomisId,
          ),
        )
      }
    }

    fun incidentMessagePayload(
      eventType: String,
      source: String = "DPS",
      dpsIncidentId: String = "87654",
      nomisIncidentId: Long = 87654L,
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "id": "$dpsIncidentId",
        "reportReference": "$nomisIncidentId",
        "source": "$source"
      }
    }
    """
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}
