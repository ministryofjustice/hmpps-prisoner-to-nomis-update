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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SearchLevelType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDate
import java.util.*

@Suppress("SameParameterValue")
class OfficialVisitsToNomisIntTest(
  @Autowired
  private val nomisApi: OfficialVisitsNomisApiMockServer,
  @Autowired
  private val mappingApi: OfficialVisitsMappingApiMockServer,
  @Autowired
  private val visitSlotsMappingApi: VisitSlotsMappingApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Nested
  @DisplayName("official-visits-api.visit.created")
  inner class OfficialVisitCreated {
    val offenderNo = "A1234KT"
    val prisonId = "MDI"
    val dpsOfficialVisitId = 12345L
    val nomisVisitId = 92492L
    val dpsLocationId: UUID = UUID.randomUUID()
    val nomisLocationId = 123456L
    val dpsVisitSlotId = 56789L
    val nomisVisitSlotId = 987654L

    @Nested
    @DisplayName("when NOMIS is the origin of a official visit create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateOfficialVisitDomainEvent(officialVisitId = dpsOfficialVisitId.toString(), prisonId = prisonId, offenderNo = offenderNo, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("official-visit-create-ignored"),
          check {
            assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a official visit create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetVisitByDpsIdsOrNull(dpsOfficialVisitId, null)
        dpsApi.stubGetOfficialVisit(
          officialVisitId = dpsOfficialVisitId,
          response = syncOfficialVisit().copy(
            officialVisitId = dpsOfficialVisitId,
            prisonCode = prisonId,
            dpsLocationId = dpsLocationId,
            prisonVisitSlotId = dpsVisitSlotId,
            prisonerNumber = offenderNo,
            visitDate = LocalDate.parse("2020-01-01"),
            startTime = "10:00",
            endTime = "11:00",
            overrideBanStaffUsername = "T.SMITH",
            statusCode = VisitStatusType.CANCELLED,
            prisonerAttendance = AttendanceType.ATTENDED,
            visitComments = "First visit",
            visitorConcernNotes = "Very concerned",
            searchType = SearchLevelType.RUB_A,
            completionCode = VisitCompletionType.STAFF_CANCELLED,
          ),
        )
        visitSlotsMappingApi.stubGetVisitSlotByDpsId(
          dpsVisitSlotId.toString(),
          VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
          ),
        )
        visitSlotsMappingApi.stubGetInternalLocationByDpsId(
          dpsLocationId.toString(),
          LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )

        nomisApi.stubCreateOfficialVisit(
          offenderNo = offenderNo,
          response = officialVisitResponse().copy(visitId = nomisVisitId),
        )
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitMapping()
          publishCreateOfficialVisitDomainEvent(officialVisitId = dpsOfficialVisitId.toString(), prisonId = prisonId, offenderNo = offenderNo, source = "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create time slot in NOMIS`() {
          val request: CreateOfficialVisitRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/prisoner/$offenderNo/official-visits")), jsonMapper)
          assertThat(request.startDateTime).isEqualTo("2020-01-01T10:00")
          assertThat(request.endDateTime).isEqualTo("2020-01-01T11:00")
          assertThat(request.prisonId).isEqualTo(prisonId)
          assertThat(request.visitSlotId).isEqualTo(nomisVisitSlotId)
          assertThat(request.internalLocationId).isEqualTo(nomisLocationId)
          assertThat(request.overrideBanStaffUsername).isEqualTo("T.SMITH")
          assertThat(request.visitStatusCode).isEqualTo("CANC")
          assertThat(request.prisonerAttendanceCode).isEqualTo("ATT")
          assertThat(request.commentText).isEqualTo("First visit")
          assertThat(request.visitorConcernText).isEqualTo("Very concerned")
          assertThat(request.prisonerSearchTypeCode).isEqualTo("RUB_A")
          assertThat(request.visitOutcomeCode).isEqualTo("HMP")
        }

        @Test
        fun `will create mapping`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/official-visits/visit"))
              .withRequestBodyJsonPath("dpsId", dpsOfficialVisitId)
              .withRequestBodyJsonPath("nomisId", nomisVisitId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("official-visit-create-success"),
            check {
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["dpsLocationId"]).isEqualTo(dpsLocationId.toString())
              assertThat(it["nomisLocationId"]).isEqualTo(nomisLocationId.toString())
              assertThat(it["dpsVisitSlotId"]).isEqualTo(dpsVisitSlotId.toString())
              assertThat(it["nomisVisitSlotId"]).isEqualTo(nomisVisitSlotId.toString())
              assertThat(it["offenderNo"]).isEqualTo(offenderNo)
              assertThat(it["prisonId"]).isEqualTo(prisonId)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingRetry {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitMappingFailureFollowedBySuccess()
          publishCreateOfficialVisitDomainEvent(officialVisitId = dpsOfficialVisitId.toString(), prisonId = prisonId, offenderNo = offenderNo, source = "DPS")
          waitForAnyProcessingToComplete("official-visit-create-success")
        }

        @Test
        fun `will create visit in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoner/$offenderNo/official-visits")))
        }

        @Test
        fun `will try to create mapping until it succeeds`() {
          mappingApi.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/official-visits/visit")),
          )
        }
      }

      @Nested
      inner class MappingDuplicate {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitMapping(
            DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = OfficialVisitMappingDto(
                  dpsId = dpsOfficialVisitId.toString(),
                  nomisId = nomisVisitId,
                  mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
                ),
                existing = OfficialVisitMappingDto(
                  dpsId = "93938593",
                  nomisId = nomisVisitId,
                  mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateOfficialVisitDomainEvent(officialVisitId = dpsOfficialVisitId.toString(), prisonId = prisonId, offenderNo = offenderNo, source = "DPS")
          waitForAnyProcessingToComplete("to-nomis-synch-official-visit-duplicate")
        }

        @Test
        fun `will create visit in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoner/$offenderNo/official-visits")))
        }

        @Test
        fun `will try to create mapping once`() {
          mappingApi.verify(
            1,
            postRequestedFor(urlEqualTo("/mapping/official-visits/visit")),
          )
        }

        @Test
        fun `will send telemetry event showing the duplicate mapping`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-official-visit-duplicate"),
            check {
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visit.deleted")
  inner class OfficialVisitDeleted {

    @Nested
    @DisplayName("when mapping exists")
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        publishDeleteOfficialVisitDomainEvent(officialVisitId = "12345", prisonId = "MDI", offenderNo = "A1234KT", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("official-visit-delete-success"),
          check {
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("12345")
            assertThat(it["offenderNo"]).isEqualTo("A1234KT")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visitor.created")
  inner class OfficialVisitorCreated {
    val dpsOfficialVisitId = 12345L
    val nomisVisitId = 92492L
    val dpsOfficialVisitorId = 248240284L
    val nomisVisitorId = 523661L
    val contactAndPersonId = 1373L
    val prisonId = "MDI"

    @Nested
    @DisplayName("when NOMIS is the origin of a official visitor create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreateOfficialVisitorDomainEvent(officialVisitorId = dpsOfficialVisitorId.toString(), officialVisitId = dpsOfficialVisitId.toString(), contactId = contactAndPersonId.toString(), prisonId = prisonId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("official-visitor-create-ignored"),
          check {
            assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
            assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
            assertThat(it["dpsContactId"]).isEqualTo(contactAndPersonId.toString())
            assertThat(it["prisonId"]).isEqualTo(prisonId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a official visit create")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        mappingApi.stubGetVisitorByDpsIdsOrNull(dpsOfficialVisitId, null)
        dpsApi.stubGetOfficialVisit(
          officialVisitId = dpsOfficialVisitId,
          response = syncOfficialVisit().copy(
            officialVisitId = dpsOfficialVisitId,
            prisonCode = prisonId,
            visitors = listOf(
              syncOfficialVisitor().copy(
                officialVisitorId = dpsOfficialVisitorId,
                contactId = contactAndPersonId,
                attendanceCode = AttendanceType.ATTENDED,
                leadVisitor = false,
                assistedVisit = true,
                visitorNotes = "First time visit",
              ),
            ),
          ),
        )
        mappingApi.stubGetVisitByDpsIdsOrNull(
          dpsOfficialVisitId,
          OfficialVisitMappingDto(
            dpsId = dpsOfficialVisitId.toString(),
            nomisId = nomisVisitId,
            mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
          ),
        )

        nomisApi.stubCreateOfficialVisitor(
          visitId = nomisVisitId,
          response = officialVisitor().copy(id = nomisVisitorId),
        )
      }

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitorMapping()
          publishCreateOfficialVisitorDomainEvent(officialVisitorId = dpsOfficialVisitorId.toString(), officialVisitId = dpsOfficialVisitId.toString(), contactId = contactAndPersonId.toString(), prisonId = prisonId, source = "DPS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create time slot in NOMIS`() {
          val request: CreateOfficialVisitorRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor")), jsonMapper)
          assertThat(request.personId).isEqualTo(contactAndPersonId)
          assertThat(request.commentText).isEqualTo("First time visit")
          assertThat(request.leadVisitor).isFalse
          assertThat(request.assistedVisit).isTrue
          assertThat(request.visitorAttendanceOutcomeCode).isEqualTo("ATT")
        }

        @Test
        fun `will create mapping`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/official-visits/visitor"))
              .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId)
              .withRequestBodyJsonPath("nomisId", nomisVisitorId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("official-visitor-create-success"),
            check {
              assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
              assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["dpsContactId"]).isEqualTo(contactAndPersonId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class MappingRetry {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitorMappingFailureFollowedBySuccess()
          publishCreateOfficialVisitorDomainEvent(officialVisitorId = dpsOfficialVisitorId.toString(), officialVisitId = dpsOfficialVisitId.toString(), contactId = contactAndPersonId.toString(), prisonId = prisonId, source = "DPS")
          waitForAnyProcessingToComplete("official-visitor-create-success")
        }

        @Test
        fun `will create visit in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor")))
        }

        @Test
        fun `will try to create mapping until it succeeds`() {
          mappingApi.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/official-visits/visitor")),
          )
        }
      }

      @Nested
      inner class MappingDuplicate {
        @BeforeEach
        fun setUp() {
          mappingApi.stubCreateVisitorMapping(
            DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = OfficialVisitorMappingDto(
                  dpsId = dpsOfficialVisitorId.toString(),
                  nomisId = nomisVisitorId,
                  mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
                ),
                existing = OfficialVisitorMappingDto(
                  dpsId = "93938593",
                  nomisId = nomisVisitorId,
                  mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreateOfficialVisitorDomainEvent(officialVisitorId = dpsOfficialVisitorId.toString(), officialVisitId = dpsOfficialVisitId.toString(), contactId = contactAndPersonId.toString(), prisonId = prisonId, source = "DPS")
          waitForAnyProcessingToComplete("to-nomis-synch-official-visitor-duplicate")
        }

        @Test
        fun `will create visitor in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor")))
        }

        @Test
        fun `will try to create mapping once`() {
          mappingApi.verify(
            1,
            postRequestedFor(urlEqualTo("/mapping/official-visits/visitor")),
          )
        }

        @Test
        fun `will send telemetry event showing the duplicate mapping`() {
          verify(telemetryClient).trackEvent(
            eq("to-nomis-synch-official-visitor-duplicate"),
            check {
              assertThat(it["dpsOfficialVisitorId"]).isEqualTo(dpsOfficialVisitorId.toString())
              assertThat(it["nomisVisitorId"]).isEqualTo(nomisVisitorId.toString())
              assertThat(it["dpsOfficialVisitId"]).isEqualTo(dpsOfficialVisitId.toString())
              assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
              assertThat(it["prisonId"]).isEqualTo(prisonId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visitor.updated")
  inner class OfficialVisitorUpdated {

    @Nested
    @DisplayName("when DPS is the origin of a visitor update")
    inner class WhenDpsCreated {

      @BeforeEach
      fun setUp() {
        publishUpdateOfficialVisitorDomainEvent(officialVisitorId = "7765", officialVisitId = "12345", contactId = "9855", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("official-visitor-update-success"),
          check {
            assertThat(it["dpsOfficialVisitorId"]).isEqualTo("7765")
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("12345")
            assertThat(it["dpsContactId"]).isEqualTo("9855")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("official-visits-api.visitor.deleted")
  inner class OfficialVisitorDeleted {

    @Nested
    @DisplayName("when mapping exists")
    inner class WhenMappingExists {

      @BeforeEach
      fun setUp() {
        publishDeleteOfficialVisitorDomainEvent(officialVisitorId = "7765", officialVisitId = "12345", prisonId = "MDI", source = "DPS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the create`() {
        verify(telemetryClient).trackEvent(
          eq("official-visitor-delete-success"),
          check {
            assertThat(it["dpsOfficialVisitorId"]).isEqualTo("7765")
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("12345")
            assertThat(it["prisonId"]).isEqualTo("MDI")
          },
          isNull(),
        )
      }
    }
  }

  private fun publishCreateOfficialVisitDomainEvent(
    officialVisitId: String,
    source: String = "DPS",
    prisonId: String,
    offenderNo: String,
  ) {
    with("official-visits-api.visit.created") {
      publishDomainEvent(eventType = this, payload = visitMessagePayload(eventType = this, officialVisitId = officialVisitId, source = source, prisonId = prisonId, offenderNo = offenderNo))
    }
  }

  private fun publishDeleteOfficialVisitDomainEvent(
    officialVisitId: String,
    source: String = "DPS",
    prisonId: String,
    offenderNo: String,
  ) {
    with("official-visits-api.visit.deleted") {
      publishDomainEvent(eventType = this, payload = visitMessagePayload(eventType = this, officialVisitId = officialVisitId, source = source, prisonId = prisonId, offenderNo = offenderNo))
    }
  }

  private fun publishCreateOfficialVisitorDomainEvent(
    officialVisitorId: String,
    officialVisitId: String,
    contactId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visitor.created") {
      publishDomainEvent(eventType = this, payload = visitorMessagePayload(eventType = this, officialVisitorId = officialVisitorId, officialVisitId = officialVisitId, source = source, prisonId = prisonId, contactId = contactId))
    }
  }
  private fun publishUpdateOfficialVisitorDomainEvent(
    officialVisitorId: String,
    officialVisitId: String,
    contactId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visitor.updated") {
      publishDomainEvent(eventType = this, payload = visitorMessagePayload(eventType = this, officialVisitorId = officialVisitorId, officialVisitId = officialVisitId, source = source, prisonId = prisonId, contactId = contactId))
    }
  }

  private fun publishDeleteOfficialVisitorDomainEvent(
    officialVisitorId: String,
    officialVisitId: String,
    source: String = "DPS",
    prisonId: String,
  ) {
    with("official-visits-api.visitor.deleted") {
      publishDomainEvent(eventType = this, payload = visitorDeletedMessagePayload(eventType = this, officialVisitorId = officialVisitorId, officialVisitId = officialVisitId, source = source, prisonId = prisonId))
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

fun visitMessagePayload(
  eventType: String,
  officialVisitId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
  offenderNo: String = "A1234KT",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "officialVisitId": "$officialVisitId",
        "prisonId": "$prisonId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$offenderNo"
          }
        ]
      }
    }
    """
fun visitorMessagePayload(
  eventType: String,
  officialVisitorId: String,
  officialVisitId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
  contactId: String = "1234",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "officialVisitorId": "$officialVisitorId",
        "officialVisitId": "$officialVisitId",
        "prisonId": "$prisonId",
        "source": "$source"
      },
      "personReference": {
        "identifiers": [
          {
            "type": "CONTACT_ID",
            "value": "$contactId"
          }
        ]
      }
    }
    """
fun visitorDeletedMessagePayload(
  eventType: String,
  officialVisitorId: String,
  officialVisitId: String,
  prisonId: String = "MDI",
  source: String = "DPS",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "officialVisitorId": "$officialVisitorId",
        "officialVisitId": "$officialVisitId",
        "prisonId": "$prisonId",
        "source": "$source"
      },
      "personReference": null
    }
    """
