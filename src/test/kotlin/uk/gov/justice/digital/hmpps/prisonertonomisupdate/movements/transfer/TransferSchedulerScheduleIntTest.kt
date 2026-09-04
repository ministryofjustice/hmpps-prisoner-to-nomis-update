package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransferScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTransferScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiMockServer
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
          mappingApi.stubGetTransferScheduleMapping(status = HttpStatus.NOT_FOUND)
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
