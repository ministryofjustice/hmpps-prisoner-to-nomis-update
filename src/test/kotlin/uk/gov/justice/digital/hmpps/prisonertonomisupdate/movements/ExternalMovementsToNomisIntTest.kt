package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.*

class ExternalMovementsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: ExternalMovementsNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

  private val dpsApi: ExternalMovementsDpsApiMockServer = dpsExternalMovementsServer

  private val today = LocalDateTime.now()
  private val tomorrow = today.plusDays(1)

  @Nested
  @DisplayName("person.temporary-absence-authorisation.approved (created)")
  inner class TemporaryAbsenceApplicationCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsId = UUID.randomUUID()
    private val nomisId = 56789L

    @Nested
    @DisplayName("when NOMIS is the origin of an authorisation approval")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishAuthorisationApproved(dpsAuthorisationId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
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

      @Test
      fun `will NOT create the application in NOMIS`() {
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
      }

      @Test
      fun `will NOT create any mappings`() {
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/temporary-absence/application")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an authorisation approval")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = NOT_FOUND)
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
          publishAuthorisationApproved(dpsId, prisonerNumber, "DPS")
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
        fun `will check if the application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
        }

        @Test
        fun `telemetry will contain key facts about the application created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-create-success"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsId.toString())
              assertThat(it).containsEntry("nomisApplicationId", nomisId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get authorisation details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-authorisations/$dpsId")))
        }

        @Test
        fun `will create the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
        }

        @Test
        fun `the created application will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("fromDate", today.toLocalDate())
              .withRequestBodyJsonPath("toDate", today.toLocalDate())
              .withRequestBodyJsonPath("comment", "Some notes")
              .withRequestBodyJsonPath("temporaryAbsenceType", "SR")
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
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()
          publishAuthorisationApproved(dpsId, prisonerNumber, "DPS")
        }

        @Test
        fun `will send telemetry for initial mapping failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will send telemetry for application create`() {
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/application")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, NOT_FOUND)
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())
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

          publishAuthorisationApproved(dpsId, prisonerNumber, "DPS")
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
        fun `will create the application in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-application-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/application")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence-authorisation.approved (updated)")
  inner class TemporaryAbsenceApplicationUpdated {
    private val prisonerNumber = "A1234BC"
    private val dpsId = UUID.randomUUID()
    private val nomisId = 56789L

    @Nested
    @DisplayName("when NOMIS is the origin of an authorisation approval")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishAuthorisationApproved(dpsAuthorisationId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
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

      @Test
      fun `will NOT create the application in NOMIS`() {
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
      }

      @Test
      fun `will NOT create any mappings`() {
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/temporary-absence/application")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an authorisation approval")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationApproved(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `will check if the application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
        }

        @Test
        fun `telemetry will contain key facts about the application updated`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-update-success"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsId.toString())
              assertThat(it).containsEntry("nomisApplicationId", nomisId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get authorisation details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-authorisations/$dpsId")))
        }

        @Test
        fun `will update the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
        }

        @Test
        fun `the updated application will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("fromDate", today.toLocalDate())
              .withRequestBodyJsonPath("toDate", today.toLocalDate())
              .withRequestBodyJsonPath("comment", "Some notes")
              .withRequestBodyJsonPath("temporaryAbsenceType", "SR")
              .withRequestBodyJsonPath("temporaryAbsenceSubType", "RDR"),
          )
        }
      }

      @Nested
      inner class WhenNomisUpdateFails {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, HttpStatus.INTERNAL_SERVER_ERROR)
          mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

          publishAuthorisationApproved(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete("temporary-absence-application-update-failed")
        }

        @Test
        fun `will check if the application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
        }

        @Test
        fun `telemetry will contain key facts about the application`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-update-failed"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get authorisation details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-authorisations/$dpsId")))
        }

        @Test
        fun `will attempt to update the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
        }

        @Test
        fun `will put the message on the DLQ`() {
          assertThat(movementsDlqClient!!.countAllMessagesOnQueue(movementsDlqUrl!!).get()).isEqualTo(1)
        }
      }
    }
  }

  @Nested
  @DisplayName("external-movements-api.temporary-absence-outside-movement.created")
  inner class TemporaryAbsenceOutsideMovementCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsOutsideMovementId = UUID.randomUUID()
    private val dpsMovementApplicationId = UUID.randomUUID()
    private val nomisMovementApplicationMultiId = 543L

    @Nested
    @DisplayName("when NOMIS is the origin of an outside movement create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId = dpsOutsideMovementId, prisonerNumber = prisonerNumber, dpsAuthorisationId = dpsMovementApplicationId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-outside-movement-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an outside movement create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceOutsideMovementMapping(dpsId = dpsOutsideMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceApplication(dpsId, temporaryAbsenceApplication())
          nomisApi.stubCreateTemporaryAbsenceOutsideMovement(prisonerNumber, createTemporaryAbsenceOutsideMovementResponse(applicationMultiId = nomisMovementApplicationMultiId))
          mappingApi.stubCreateOutsideMovementMapping()
          publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-outside-movement-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the contact created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-outside-movement-create-success"),
            check {
              assertThat(it).containsEntry("dpsMovementApplicationId", dpsMovementApplicationId.toString())
              assertThat(it).containsEntry("dpsOutsideMovementId", dpsOutsideMovementId.toString())
              assertThat(it).containsEntry("nomisMovementApplicationMultiId", nomisMovementApplicationMultiId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }

        @Test
        fun `will check if the outside movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/outside-movement/dps-id/$dpsOutsideMovementId")))
        }

        @Test
        fun `will check if the parent application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get application details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-outside-movement/$dpsId")))
        }

        @Test
        fun `will create the application in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement")))
        }

        @Test
        fun `the created application will contain correct details`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "C5")
              .withRequestBodyJsonPath("fromDate", today.toLocalDate())
              .withRequestBodyJsonPath("toDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Outside Movement Create comment")
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
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/outside-movement"))
              .withRequestBodyJsonPath("dpsOutsideMovementId", "$dpsOutsideMovementId")
              .withRequestBodyJsonPath("nomisMovementApplicationMultiId", "$nomisMovementApplicationMultiId")
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
          mappingApi.stubGetTemporaryAbsenceOutsideMovementMapping(dpsId = dpsOutsideMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceOutsideMovement(dpsOutsideMovementId, temporaryAbsenceOutsideMovement())
          nomisApi.stubCreateTemporaryAbsenceOutsideMovement(prisonerNumber, createTemporaryAbsenceOutsideMovementResponse())
          mappingApi.stubCreateOutsideMovementMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-outside-movement-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-outside-movement-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the outside movement in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-outside-movement-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/outside-movement")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceOutsideMovementMapping(dpsId = dpsOutsideMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceOutsideMovement(dpsOutsideMovementId, temporaryAbsenceOutsideMovement())
          nomisApi.stubCreateTemporaryAbsenceOutsideMovement(prisonerNumber, createTemporaryAbsenceOutsideMovementResponse())
          mappingApi.stubCreateOutsideMovementMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = TemporaryAbsenceOutsideMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisMovementApplicationMultiId = nomisMovementApplicationMultiId,
                  dpsOutsideMovementId = dpsOutsideMovementId,
                  mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
                duplicate = TemporaryAbsenceOutsideMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisMovementApplicationMultiId = nomisMovementApplicationMultiId + 1,
                  dpsOutsideMovementId = dpsOutsideMovementId,
                  mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-outside-movement-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-outside-movement-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/outside-movement")))
          }
        }
      }

      @Nested
      @DisplayName("when parent mapping does not exist")
      inner class ParentMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceOutsideMovementMapping(dpsId = dpsOutsideMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, status = NOT_FOUND)

          publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/outside-movement")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/outside-movement")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence.scheduled")
  inner class TemporaryAbsenceScheduledMovementOutCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsOccurrenceId = UUID.randomUUID()
    private val dpsAuthorisationId = UUID.randomUUID()
    private val nomisApplicationId = 543L
    private val nomisEventId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of a scheduled movement create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId = dpsOccurrenceId, prisonerNumber = prisonerNumber, dpsAuthorisationId = dpsAuthorisationId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-schedule-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a scheduled movement create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMapping()
          publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, dpsAuthorisationId, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the schedule created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-create-success"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
            },
            isNull(),
          )
        }

        @Test
        fun `will check if the scheduled movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsOccurrenceId")))
        }

        @Test
        fun `will check if the parent application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsAuthorisationId")))
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get schedule details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-scheduled-movement/$dpsId")))
        }

        @Test
        fun `will upsert the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `the created scheduled movement will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("movementApplicationId", nomisApplicationId)
              .withRequestBodyJsonPath("eventSubType", "C5")
              .withRequestBodyJsonPath("eventStatus", "SCH")
              .withRequestBodyJsonPath("escort", "U")
              .withRequestBodyJsonPath("fromPrison", "LEI")
              .withRequestBodyJsonPath("eventDate", today.toLocalDate())
              .withRequestBodyJsonPath("returnDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Scheduled temporary absence comment")
              .withRequestBodyJsonPath("toAgency", "HAZLWD")
              .withRequestBodyJsonPath("toAddressId", 3456)
              .withRequestBodyJsonPath("transportType", "VAN"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement"))
              .withRequestBodyJsonPath("dpsOccurrenceId", "$dpsOccurrenceId")
              .withRequestBodyJsonPath("nomisEventId", "$nomisEventId")
              .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
              .withRequestBodyJsonPath("bookingId", "12345")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            // TODO check address mappings
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, dpsAuthorisationId, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the scheduled movement in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = ScheduledMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisEventId = nomisEventId,
                  dpsOccurrenceId = dpsOccurrenceId,
                  mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                  eventTime = "",
                ),
                duplicate = ScheduledMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisEventId = nomisEventId + 1,
                  dpsOccurrenceId = dpsOccurrenceId,
                  mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                  eventTime = "",
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, dpsAuthorisationId, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-schedule-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will upsert the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-schedule-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when parent mapping does not exist")
      inner class ParentMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, status = NOT_FOUND)

          publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, dpsAuthorisationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement")),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("external-movements-api.temporary-absence-external-movement-out.created")
  inner class TemporaryAbsenceExternalMovementOutCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsExternalMovementId = UUID.randomUUID()
    private val dpsScheduledMovementId = UUID.randomUUID()
    private val dpsMovementApplicationId = UUID.randomUUID()
    private val nomisBookingId = 12345L
    private val nomisMovementSeq = 2
    private val nomisMovementApplicationId = 543L
    private val nomisEventId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of an external movement create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId = dpsExternalMovementId, dpsScheduledMovementId = dpsScheduledMovementId, prisonerNumber = prisonerNumber, dpsAuthorisationId = dpsMovementApplicationId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an external movement create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsence(prisonerNumber, createTemporaryAbsenceResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMapping()
          publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-external-movement-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the external movement created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-external-movement-create-success"),
            check {
              assertThat(it).containsEntry("dpsMovementApplicationId", dpsMovementApplicationId.toString())
              assertThat(it).containsEntry("dpsScheduledMovementOutId", dpsScheduledMovementId.toString())
              assertThat(it).containsEntry("dpsExternalMovementId", dpsExternalMovementId.toString())
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
              assertThat(it).containsEntry("nomisMovementSeq", "2")
              assertThat(it).containsEntry("direction", "OUT")
            },
            isNull(),
          )
        }

        @Test
        fun `will check if the external movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement/dps-id/$dpsExternalMovementId")))
        }

        @Test
        fun `will check if the parent scheduled movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        fun `will check if the parent application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get external details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-external-movement/$dpsId")))
        }

        @Test
        fun `will create the external movement in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
        }

        @Test
        fun `the created external movement will contain correct details`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("scheduledTemporaryAbsenceId", nomisEventId)
              .withRequestBodyJsonPath("movementDate", today.toLocalDate())
              .withRequestBodyJsonPath("movementReason", "C5")
              .withRequestBodyJsonPath("arrestAgency", "POL")
              .withRequestBodyJsonPath("escort", "U")
              .withRequestBodyJsonPath("escortText", "Temporary absence escort text")
              .withRequestBodyJsonPath("fromPrison", "LEI")
              .withRequestBodyJsonPath("toAgency", "HAZLWD")
              .withRequestBodyJsonPath("commentText", "Temporary absence comment")
              .withRequestBodyJsonPath("toAddressId", 76543)
              .withRequestBodyJsonPath("toCity", "765"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement"))
              .withRequestBodyJsonPath("dpsMovementId", "$dpsExternalMovementId")
              .withRequestBodyJsonPath("nomisMovementSeq", "$nomisMovementSeq")
              .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
              .withRequestBodyJsonPath("bookingId", "$nomisBookingId")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            // TODO check address mappings
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsence(prisonerNumber, createTemporaryAbsenceResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the external movement in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsence(prisonerNumber, createTemporaryAbsenceResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = ExternalMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = nomisBookingId,
                  nomisMovementSeq = nomisMovementSeq,
                  dpsMovementId = dpsExternalMovementId,
                  mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                ),
                duplicate = ExternalMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = nomisBookingId,
                  nomisMovementSeq = nomisMovementSeq + 1,
                  dpsMovementId = dpsExternalMovementId,
                  mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-external-movement-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-external-movement-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when parent application mapping does not exist")
      inner class ParentApplicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, status = NOT_FOUND)

          publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement")),
          )
        }
      }

      @Nested
      @DisplayName("when parent scheduled movement mapping does not exist")
      inner class ParentScheduledMovementMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)

          publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement")),
          )
        }
      }
    }

    @Nested
    @DisplayName("Unscheduled movement, when all goes ok")
    inner class UnscheduledHappyPath {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
        nomisApi.stubCreateTemporaryAbsence(prisonerNumber, createTemporaryAbsenceResponse(nomisBookingId, nomisMovementSeq))
        mappingApi.stubCreateExternalMovementMapping()
        publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId, null, prisonerNumber, null, "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `telemetry will contain key facts about the external movement created`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-success"),
          check {
            assertThat(it["dpsMovementApplicationId"]).isNull()
            assertThat(it["dpsScheduledMovementId"]).isNull()
            assertThat(it).containsEntry("dpsExternalMovementId", dpsExternalMovementId.toString())
            assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
            assertThat(it).containsEntry("bookingId", "12345")
            assertThat(it).containsEntry("nomisMovementSeq", "2")
            assertThat(it).containsEntry("direction", "OUT")
          },
          isNull(),
        )
      }

      @Test
      fun `will check if the external movement mapping exists`() {
        mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement/dps-id/$dpsExternalMovementId")))
      }

      @Test
      @Disabled("TODO waiting for DPS API")
      fun `will call back to DPS to get external details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-external-movement/$dpsId")))
      }

      @Test
      fun `will create the external movement in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence")))
      }

      @Test
      fun `the created external movement will contain correct details`() {
        nomisApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("scheduledTemporaryAbsenceId", absent())
            .withRequestBodyJsonPath("movementDate", today.toLocalDate())
            .withRequestBodyJsonPath("movementReason", "C5")
            .withRequestBodyJsonPath("arrestAgency", "POL")
            .withRequestBodyJsonPath("escort", "U")
            .withRequestBodyJsonPath("escortText", "Temporary absence escort text")
            .withRequestBodyJsonPath("fromPrison", "LEI")
            .withRequestBodyJsonPath("toAgency", "HAZLWD")
            .withRequestBodyJsonPath("commentText", "Temporary absence comment")
            .withRequestBodyJsonPath("toAddressId", 76543)
            .withRequestBodyJsonPath("toCity", "765"),
        )
      }

      @Test
      fun `will create a mapping between the NOMIS and DPS ids`() {
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", "$dpsExternalMovementId")
            .withRequestBodyJsonPath("nomisMovementSeq", "$nomisMovementSeq")
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", "$nomisBookingId")
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          // TODO check address mappings
        )
      }
    }
  }

  @Nested
  @DisplayName("external-movements-api.temporary-absence-external-movement-in.created")
  inner class TemporaryAbsenceExternalMovementInCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsExternalMovementId = UUID.randomUUID()
    private val dpsScheduledMovementId = UUID.randomUUID()
    private val dpsMovementApplicationId = UUID.randomUUID()
    private val nomisBookingId = 12345L
    private val nomisMovementSeq = 3
    private val nomisMovementApplicationId = 543L
    private val nomisEventId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of an external movement create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId = dpsExternalMovementId, dpsScheduledMovementId = dpsScheduledMovementId, prisonerNumber = prisonerNumber, dpsAuthorisationId = dpsMovementApplicationId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-ignored"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an external movement create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsenceReturn(prisonerNumber, createTemporaryAbsenceReturnResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMapping()
          publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-external-movement-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the external movement created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-external-movement-create-success"),
            check {
              assertThat(it).containsEntry("dpsMovementApplicationId", dpsMovementApplicationId.toString())
              assertThat(it).containsEntry("dpsScheduledMovementInId", dpsScheduledMovementId.toString())
              assertThat(it).containsEntry("dpsExternalMovementId", dpsExternalMovementId.toString())
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
              assertThat(it).containsEntry("nomisMovementSeq", nomisMovementSeq.toString())
              assertThat(it).containsEntry("direction", "IN")
            },
            isNull(),
          )
        }

        @Test
        fun `will check if the external movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement/dps-id/$dpsExternalMovementId")))
        }

        @Test
        fun `will check if the parent scheduled movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        fun `will check if the parent application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get external details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-external-movement/$dpsId")))
        }

        @Test
        fun `will create the external movement in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
        }

        @Test
        fun `the created external movement will contain correct details`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("scheduledTemporaryAbsenceReturnId", nomisEventId)
              .withRequestBodyJsonPath("movementDate", today.toLocalDate())
              .withRequestBodyJsonPath("movementReason", "C5")
              .withRequestBodyJsonPath("arrestAgency", "POL")
              .withRequestBodyJsonPath("escort", "U")
              .withRequestBodyJsonPath("escortText", "Temporary absence escort text")
              .withRequestBodyJsonPath("fromAgency", "HAZLWD")
              .withRequestBodyJsonPath("toPrison", "LEI")
              .withRequestBodyJsonPath("commentText", "Temporary absence comment")
              .withRequestBodyJsonPath("fromAddressId", 76543),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement"))
              .withRequestBodyJsonPath("dpsMovementId", "$dpsExternalMovementId")
              .withRequestBodyJsonPath("nomisMovementSeq", "$nomisMovementSeq")
              .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
              .withRequestBodyJsonPath("bookingId", "$nomisBookingId")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            // TODO check address mappings
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsenceReturn(prisonerNumber, createTemporaryAbsenceReturnResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the external movement in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-external-movement-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, nomisEventId = nomisEventId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
          nomisApi.stubCreateTemporaryAbsenceReturn(prisonerNumber, createTemporaryAbsenceReturnResponse(nomisBookingId, nomisMovementSeq))
          mappingApi.stubCreateExternalMovementMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = ExternalMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = nomisBookingId,
                  nomisMovementSeq = nomisMovementSeq,
                  dpsMovementId = dpsExternalMovementId,
                  mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                ),
                duplicate = ExternalMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = nomisBookingId,
                  nomisMovementSeq = nomisMovementSeq + 1,
                  dpsMovementId = dpsExternalMovementId,
                  mappingType = ExternalMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  // TODO add address mapping details
                  nomisAddressId = 0,
                  nomisAddressOwnerClass = "",
                  dpsAddressText = "",
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-external-movement-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-external-movement-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
          }
        }
      }

      @Nested
      @DisplayName("when parent application mapping does not exist")
      inner class ParentApplicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, status = NOT_FOUND)

          publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement")),
          )
        }
      }

      @Nested
      @DisplayName("when parent scheduled movement mapping does not exist")
      inner class ParentScheduledMovementMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, nomisMovementApplicationId = nomisMovementApplicationId)
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)

          publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
        }

        @Test
        fun `will not create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement")),
          )
        }
      }
    }

    @Nested
    @DisplayName("Unscheduled movement, when all goes ok")
    inner class UnscheduledHappyPath {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceExternalMovementMapping(dpsId = dpsExternalMovementId, status = NOT_FOUND)
// TODO         dpsApi.stubGetTemporaryAbsenceExternalMovement(dpsId, temporaryAbsenceExternalMovement())
        nomisApi.stubCreateTemporaryAbsenceReturn(prisonerNumber, createTemporaryAbsenceReturnResponse(nomisBookingId, nomisMovementSeq))
        mappingApi.stubCreateExternalMovementMapping()
        publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId, null, prisonerNumber, null, "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `telemetry will contain key facts about the external movement created`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-external-movement-create-success"),
          check {
            assertThat(it["dpsMovementApplicationId"]).isNull()
            assertThat(it["dpsScheduledMovementId"]).isNull()
            assertThat(it).containsEntry("dpsExternalMovementId", dpsExternalMovementId.toString())
            assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
            assertThat(it).containsEntry("bookingId", "12345")
            assertThat(it).containsEntry("nomisMovementSeq", nomisMovementSeq.toString())
            assertThat(it).containsEntry("direction", "IN")
          },
          isNull(),
        )
      }

      @Test
      fun `will check if the external movement mapping exists`() {
        mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement/dps-id/$dpsExternalMovementId")))
      }

      @Test
      @Disabled("TODO waiting for DPS API")
      fun `will call back to DPS to get external details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-external-movement/$dpsId")))
      }

      @Test
      fun `will create the external movement in NOMIS`() {
        nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/temporary-absence-return")))
      }

      @Test
      fun `the created external movement will contain correct details`() {
        nomisApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBodyJsonPath("scheduledTemporaryAbsenceId", absent())
            .withRequestBodyJsonPath("movementDate", today.toLocalDate())
            .withRequestBodyJsonPath("movementReason", "C5")
            .withRequestBodyJsonPath("arrestAgency", "POL")
            .withRequestBodyJsonPath("escort", "U")
            .withRequestBodyJsonPath("escortText", "Temporary absence escort text")
            .withRequestBodyJsonPath("fromAgency", "HAZLWD")
            .withRequestBodyJsonPath("toPrison", "LEI")
            .withRequestBodyJsonPath("commentText", "Temporary absence comment")
            .withRequestBodyJsonPath("fromAddressId", 76543),
        )
      }

      @Test
      fun `will create a mapping between the NOMIS and DPS ids`() {
        mappingApi.verify(
          postRequestedFor(urlEqualTo("/mapping/temporary-absence/external-movement"))
            .withRequestBodyJsonPath("dpsMovementId", "$dpsExternalMovementId")
            .withRequestBodyJsonPath("nomisMovementSeq", "$nomisMovementSeq")
            .withRequestBodyJsonPath("prisonerNumber", "A1234BC")
            .withRequestBodyJsonPath("bookingId", "$nomisBookingId")
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          // TODO check address mappings
        )
      }
    }
  }

  private fun publishAuthorisationApproved(dpsAuthorisationId: UUID, prisonerNumber: String, source: String = "DPS") {
    with("person.temporary-absence-authorisation.approved") {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, dpsAuthorisationId = dpsAuthorisationId, prisonerNumber = prisonerNumber, source = source))
    }
  }

  private fun publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId: UUID, prisonerNumber: String, dpsAuthorisationId: UUID, source: String = "DPS") {
    with("external-movements-api.temporary-absence-outside-movement.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsOutsideMovementId = dpsOutsideMovementId, prisonerNumber = prisonerNumber, source = source),
      )
    }
  }

  private fun publishTemporaryAbsenceOccurrenceDomainEvent(dpsOccurrenceId: UUID, prisonerNumber: String, dpsAuthorisationId: UUID, source: String = "DPS") {
    with("person.temporary-absence.scheduled") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsOccurrenceId = dpsOccurrenceId, prisonerNumber = prisonerNumber, source = source),
      )
    }
  }

  private fun publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId: UUID, dpsScheduledMovementId: UUID? = null, prisonerNumber: String, dpsAuthorisationId: UUID? = null, source: String = "DPS") {
    with("external-movements-api.temporary-absence-external-movement-out.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsScheduledMovementOutId = dpsScheduledMovementId, dpsExternalMovementOutId = dpsExternalMovementId, prisonerNumber = prisonerNumber, source = source),
      )
    }
  }

  private fun publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId: UUID, dpsScheduledMovementId: UUID? = null, prisonerNumber: String, dpsAuthorisationId: UUID? = null, source: String = "DPS") {
    with("external-movements-api.temporary-absence-external-movement-in.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsScheduledMovementInId = dpsScheduledMovementId, dpsExternalMovementInId = dpsExternalMovementId, prisonerNumber = prisonerNumber, source = source),
      )
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
  dpsAuthorisationId: UUID? = null,
  dpsOutsideMovementId: UUID? = null,
  dpsScheduledMovementOutId: UUID? = null,
  dpsScheduledMovementInId: UUID? = null,
  dpsOccurrenceId: UUID? = null,
  dpsExternalMovementOutId: UUID? = null,
  dpsExternalMovementInId: UUID? = null,
  source: String,
) = //language=JSON
  """
    {
      "description":"Some event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        ${dpsAuthorisationId?.let { """"authorisationId": "$dpsAuthorisationId",""" } ?: ""}
        ${dpsOutsideMovementId?.let { """"outsideMovementId": "$dpsOutsideMovementId",""" } ?: ""}
        ${dpsOccurrenceId?.let { """"occurrenceId": "$dpsOccurrenceId",""" } ?: ""}
        ${dpsScheduledMovementOutId?.let { """"scheduledMovementOutId": "$dpsScheduledMovementOutId",""" } ?: ""}
        ${dpsScheduledMovementInId?.let { """"scheduledMovementInId": "$dpsScheduledMovementInId",""" } ?: ""}
        ${dpsExternalMovementOutId?.let { """"externalMovementOutId": "$dpsExternalMovementOutId",""" } ?: ""}
        ${dpsExternalMovementInId?.let { """"externalMovementInId": "$dpsExternalMovementInId",""" } ?: ""}
        "source": "$source"
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
