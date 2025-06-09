package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

class IncidentsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: IncidentsNomisApiMockServer

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
            eq("incident-upsert-ignored"),
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
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,
              ),
            )
            nomisApi.stubUpsertIncident(nomisId)
            publishCreateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("incident-upsert-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the incident created`() {
            verify(telemetryClient).trackEvent(
              eq("incident-upsert-success"),
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
            eq("incident-upsert-ignored"),
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
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,
              ),
            )
            nomisApi.stubUpsertIncident(incidentId = nomisId)
            publishUpdateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("incident-upsert-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the  updated`() {
            verify(telemetryClient).trackEvent(
              eq("incident-upsert-success"),
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
            nomisApi.stubDeleteIncident(nomisId)
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
