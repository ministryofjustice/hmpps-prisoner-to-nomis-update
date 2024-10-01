package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.matching.StringValuePattern
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
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
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
    @DisplayName("when NOMIS is the origin of a CSIP create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setup() {
        publishCreateCSIPDomainEvent(source = CSIPSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignore the create`() {
        verify(telemetryClient).trackEvent(
          eq("csip-create-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to create the CSIP in NOMIS`() {
        csipNomisApi.verify(0, postRequestedFor(anyUrl()))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a CSIP create")
    inner class WhenDpsCreated {
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
              createdBy = "BOBBY.BEANS",
            ),
          )
          csipNomisApi.stubPutCSIP(
            csipResponse =
            upsertCSIPResponse(nomisCSIPReportId = nomisCSIPReportId),
          )
          csipMappingApi.stubPostMapping()
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, dpsCSIPReportId = dpsCSIPId)
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
              .withRequestBodyJsonPath("incidentDate", "2024-10-01")
              .withRequestBodyJsonPath("typeCode", "INT")
              .withRequestBodyJsonPath("locationCode", "LIB")
              .withRequestBodyJsonPath("areaOfWorkCode", "EDU")
              .withRequestBodyJsonPath("reportedBy", "JIM_ADM")
              .withRequestBodyJsonPath("reportedDate", "2024-10-01")
              .withRequestBodyJsonPath("createUsername", "BOBBY.BEANS"),

          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids with no child mappings`() {
          csipMappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/csip/all"))
              .withRequestBodyJsonPath("nomisCSIPReportId", nomisCSIPReportId)
              .withRequestBodyJsonPath("dpsCSIPReportId", dpsCSIPId)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED")
              .withRequestBody(matchingJsonPath("attendeeMappings.size()", equalTo("0")))
              .withRequestBody(matchingJsonPath("factorMappings.size()", equalTo("0")))
              .withRequestBody(matchingJsonPath("interviewMappings.size()", equalTo("0")))
              .withRequestBody(matchingJsonPath("planMappings.size()", equalTo("0")))
              .withRequestBody(matchingJsonPath("reviewMappings.size()", equalTo("0"))),
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
          csipDpsApi.stubGetCsipReport(dpsCsipRecord().copy(recordUuid = UUID.fromString(dpsCSIPId)))
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
            publishCreateCSIPDomainEvent(offenderNo = offenderNo, dpsCSIPReportId = dpsCSIPId)
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the csip in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("csip-create-success"),
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
                eq("csip-create-success"),
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
            publishCreateCSIPDomainEvent(offenderNo = offenderNo, dpsCSIPReportId = dpsCSIPId)
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
          publishCreateCSIPDomainEvent(offenderNo = offenderNo, dpsCSIPReportId = dpsCSIPId)
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
  @DisplayName("person.csip-record.deleted")
  inner class CSIPDeleted {
    @Nested
    @DisplayName("when NOMIS is the origin of the CSIP delete")
    inner class WhenNomisDeleted {
      @Nested
      inner class WhenUserDeleted {
        @BeforeEach
        fun setup() {
          publishDeleteCSIPDomainEvent(source = CSIPSource.NOMIS)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing it ignored the update`() {
          verify(telemetryClient).trackEvent(
            eq("csip-deleted-ignored"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `will not try to delete the CSIP in NOMIS`() {
          csipNomisApi.verify(0, deleteRequestedFor(anyUrl()))
        }
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the CSIP delete")
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
          publishDeleteCSIPDomainEvent(dpsCSIPReportId = dpsCSIPReportId, offenderNo = offenderNo)
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
              assertThat(it).containsEntry("dpsCsipReportId", dpsCSIPReportId)
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
          publishDeleteCSIPDomainEvent(dpsCSIPReportId = dpsCSIPReportId, offenderNo = offenderNo)
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
    dpsCSIPReportId: String = UUID.randomUUID().toString(),
    source: CSIPSource = CSIPSource.DPS,
  ) {
    publishCSIPDomainEvent("person.csip-record.created", offenderNo, dpsCSIPReportId, source)
  }

  private fun publishDeleteCSIPDomainEvent(
    offenderNo: String = "A1234KT",
    dpsCSIPReportId: String = UUID.randomUUID().toString(),
    source: CSIPSource = CSIPSource.DPS,
  ) {
    publishCSIPDomainEvent("person.csip-record.deleted", offenderNo, dpsCSIPReportId, source)
  }

  private fun publishCSIPDomainEvent(
    eventType: String,
    offenderNo: String,
    recordUuid: String,
    source: CSIPSource,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          csipMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            recordUuid = recordUuid,
            source = source,
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
  source: CSIPSource,
) =
  //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "recordUuid": "$recordUuid",
        "source": "${source.name}",
        "affectedComponents": []
      },
      "personReference": {
        "identifiers": [
          {
            "type" : "NOMS",
            "value": "$offenderNo"
          }
        ]
      }
    }
    """

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, pattern: StringValuePattern): RequestPatternBuilder =
  this.withRequestBody(matchingJsonPath(path, pattern))

fun RequestPatternBuilder.withRequestBodyJsonPath(path: String, equalTo: Any): RequestPatternBuilder =
  this.withRequestBodyJsonPath(path, equalTo(equalTo.toString()))
