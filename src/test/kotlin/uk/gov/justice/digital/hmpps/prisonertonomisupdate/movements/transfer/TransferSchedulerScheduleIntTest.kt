package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiMockServer
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class TransferSchedulerScheduleIntTest(
  @Autowired private val mappingApi: TransferSchedulerMappingApiMockServer,
  @Autowired private val nomisApi: TransferSchedulerNomisApiMockServer,
) : SqsIntegrationTestBase() {

  private val dpsApi = transferSchedulerDpsApiServer

  @Nested
  inner class TransferScheduleUpserted {
    private val prisonerNumber = "A1234BC"
    private val dpsTransferScheduleId = UUID.randomUUID()
    private val nomisEventId = 123L
    private val start = LocalDateTime.now()

    @Nested
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetTransferSchedule(id = dpsTransferScheduleId, start = start)
          nomisApi.stubUpsertTransferScheduleOut(response = UpsertTransferScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateTransferScheduleMapping()

          publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber)
          waitForAnyProcessingToComplete("transfer-scheduler-schedule-create-success")
        }

        @Test
        fun `will check for existing mapping`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule/dps-id/$dpsTransferScheduleId")))
        }

        @Test
        fun `will get DPS transfer schedule`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/transfers/$dpsTransferScheduleId")))
        }

        @Test
        fun `will upsert NOMIS transfer schedule`() {
          NomisApiMockServer.getRequestBody<UpsertTransferScheduleOut>(
            putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out")),
          ).also { request ->
            with(request) {
              assertThat(eventId).isNull()
              assertThat(eventSubType).isEqualTo("TRN")
              assertThat(eventStatus).isEqualTo("SCH")
              assertThat(startTime).isEqualTo(start)
              assertThat(fromPrison).isEqualTo("BXI")
              assertThat(toPrison).isEqualTo("LEI")
              assertThat(comment).isEqualTo("Some schedule comment")
              assertThat(escortCode).isEqualTo("PECS")
              with(request.waitlist!!) {
                assertThat(requestDate).isEqualTo(LocalDate.now().minusDays(1))
                assertThat(status).isEqualTo("CANC")
                assertThat(priority).isEqualTo("3")
                assertThat(approvedUserName).isEqualTo("APPROVE_USER")
                assertThat(comment).isEqualTo("some waitlist comment")
              }
            }
          }
        }

        @Test
        fun `will create mapping`() {
          MappingMockServer.getRequestBody<TransferScheduleMappingDto>(
            postRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule")),
          ).also { request ->
            assertThat(request.nomisEventId).isEqualTo(nomisEventId)
            assertThat(request.dpsTransferScheduleId).isEqualTo(dpsTransferScheduleId)
          }
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("transfer-scheduler-schedule-create-success"),
            check {
              assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
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
          mappingApi.stubGetTransferScheduleMapping(dpsId = dpsTransferScheduleId, nomisEventId = nomisEventId)
          dpsApi.stubGetTransferSchedule(id = dpsTransferScheduleId, start = start)
          nomisApi.stubUpsertTransferScheduleOut(response = UpsertTransferScheduleOutResponse(12345L, nomisEventId))

          publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber)
          waitForAnyProcessingToComplete("transfer-scheduler-schedule-update-success")
        }

        @Test
        fun `will check for existing mapping`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule/dps-id/$dpsTransferScheduleId")))
        }

        @Test
        fun `will get DPS transfer schedule`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/transfers/$dpsTransferScheduleId")))
        }

        @Test
        fun `will upsert NOMIS transfer schedule`() {
          NomisApiMockServer.getRequestBody<UpsertTransferScheduleOut>(
            putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out")),
          ).also { request ->
            with(request) {
              assertThat(eventId).isEqualTo(nomisEventId)
            }
          }
        }

        @Test
        fun `will NOT create mapping`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule")),
          )
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("transfer-scheduler-schedule-update-success"),
            check {
              assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
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
          mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetTransferSchedule(id = dpsTransferScheduleId, start = start)
          nomisApi.stubUpsertTransferScheduleOut(response = UpsertTransferScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateTransferScheduleMappingFailureFollowedBySuccess()

          publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber)
          waitForAnyProcessingToComplete("transfer-scheduler-schedule-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("transfer-scheduler-schedule-mapping-create-error"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will upsert NOMIS transfer schedule`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out")),
          )
        }

        @Test
        fun `will create mapping on the 2nd call`() {
          mappingApi.verify(
            count = 2,
            postRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule")),
          )
        }

        @Test
        fun `will publish success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("transfer-scheduler-schedule-create-success"),
            check {
              assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
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
          mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetTransferSchedule(id = dpsTransferScheduleId, start = start)
          nomisApi.stubUpsertTransferScheduleOut(response = UpsertTransferScheduleOutResponse(12345L, nomisEventId))
          mappingApi.stubCreateTransferScheduleMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = TransferScheduleMappingDto(
                  prisonerNumber = prisonerNumber,
                  bookingId = 12345L,
                  nomisEventId = nomisEventId,
                  dpsTransferScheduleId = dpsTransferScheduleId,
                  mappingType = TransferScheduleMappingDto.MappingType.DPS_CREATED,
                ),
                duplicate = TransferScheduleMappingDto(
                  prisonerNumber = prisonerNumber,
                  bookingId = 12345L,
                  nomisEventId = nomisEventId + 1,
                  dpsTransferScheduleId = dpsTransferScheduleId,
                  mappingType = TransferScheduleMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber)
          waitForAnyProcessingToComplete("transfer-scheduler-schedule-mapping-create-error")
        }

        @Test
        fun `will upsert NOMIS transfer schedule`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out")),
          )
        }

        @Test
        fun `will publish duplicate telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-transfer-scheduler-schedule-duplicate"),
            check {
              assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class SyncFails {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)
          dpsApi.stubGetTransferSchedule(id = dpsTransferScheduleId, start = start)
          nomisApi.stubUpsertTransferScheduleOut(status = BAD_REQUEST)
          mappingApi.stubCreateTransferScheduleMapping()

          publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber)
          waitForAnyProcessingToComplete("transfer-scheduler-schedule-create-error")
        }

        @Test
        fun `will attempt to upsert NOMIS transfer schedule`() {
          nomisApi.verify(
            putRequestedFor(urlEqualTo("/movements/A1234BC/transfers/schedule/out")),
          )
        }

        @Test
        fun `will NOT create mapping`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule")),
          )
        }

        @Test
        fun `will publish error telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("transfer-scheduler-schedule-create-error"),
            check {
              assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("error", "400 Bad Request from PUT http://localhost:8082/movements/A1234BC/transfers/schedule/out")
            },
            isNull(),
          )
        }

        @Test
        fun `message should end up on the DLQ`() {
          await untilAsserted {
            assertThat(
              transferMovementsDlqClient.countAllMessagesOnQueue(transferMovementsDlqUrl).get(),
            ).isEqualTo(1)
          }
        }
      }
    }

    @Nested
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTransferDomainEvent(dpsTransferScheduleId, "A1234BC", source = "NOMIS")
        waitForAnyProcessingToComplete("transfer-scheduler-schedule-ignored")
      }

      @Test
      fun `will NOT check for existing mapping`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlEqualTo("/mapping/transfer-scheduler/schedule/dps-id/$dpsTransferScheduleId")),
        )
      }

      @Test
      fun `will publish ignored telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("transfer-scheduler-schedule-ignored"),
          check {
            assertThat(it).containsEntry("dpsTransferScheduleId", "$dpsTransferScheduleId")
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class TransferScheduleDeleted {
    private val prisonerNumber = "A1234BC"
    private val dpsTransferScheduleId = UUID.randomUUID()
    private val nomisEventId = 123L

    @BeforeEach
    fun setUp() {
      mappingApi.stubGetTransferScheduleMapping(dpsId = dpsTransferScheduleId)
      nomisApi.stubDeleteTransferScheduleOut(prisonerNumber, nomisEventId)
    }

    private fun publishDeleteEvent(source: String = "DPS", completedTelemetry: String? = null) {
      publishTransferDomainEvent(dpsTransferScheduleId, prisonerNumber, source, "person.transfer.deleted")
      if (completedTelemetry == null) {
        waitForAnyProcessingToComplete()
      } else {
        waitForAnyProcessingToComplete(completedTelemetry)
      }
    }

    @Test
    fun `should delete the scheduled transfer in NOMIS`() {
      publishDeleteEvent()

      nomisApi.verify(deleteRequestedFor(urlEqualTo("/movements/$prisonerNumber/transfers/schedule/out/$nomisEventId")))
    }

    @Test
    fun `should publish telemetry`() {
      publishDeleteEvent()

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-schedule-delete-success"),
        check {
          assertThat(it).containsEntry("dpsTransferScheduleId", dpsTransferScheduleId.toString())
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
        eq("transfer-scheduler-schedule-delete-ignored"),
        check {
          assertThat(it).containsEntry("dpsTransferScheduleId", dpsTransferScheduleId.toString())
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if mapping does not exist`() {
      mappingApi.stubGetTransferScheduleMapping(status = NOT_FOUND)

      publishDeleteEvent(completedTelemetry = "transfer-scheduler-schedule-delete-error")

      await untilAsserted {
        assertThat(transferMovementsDlqClient.countAllMessagesOnQueue(transferMovementsDlqUrl).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsTransferScheduleId", dpsTransferScheduleId.toString())
          assertThat(it).containsEntry("error", "Cannot find transfer schedule mapping for $dpsTransferScheduleId")
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if NOMIS returns a conflict`() {
      nomisApi.stubDeleteTransferScheduleOut(prisonerNumber, nomisEventId, status = CONFLICT)

      publishDeleteEvent(completedTelemetry = "transfer-scheduler-schedule-delete-error")

      await untilAsserted {
        assertThat(transferMovementsDlqClient.countAllMessagesOnQueue(transferMovementsDlqUrl).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("transfer-scheduler-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsTransferScheduleId", dpsTransferScheduleId.toString())
          assertThat(it).containsEntry("error", "409 Conflict from DELETE http://localhost:8082/movements/$prisonerNumber/transfers/schedule/out/$nomisEventId")
        },
        isNull(),
      )
    }
  }

  private fun publishTransferDomainEvent(dpsId: UUID, prisonerNumber: String, source: String = "DPS", eventType: String = "person.transfer.scheduled") {
    with(eventType) {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, id = dpsId, prisonerNumber = prisonerNumber, source = source))
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
  ) = //language=JSON
    """
    {
      "description":"Some event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        "id": "$id",
        "source": "$source",
        "stage": "ANY"
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
