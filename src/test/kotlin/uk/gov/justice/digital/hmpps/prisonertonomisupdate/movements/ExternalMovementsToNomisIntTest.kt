package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
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
        publishTemporaryAbsenceApplicationDomainEvent(dpsMovementApplicationId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
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
        fun `will check if the outside movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
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
        publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId = dpsOutsideMovementId, prisonerNumber = prisonerNumber, dpsMovementApplicationId = dpsMovementApplicationId, source = "NOMIS")
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
  @DisplayName("external-movements-api.temporary-absence-scheduled-movement-out.created")
  inner class TemporaryAbsenceScheduledMovementOutCreated {
    private val prisonerNumber = "A1234BC"
    private val dpsScheduledMovementId = UUID.randomUUID()
    private val dpsMovementApplicationId = UUID.randomUUID()
    private val nomisEventId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of a scheduled movement create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId = dpsScheduledMovementId, prisonerNumber = prisonerNumber, dpsMovementApplicationId = dpsMovementApplicationId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-scheduled-movement-create-ignored"),
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
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubCreateScheduledTemporaryAbsence(prisonerNumber, createScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMapping()
          publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-scheduled-movement-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the schedule created`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-scheduled-movement-create-success"),
            check {
              assertThat(it).containsEntry("dpsMovementApplicationId", dpsMovementApplicationId.toString())
              assertThat(it).containsEntry("dpsScheduledMovementId", dpsScheduledMovementId.toString())
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("prisonerNumber", prisonerNumber)
              assertThat(it).containsEntry("bookingId", "12345")
              assertThat(it).containsEntry("direction", "OUT")
            },
            isNull(),
          )
        }

        @Test
        fun `will check if the scheduled movement mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsScheduledMovementId")))
        }

        @Test
        fun `will check if the parent application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsMovementApplicationId")))
        }

        @Test
        @Disabled("TODO waiting for DPS API")
        fun `will call back to DPS to get schedule details`() {
// TODO         dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-scheduled-movement/$dpsId")))
        }

        @Test
        fun `will create the application in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `the created application will contain correct details`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
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
              .withRequestBodyJsonPath("dpsScheduledMovementId", "$dpsScheduledMovementId")
              .withRequestBodyJsonPath("nomisEventId", "$nomisEventId")
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
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubCreateScheduledTemporaryAbsence(prisonerNumber, createScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()
          publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-scheduled-movement-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-scheduled-movement-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the scheduled movement in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-scheduled-movement-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId)
// TODO         dpsApi.stubGetTemporaryAbsenceScheduledMovement(dpsId, temporaryAbsenceScheduledMovement())
          nomisApi.stubCreateScheduledTemporaryAbsence(prisonerNumber, createScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingConflict(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                existing = ScheduledMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisEventId = nomisEventId,
                  dpsScheduledMovementId = dpsScheduledMovementId,
                  mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
                duplicate = ScheduledMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisEventId = nomisEventId + 1,
                  dpsScheduledMovementId = dpsScheduledMovementId,
                  mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-scheduled-movement-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-scheduled-movement-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when parent mapping does not exist")
      inner class ParentMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsScheduledMovementId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsMovementApplicationId, status = NOT_FOUND)

          publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId, prisonerNumber, dpsMovementApplicationId, "DPS")
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

  private fun publishTemporaryAbsenceApplicationDomainEvent(dpsMovementApplicationId: UUID, prisonerNumber: String, source: String = "DPS") {
    with("external-movements-api.temporary-absence-application.created") {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, dpsMovementApplicationId = dpsMovementApplicationId, prisonerNumber = prisonerNumber, source = source))
    }
  }

  private fun publishTemporaryAbsenceOutsideMovementDomainEvent(dpsOutsideMovementId: UUID, prisonerNumber: String, dpsMovementApplicationId: UUID, source: String = "DPS") {
    with("external-movements-api.temporary-absence-outside-movement.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsMovementApplicationId = dpsMovementApplicationId, dpsOutsideMovementId = dpsOutsideMovementId, prisonerNumber = prisonerNumber, source = source),
      )
    }
  }

  private fun publishTemporaryAbsenceScheduledMovementOutDomainEvent(dpsScheduledMovementId: UUID, prisonerNumber: String, dpsMovementApplicationId: UUID, source: String = "DPS") {
    with("external-movements-api.temporary-absence-scheduled-movement-out.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, dpsMovementApplicationId = dpsMovementApplicationId, dpsScheduledMovementId = dpsScheduledMovementId, prisonerNumber = prisonerNumber, source = source),
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
  dpsMovementApplicationId: UUID,
  dpsOutsideMovementId: UUID? = null,
  dpsScheduledMovementId: UUID? = null,
  source: String,
) = //language=JSON
  """
    {
      "description":"Soem event", 
      "eventType":"$eventType", 
      "additionalInformation": {
        "applicationId": "$dpsMovementApplicationId",
        ${dpsOutsideMovementId?.let { """"outsideMovementId": "$dpsOutsideMovementId",""" } ?: ""}
        ${dpsScheduledMovementId?.let { """"scheduledMovementId": "$dpsScheduledMovementId",""" } ?: ""}
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
