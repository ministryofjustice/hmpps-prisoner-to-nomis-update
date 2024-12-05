package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension.Companion.caseNotesDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDateTime
import java.util.UUID

private val NOMIS_CASE_NOTE_ID2 = 123456L
private val OFFENDER_NO = "A1234KT"
private val DPS_CASE_NOTE_ID = UUID.randomUUID().toString()
private val NOMIS_BOOKING_ID = 1L
private val NOMIS_CASE_NOTE_ID = 3L

class CaseNotesToNomisIntTest : SqsIntegrationTestBase() {

  @Autowired
  private lateinit var caseNotesNomisApi: CaseNotesNomisApiMockServer

  @Autowired
  private lateinit var caseNotesMappingApi: CaseNotesMappingApiMockServer

  @Nested
  @DisplayName("person.case-note.created")
  inner class CaseNoteCreated {
    @Nested
    @DisplayName("when NOMIS is the origin of a CaseNote create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCreateCaseNoteDomainEvent(source = CaseNoteSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the create`() {
        verify(telemetryClient).trackEvent(
          eq("casenotes-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the CaseNote in NOMIS`() {
        caseNotesNomisApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when casenote is DPS-only")
    inner class WhenDpsOnly {
      @BeforeEach
      fun setup() {
        publishCreateCaseNoteDomainEvent(syncToNomis = false)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the create`() {
        verify(telemetryClient).trackEvent(
          eq("casenotes-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the CaseNote in NOMIS`() {
        caseNotesNomisApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a CaseNote create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          caseNotesDpsApi.stubGetCaseNote(
            dpsCaseNote().copy(
              caseNoteId = DPS_CASE_NOTE_ID,
              offenderIdentifier = OFFENDER_NO,
              type = "HPI",
            ),
          )
          caseNotesNomisApi.stubPostCaseNote(OFFENDER_NO, NOMIS_CASE_NOTE_ID)
          caseNotesMappingApi.stubPostMapping()
          publishCreateCaseNoteDomainEvent(offenderNo = OFFENDER_NO, caseNoteUuid = DPS_CASE_NOTE_ID)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the caseNote created`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-create-success"),
            check {
              assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("mappingType", "DPS_CREATED")
              assertThat(it).containsEntry("nomisCaseNoteId", "$NOMIS_CASE_NOTE_ID")
              assertThat(it).containsEntry("nomisBookingId", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get caseNote details`() {
          caseNotesDpsApi.verify(getRequestedFor(urlMatching("/case-notes/$OFFENDER_NO/$DPS_CASE_NOTE_ID")))
        }

        @Test
        fun `will create the caseNote in NOMIS`() {
          caseNotesNomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/casenotes")))
        }

        @Test
        fun `the created caseNote will contain details of the DPS caseNote`() {
          caseNotesNomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("caseNoteType", "HPI")
              .withRequestBodyJsonPath("caseNoteSubType", "Y")
              .withRequestBodyJsonPath("occurrenceDateTime", "2024-01-01T01:01")
              .withRequestBodyJsonPath("authorUsername", "ME.COM")
              .withRequestBodyJsonPath("caseNoteText", "contents of case note"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          caseNotesMappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/casenotes"))
              .withRequestBodyJsonPath("offenderNo", OFFENDER_NO)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED")
              .withRequestBodyJsonPath("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              .withRequestBodyJsonPath("nomisCaseNoteId", NOMIS_CASE_NOTE_ID)
              .withRequestBodyJsonPath("nomisBookingId", NOMIS_BOOKING_ID),
          )
        }

        @Test
        fun `the created mapping will contain the IDs`() {
          caseNotesMappingApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              .withRequestBodyJsonPath("nomisBookingId", NOMIS_BOOKING_ID)
              .withRequestBodyJsonPath("nomisCaseNoteId", NOMIS_CASE_NOTE_ID)
              .withRequestBodyJsonPath("mappingType", CaseNoteMappingDto.MappingType.DPS_CREATED.name),
          )
        }
      }

      @Nested
      @DisplayName("when the create of the mapping fails")
      inner class WithCreateMappingFailures {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          caseNotesDpsApi.stubGetCaseNote(dpsCaseNote().copy(caseNoteId = DPS_CASE_NOTE_ID))
          caseNotesNomisApi.stubPostCaseNote(OFFENDER_NO, NOMIS_CASE_NOTE_ID)
        }

        @Nested
        @DisplayName("fails once")
        inner class MappingFailsOnce {
          @BeforeEach
          fun setUp() {
            caseNotesMappingApi.stubPostMappingFollowedBySuccess(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateCaseNoteDomainEvent(offenderNo = OFFENDER_NO, caseNoteUuid = DPS_CASE_NOTE_ID)
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the caseNote in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-create-success"),
                any(),
                isNull(),
              )
            }

            caseNotesNomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/casenotes")))
          }

          @Test
          fun `telemetry will contain key facts about the caseNote created`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("casenotes-create-success"),
                check {
                  assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
                  assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
                  assertThat(it).containsEntry("mappingType", "DPS_CREATED")
                  assertThat(it).containsEntry("nomisCaseNoteId", "$NOMIS_CASE_NOTE_ID")
                  assertThat(it).containsEntry("nomisBookingId", "$NOMIS_BOOKING_ID")
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("always fails")
        inner class MappingAlwaysFails {
          @BeforeEach
          fun setUp() {
            caseNotesMappingApi.stubPostMapping(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateCaseNoteDomainEvent(offenderNo = OFFENDER_NO, caseNoteUuid = DPS_CASE_NOTE_ID)
          }

          @Test
          fun `will add message to dead letter queue`() {
            await untilCallTo {
              awsSqsCaseNotesDlqClient!!.countAllMessagesOnQueue(caseNotesDlqUrl!!).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create the casenote in NOMIS once`() {
            await untilCallTo {
              awsSqsCaseNotesDlqClient!!.countAllMessagesOnQueue(caseNotesDlqUrl!!).get()
            } matches { it == 1 }

            caseNotesNomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/casenotes")))
          }
        }
      }

      @Nested
      @DisplayName("when caseNote has already been created")
      inner class WhenCaseNoteAlreadyCreated {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            dpsCaseNoteId = DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          publishCreateCaseNoteDomainEvent(offenderNo = OFFENDER_NO, caseNoteUuid = DPS_CASE_NOTE_ID)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `it will not call back to DPS API`() {
          caseNotesDpsApi.verify(0, getRequestedFor(anyUrl()))
        }

        @Test
        fun `it will not create the caseNote again in NOMIS`() {
          caseNotesNomisApi.verify(0, postRequestedFor(anyUrl()))
        }
      }
    }
  }

  @Nested
  @DisplayName("person.case-note.updated")
  inner class CaseNoteUpdated {
    @Nested
    @DisplayName("when NOMIS is the origin of the CaseNote update")
    inner class WhenNomisUpdated {
      @BeforeEach
      fun setup() {
        publishUpdateCaseNoteDomainEvent(source = CaseNoteSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("casenotes-amend-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to update the CaseNote in NOMIS`() {
        caseNotesNomisApi.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when casenote is DPS-only")
    inner class WhenDpsOnly {
      @BeforeEach
      fun setup() {
        publishUpdateCaseNoteDomainEvent(syncToNomis = false)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("casenotes-amend-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to update the CaseNote in NOMIS`() {
        caseNotesNomisApi.verify(0, putRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the CaseNote update")
    inner class WhenDpsUpdated {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          publishUpdateCaseNoteDomainEvent()
        }

        @Test
        fun `will treat this as an error and message will go on DLQ`() {
          await untilCallTo {
            awsSqsCaseNotesDlqClient!!.countAllMessagesOnQueue(caseNotesDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will send telemetry event showing it failed to update for each retry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("casenotes-amend-failed"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class WhenMappingIsFound {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID2,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesDpsApi.stubGetCaseNote(
            caseNote = dpsCaseNote().copy(
              caseNoteId = DPS_CASE_NOTE_ID,
              offenderIdentifier = OFFENDER_NO,
              type = "HPI",
              amendments = listOf(
                CaseNoteAmendment(
                  additionalNoteText = "amendment",
                  authorUserId = "54321",
                  creationDateTime = LocalDateTime.parse("2024-05-06T07:08:09"),
                  authorName = "ME SMITH",
                  authorUserName = "ME",
                ),
              ),
            ),
          )
          caseNotesNomisApi.stubPutCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID2)
          publishUpdateCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-amend-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the updated caseNote`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-amend-success"),
            check {
              assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("nomisCaseNoteId-1", "$NOMIS_CASE_NOTE_ID2")
              assertThat(it).containsEntry("nomisBookingId-1", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS caseNote id`() {
          caseNotesMappingApi.verify(getRequestedFor(urlMatching("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID/all")))
        }

        @Test
        fun `will call back to DPS to get caseNote details`() {
          caseNotesDpsApi.verify(getRequestedFor(urlMatching("/case-notes/$OFFENDER_NO/$DPS_CASE_NOTE_ID")))
        }

        @Test
        fun `will update the caseNote in NOMIS`() {
          caseNotesNomisApi.verify(putRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID2")))
        }

        @Test
        fun `the update caseNote will contain details of the DPS caseNote`() {
          caseNotesNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("text", "contents of case note")
              .withRequestBodyJsonPath("amendments[0].text", "amendment")
              .withRequestBodyJsonPath("amendments[0].authorUsername", "ME")
              .withRequestBodyJsonPath("amendments[0].createdDateTime", "2024-05-06T07:08:09"),
          )
        }
      }

      @Nested
      @DisplayName("when there is a merge duplicate")
      inner class WhenMerge {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID2,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesDpsApi.stubGetCaseNote(
            caseNote = dpsCaseNote().copy(
              caseNoteId = DPS_CASE_NOTE_ID,
              offenderIdentifier = OFFENDER_NO,
              type = "HPI",
              amendments = listOf(
                CaseNoteAmendment(
                  additionalNoteText = "amendment",
                  authorUserId = "54321",
                  creationDateTime = LocalDateTime.parse("2024-05-06T07:08:09"),
                  authorName = "ME SMITH",
                  authorUserName = "ME",
                ),
              ),
            ),
          )
          caseNotesNomisApi.stubPutCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID)
          caseNotesNomisApi.stubPutCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID2)
          publishUpdateCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event containing key facts about the updated caseNote`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-amend-success"),
            check {
              assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("nomisCaseNoteId-1", "$NOMIS_CASE_NOTE_ID")
              assertThat(it).containsEntry("nomisBookingId-1", "$NOMIS_BOOKING_ID")
              assertThat(it).containsEntry("nomisCaseNoteId-2", "$NOMIS_CASE_NOTE_ID2")
              assertThat(it).containsEntry("nomisBookingId-2", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS caseNote id`() {
          caseNotesMappingApi.verify(getRequestedFor(urlMatching("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID/all")))
        }

        @Test
        fun `will call back to DPS to get caseNote details`() {
          caseNotesDpsApi.verify(getRequestedFor(urlMatching("/case-notes/$OFFENDER_NO/$DPS_CASE_NOTE_ID")))
        }

        @Test
        fun `will update the caseNotes in NOMIS with details of the DPS caseNote`() {
          caseNotesNomisApi.verify(
            putRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID"))
              .withRequestBodyJsonPath("text", "contents of case note")
              .withRequestBodyJsonPath("amendments[0].text", "amendment")
              .withRequestBodyJsonPath("amendments[0].authorUsername", "ME")
              .withRequestBodyJsonPath("amendments[0].createdDateTime", "2024-05-06T07:08:09"),
          )
          caseNotesNomisApi.verify(
            putRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID2"))
              .withRequestBodyJsonPath("text", "contents of case note")
              .withRequestBodyJsonPath("amendments[0].text", "amendment")
              .withRequestBodyJsonPath("amendments[0].authorUsername", "ME")
              .withRequestBodyJsonPath("amendments[0].createdDateTime", "2024-05-06T07:08:09"),
          )
        }
      }

      @Nested
      inner class Exceptions {
        @Nested
        @DisplayName("when DPS fails")
        inner class WhenUnexpectedDPsError {
          @BeforeEach
          fun setUp() {
            caseNotesMappingApi.stubGetByDpsId(
              DPS_CASE_NOTE_ID,
              listOf(
                CaseNoteMappingDto(
                  dpsCaseNoteId = DPS_CASE_NOTE_ID,
                  nomisBookingId = NOMIS_BOOKING_ID,
                  offenderNo = "A1234AA",
                  nomisCaseNoteId = NOMIS_CASE_NOTE_ID2,
                  mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
                ),
              ),
            )
            caseNotesDpsApi.stubGetCaseNote(
              status = 500,
            )
            publishUpdateCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
            waitForAnyProcessingToComplete(3)
          }

          @Test
          fun `will treat this as an error and message will go on DLQ`() {
            await untilCallTo {
              awsSqsCaseNotesDlqClient!!.countAllMessagesOnQueue(caseNotesDlqUrl!!).get()
            } matches { it == 1 }
          }

          @Test
          fun `will send telemetry event showing it failed to update for each retry`() {
            await untilAsserted {
              verify(telemetryClient, times(3)).trackEvent(
                eq("casenotes-amend-failed"),
                any(),
                isNull(),
              )
            }
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("person.case-note.deleted")
  inner class CaseNoteDeleted {
    @Nested
    @DisplayName("when NOMIS is the origin of the CaseNote delete")
    inner class WhenNomisDeleted {
      @Nested
      inner class WhenUserDeleted {
        @BeforeEach
        fun setup() {
          publishDeleteCaseNoteDomainEvent(source = CaseNoteSource.NOMIS)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing it ignored the update`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-deleted-ignored"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `will not try to delete the CaseNote in NOMIS`() {
          caseNotesNomisApi.verify(0, deleteRequestedFor(anyUrl()))
        }
      }
    }

    @Nested
    @DisplayName("when casenote is DPS-only")
    inner class WhenDpsOnly {
      @BeforeEach
      fun setup() {
        publishDeleteCaseNoteDomainEvent(syncToNomis = false)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("casenotes-deleted-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to delete the CaseNote in NOMIS`() {
        caseNotesNomisApi.verify(0, deleteRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the CaseNote delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(HttpStatus.NOT_FOUND)
          publishDeleteCaseNoteDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will ignore request since delete may have happened already by previous event`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-deleted-skipped"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class WhenMappingIsFound {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesNomisApi.stubDeleteCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID)
          caseNotesMappingApi.stubDeleteByDpsId(DPS_CASE_NOTE_ID)
          publishDeleteCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-deleted-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the deleted caseNote`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-deleted-success"),
            check {
              assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("nomisCaseNoteId-1", "$NOMIS_CASE_NOTE_ID")
              assertThat(it).containsEntry("nomisBookingId-1", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS caseNote id`() {
          caseNotesMappingApi.verify(getRequestedFor(urlMatching("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID/all")))
        }

        @Test
        fun `will delete the caseNote in NOMIS`() {
          caseNotesNomisApi.verify(deleteRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID")))
        }

        @Test
        fun `will delete the caseNote mapping`() {
          caseNotesMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")))
        }
      }

      @Nested
      @DisplayName("when there are merged duplicates")
      inner class WhenMerges {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID2,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesNomisApi.stubDeleteCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID)
          caseNotesNomisApi.stubDeleteCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID2)
          caseNotesMappingApi.stubDeleteByDpsId(DPS_CASE_NOTE_ID)
          publishDeleteCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry containing key facts about the deleted caseNote`() {
          verify(telemetryClient).trackEvent(
            eq("casenotes-deleted-success"),
            check {
              assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
              assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              assertThat(it).containsEntry("nomisCaseNoteId-1", "$NOMIS_CASE_NOTE_ID")
              assertThat(it).containsEntry("nomisBookingId-1", "$NOMIS_BOOKING_ID")
              assertThat(it).containsEntry("nomisCaseNoteId-2", "$NOMIS_CASE_NOTE_ID2")
              assertThat(it).containsEntry("nomisBookingId-2", "$NOMIS_BOOKING_ID")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS caseNote id`() {
          caseNotesMappingApi.verify(getRequestedFor(urlMatching("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID/all")))
        }

        @Test
        fun `will delete the caseNote in NOMIS`() {
          caseNotesNomisApi.verify(deleteRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID")))
          caseNotesNomisApi.verify(deleteRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID2")))
        }

        @Test
        fun `will delete the caseNote mapping`() {
          caseNotesMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")))
        }
      }

      @Nested
      @DisplayName("when mapping delete fails")
      inner class WhenMappingDeleteFails {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = "A1234AA",
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesNomisApi.stubDeleteCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID)
          caseNotesMappingApi.stubDeleteByDpsId(status = HttpStatus.INTERNAL_SERVER_ERROR)
          publishDeleteCaseNoteDomainEvent(caseNoteUuid = DPS_CASE_NOTE_ID, offenderNo = OFFENDER_NO)
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("casenotes-deleted-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will delete the caseNote in NOMIS`() {
          caseNotesNomisApi.verify(deleteRequestedFor(urlEqualTo("/casenotes/$NOMIS_CASE_NOTE_ID")))
        }

        @Test
        fun `will try delete the caseNote mapping once and ignore failure`() {
          caseNotesMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID")))
          verify(telemetryClient).trackEvent(
            eq("casenotes-mapping-deleted-failed"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreateCaseNoteDomainEvent(
    offenderNo: String = OFFENDER_NO,
    caseNoteUuid: String = UUID.randomUUID().toString(),
    source: CaseNoteSource = CaseNoteSource.DPS,
    syncToNomis: Boolean = true,
  ) {
    publishCaseNoteDomainEvent("person.case-note.created", offenderNo, caseNoteUuid, NOMIS_CASE_NOTE_ID2, source, syncToNomis)
  }

  private fun publishUpdateCaseNoteDomainEvent(
    offenderNo: String = OFFENDER_NO,
    caseNoteUuid: String = UUID.randomUUID().toString(),
    source: CaseNoteSource = CaseNoteSource.DPS,
    syncToNomis: Boolean = true,
  ) {
    publishCaseNoteDomainEvent("person.case-note.updated", offenderNo, caseNoteUuid, NOMIS_CASE_NOTE_ID2, source, syncToNomis)
  }

  private fun publishDeleteCaseNoteDomainEvent(
    offenderNo: String = OFFENDER_NO,
    caseNoteUuid: String = UUID.randomUUID().toString(),
    source: CaseNoteSource = CaseNoteSource.DPS,
    syncToNomis: Boolean = true,
  ) {
    publishCaseNoteDomainEvent("person.case-note.deleted", offenderNo, caseNoteUuid, NOMIS_CASE_NOTE_ID2, source, syncToNomis)
  }

  private fun publishCaseNoteDomainEvent(
    eventType: String,
    offenderNo: String,
    caseNoteUuid: String,
    nomisCaseNoteId: Long,
    source: CaseNoteSource,
    syncToNomis: Boolean,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          caseNoteMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            caseNoteUuid = caseNoteUuid,
            nomisCaseNoteId = nomisCaseNoteId,
            source = source,
            syncToNomis = syncToNomis,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun caseNoteMessagePayload(
  eventType: String,
  offenderNo: String,
  caseNoteUuid: String,
  nomisCaseNoteId: Long,
  source: CaseNoteSource,
  syncToNomis: Boolean,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "id": "$caseNoteUuid",
        "legacyId": $nomisCaseNoteId,
        "type": "CODE",
        "subType": "SUBCODE",
        "source": "${source.name}",
        "syncToNomis": $syncToNomis,
        "systemGenerated": false
      },
      "personReference": {
        "identifiers": [ { "type" : "NOMS", "value": "$offenderNo" } ]
      },
      "occurredAt": "2024-09-27T13:05:05.705Z",
      "description": "A case note has been created",
      "version": "1"
    }
    """
