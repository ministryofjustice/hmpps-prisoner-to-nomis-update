package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.upsertTapApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.temporaryAbsenceScheduledMovementMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.*

class TapAuthorisationIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: TapNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

  private val dpsApi: TapDpsApiMockServer = dpsExternalMovementsServer

  private val today = LocalDateTime.now()
  private val tomorrow = today.plusDays(1)

  @Nested
  @DisplayName("person.temporary-absence-authorisation.approved (created)")
  inner class TapApplicationCreated {
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
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")))
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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())
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
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")))
        }

        @Test
        fun `the created application will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventSubType", "R2")
              .withRequestBodyJsonPath("fromDate", today.toLocalDate())
              .withRequestBodyJsonPath("toDate", tomorrow.toLocalDate())
              .withRequestBodyJsonPath("comment", "Some notes")
              .withRequestBodyJsonPath("tapType", "SR")
              .withRequestBodyJsonPath("tapSubType", "RDR")
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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())
          mappingApi.stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess()
          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
        }

        @Test
        fun `will send telemetry for initial mapping failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-application-mapping-create-error"),
              check {
                assertThat(it).containsEntry("error", "500 Internal Server Error from POST http://localhost:8084/mapping/temporary-absence/application")
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/application")))
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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/application")))
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
          nomisApi.verify(0, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/application")))
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence-authorisation.approved (updated)")
  inner class TapApplicationUpdated {
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
        nomisApi.verify(0, putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")))
      }

      @Test
      fun `will NOT create any mappings`() {
        mappingApi.verify(0, postRequestedFor(urlEqualTo("/mapping/temporary-absence/application")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of an authorisation approval")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val startTime = today
        private val endTime = today.plusHours(1)

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 0, startTime = startTime, endTime = endTime))
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

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
            putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application"))
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
              .withRequestBodyJsonPath("tapType", "SR")
              .withRequestBodyJsonPath("tapSubType", "RDR")
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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())
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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

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
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

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
            putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")),
          )
        }
      }

      @Nested
      @DisplayName("when we receive an authorisation paused status")
      inner class UpdateForAuthorisationPaused {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId, response = dpsApi.tapAuthorisation(id = dpsId, occurrenceCount = 1, statusCode = "PAUSED"))
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS", "person.temporary-absence-authorisation.paused")
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
        fun `will update the application to pending in NOMIS`() {
          nomisApi.verify(
            putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application"))
              .withRequestBodyJsonPath("applicationStatus", "PEN"),
          )
        }
      }

      @Nested
      inner class WhenNomisUpdateFails {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsId, nomisMovementApplicationId = nomisId)
          dpsApi.stubGetTapAuthorisation(dpsId)
          nomisApi.stubUpsertTapApplication(prisonerNumber, HttpStatus.INTERNAL_SERVER_ERROR)
          mappingApi.stubCreateTemporaryAbsenceApplicationMapping()

          publishAuthorisationDomainEvent(dpsId, prisonerNumber, "DPS")
          waitForAnyProcessingToComplete("temporary-absence-application-update-error")
        }

        @Test
        fun `will check if the application mapping exists`() {
          mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")))
        }

        @Test
        fun `telemetry will contain key facts about the application`() {
          verify(telemetryClient).trackEvent(
            eq("temporary-absence-application-update-error"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsId.toString())
              assertThat(it).containsEntry("offenderNo", prisonerNumber)
              assertThat(it).containsEntry("error", "500 Internal Server Error from PUT http://localhost:8082/movements/A1234BC/taps/application")
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
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")))
        }

        @Test
        fun `will put the message on the DLQ`() {
          assertThat(movementsDlqClient!!.countAllMessagesOnQueue(movementsDlqUrl!!).get()).isEqualTo(1)
        }
      }
    }
  }

  private fun publishAuthorisationDomainEvent(dpsAuthorisationId: UUID, prisonerNumber: String, source: String = "DPS", eventType: String = "person.temporary-absence-authorisation.approved") {
    with(eventType) {
      publishDomainEvent(eventType = this, payload = messagePayload(eventType = this, id = dpsAuthorisationId, prisonerNumber = prisonerNumber, source = source))
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
}
