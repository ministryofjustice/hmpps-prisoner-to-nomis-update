package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.absent
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPChildMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.util.UUID

class CSIPToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @Nested
  @DisplayName("person.csip-record.created")
  inner class CSIPCreated {
    @Nested
    @DisplayName("When CSIP created")
    inner class WhenCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val offenderNo = "A1234KT"
        private val dpsCSIPId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubGetCsipReport(
            dpsCsipRecord().copy(
              recordUuid = UUID.fromString(dpsCSIPId),
              prisonNumber = offenderNo,
              logCode = "A1234",
            ),
          )
          csipNomisApi.stubPutCSIP(
            csipResponse =
            upsertCSIPResponse(nomisCSIPReportId = nomisCSIPReportId),
          )
          csipMappingApi.stubPostMapping()
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("csip-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the csip created`() {
          verify(telemetryClient).trackEvent(
            eq("csip-create-success"),
            check {
              assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
              assertThat(it).containsEntry("nomisCSIPReportId", "$nomisCSIPReportId")
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get csip details`() {
          csipDpsApi.verify(getRequestedFor(urlMatching("/csip-records/$dpsCSIPId")))
        }

        @Test
        fun `will create the csip in NOMIS`() {
          csipNomisApi.verify(putRequestedFor(urlEqualTo("/csip")))
        }

        @Test
        fun `the created csip will contain details of the DPS csip`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("offenderNo", "A1234KT")
              .withRequestBodyJsonPath("incidentDate", "2024-06-12")
              .withRequestBodyJsonPath("typeCode", "INT")
              .withRequestBodyJsonPath("locationCode", "LIB")
              .withRequestBodyJsonPath("areaOfWorkCode", "EDU")
              .withRequestBodyJsonPath("reportedBy", "JIM_ADM")
              .withRequestBodyJsonPath("reportedDate", "2024-10-01")
              .withRequestBodyJsonPath("prisonCodeWhenRecorded", "MDI")
              .withRequestBodyJsonPath("logNumber", "A1234")
              .withRequestBodyJsonPath("incidentTime", "10:32:12")
              .withRequestBodyJsonPath("staffAssaulted", true)
              .withRequestBodyJsonPath("staffAssaultedName", "Fred Jones")
              .withRequestBodyJsonPath("reportDetailRequest.involvementCode", "PER")
              .withRequestBodyJsonPath("reportDetailRequest.concern", "There was a worry about the offender")
              .withRequestBodyJsonPath("reportDetailRequest.knownReasons", "known reasons details go in here")
              .withRequestBodyJsonPath("reportDetailRequest.otherInformation", "other information goes in here")
              .withRequestBodyJsonPath("reportDetailRequest.saferCustodyTeamInformed", false)
              .withRequestBodyJsonPath("reportDetailRequest.referralComplete", true)
              .withRequestBodyJsonPath("reportDetailRequest.referralCompletedBy", "JIM_ADM")
              .withRequestBodyJsonPath("reportDetailRequest.referralCompletedDate", "2024-04-04"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip factor`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reportDetailRequest.factors[0].dpsId", "8cdadcf3-b003-4116-9956-c99bd8df6111")
              .withRequestBodyJsonPath("reportDetailRequest.factors[0].typeCode", "BUL")
              .withRequestBodyJsonPath("reportDetailRequest.factors[0].comment", "Offender causes trouble"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip investigation`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("investigation.staffInvolved", "some people")
              .withRequestBodyJsonPath("investigation.evidenceSecured", "A piece of pipe")
              .withRequestBodyJsonPath("investigation.reasonOccurred", "bad behaviour")
              .withRequestBodyJsonPath("investigation.usualBehaviour", "Good person")
              .withRequestBodyJsonPath("investigation.trigger", "missed meal")
              .withRequestBodyJsonPath("investigation.protectiveFactors", "ensure taken to canteen"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip intervies`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("investigation.interviews[0].dpsId", "8cdadcf3-b003-4116-9956-c99bd8df6222")
              .withRequestBodyJsonPath("investigation.interviews[0].interviewee", "Bill Black")
              .withRequestBodyJsonPath("investigation.interviews[0].date", "2024-06-06")
              .withRequestBodyJsonPath("investigation.interviews[0].roleCode", "WITNESS")
              .withRequestBodyJsonPath("investigation.interviews[0].comments", "Saw a pipe in his hand"),
          )
        }

        @Test
        fun `the created csip will contain the Safer Custody Screening details`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("saferCustodyScreening.scsOutcomeCode", "CUR")
              .withRequestBodyJsonPath("saferCustodyScreening.recordedBy", "FRED_ADM")
              .withRequestBodyJsonPath("saferCustodyScreening.recordedDate", "2024-04-08")
              .withRequestBodyJsonPath("saferCustodyScreening.reasonForDecision", "There is a reason for the decision - it goes here"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip plan`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("caseManager", "C Jones")
              .withRequestBodyJsonPath("firstCaseReviewDate", "2024-04-15")
              .withRequestBodyJsonPath("planReason", "helper")
              .withRequestBodyJsonPath("plans[0].dpsId", "8cdadcf3-b003-4116-9956-c99bd8df6333")
              .withRequestBodyJsonPath("plans[0].identifiedNeed", "they need help")
              .withRequestBodyJsonPath("plans[0].intervention", "dd")
              .withRequestBodyJsonPath("plans[0].identifiedNeed", "they need help")
              .withRequestBodyJsonPath("plans[0].referredBy", "Jason")
              .withRequestBodyJsonPath("plans[0].targetDate", "2024-08-20")
              .withRequestBodyJsonPath("plans[0].progression", "there was some improvement")
              .withRequestBodyJsonPath("plans[0].closedDate", "2024-04-17"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip reviews`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reviews[0].dpsId", "8cdadcf3-b003-4116-9956-c99bd8df6444")
              .withRequestBodyJsonPath("reviews[0].remainOnCSIP", true)
              .withRequestBodyJsonPath("reviews[0].csipUpdated", false)
              .withRequestBodyJsonPath("reviews[0].caseNote", false)
              .withRequestBodyJsonPath("reviews[0].closeCSIP", true)
              .withRequestBodyJsonPath("reviews[0].peopleInformed", false)
              .withRequestBodyJsonPath("reviews[0].recordedDate", "2024-04-01")
              .withRequestBodyJsonPath("reviews[0].recordedBy", "JSMITH")
              .withRequestBodyJsonPath("reviews[0].closeDate", "2024-04-16"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip attendees`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reviews[0].attendees[0].dpsId", "8cdadcf3-b003-4116-9956-c99bd8df6555")
              .withRequestBodyJsonPath("reviews[0].attendees[0].attended", true)
              .withRequestBodyJsonPath("reviews[0].attendees[0].name", "sam jones")
              .withRequestBodyJsonPath("reviews[0].attendees[0].role", "person")
              .withRequestBodyJsonPath("reviews[0].attendees[0].contribution", "talked about things"),
          )
        }

        @Test
        fun `the created csip will contain details of the DPS csip decision and actions`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("decision.conclusion", "Offender needs help")
              .withRequestBodyJsonPath("decision.decisionOutcomeCode", "OPE")
              .withRequestBodyJsonPath("decision.signedOffRoleCode", "CUSTMAN")
              .withRequestBodyJsonPath("decision.recordedBy", "FRED_ADM")
              .withRequestBodyJsonPath("decision.recordedDate", "2024-04-08")
              .withRequestBodyJsonPath("decision.otherDetails", "Some other info here")
              .withRequestBodyJsonPath("decision.actions.openCSIPAlert", false)
              .withRequestBodyJsonPath("decision.actions.nonAssociationsUpdated", true)
              .withRequestBodyJsonPath("decision.actions.observationBook", true)
              .withRequestBodyJsonPath("decision.actions.unitOrCellMove", false)
              .withRequestBodyJsonPath("decision.actions.csraOrRsraReview", false)
              .withRequestBodyJsonPath("decision.actions.serviceReferral", true)
              .withRequestBodyJsonPath("decision.actions.simReferral", false),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids with child mappings`() {
          csipMappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/all"))
              .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
              .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED")
              .withRequestBodyJsonPath("attendeeMappings.size()", equalTo("0"))
              .withRequestBodyJsonPath("factorMappings.size()", equalTo("1"))
              .withRequestBodyJsonPath("interviewMappings.size()", equalTo("0"))
              .withRequestBodyJsonPath("planMappings.size()", equalTo("1"))
              .withRequestBodyJsonPath("reviewMappings.size()", equalTo("0")),
          )
        }

        @Test
        fun `the created mapping will contain the IDs`() {
          csipMappingApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
              .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPId)
              .withRequestBodyJsonPath("mappingType", CSIPFullMappingDto.MappingType.DPS_CREATED.name),
          )
        }
      }

      @Nested
      inner class WhenCreateInDpsWithNonNomisData {
        private val offenderNo = "A1234KP"
        private val dpsCSIPId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43218L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubGetCsipReport(
            dpsCsipRecord(decisionSignedOffRole = "OTHER").copy(
              recordUuid = UUID.fromString(dpsCSIPId),
            ),
          )
          csipNomisApi.stubPutCSIP(
            csipResponse =
            upsertCSIPResponse(nomisCSIPReportId = nomisCSIPReportId),
          )
          csipMappingApi.stubPostMapping()
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the created csip will not contain details of the DPS csip signed off role if set to 'OTHER'`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("decision.actions.nonAssociationsUpdated", true)
              .withRequestBodyJsonPath("decision.signedOffRoleCode", absent()),
          )
        }
      }

      @Nested
      inner class WhenCreateByNomisSuccessWithMinimalData {
        private val offenderNo = "A1234KT"
        private val dpsCSIPId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubGetCsipReport(
            dpsCsipRecordMinimal().copy(
              recordUuid = UUID.fromString(dpsCSIPId),
              prisonNumber = offenderNo,
              logCode = "A1234",
            ),
          )
          csipNomisApi.stubPutCSIP(
            csipResponse =
            upsertCSIPResponse(nomisCSIPReportId = nomisCSIPReportId),
          )
          csipMappingApi.stubPostMapping()
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `the created csip will contain details of the DPS csip`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("offenderNo", "A1234KT")
              .withRequestBodyJsonPath("incidentDate", "2024-08-09")
              .withRequestBodyJsonPath("typeCode", "INT")
              .withRequestBodyJsonPath("prisonCodeWhenRecorded", "ASI")
              .withRequestBodyJsonPath("locationCode", "LIB")
              .withRequestBodyJsonPath("areaOfWorkCode", "EDU")
              .withRequestBodyJsonPath("reportedBy", "JIM_ADM")
              .withRequestBodyJsonPath("reportedDate", "2024-10-01"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids with no child mappings`() {
          csipMappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/all"))
              .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
              .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED")
              .withRequestBodyJsonPath("attendeeMappings.size()", equalTo("0"))
              .withRequestBodyJsonPath("factorMappings.size()", equalTo("1"))
              .withRequestBodyJsonPath("interviewMappings.size()", equalTo("0"))
              .withRequestBodyJsonPath("planMappings.size()", equalTo("1"))
              .withRequestBodyJsonPath("reviewMappings.size()", equalTo("0")),
          )
        }

        @Test
        fun `the created mapping will contain the IDs`() {
          csipMappingApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
              .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPId)
              .withRequestBodyJsonPath("mappingType", CSIPFullMappingDto.MappingType.DPS_CREATED.name),
          )
        }
      }

      @Nested
      @DisplayName("when the create of the mapping fails")
      inner class WithCreateMappingFailures {
        private val offenderNo = "A1234KT"
        private val dpsCSIPId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          csipDpsApi.stubGetCsipReport(dpsCsipRecordMinimal().copy(recordUuid = UUID.fromString(dpsCSIPId)))
          csipNomisApi.stubPutCSIP(
            csipResponse = upsertCSIPResponse(nomisCSIPReportId = nomisCSIPReportId),
          )
        }

        @Nested
        @DisplayName("fails once")
        inner class MappingFailsOnce {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMappingFollowedBySuccess(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-create-mapping-retry-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the csip in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-create-mapping-retry-success"),
                any(),
                isNull(),
              )
            }

            csipNomisApi.verify(1, putRequestedFor(urlEqualTo("/csip")))
          }

          @Test
          fun `telemetry will contain key facts about the csip created`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-create-mapping-retry-success"),
                check {
                  assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPId)
                  assertThat(it).containsEntry("offenderNo", offenderNo)
                  assertThat(it).containsEntry("nomisCSIPReportId", "$nomisCSIPReportId")
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
            csipMappingApi.stubPostMapping(HttpStatus.INTERNAL_SERVER_ERROR)
            publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          }

          @Test
          fun `will add message to dead letter queue`() {
            await untilCallTo {
              awsSqsCSIPDlqClient!!.countAllMessagesOnQueue(csipDlqUrl!!).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create the csip in NOMIS once`() {
            await untilCallTo {
              awsSqsCSIPDlqClient!!.countAllMessagesOnQueue(csipDlqUrl!!).get()
            } matches { it == 1 }

            csipNomisApi.verify(1, putRequestedFor(urlEqualTo("/csip")))
          }
        }
      }

      @Nested
      @DisplayName("when csip has already been created")
      inner class WhenCSIPAlreadyCreated {
        private val dpsCSIPId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId = dpsCSIPId,
            CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPId,
              nomisCSIPReportId = nomisCSIPReportId,
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
          )
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `it will not call back to DPS API`() {
          csipDpsApi.verify(0, getRequestedFor(anyUrl()))
        }

        @Test
        fun `it will not create the csip again in NOMIS`() {
          csipNomisApi.verify(0, postRequestedFor(anyUrl()))
        }
      }
    }
  }

  @Nested
  @DisplayName("person.csip-report.updated")
  inner class CSIPUpdated {

    @Nested
    @DisplayName("When CSIP updated")
    inner class WhenCSIPUpdated {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          publishUpdateCSIPDomainEvent()
        }

        @Test
        fun `will treat this as an error and message will go on DLQ`() {
          await untilCallTo {
            awsSqsCSIPDlqClient!!.countAllMessagesOnQueue(csipDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will send telemetry event showing it failed to update for each retry`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("csip-children-create-failed"),
              any(),
              isNull(),
            )
          }
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class HappyPath {
        private val dpsCSIPReportId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId,
            CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPReportId,
              nomisCSIPReportId = nomisCSIPReportId,
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
          )
          csipDpsApi.stubGetCsipReport(
            dpsCsipReport = dpsCsipRecord().copy(
              recordUuid = UUID.fromString(dpsCSIPReportId),
              logCode = "LG123",
            ),
          )
          csipNomisApi.stubPutCSIP(upsertCSIPResponse().copy(nomisCSIPReportId = nomisCSIPReportId))
          csipMappingApi.stubPostChildrenMapping()

          publishUpdateCSIPDomainEvent(recordUuid = dpsCSIPReportId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-children-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the updated csip`() {
          verify(telemetryClient).trackEvent(
            eq("csip-children-create-success"),
            check {
              assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPReportId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
              assertThat(it).containsEntry("nomisCSIPReportId", "$nomisCSIPReportId")
              // TODO add in assertThat(it).containsEntry("factorMappings", "$nomisCSIPReportId")
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS csip id`() {
          csipMappingApi.verify(getRequestedFor(urlMatching("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
        }

        @Test
        fun `will call back to DPS to get csip details`() {
          csipDpsApi.verify(getRequestedFor(urlMatching("/csip-records/$dpsCSIPReportId")))
        }

        @Test
        fun `will update the csip in NOMIS`() {
          csipNomisApi.verify(putRequestedFor(urlEqualTo("/csip")))
        }

        @Test
        fun `the update csip will contain details of the DPS csip`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("id", nomisCSIPReportId)
              .withRequestBodyJsonPath("logNumber", "LG123")
              .withRequestBodyJsonPath("offenderNo", "A1234KT")
              .withRequestBodyJsonPath("incidentDate", "2024-06-12")
              .withRequestBodyJsonPath("typeCode", "INT")
              .withRequestBodyJsonPath("locationCode", "LIB")
              .withRequestBodyJsonPath("areaOfWorkCode", "EDU")
              .withRequestBodyJsonPath("reportedBy", "JIM_ADM")
              .withRequestBodyJsonPath("reportedDate", "2024-10-01"),
          )
        }

        @Test
        fun `will create a mapping for the csip children`() {
          csipMappingApi.verify(1, postRequestedFor(urlPathEqualTo("/mapping/csip/children/all")))
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class HappyPathWithExistingMappings {
        private val dpsCSIPReportId = UUID.randomUUID().toString()
        private val dpsCSIPAttendeeId = "8cdadcf3-b003-4116-9956-c99bd8df6555"
        private val dpsCSIPFactorId = "8cdadcf3-b003-4116-9956-c99bd8df6111"
        private val dpsCSIPInterviewId = "8cdadcf3-b003-4116-9956-c99bd8df6222"
        private val dpsCSIPPlanId = "8cdadcf3-b003-4116-9956-c99bd8df6333"
        private val dpsCSIPReviewId = "8cdadcf3-b003-4116-9956-c99bd8df6444"
        private val nomisCSIPReportId = 43217L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId,
            CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPReportId,
              nomisCSIPReportId = nomisCSIPReportId,
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
              attendeeMappings = listOf(
                CSIPChildMappingDto(
                  dpsCSIPReportId = dpsCSIPReportId,
                  dpsId = dpsCSIPAttendeeId,
                  nomisId = 6555,
                  mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              factorMappings = listOf(
                CSIPChildMappingDto(
                  dpsCSIPReportId = dpsCSIPReportId,
                  dpsId = dpsCSIPFactorId,
                  nomisId = 6111,
                  mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              interviewMappings = listOf(
                CSIPChildMappingDto(
                  dpsCSIPReportId = dpsCSIPReportId,
                  dpsId = dpsCSIPInterviewId,
                  nomisId = 6222,
                  mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              planMappings = listOf(
                CSIPChildMappingDto(
                  dpsCSIPReportId = dpsCSIPReportId,
                  dpsId = dpsCSIPPlanId,
                  nomisId = 6333,
                  mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              reviewMappings = listOf(
                CSIPChildMappingDto(
                  dpsCSIPReportId = dpsCSIPReportId,
                  dpsId = dpsCSIPReviewId,
                  nomisId = 6444,
                  mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
                ),
              ),
            ),
          )
          csipDpsApi.stubGetCsipReport(
            dpsCsipReport = dpsCsipRecord().copy(
              recordUuid = UUID.fromString(dpsCSIPReportId),
              logCode = "LG123",

            ),
          )
          csipNomisApi.stubPutCSIP(upsertCSIPResponse().copy(nomisCSIPReportId = nomisCSIPReportId))
          csipMappingApi.stubPostChildrenMapping()

          publishUpdateCSIPDomainEvent(recordUuid = dpsCSIPReportId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-children-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `the update csip will contain details of the DPS csip factor being updated`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reportDetailRequest.factors[0].dpsId", dpsCSIPFactorId)
              .withRequestBodyJsonPath("reportDetailRequest.factors[0].id", 6111),

          )
        }

        @Test
        fun `the update csip will contain details of the DPS csip plan being updated`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("plans[0].dpsId", dpsCSIPPlanId)
              .withRequestBodyJsonPath("plans[0].id", 6333),
          )
        }

        @Test
        fun `the update csip will contain details of the DPS csip interview being updated`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("investigation.interviews[0].dpsId", dpsCSIPInterviewId)
              .withRequestBodyJsonPath("investigation.interviews[0].id", 6222),
          )
        }

        @Test
        fun `the update csip will contain details of the DPS csip review being updated`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reviews[0].dpsId", dpsCSIPReviewId)
              .withRequestBodyJsonPath("reviews[0].id", 6444),
          )
        }

        @Test
        fun `the update csip will contain details of the DPS csip attendee being updated`() {
          csipNomisApi.verify(
            putRequestedFor(anyUrl())
              .withRequestBodyJsonPath("reviews[0].attendees[0].dpsId", dpsCSIPAttendeeId)
              .withRequestBodyJsonPath("reviews[0].attendees[0].id", 6555),
          )
        }
      }

      @Nested
      @DisplayName("when the update of the child mappings fails")
      inner class WithUpdateChildrenMappingFailures {
        private val offenderNo = "A1234KT"
        private val dpsCSIPReportId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId,
            CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPReportId,
              nomisCSIPReportId = nomisCSIPReportId,
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
            ),
          )
          csipDpsApi.stubGetCsipReport(
            dpsCsipReport = dpsCsipRecord().copy(
              recordUuid = UUID.fromString(dpsCSIPReportId),
              logCode = "LG123",
            ),
          )
          csipNomisApi.stubPutCSIP(
            csipResponse = upsertCSIPResponse().copy(
              nomisCSIPReportId = nomisCSIPReportId,
            ),
          )
        }

        @Nested
        @DisplayName("fails once")
        inner class MappingFailsOnce {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostChildrenMappingFollowedBySuccess(HttpStatus.INTERNAL_SERVER_ERROR)
            publishUpdateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPReportId)
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-children-create-mapping-retry-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the csip in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-children-create-mapping-retry-success"),
                any(),
                isNull(),
              )
            }

            csipNomisApi.verify(1, putRequestedFor(urlEqualTo("/csip")))
          }

          @Test
          fun `telemetry will contain key facts about the csip created`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-children-create-mapping-retry-success"),
                check {
                  assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPReportId)
                  assertThat(it).containsEntry("offenderNo", offenderNo)
                  assertThat(it).containsEntry("nomisCSIPReportId", "$nomisCSIPReportId")
                },
                isNull(),
              )
            }
          }
        }

        @Nested
        @DisplayName("always fails")
        inner class MappingSaveAlwaysFails {
          @BeforeEach
          fun setUp() {
            csipMappingApi.stubPostMapping(HttpStatus.INTERNAL_SERVER_ERROR)
            publishUpdateCSIPDomainEvent(offenderNo = offenderNo, recordUuid = dpsCSIPReportId)
          }

          @Test
          fun `will add message to dead letter queue`() {
            await untilCallTo {
              awsSqsCSIPDlqClient!!.countAllMessagesOnQueue(csipDlqUrl!!).get()
            } matches { it == 1 }
          }

          @Test
          fun `will create the csip in NOMIS once`() {
            await untilCallTo {
              awsSqsCSIPDlqClient!!.countAllMessagesOnQueue(csipDlqUrl!!).get()
            } matches { it == 1 }

            csipNomisApi.verify(1, putRequestedFor(urlEqualTo("/csip")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("person.csip-record.deleted")
  inner class CSIPDeleted {
    @Nested
    @DisplayName("When CSIP deleted")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenNoMappingFound {
        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(HttpStatus.NOT_FOUND)
          publishDeleteCSIPDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will ignore request since delete may have happened already by previous event`() {
          verify(telemetryClient).trackEvent(
            eq("csip-deleted-skipped"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class WhenMappingIsFound {
        private val dpsCSIPReportId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId = dpsCSIPReportId,
            mapping = CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPReportId,
              nomisCSIPReportId = nomisCSIPReportId,
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
            ),
          )
          csipNomisApi.stubDeleteCSIP(nomisCSIPReportId = nomisCSIPReportId)
          csipMappingApi.stubDeleteByDpsId(dpsCSIPReportId)
          publishDeleteCSIPDomainEvent(recordUuid = dpsCSIPReportId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("csip-deleted-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the deleted csip`() {
          verify(telemetryClient).trackEvent(
            eq("csip-deleted-success"),
            check {
              assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPReportId)
              assertThat(it).containsEntry("offenderNo", offenderNo)
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS csip id`() {
          csipMappingApi.verify(getRequestedFor(urlMatching("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
        }

        @Test
        fun `will delete the csip in NOMIS`() {
          csipNomisApi.verify(deleteRequestedFor(urlEqualTo("/csip/$nomisCSIPReportId")))
        }

        @Test
        fun `will delete the csip mapping`() {
          csipMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
        }
      }

      @Nested
      @DisplayName("when mapping delete fails")
      inner class WhenMappingDeleteFails {
        private val dpsCSIPReportId = UUID.randomUUID().toString()
        private val nomisCSIPReportId = 43217L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          csipMappingApi.stubGetByDpsReportId(
            dpsCSIPReportId = dpsCSIPReportId,
            mapping = CSIPFullMappingDto(
              dpsCSIPReportId = dpsCSIPReportId,
              nomisCSIPReportId = nomisCSIPReportId,
              attendeeMappings = listOf(),
              factorMappings = listOf(),
              interviewMappings = listOf(),
              planMappings = listOf(),
              reviewMappings = listOf(),
              mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
            ),
          )
          csipNomisApi.stubDeleteCSIP(nomisCSIPReportId = nomisCSIPReportId)
          csipMappingApi.stubDeleteByDpsId(status = HttpStatus.INTERNAL_SERVER_ERROR)
          publishDeleteCSIPDomainEvent(recordUuid = dpsCSIPReportId, offenderNo = offenderNo)
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("csip-deleted-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will delete the csip in NOMIS`() {
          csipNomisApi.verify(deleteRequestedFor(urlEqualTo("/csip/$nomisCSIPReportId")))
        }

        @Test
        fun `will try delete the csip mapping once and ignore failure`() {
          csipMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
          verify(telemetryClient).trackEvent(
            eq("csip-mapping-deleted-failed"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun publishCreateCSIPDomainEvent(
    offenderNo: String = "A1234KT",
    recordUuid: String = UUID.randomUUID().toString(),
  ) {
    publishCSIPDomainEvent("person.csip-record.created", offenderNo, recordUuid)
  }

  private fun publishUpdateCSIPDomainEvent(
    offenderNo: String = "A1234KT",
    recordUuid: String = UUID.randomUUID().toString(),
  ) {
    publishCSIPDomainEvent("person.csip-record.updated", offenderNo, recordUuid)
  }

  private fun publishDeleteCSIPDomainEvent(
    offenderNo: String = "A1234KT",
    recordUuid: String = UUID.randomUUID().toString(),
  ) {
    publishCSIPDomainEvent("person.csip-record.deleted", offenderNo, recordUuid)
  }

  private fun publishCSIPDomainEvent(
    eventType: String,
    offenderNo: String,
    recordUuid: String,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          csipMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            recordUuid = recordUuid,
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}

fun csipMessagePayload(
  eventType: String,
  offenderNo: String,
  recordUuid: String,
) =
  //language=JSON
  """
    {
      "eventType": "$eventType",
      "detailUrl": "https://somecallback",
      "additionalInformation": {
        "recordUuid": "$recordUuid"
      },
      "description": "A CSIP record change in the CSIP service",
      "occurredAt": "2024-07-15T09:03:38.927167845+01:00",
      "personReference": {
        "identifiers": [
          {
            "type": "NOMS",
            "value": "$offenderNo"
          }
        ]
      },
      "version": 1
    }
    """
