package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiMockServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.*

class CourtSchedulerScheduleIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = courtSchedulerDpsApiServer

  @Nested
  @DisplayName("Court appearance upserted")
  inner class CourtAppearanceUpserted {
    private val prisonerNumber = "A1234BC"
    private val dpsCourtAppearanceId = UUID.randomUUID()
    private val nomisEventId = 123L
    private val startTime = LocalDateTime.now()

    @Nested
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMapping()

          publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, "A1234BC")
          waitForAnyProcessingToComplete("court-scheduler-schedule-create-success")
        }

        @Test
        fun `will check for existing mapping`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")))
        }

        @Test
        fun `will get DPS court appearance`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/court-appearances/$dpsCourtAppearanceId")))
        }

        @Test
        fun `will upsert NOMIS court schedule`() {
          NomisApiMockServer.getRequestBody<UpsertCourtScheduleOut>(
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=false")),
          ).also { request ->
            assertThat(request.eventId).isNull()
            assertThat(request.court).isEqualTo("LEEDMC")
            assertThat(request.startTime).isEqualTo(startTime)
            assertThat(request.eventType).isEqualTo("CRT")
            assertThat(request.eventStatus).isEqualTo("COMP")
            assertThat(request.returnStatus).isEqualTo("COMP")
            assertThat(request.comment).isEqualTo("court event comment")
          }
        }

        @Test
        fun `will create mapping`() {
          MappingMockServer.getRequestBody<CourtScheduleMappingDto>(
            postRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule")),
          ).also { request ->
            assertThat(request.nomisEventId).isEqualTo(nomisEventId)
            assertThat(request.dpsCourtAppearanceId).isEqualTo(dpsCourtAppearanceId)
          }
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("court-scheduler-schedule-create-success"),
            check {
              assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class HappyPathUpdated {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetCourtScheduleMapping(dpsId = dpsCourtAppearanceId, nomisEventId = nomisEventId)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMapping()

          publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, "A1234BC")
          waitForAnyProcessingToComplete("court-scheduler-schedule-update-success")
        }

        @Test
        fun `will upsert NOMIS court schedule with event ID`() {
          NomisApiMockServer.getRequestBody<UpsertCourtScheduleOut>(
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=false")),
          ).also { request ->
            assertThat(request.eventId).isEqualTo(nomisEventId)
          }
        }

        @Test
        fun `will NOT create mapping`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule")),
          )
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("court-scheduler-schedule-update-success"),
            check {
              assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMappingFailureFollowedBySuccess()

          publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, "A1234BC")
          waitForAnyProcessingToComplete("court-scheduler-schedule-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("court-scheduler-schedule-mapping-create-error"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will upsert NOMIS court schedule once`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=false")),
          )
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("court-scheduler-schedule-create-success"),
            check {
              assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class DuplicateMapping {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = CourtScheduleMappingDto(
                  prisonerNumber = prisonerNumber,
                  bookingId = 12345L,
                  nomisEventId = nomisEventId,
                  dpsCourtAppearanceId = dpsCourtAppearanceId,
                  mappingType = CourtScheduleMappingDto.MappingType.DPS_CREATED,
                ),
                duplicate = CourtScheduleMappingDto(
                  prisonerNumber = prisonerNumber,
                  bookingId = 12345L,
                  nomisEventId = nomisEventId + 1,
                  dpsCourtAppearanceId = dpsCourtAppearanceId,
                  mappingType = CourtScheduleMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, "A1234BC")
          waitForAnyProcessingToComplete("court-scheduler-schedule-mapping-create-error")
        }

        @Test
        fun `will upsert NOMIS court schedule once`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=false")),
          )
        }

        @Test
        fun `will publish duplicate telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-court-scheduler-schedule-duplicate"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenUpdateComesFromSentencingAndWeIgnoreEventType {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMapping()

          publishCourtAppearanceDomainEvent(
            dpsId = dpsCourtAppearanceId,
            prisonerNumber = "A1234BC",
            eventType = "person.court-appearance.scheduled",
            externalReferenceExists = true,
          )
          waitForAnyProcessingToComplete("court-scheduler-schedule-ignored")
        }

        @Test
        fun `will not check for existing mapping`() {
          mappingApi.verify(
            count = 0,
            getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")),
          )
        }

        @Test
        fun `will not upsert NOMIS court schedule`() {
          nomisApi.verify(
            count = 0,
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out")),
          )
        }

        @Test
        fun `will publish ignored telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("court-scheduler-schedule-ignored"),
            check {
              assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("externalReferenceUrn", "some-ext-ref")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenUpdateComesFromSentencingAndWeIgnoreAllEventTypes {

        @BeforeEach
        fun setUp() {
          // Note that the feature switch is on
          doReturn(true).whenever(courtSchedulerFeature).ignoreAllSentencingEvents

          mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId, startTime = startTime)
          nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateCourtScheduleMapping()

          publishCourtAppearanceDomainEvent(
            dpsId = dpsCourtAppearanceId,
            prisonerNumber = "A1234BC",
            // Note the event type is in the list to ignore
            eventType = "person.court-appearance.relocated",
            externalReferenceExists = true,
          )
          waitForAnyProcessingToComplete("court-scheduler-schedule-ignored")
        }

        @Test
        fun `will not check for existing mapping`() {
          mappingApi.verify(
            count = 0,
            getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")),
          )
        }

        @Test
        fun `will not upsert NOMIS court schedule`() {
          nomisApi.verify(
            count = 0,
            putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out")),
          )
        }

        @Test
        fun `will publish ignored telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("court-scheduler-schedule-ignored"),
            check {
              assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("externalReferenceUrn", "some-ext-ref")
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, "A1234BC", source = "NOMIS")
        waitForAnyProcessingToComplete("court-scheduler-schedule-ignored")
      }

      @Test
      fun `will NOT check for existing mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")),
        )
      }

      @Test
      fun `will publish ignored telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-schedule-ignored"),
          check {
            assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("Court Appearance Deleted")
  inner class CourtAppearanceDeleted {
    private val prisonerNumber = "A1234BC"
    private val dpsCourtAppearanceId = UUID.randomUUID()
    private val nomisEventId = 123L

    @BeforeEach
    fun setUp() {
      mappingApi.stubGetCourtScheduleMapping(dpsId = dpsCourtAppearanceId)
      nomisApi.stubDeleteCourtScheduleOut(prisonerNumber, nomisEventId)
    }

    private fun publishDeleteEvent(source: String = "DPS", completedTelemetry: String? = null, externalReferenceExists: Boolean = false) {
      publishCourtAppearanceDomainEvent(dpsCourtAppearanceId, prisonerNumber, source, "person.court-appearance.cancelled", externalReferenceExists)
      if (completedTelemetry == null) {
        waitForAnyProcessingToComplete()
      } else {
        waitForAnyProcessingToComplete(completedTelemetry)
      }
    }

    @Test
    fun `should delete the scheduled movement in NOMIS`() {
      publishDeleteEvent()

      nomisApi.verify(deleteRequestedFor(urlEqualTo("/movements/$prisonerNumber/court/schedule/out/$nomisEventId")))
    }

    @Test
    fun `should publish telemetry`() {
      publishDeleteEvent()

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-schedule-delete-success"),
        check {
          assertThat(it).containsEntry("dpsCourtAppearanceId", dpsCourtAppearanceId.toString())
          assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `should ignore if triggered by NOMIS`() {
      publishDeleteEvent(source = "NOMIS")

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-schedule-delete-ignored"),
        check {
          assertThat(it).containsEntry("dpsCourtAppearanceId", dpsCourtAppearanceId.toString())
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if mapping does not exist`() {
      mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)

      publishDeleteEvent(completedTelemetry = "court-scheduler-schedule-delete-error")

      await untilAsserted {
        assertThat(courtMovementsDlqClient!!.countAllMessagesOnQueue(courtMovementsDlqUrl!!).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsCourtAppearanceId", dpsCourtAppearanceId.toString())
          assertThat(it).containsEntry("error", "Cannot find court schedule mapping for $dpsCourtAppearanceId")
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if NOMIS returns a conflict`() {
      nomisApi.stubDeleteCourtScheduleOut(prisonerNumber, nomisEventId, status = CONFLICT)

      publishDeleteEvent(completedTelemetry = "court-scheduler-schedule-delete-error")

      await untilAsserted {
        assertThat(courtMovementsDlqClient!!.countAllMessagesOnQueue(courtMovementsDlqUrl!!).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsCourtAppearanceId", dpsCourtAppearanceId.toString())
          assertThat(it).containsEntry("error", "409 Conflict from DELETE http://localhost:8082/movements/$prisonerNumber/court/schedule/out/$nomisEventId")
        },
        isNull(),
      )
    }

    @Test
    fun `should ignore if message has a sentencing external reference`() {
      publishDeleteEvent(externalReferenceExists = true)

      verify(telemetryClient).trackEvent(
        eq("court-scheduler-schedule-delete-ignored"),
        check {
          assertThat(it).containsEntry("dpsCourtAppearanceId", dpsCourtAppearanceId.toString())
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
        },
        isNull(),
      )
    }
  }

  private fun publishCourtAppearanceDomainEvent(
    dpsId: UUID,
    prisonerNumber: String,
    source: String = "DPS",
    eventType: String = "person.court-appearance.scheduled",
    externalReferenceExists: Boolean = false,
  ) {
    with(eventType) {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, id = dpsId, prisonerNumber = prisonerNumber, source = source, externalReferenceExists = externalReferenceExists))
    }
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

  fun messagePayload(
    eventType: String,
    prisonerNumber: String,
    id: UUID,
    source: String,
    externalReferenceExists: Boolean = false,
  ) = //language=JSON
    """
    {
      "description":"Some event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        "id": "$id",
        "source": "$source"
        ${if (externalReferenceExists) """, "externalReferenceUrn":"some-ext-ref"""" else "" }
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          }
        ]
      }
    }
    """
}
