package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.*

class ExternalMovementsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: ExternalMovementsNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

//  @Autowired
// TODO  private lateinit var dpsApi: ExternalMovementsDpsApiMockServer

  private val today = LocalDateTime.now()
  private val tomorrow = today.plusDays(1)

  @Nested
  @DisplayName("external-movements-api.temporary-absence-application.created")
  inner class TemporaryAbsenceApplicationCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsId = UUID.randomUUID()
    private val nomisId = 56789L

    @Nested
    @DisplayName("when NOMIS is the origin of an application create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceApplicationDomainEvent(dpsId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-application-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an application create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = NOT_FOUND)
// TODO         dpsApi.stubGetTemporaryAbsenceApplication(dpsId, temporaryAbsenceApplication())
          nomisApi.stubCreateTemporaryAbsenceApplication(prisonerNumber, createTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
          publishTemporaryAbsenceApplicationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-create-success"),
            check {
              assertThat(it).containsEntry("dpsMovementApplicationId", dpsId.toString())
              assertThat(it).containsEntry("nomisMovementApplicationId", nomisId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get application details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-application/$dpsId")))
        }

        @Test
        fun `will create the application in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
        }

        @Test
        fun `the created application will contain correct details`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "C5")
              .withRequestBodyJsonPath("fromDate", today.toLocalDate())
              .withRequestBodyJsonPath("toDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Tap Application Create comment")
              .withRequestBodyJsonPath("toAgencyId", "HAZLWD")
              .withRequestBodyJsonPath("toAddressId", 3456)
              .withRequestBodyJsonPath("contactPersonName", "Deek Sanderson")
              .withRequestBodyJsonPath("temporaryAbsenceType", "RR")
              .withRequestBodyJsonPath("temporaryAbsenceSubType", "RDR"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/application"))
              .withRequestBodyJsonPath("dpsMovementApplicationId", "$dpsId")
              .withRequestBodyJsonPath("nomisMovementApplicationId", "$nomisId")
              .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
              .withRequestBodyJsonPath("bookingId", "12345")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = NOT_FOUND)
// TODO         dpsApi.stubGetTemporaryAbsenceApplication(dpsId, temporaryAbsenceApplication())
          nomisApi.stubCreateTemporaryAbsenceApplication(prisonerNumber, createTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceApplicationDomainEvent(dpsId, prisonerNumber, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the application in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/application")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, NOT_FOUND)
// TODO         dpsApi.stubGetTemporaryAbsenceApplication(dpsId, temporaryAbsenceApplication())
          nomisApi.stubCreateTemporaryAbsenceApplication(prisonerNumber, createTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = TemporaryAbsenceApplicationSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisMovementApplicationId = nomisId,
                  dpsMovementApplicationId = dpsId,
                  mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
                duplicate = TemporaryAbsenceApplicationSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisMovementApplicationId = nomisId + 1,
                  dpsMovementApplicationId = dpsId,
                  mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceApplicationDomainEvent(dpsId, prisonerNumber, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-application-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-application-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/application")))
          }
        }
      }
    }
  }

  private fun publishTemporaryAbsenceApplicationDomainEvent(dpsId: UUID, prisonerNumber: String, source: String = "DPS") {
    with("external-movements-api.temporary-absence-application.created") {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, dpsId = dpsId, prisonerNumber = prisonerNumber, source = source))
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
}

fun messagePayload(
  eventType: String,
  prisonerNumber: String,
  dpsId: UUID,
  source: String,
) = //language=JSON
  """
    {
      "description":"Soem event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        "applicationId": "$dpsId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$prisonerNumber"
          },
          {
            "type": "DPS_ID",
            "value": "$dpsId"
          }
        ]
      }
    }
    """
