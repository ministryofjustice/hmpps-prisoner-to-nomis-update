package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Status
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.UUID

class IncidentsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var incidentsNomisApi: IncidentsNomisApiMockServer

  private val nomisApi = NomisApiExtension.nomisApi
  private val dpsApi = IncidentsDpsApiExtension.incidentsDpsApi

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
          nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
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
      @DisplayName("when the incident status from DPS is to be ignored")
      inner class WhenDpsIgnoredStatus {
        private val dpsId = UUID.randomUUID()
        private val nomisId = 12345L

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetIncident(
            dpsIncident().copy(
              id = dpsId,
              status = Status.DRAFT,
            ),
          )
          publishCreateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-upsert-status-ignored"),
            check {
              assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
              assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              assertThat(it).containsEntry("status", "DRAFT")
            },
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

          @BeforeEach
          fun setUp() {
            dpsApi.stubGetIncident(
              dpsIncident().copy(
                id = dpsId,

                correctionRequests = listOf(
                  CorrectionRequest(
                    sequence = 0,
                    descriptionOfChange = "There was a change",
                    correctionRequestedBy = "Fred Black",
                    correctionRequestedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
                    location = "MDI",
                  ),
                  CorrectionRequest(
                    sequence = 1,
                    descriptionOfChange = "Data warden request",
                    correctionRequestedBy = "Jim Blue",
                    correctionRequestedAt = LocalDateTime.parse("2021-07-08T10:35:17"),
                    userType = CorrectionRequest.UserType.DATA_WARDEN,
                  ),
                  CorrectionRequest(
                    sequence = 2,
                    descriptionOfChange = "Reporting Officer request",
                    correctionRequestedBy = "Bob Green",
                    correctionRequestedAt = LocalDateTime.parse("2021-07-12T10:35:17"),
                    userType = CorrectionRequest.UserType.REPORTING_OFFICER,
                  ),
                ),
              ),

            )
            incidentsNomisApi.stubUpsertIncident(nomisId)
            nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
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
            incidentsNomisApi.verify(putRequestedFor(urlEqualTo("/incidents/$nomisId")))
          }

          @Test
          fun `the created incident will contain details of the DPS incident`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("location", "ASI")
                .withRequestBodyJsonPath("statusCode", "AWAN")
                .withRequestBodyJsonPath("typeCode", "ATT_ESC_E")
                .withRequestBodyJsonPath("incidentDateTime", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("reportedDateTime", "2021-07-07T10:35:17.12345")
                .withRequestBodyJsonPath("reportedBy", "FSTAFF_GEN")
                .withRequestBodyJsonPath("title", "There was an incident in the exercise yard")
                .withRequestBodyJsonPath("description", "Fred and Jimmy were fighting outside."),
            )
          }

          @Test
          fun `the created incident will contain details of the DPS amendment`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("descriptionAmendments[0].text", "There was an amendment")
                .withRequestBodyJsonPath("descriptionAmendments[0].firstName", "Dave")
                .withRequestBodyJsonPath("descriptionAmendments[0].lastName", "Jones")
                .withRequestBodyJsonPath("descriptionAmendments[0].createdDateTime", "2021-07-05T10:35:17"),
            )
          }

          @Test
          fun `the created incident will contain details of the DPS correction requests`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("requirements[0].comment", "There was a change")
                .withRequestBodyJsonPath("requirements[0].username", "Fred Black")
                .withRequestBodyJsonPath("requirements[0].date", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("requirements[0].location", "MDI")
                .withRequestBodyJsonPath("requirements.length()", "3"),
            )
          }

          @Test
          fun `the created incident will contain the correct location for DPS correction request with userType DATA_WARDEN`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("requirements[1].comment", "Data warden request")
                .withRequestBodyJsonPath("requirements[1].username", "Jim Blue")
                .withRequestBodyJsonPath("requirements[1].date", "2021-07-08T10:35:17")
                .withRequestBodyJsonPath("requirements[1].location", "NOU"),
            )
          }

          @Test
          fun `the created incident will contain the correct location for DPS correction request with userType REPORTING_OFFICER`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("requirements[2].comment", "Reporting Officer request")
                .withRequestBodyJsonPath("requirements[2].username", "Bob Green")
                .withRequestBodyJsonPath("requirements[2].date", "2021-07-12T10:35:17")
                .withRequestBodyJsonPath("requirements[2].location", "ASI"),
            )
          }

          @Test
          fun `the created incident will contain details of the DPS prisoners involved`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("offenderParties[0].comment", "There were issues")
                .withRequestBodyJsonPath("offenderParties[0].prisonNumber", "A1234BC")
                .withRequestBodyJsonPath("offenderParties[0].role", "ABS")
                .withRequestBodyJsonPath("offenderParties[0].outcome", "POR")
                .withRequestBody(matchingJsonPath("offenderParties[1].comment", absent()))
                .withRequestBodyJsonPath("offenderParties[1].prisonNumber", "A1234BD")
                .withRequestBodyJsonPath("offenderParties[1].role", "FIGHT")
                .withRequestBodyJsonPath("offenderParties[1].outcome", "IPOL")
                .withRequestBodyJsonPath("offenderParties.length()", "2"),
            )
          }

          @Test
          fun `the created incident will contain details of the DPS staff involved`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("staffParties[0].comment", "Dave was hit")
                .withRequestBodyJsonPath("staffParties[0].username", "DJONES")
                .withRequestBodyJsonPath("staffParties[0].role", "AI")
                .withRequestBodyJsonPath("staffParties.length()", "2"),
            )
          }

          @Test
          fun `the created incident will contain details of the questions`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("questions[0].questionId", "1234")
                .withRequestBodyJsonPath("questions[1].questionId", "12345")
                .withRequestBodyJsonPath("questions.length()", "2"),
            )
          }

          @Test
          fun `the created incident will contain details of the responses`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("questions[0].questionId", "1234")
                .withRequestBodyJsonPath("questions[0].responses[0].answerId", "123")
                .withRequestBodyJsonPath("questions[0].responses[0].sequence", 0)
                .withRequestBodyJsonPath("questions[0].responses[0].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[0].responses[0].responseDate", "2021-06-05")
                .withRequestBody(matchingJsonPath("questions[0].responses[0].comment", absent()))
                .withRequestBodyJsonPath("questions[1].questionId", "12345")
                .withRequestBodyJsonPath("questions[1].responses[0].answerId", "456")
                .withRequestBodyJsonPath("questions[1].responses[0].sequence", 1)
                .withRequestBodyJsonPath("questions[1].responses[0].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[1].responses[0].comment", "some comment")
                .withRequestBody(matchingJsonPath("questions[1].responses[0].responseDate", absent()))
                .withRequestBodyJsonPath("questions[1].responses[1].answerId", "789")
                .withRequestBodyJsonPath("questions[1].responses[1].sequence", 2)
                .withRequestBodyJsonPath("questions[1].responses[1].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[1].responses[1].comment", "some additional comment")
                .withRequestBody(matchingJsonPath("questions[1].responses[1].responseDate", absent())),
            )
          }

          @Test
          fun `the created incident will contain details of the history`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("history[0].typeCode", "ABSCOND")
                .withRequestBodyJsonPath("history[0].incidentChangeDateTime", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("history[0].incidentChangeUsername", "JSMITH")
                .withRequestBodyJsonPath("history[0].questions[0].questionId", "998")
                .withRequestBodyJsonPath("history[0].questions[0].responses[0].answerId", "123")
                .withRequestBodyJsonPath("history[0].questions[0].responses[0].sequence", 0)
                .withRequestBodyJsonPath("history[0].questions[0].responses[0].recordingUsername", "Fred Jones")
                .withRequestBodyJsonPath("history[0].questions[0].responses[0].comment", "more info")
                .withRequestBody(matchingJsonPath("history[0].questions[0].responses[0].responseDate", absent()))
                .withRequestBodyJsonPath("history[0].questions[1].questionId", "999")
                .withRequestBodyJsonPath("history[0].questions[1].responses[0].answerId", "456")
                .withRequestBodyJsonPath("history[0].questions[1].responses[0].sequence", 1)
                .withRequestBodyJsonPath("history[0].questions[1].responses[0].recordingUsername", "Fred Jones")
                .withRequestBody(matchingJsonPath("history[0].questions[1].responses[0].comment", absent()))
                .withRequestBody(matchingJsonPath("history[0].questions[1].responses[0].responseDate", absent())),
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
      @DisplayName("when the incident status from DPS is to be ignored")
      inner class WhenDpsIgnoredStatus {
        private val dpsId = UUID.randomUUID()
        private val nomisId = 12345L

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetIncident(
            dpsIncident().copy(
              id = dpsId,
              status = Status.DRAFT,
            ),
          )
          nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
          publishUpdateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-upsert-status-ignored"),
            check {
              assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
              assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              assertThat(it).containsEntry("status", "DRAFT")
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when the incident location from DPS is to be ignored")
      inner class WhenAgencyLocationNotEnabled {
        private val dpsId = UUID.randomUUID()
        private val nomisId = 12345L

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetIncident(
            dpsIncident().copy(
              id = dpsId,
              status = Status.AWAITING_REVIEW,
            ),
          )
          nomisApi.stubCheckAgencySwitchForAgencyNotFound("INCIDENTS", "ASI")
          publishUpdateIncidentDomainEvent(dpsId = dpsId.toString(), nomisId = nomisId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("incident-upsert-location-ignored"),
            check {
              assertThat(it).containsEntry("dpsIncidentId", dpsId.toString())
              assertThat(it).containsEntry("nomisIncidentId", nomisId.toString())
              assertThat(it).containsEntry("location", "ASI")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get incident details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/incident-reports/$dpsId/with-details")))
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
            incidentsNomisApi.stubUpsertIncident(incidentId = nomisId)
            nomisApi.stubCheckAgencySwitchForAgency("INCIDENTS", "ASI")
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
          fun `telemetry will contain key facts about the incident updated`() {
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
            incidentsNomisApi.verify(putRequestedFor(urlEqualTo("/incidents/$nomisId")))
          }

          @Test
          fun `the updated incident will contain details of the DPS incident`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("location", "ASI")
                .withRequestBodyJsonPath("statusCode", "AWAN")
                .withRequestBodyJsonPath("typeCode", "ATT_ESC_E")
                .withRequestBodyJsonPath("incidentDateTime", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("reportedDateTime", "2021-07-07T10:35:17.12345")
                .withRequestBodyJsonPath("reportedBy", "FSTAFF_GEN")
                .withRequestBodyJsonPath("title", "There was an incident in the exercise yard")
                .withRequestBodyJsonPath("description", "Fred and Jimmy were fighting outside."),
            )
          }

          @Test
          fun `the updated incident will contain details of the DPS correction request`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("requirements[0].comment", "There was a change")
                .withRequestBodyJsonPath("requirements[0].username", "Fred Black")
                .withRequestBodyJsonPath("requirements[0].date", "2021-07-05T10:35:17")
                .withRequestBodyJsonPath("requirements[0].location", "MDI")
                .withRequestBodyJsonPath("requirements.length()", "1"),
            )
          }

          @Test
          fun `the updated incident will contain details of the DPS prisoners involved`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("offenderParties[0].comment", "There were issues")
                .withRequestBodyJsonPath("offenderParties[0].prisonNumber", "A1234BC")
                .withRequestBodyJsonPath("offenderParties[0].role", "ABS")
                .withRequestBodyJsonPath("offenderParties[0].outcome", "POR")
                .withRequestBody(matchingJsonPath("offenderParties[1].comment", absent()))
                .withRequestBodyJsonPath("offenderParties[1].prisonNumber", "A1234BD")
                .withRequestBodyJsonPath("offenderParties[1].role", "FIGHT")
                .withRequestBodyJsonPath("offenderParties[1].outcome", "IPOL")
                .withRequestBodyJsonPath("offenderParties.length()", "2"),
            )
          }

          @Test
          fun `the updated incident will contain details of the DPS staff involved`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("staffParties[0].comment", "Dave was hit")
                .withRequestBodyJsonPath("staffParties[0].username", "DJONES")
                .withRequestBodyJsonPath("staffParties[0].role", "AI")
                .withRequestBodyJsonPath("staffParties.length()", "2"),
            )
          }

          @Test
          fun `the updated incident will contain details of the questions`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("questions[0].questionId", "1234")
                .withRequestBodyJsonPath("questions[1].questionId", "12345")
                .withRequestBodyJsonPath("questions.length()", "2"),
            )
          }

          @Test
          fun `the updated incident will contain details of the responses`() {
            incidentsNomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("questions[0].questionId", "1234")
                .withRequestBodyJsonPath("questions[0].responses[0].answerId", "123")
                .withRequestBodyJsonPath("questions[0].responses[0].sequence", 0)
                .withRequestBodyJsonPath("questions[0].responses[0].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[0].responses[0].responseDate", "2021-06-05")
                .withRequestBody(matchingJsonPath("questions[0].responses[0].comment", absent()))
                .withRequestBodyJsonPath("questions[1].questionId", "12345")
                .withRequestBodyJsonPath("questions[1].responses[0].answerId", "456")
                .withRequestBodyJsonPath("questions[1].responses[0].sequence", 1)
                .withRequestBodyJsonPath("questions[1].responses[0].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[1].responses[0].comment", "some comment")
                .withRequestBody(matchingJsonPath("questions[1].responses[0].responseDate", absent()))
                .withRequestBodyJsonPath("questions[1].responses[1].answerId", "789")
                .withRequestBodyJsonPath("questions[1].responses[1].sequence", 2)
                .withRequestBodyJsonPath("questions[1].responses[1].recordingUsername", "JSMITH")
                .withRequestBodyJsonPath("questions[1].responses[1].comment", "some additional comment")
                .withRequestBody(matchingJsonPath("questions[1].responses[1].responseDate", absent())),
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
            incidentsNomisApi.stubDeleteIncident(nomisId)
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
            incidentsNomisApi.verify(deleteRequestedFor(urlEqualTo("/incidents/$nomisId")))
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
