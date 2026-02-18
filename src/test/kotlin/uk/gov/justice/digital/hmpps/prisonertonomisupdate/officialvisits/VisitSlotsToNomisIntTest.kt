package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.api.VisitsConfigurationResourceApi.DayOfWeekCreateVisitTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitTimeSlotRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncTimeSlot
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.VisitSlotsNomisApiMockServer.Companion.visitTimeSlotResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.DayType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class VisitSlotsToNomisIntTest(
  @Autowired
  private val nomisApi: VisitSlotsNomisApiMockServer,
  @Autowired
  private val mappingApi: VisitSlotsMappingApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Nested
  @DisplayName("official-visits-api.time-slot.created")
  inner class TimeSlotCreated {
    val dpsTimeSlotId = "12345"
    val nomisTimeSlotSequence: Int = 2
    val prisonId = "MDI"

    @Nested
    @DisplayName("when NOMIS is the origin of a time slot create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateTimeSlotDomainEvent(timeSlotId = dpsTimeSlotId, prisonId = prisonId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create is skipped`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-create-ignored"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo(dpsTimeSlotId)
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a time slot create")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTimeSlotByDpsIdOrNull(
            dpsId = dpsTimeSlotId,
            mapping = null,
          )
          dpsApi.stubGetTimeSlot(
            prisonTimeSlotId = dpsTimeSlotId.toLong(),
            response = syncTimeSlot().copy(
              prisonCode = prisonId,
              dayCode = DayType.MON,
              startTime = "10:00",
              endTime = "11:00",
              effectiveDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2030-01-01"),
            ),
          )
          nomisApi.stubCreateTimeSlot(
            prisonId = prisonId,
            dayOfWeek = DayOfWeekCreateVisitTimeSlot.MON,
            response = visitTimeSlotResponse().copy(prisonId = prisonId, dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON, timeSlotSequence = nomisTimeSlotSequence),
          )
          mappingApi.stubCreateTimeSlotMapping()
          publishCreateTimeSlotDomainEvent(timeSlotId = dpsTimeSlotId, prisonId = prisonId, source = "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create time slot in NOMIS`() {
          val request: CreateVisitTimeSlotRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/visits/configuration/time-slots/prison-id/MDI/day-of-week/MON")), jsonMapper)
          assertThat(request.startTime).isEqualTo("10:00")
          assertThat(request.endTime).isEqualTo("11:00")
          assertThat(request.effectiveDate).isEqualTo("2020-01-01")
          assertThat(request.expiryDate).isEqualTo("2030-01-01")
        }

        @Test
        fun `will create mapping`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/visit-slots/time-slots"))
              .withRequestBodyJsonPath("dpsId", dpsTimeSlotId)
              .withRequestBodyJsonPath("nomisSlotSequence", nomisTimeSlotSequence)
              .withRequestBodyJsonPath("nomisPrisonId", prisonId)
              .withRequestBodyJsonPath("nomisDayOfWeek", "MON")
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("time-slot-create-success"),
            check {
              assertThat(it["dpsTimeSlotId"]).isEqualTo(dpsTimeSlotId)
              assertThat(it["dayOfWeek"]).isEqualTo("MON")
              assertThat(it["nomisTimeSlotSequence"]).isEqualTo(nomisTimeSlotSequence.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingFailureAndRecovery {

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetTimeSlotByDpsIdOrNull(
            dpsId = dpsTimeSlotId,
            mapping = null,
          )
          dpsApi.stubGetTimeSlot(
            prisonTimeSlotId = dpsTimeSlotId.toLong(),
            response = syncTimeSlot().copy(
              prisonCode = prisonId,
              dayCode = DayType.MON,
              startTime = "10:00",
              endTime = "11:00",
              effectiveDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2030-01-01"),
            ),
          )
          nomisApi.stubCreateTimeSlot(
            prisonId = prisonId,
            dayOfWeek = DayOfWeekCreateVisitTimeSlot.MON,
            response = visitTimeSlotResponse().copy(prisonId = prisonId, dayOfWeek = VisitTimeSlotResponse.DayOfWeek.MON, timeSlotSequence = nomisTimeSlotSequence),
          )
        }

        @Nested
        inner class MappingRetry {
          @BeforeEach
          fun setUp() {
            mappingApi.stubCreateTimeSlotMappingFollowedBySuccess()
            publishCreateTimeSlotDomainEvent(timeSlotId = dpsTimeSlotId, prisonId = prisonId, source = "DPS")
            waitForAnyProcessingToComplete("time-slot-create-success")
          }

          @Test
          fun `will create time slot in NOMIS once`() {
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/visits/configuration/time-slots/prison-id/MDI/day-of-week/MON")))
          }

          @Test
          fun `will try to create mapping until it succeeds`() {
            mappingApi.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/visit-slots/time-slots")),
            )
          }
        }

        @Nested
        inner class MappingDuplicate {
          @BeforeEach
          fun setUp() {
            mappingApi.stubCreateTimeSlotMapping(
              DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = VisitTimeSlotMappingDto(
                    dpsId = dpsTimeSlotId,
                    nomisPrisonId = "MDO",
                    nomisDayOfWeek = "MON",
                    nomisSlotSequence = 1,
                    mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
                  ),
                  existing = VisitTimeSlotMappingDto(
                    dpsId = "93938593",
                    nomisPrisonId = "WWI",
                    nomisDayOfWeek = "MON",
                    nomisSlotSequence = 2,
                    mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateTimeSlotDomainEvent(timeSlotId = dpsTimeSlotId, prisonId = prisonId, source = "DPS")
            waitForAnyProcessingToComplete("to-nomis-synch-time-slot-duplicate")
          }

          @Test
          fun `will create time slot in NOMIS once`() {
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/visits/configuration/time-slots/prison-id/MDI/day-of-week/MON")))
          }

          @Test
          fun `will try to create mapping once`() {
            mappingApi.verify(
              1,
              postRequestedFor(urlEqualTo("/mapping/visit-slots/time-slots")),
            )
          }

          @Test
          fun `will send telemetry event showing the create and the duplicate mapping`() {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-time-slot-duplicate"),
              check {
                assertThat(it["dpsTimeSlotId"]).isEqualTo(dpsTimeSlotId)
                assertThat(it["dayOfWeek"]).isEqualTo("MON")
                assertThat(it["nomisTimeSlotSequence"]).isEqualTo(nomisTimeSlotSequence.toString())
                assertThat(it["prisonId"]).isEqualTo(prisonId)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.time-slot.updated")
  inner class TimeSlotUpdated {

    @Nested
    @DisplayName("when DPS is the origin of a time slot update")
    inner class WhenDpsUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateTimeSlotDomainEvent(timeSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-update-success"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.time-slot.deleted")
  inner class TimeSlotDeleted {

    @Nested
    @DisplayName("when DPS is the origin of a time slot delete")
    inner class WhenDpsDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteTimeSlotDomainEvent(timeSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the delete`() {
        verify(telemetryClient).trackEvent(
          eq("time-slot-delete-success"),
          check {
            assertThat(it["dpsTimeSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.created")
  inner class VisitSlotCreated {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishCreateVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-create-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.updated")
  inner class VisitSlotUpdated {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot update")
    inner class WhenDpsUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdateVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-update-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit-slot.deleted")
  inner class VisitSlotDeleted {

    @Nested
    @DisplayName("when DPS is the origin of a visit slot delete")
    inner class WhenDpsDeleted {

      @BeforeEach
      fun setUp() {
        publishDeleteVisitSlotDomainEvent(visitSlotId = "12345", prisonId = "MDI", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the delete`() {
        verify(telemetryClient).trackEvent(
          eq("visit-slot-delete-success"),
          check {
            assertThat(it["dpsVisitSlotId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.created") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishUpdateTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.updated") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishDeleteTimeSlotDomainEvent(
    timeSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.time-slot.deleted") {
      publishDomainEvent(eventType = this, payload = timeSlotMessagePayload(eventType = this, timeSlotId = timeSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreateVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.created") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishUpdateVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.updated") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
    }
  }

  @Suppress("SameParameterValue")
  private fun publishDeleteVisitSlotDomainEvent(
    visitSlotId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visit-slot.deleted") {
      publishDomainEvent(eventType = this, payload = visitSlotMessagePayload(eventType = this, visitSlotId = visitSlotId, source = source, prisonId = prisonId))
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

fun timeSlotMessagePayload(
  eventType: String,
  timeSlotId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "timeSlotId": "$timeSlotId",
        "prisonId": "$prisonId",
        "source": "$source"
      }
    }
    """

fun visitSlotMessagePayload(
  eventType: String,
  visitSlotId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "visitSlotId": "$visitSlotId",
        "prisonId": "$prisonId",
        "source": "$source"
      }
    }
    """
