package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalToDateTime
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.createTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsNomisApiMockServer.Companion.upsertTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
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
        publishAuthorisationDomainEvent(dpsAuthorisationId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
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
          dpsApi.stubGetTapAuthorisation(id = dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 0, startTime = today, endTime = tomorrow))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMapping()
          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
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
              .withRequestBodyJsonPath("toDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Some notes")
              .withRequestBodyJsonPath("temporaryAbsenceType", "SR")
              .withRequestBodyJsonPath("temporaryAbsenceSubType", "RDR")
              .withRequestBodyJsonPath("transportType", "VAN"),
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
          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
        }

        @Test
        fun `will send telemetry for initial mapping failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("reason", "500 Internal Server Error from POST http://localhost:8084/mapping/temporary-absence/application")
              },
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

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
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

    @Nested
    @DisplayName("when we receive a relocated event for a new authorisation")
    inner class IgnoreRelocatedEventType {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, status = NOT_FOUND)

        publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS", "person.temporary-absence-authorisation.relocated")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will check if the application mapping exists`() {
        mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
      }

      @Test
      fun `will send telemetry event showing the create was ignored`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-application-create-ignored"),
          check {
            assertThat(it).containsEntry("dpsAuthorisationId", dpsId.toString())
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
            assertThat(it).containsEntry("reason", "This event is applied to updates only")
          },
          isNull(),
        )
      }

      @Test
      fun `will NOT create the application in NOMIS once`() {
        await untilAsserted {
          nomisApi.verify(0, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/application")))
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
        publishAuthorisationDomainEvent(dpsAuthorisationId = dpsId, prisonerNumber = prisonerNumber, source = "NOMIS")
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
        private val startTime = today
        private val endTime = today.plusHours(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 0, startTime = startTime, endTime = endTime))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
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
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application"))
              .withRequestBodyJsonPath("escortCode", "U"),
          )
        }

        @Test
        fun `the updated application will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("fromDate", startTime.toLocalDate())
              .withRequestBodyJsonPath("toDate", endTime.toLocalDate())
              .withRequestBodyJsonPath("releaseTime", equalToDateTime(startTime.toLocalDate().atStartOfDay()))
              .withRequestBodyJsonPath("returnTime", equalToDateTime(endTime.plusDays(1).toLocalDate().atStartOfDay().minusMinutes(1)))
              .withRequestBodyJsonPath("comment", "Some notes")
              .withRequestBodyJsonPath("temporaryAbsenceType", "SR")
              .withRequestBodyJsonPath("temporaryAbsenceSubType", "RDR")
              .withRequestBodyJsonPath("transportType", "VAN")
              // Address is only updated when the application update is triggered by synchronising the DPS occurrence
              .withRequestBodyJsonPath("toAddress", absent()),
          )
        }
      }

      @Nested
      @DisplayName("when all goes ok for a single schedule")
      inner class HappyPathWithSingleSchedule {
        private val startTime = today
        private val endTime = today.plusHours(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(id = dpsId, response = dpsApi.tapAuthorisation(id = dpsId, repeat = false, occurrenceCount = 1, startTime = startTime, endTime = endTime))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the updated application's start and end times to match occurrences`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("fromDate", "${startTime.toLocalDate()}")
              .withRequestBodyJsonPath("toDate", "${endTime.toLocalDate()}")
              .withRequestBodyJsonPath("releaseTime", equalToDateTime(startTime))
              .withRequestBodyJsonPath("returnTime", equalToDateTime(endTime)),
          )
        }
      }

      @Nested
      @DisplayName("when all goes ok for multiple schedules")
      inner class HappyPathWithMultipleSchedules {
        private val startTime = today
        private val endTime = tomorrow.plusDays(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(id = dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 2, startTime = startTime, endTime = endTime))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the updated application's start and end times to match occurrences`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("fromDate", "${startTime.toLocalDate()}")
              .withRequestBodyJsonPath("toDate", "${endTime.toLocalDate()}")
              .withRequestBodyJsonPath("releaseTime", equalToDateTime(startTime.toLocalDate().atStartOfDay()))
              .withRequestBodyJsonPath("returnTime", equalToDateTime(endTime.plusDays(1).toLocalDate().atStartOfDay().minusMinutes(1))),
          )
        }

        @Test
        fun `all occurrence addresses are sent to NOMIS`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("toAddresses.length()", 2)
              .withRequestBodyJsonPath("toAddresses[0].addressText", "some address 1")
              .withRequestBodyJsonPath("toAddresses[0].name", "some description 1")
              .withRequestBodyJsonPath("toAddresses[0].postalCode", "some postcode 1")
              .withRequestBodyJsonPath("toAddresses[1].addressText", "some address 2")
              .withRequestBodyJsonPath("toAddresses[1].name", "some description 2")
              .withRequestBodyJsonPath("toAddresses[1].postalCode", "some postcode 2"),
          )
        }
      }

      @Nested
      @DisplayName("when there are multiple schedules with the same NOMIS address")
      inner class MultipleSchedulesWithSameNomisAddress {
        private val startTime = today
        private val endTime = tomorrow.plusDays(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          val authorisation = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 3, startTime = startTime, endTime = endTime)
          dpsApi.stubGetTapAuthorisation(id = dpsId, response = authorisation)
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())
          // Create a stub for the first schedule mapping only (which is same as third schedule)
          with(authorisation.occurrences[0]) {
            mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(
              dpsId = id,
              mapping = temporaryAbsenceScheduledMovementMapping().copy(
                prisonerNumber = prisonerNumber,
                dpsOccurrenceId = id,
                nomisAddressId = 1,
                dpsDescription = "some description 1",
                dpsAddressText = "some address 1",
                dpsPostcode = "some postcode 1",
              ),
            )
          }

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `we get the occurrence mappings for the 2 unique addresses`() {
          mappingApi.verify(
            2,
            getRequestedFor(urlMatching("/mapping/temporary-absence/scheduled-movement/dps-id/.*")),
          )
        }

        @Test
        fun `the updated application's locations contain the NOMIS address IDs from the occurrence mappings`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("toAddresses.length()", 2)
              // we found the existing NOMIS address
              .withRequestBodyJsonPath("toAddresses[0].id", 1)
              .withRequestBodyJsonPath("toAddresses[0].addressText", absent())
              // there is no NOMIS address so NOMIS will create a new one
              .withRequestBodyJsonPath("toAddresses[1].id", absent())
              .withRequestBodyJsonPath("toAddresses[1].name", "some description 2")
              .withRequestBodyJsonPath("toAddresses[1].addressText", "some address 2")
              .withRequestBodyJsonPath("toAddresses[1].postalCode", "some postcode 2"),
          )
        }
      }

      @Nested
      @DisplayName("when all goes ok for zero schedules")
      inner class HappyPathWithZeroSchedules {
        private val startTime = today
        private val endTime = tomorrow.plusDays(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(id = dpsId, response = dpsApi.tapAuthorisation(id = dpsId, repeat = false, occurrenceCount = 0, startTime = startTime, endTime = endTime, statusCode = "EXPIRED"))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the updated application's start and end times match DPS`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("applicationStatus", "PEN")
              .withRequestBodyJsonPath("fromDate", "${startTime.toLocalDate()}")
              .withRequestBodyJsonPath("toDate", "${endTime.toLocalDate()}")
              .withRequestBodyJsonPath("releaseTime", equalToDateTime(startTime.toLocalDate().atStartOfDay()))
              .withRequestBodyJsonPath("returnTime", equalToDateTime(endTime.plusDays(1).toLocalDate().atStartOfDay().minusMinutes(1))),
          )
        }
      }

      @Nested
      @DisplayName("when we receive an update request for authorisation relocated")
      inner class UpdateForRelocatedEventType {
        private val startTime = today
        private val endTime = today.plusHours(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 0, startTime = startTime, endTime = endTime))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS", "person.temporary-absence-authorisation.relocated")
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
        fun `will update the application in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")),
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

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
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
              assertThat(it).containsEntry("reason", "500 Internal Server Error from PUT http://localhost:8082/movements/A1234BC/temporary-absences/application")
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
  @DisplayName("person.temporary-absence.scheduled (created)")
  inner class TemporaryAbsenceScheduledMovementCreated {
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
        publishTapOccurrenceDomainEvent(dpsOccurrenceId = dpsOccurrenceId, prisonerNumber = prisonerNumber, source = "NOMIS")
        waitForAnyProcessingToComplete("temporary-absence-schedule-create-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-schedule-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will NOT check if the scheduled movement mapping exists`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsOccurrenceId")),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a scheduled movement create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsLocation = Location(uprn = 654, address = "some address", postcode = "SW1A 1AA")

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId, dpsLocation)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId, addressId = 54321, addressOwnerClass = "OFF"))
          mappingApi.stubCreateScheduledMovementMapping()

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.scheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-success")
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
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
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
        fun `will call back to DPS to get schedule details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-occurrences/$dpsOccurrenceId")))
        }

        @Test
        fun `will upsert the schedule in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `the created scheduled movement will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventId", absent())
              .withRequestBodyJsonPath("movementApplicationId", nomisApplicationId)
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("eventStatus", "SCH")
              .withRequestBodyJsonPath("parentEventStatus", absent())
              .withRequestBodyJsonPath("escort", "U")
              .withRequestBodyJsonPath("fromPrison", "LEI")
              .withRequestBodyJsonPath("eventDate", today.toLocalDate())
              .withRequestBodyJsonPath("returnDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Tap occurrence comment")
              .withRequestBodyJsonPath("transportType", "TAX"),
          )
        }

        @Test
        fun `the created scheduled movement will send address details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("toAddress.id", absent())
              .withRequestBodyJsonPath("toAddress.addressText", "some address")
              .withRequestBodyJsonPath("toAddress.postalCode", "SW1A 1AA"),
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
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED")
              .withRequestBodyJsonPath("dpsAddressText", "some address")
              .withRequestBodyJsonPath("nomisAddressId", "54321")
              .withRequestBodyJsonPath("nomisAddressOwnerClass", "OFF")
              .withRequestBodyJsonPath("dpsUprn", "654")
              .withRequestBodyJsonPath("dpsDescription", absent())
              .withRequestBodyJsonPath("dpsPostcode", "SW1A 1AA"),
          )
        }
      }

      @Nested
      @DisplayName("an occurrence rescheduled event also updates the authorisation")
      inner class HappyPathEventTriggersAuthorisationSync {
        private val dpsLocation = Location(uprn = 654, address = "some address", postcode = "SW1A 1AA")

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId, dpsLocation)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId, addressId = 54321, addressOwnerClass = "OFF"))
          mappingApi.stubCreateScheduledMovementMapping()

          // stubs for the authorisation sync
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          dpsApi.stubGetTapAuthorisation(dpsAuthorisationId, response = dpsApi.tapAuthorisation(id = dpsAuthorisationId))
          nomisApi.stubUpsertTemporaryAbsenceApplication(prisonerNumber, upsertTemporaryAbsenceApplicationResponse())

          // publish event that triggers authorisation sync
          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.rescheduled")
          waitForAnyProcessingToComplete("temporary-absence-application-update-success")
        }

        @Test
        fun `will upsert the schedule in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `will upsert the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/application")))
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId)
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.scheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-create-mapping-create-failed"),
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
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId)
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
                  nomisAddressId = 54312,
                  nomisAddressOwnerClass = "any",
                  dpsAddressText = "any",
                  eventTime = "any",
                ),
                duplicate = ScheduledMovementSyncMappingDto(
                  prisonerNumber = "A1234BC",
                  bookingId = 12345L,
                  nomisEventId = nomisEventId + 1,
                  dpsOccurrenceId = dpsOccurrenceId,
                  mappingType = ScheduledMovementSyncMappingDto.MappingType.NOMIS_CREATED,
                  nomisAddressId = 54312,
                  nomisAddressOwnerClass = "any",
                  dpsAddressText = "any",
                  eventTime = "any",
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.scheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-mapping-create-failed")
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-schedule-create-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will upsert the person in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-temporary-absence-schedule-create-duplicate"),
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
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, status = NOT_FOUND)

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-failed")
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

        @Test
        fun `will publish failed telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-create-failed"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("reason", "Cannot find parent application mapping for $dpsAuthorisationId")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence.scheduled (updated)")
  inner class TemporaryAbsenceScheduledMovementUpdated {
    private val prisonerNumber = "A1234BC"
    private val dpsOccurrenceId = UUID.randomUUID()
    private val dpsAuthorisationId = UUID.randomUUID()
    private val nomisApplicationId = 543L
    private val nomisEventId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of a scheduled movement update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishTapOccurrenceDomainEvent(dpsOccurrenceId = dpsOccurrenceId, prisonerNumber = prisonerNumber, source = "NOMIS")
        waitForAnyProcessingToComplete("temporary-absence-schedule-create-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("temporary-absence-schedule-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will NOT check if the scheduled movement mapping exists`() {
        mappingApi.verify(
          count = 0,
          getRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsOccurrenceId")),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a scheduled movement update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsLocation = Location(uprn = 654, address = "some address", postcode = "SW1A 1AA")

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, nomisEventId = nomisEventId, addressId = 54321)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId, dpsLocation)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId, addressId = 54321))

          // publish an update event
          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.rescheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-update-success")
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-update-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the schedule updated`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-update-success"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
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
        fun `will call back to DPS to get schedule details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/temporary-absence-occurrences/$dpsOccurrenceId")))
        }

        @Test
        fun `will NOT get the existing address mapping - address not changed`() {
          mappingApi.verify(
            count = 0,
            postRequestedFor(urlEqualTo("/mapping/temporary-absence/addresses/by-dps-id")),
          )
        }

        @Test
        fun `will upsert the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `the created scheduled movement will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventId", nomisEventId)
              .withRequestBodyJsonPath("movementApplicationId", nomisApplicationId)
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("eventStatus", "SCH")
              .withRequestBodyJsonPath("parentEventStatus", absent())
              .withRequestBodyJsonPath("escort", "U")
              .withRequestBodyJsonPath("fromPrison", "LEI")
              .withRequestBodyJsonPath("eventDate", today.toLocalDate())
              .withRequestBodyJsonPath("returnDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Tap occurrence comment")
              .withRequestBodyJsonPath("transportType", "TAX"),
          )
        }

        @Test
        fun `will NOT update mapping - address not changed`() {
          mappingApi.verify(
            count = 0,
            putRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement")),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails to update mapping due to address change")
      inner class MappingFailure {
        private val dpsLocation = Location(uprn = 987, address = "unknown address", postcode = "SW1A 1AA")

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, nomisEventId = nomisEventId, addressId = 55555)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId, dpsLocation)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          nomisApi.stubUpsertScheduledTemporaryAbsence(prisonerNumber, upsertScheduledTemporaryAbsenceResponse(eventId = nomisEventId, addressId = 54321))
          mappingApi.stubUpdateScheduledMovementMappingFailureFollowedBySuccess()

          // publish an update event
          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.rescheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-update-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-update-mapping-create-failed"),
              check {
                assertThat(it).containsEntry("reason", "500 Internal Server Error from PUT http://localhost:8084/mapping/temporary-absence/scheduled-movement")
              },
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-update-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will update the scheduled movement in NOMIS once`() {
          await untilAsserted {
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
          }
        }
      }

      @Nested
      @DisplayName("when parent mapping does not exist")
      inner class ParentMappingNotFound {
        private val dpsLocation = Location(uprn = 987, address = "unknown address", postcode = "SW1A 1AA")

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, nomisEventId = nomisEventId)
          dpsApi.stubGetTapOccurrence(dpsOccurrenceId, dpsAuthorisationId, dpsLocation)
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, status = NOT_FOUND)

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete("temporary-absence-schedule-update-failed")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/temporary-absences/scheduled-temporary-absence")))
        }

        @Test
        fun `will not update a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            count = 0,
            putRequestedFor(urlEqualTo("/mapping/temporary-absence/scheduled-movement")),
          )
        }

        @Test
        fun `will publish failed telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-schedule-update-failed"),
            check {
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("reason", "Cannot find parent application mapping for $dpsAuthorisationId")
            },
            isNull(),
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
        waitForAnyProcessingToComplete("temporary-absence-external-movement-create-ignored")
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
          waitForAnyProcessingToComplete("temporary-absence-external-movement-create-failed")
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
          waitForAnyProcessingToComplete("temporary-absence-external-movement-create-failed")
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

  private fun publishAuthorisationDomainEvent(dpsAuthorisationId: UUID, prisonerNumber: String, source: String = "DPS", eventType: String = "person.temporary-absence-authorisation.approved") {
    with(eventType) {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, id = dpsAuthorisationId, prisonerNumber = prisonerNumber, source = source))
    }
  }

  private fun publishTapOccurrenceDomainEvent(dpsOccurrenceId: UUID, prisonerNumber: String, source: String = "DPS", eventType: String = "person.temporary-absence.scheduled") {
    with(eventType) {
      publishDomainEvent(
        eventType = this,
        payload = messagePayload(eventType = this, id = dpsOccurrenceId, prisonerNumber = prisonerNumber, source = source),
      )
    }
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

  private fun publishTemporaryAbsenceExternalMovementOutDomainEvent(dpsExternalMovementId: UUID, dpsScheduledMovementId: UUID? = null, prisonerNumber: String, dpsAuthorisationId: UUID? = null, source: String = "DPS") {
    with("external-movements-api.temporary-absence-external-movement-out.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayloadExternalMovements(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsScheduledMovementOutId = dpsScheduledMovementId, dpsExternalMovementOutId = dpsExternalMovementId, prisonerNumber = prisonerNumber, source = source),
      )
    }
  }

  private fun publishTemporaryAbsenceExternalMovementInDomainEvent(dpsExternalMovementId: UUID, dpsScheduledMovementId: UUID? = null, prisonerNumber: String, dpsAuthorisationId: UUID? = null, source: String = "DPS") {
    with("external-movements-api.temporary-absence-external-movement-in.created") {
      publishDomainEvent(
        eventType = this,
        payload = messagePayloadExternalMovements(eventType = this, dpsAuthorisationId = dpsAuthorisationId, dpsScheduledMovementInId = dpsScheduledMovementId, dpsExternalMovementInId = dpsExternalMovementId, prisonerNumber = prisonerNumber, source = source),
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

fun messagePayloadExternalMovements(
  eventType: String,
  prisonerNumber: String,
  dpsAuthorisationId: UUID? = null,
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
