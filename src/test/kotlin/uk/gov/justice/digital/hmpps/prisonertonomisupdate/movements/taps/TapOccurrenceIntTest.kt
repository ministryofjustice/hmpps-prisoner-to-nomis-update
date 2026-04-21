package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
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
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.upsertTapApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapNomisApiMockServer.Companion.upsertTapScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.*

class TapOccurrenceIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var nomisApi: TapNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ExternalMovementsMappingApiMockServer

  private val dpsApi: TapDpsApiMockServer = dpsExternalMovementsServer

  private val today = LocalDateTime.now()
  private val tomorrow = today.plusDays(1)

  @Nested
  @DisplayName("person.temporary-absence.scheduled (created)")
  inner class TapOccurrenceMovementCreated {
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId, addressId = 54321, addressOwnerClass = "OFF"))
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
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out")))
        }

        @Test
        fun `the created scheduled movement will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventId", absent())
              .withRequestBodyJsonPath("tapApplicationId", nomisApplicationId)
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId, addressId = 54321, addressOwnerClass = "OFF"))
          mappingApi.stubCreateScheduledMovementMapping()

          // stubs for the authorisation sync
          mappingApi.stubGetTemporaryAbsenceApplicationMapping(dpsId = dpsAuthorisationId, nomisMovementApplicationId = nomisApplicationId)
          dpsApi.stubGetTapAuthorisation(dpsAuthorisationId, response = dpsApi.tapAuthorisation(id = dpsAuthorisationId))
          nomisApi.stubUpsertTapApplication(prisonerNumber, upsertTapApplicationResponse())

          // publish event that triggers authorisation sync
          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.rescheduled")
          waitForAnyProcessingToComplete("temporary-absence-application-update-success")
        }

        @Test
        fun `will upsert the schedule in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out")))
        }

        @Test
        fun `will upsert the application in NOMIS`() {
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/application")))
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId))
          mappingApi.stubCreateScheduledMovementMappingFailureFollowedBySuccess()

          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.scheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-create-mapping-create-error"),
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/schedule/out")))
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId))
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
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-mapping-create-error")
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/schedule/out")))
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
          waitForAnyProcessingToComplete("temporary-absence-schedule-create-awaiting-parent")
        }

        @Test
        fun `will not create the application in NOMIS`() {
          nomisApi.verify(count = 0, postRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out")))
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
            eq("temporary-absence-schedule-create-awaiting-parent"),
            check {
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("error", "Expected parent entity not found, retrying")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence.scheduled (updated)")
  inner class TapOccurrenceMovementUpdated {
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId, addressId = 54321))

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
          nomisApi.verify(putRequestedFor(urlPathEqualTo("/movements/A1234BC/taps/schedule/out")))
        }

        @Test
        fun `the created scheduled movement will contain correct details`() {
          nomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("eventId", nomisEventId)
              .withRequestBodyJsonPath("tapApplicationId", nomisApplicationId)
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
          nomisApi.stubUpsertTapScheduleOut(prisonerNumber, upsertTapScheduleOutResponse(eventId = nomisEventId, addressId = 54321))
          mappingApi.stubUpdateScheduledMovementMappingFailureFollowedBySuccess()

          // publish an update event
          publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, "DPS", "person.temporary-absence.rescheduled")
          waitForAnyProcessingToComplete("temporary-absence-schedule-update-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("temporary-absence-schedule-update-mapping-create-error"),
              check {
                assertThat(it).containsEntry("error", "500 Internal Server Error from PUT http://localhost:8084/mapping/temporary-absence/scheduled-movement")
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
            nomisApi.verify(1, putRequestedFor(urlEqualTo("/movements/A1234BC/taps/schedule/out")))
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
          waitForAnyProcessingToComplete("temporary-absence-schedule-update-awaiting-parent")
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
            eq("temporary-absence-schedule-update-awaiting-parent"),
            check {
              assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
              assertThat(it).containsEntry("dpsAuthorisationId", dpsAuthorisationId.toString())
              assertThat(it).containsEntry("error", "Expected parent entity not found, retrying")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("person.temporary-absence.unscheduled")
  inner class TapOccurrenceMovementDeleted {
    private val prisonerNumber = "A1234BC"
    private val dpsOccurrenceId = UUID.randomUUID()
    private val nomisEventId = 54321L

    @BeforeEach
    fun setUp() {
      mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, nomisEventId = nomisEventId, addressId = 54321)
      nomisApi.stubDeleteTapScheduleOut(prisonerNumber, nomisEventId)
    }

    private fun publishDeleteEvent(source: String = "DPS", completedTelemetry: String? = null) {
      publishTapOccurrenceDomainEvent(dpsOccurrenceId, prisonerNumber, source, "person.temporary-absence.unscheduled")
      if (completedTelemetry == null) {
        waitForAnyProcessingToComplete()
      } else {
        waitForAnyProcessingToComplete(completedTelemetry)
      }
    }

    @Test
    fun `should delete the scheduled movement in NOMIS`() {
      publishDeleteEvent()

      nomisApi.verify(deleteRequestedFor(urlEqualTo("/movements/$prisonerNumber/taps/schedule/out/$nomisEventId")))
    }

    @Test
    fun `should publish telemetry`() {
      publishDeleteEvent()

      verify(telemetryClient).trackEvent(
        eq("temporary-absence-schedule-delete-success"),
        check {
          assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
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
        eq("temporary-absence-schedule-delete-ignored"),
        check {
          assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if mapping does not exist`() {
      mappingApi.stubGetTemporaryAbsenceScheduledMovementMapping(dpsId = dpsOccurrenceId, status = NOT_FOUND)

      publishDeleteEvent(completedTelemetry = "temporary-absence-schedule-delete-error")

      await untilAsserted {
        assertThat(movementsDlqClient!!.countAllMessagesOnQueue(movementsDlqUrl!!).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("temporary-absence-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
          assertThat(it).containsEntry("error", "Cannot find scheduled movement mapping for $dpsOccurrenceId")
        },
        isNull(),
      )
    }

    @Test
    fun `should end up on DLQ if NOMIS returns a conflict`() {
      nomisApi.stubDeleteTapScheduleOut(prisonerNumber, nomisEventId, status = CONFLICT)

      publishDeleteEvent(completedTelemetry = "temporary-absence-schedule-delete-error")

      await untilAsserted {
        assertThat(movementsDlqClient!!.countAllMessagesOnQueue(movementsDlqUrl!!).get()).isEqualTo(1)
      }

      verify(telemetryClient).trackEvent(
        eq("temporary-absence-schedule-delete-error"),
        check {
          assertThat(it).containsEntry("offenderNo", prisonerNumber)
          assertThat(it).containsEntry("dpsOccurrenceId", dpsOccurrenceId.toString())
          assertThat(it).containsEntry("error", "409 Conflict from DELETE http://localhost:8082/movements/$prisonerNumber/taps/schedule/out/$nomisEventId")
        },
        isNull(),
      )
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
