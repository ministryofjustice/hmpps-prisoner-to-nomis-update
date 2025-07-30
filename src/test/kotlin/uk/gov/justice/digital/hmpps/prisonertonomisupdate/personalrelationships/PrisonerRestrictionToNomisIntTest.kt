package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.prisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPrisonerRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDate

class PrisonerRestrictionToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: ContactPersonNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: ContactPersonMappingApiMockServer

  private val dpsApi: ContactPersonDpsApiMockServer = ContactPersonDpsApiExtension.dpsContactPersonServer

  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.created")
  inner class RestrictionCreated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction create")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-create-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of a Prisoner Restriction create")
    inner class WhenDpsCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        private val dpsPrisonerRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerRestrictionIdOrNull(dpsPrisonerRestrictionId = dpsPrisonerRestrictionId, null)
          dpsApi.stubGetPrisonerRestrictionOrNull(
            prisonerRestrictionId = dpsPrisonerRestrictionId,
            prisonerRestriction().copy(
              prisonerRestrictionId = dpsPrisonerRestrictionId,
              restrictionType = "BAN",
              commentText = "Banned for life",
              effectiveDate = LocalDate.parse("2020-01-01"),
              expiryDate = LocalDate.parse("2026-01-01"),
              createdBy = "T.SWIFT",
              authorisedUsername = "T.SMITH",
            ),
          )
          nomisApi.stubCreatePrisonerRestriction(offenderNo = offenderNo, createPrisonerRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePrisonerRestrictionMapping()
          publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId = dpsPrisonerRestrictionId.toString(), offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the create`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-restriction-create-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the restriction created`() {
          verify(telemetryClient).trackEvent(
            eq("prisoner-restriction-create-success"),
            check {
              assertThat(it).containsEntry("dpsRestrictionId", dpsPrisonerRestrictionId.toString())
              assertThat(it).containsEntry("nomisRestrictionId", nomisRestrictionId.toString())
              assertThat(it).containsEntry("offenderNo", offenderNo)
            },
            isNull(),
          )
        }

        @Test
        fun `will call back to DPS to get restriction details`() {
          dpsApi.verify(getRequestedFor(urlEqualTo("/sync/prisoner-restriction/$dpsPrisonerRestrictionId")))
        }

        @Test
        fun `will create the restriction in NOMIS`() {
          nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$offenderNo/restriction")))
        }

        @Test
        fun `the created restriction will contain details of the DPS prisoner restriction`() {
          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBodyJsonPath("typeCode", "BAN")
              .withRequestBodyJsonPath("comment", "Banned for life")
              .withRequestBodyJsonPath("enteredStaffUsername", "T.SWIFT")
              .withRequestBodyJsonPath("authorisedStaffUsername", "T.SMITH")
              .withRequestBodyJsonPath("effectiveDate", "2020-01-01")
              .withRequestBodyJsonPath("expiryDate", "2026-01-01"),
          )
        }

        @Test
        fun `will create a mapping between the NOMIS and DPS ids`() {
          mappingApi.verify(
            postRequestedFor(urlEqualTo("/mapping/contact-person/prisoner-restriction"))
              .withRequestBodyJsonPath("dpsId", "$dpsPrisonerRestrictionId")
              .withRequestBodyJsonPath("nomisId", nomisRestrictionId)
              .withRequestBodyJsonPath("offenderNo", offenderNo)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val dpsPrisonerRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerRestrictionIdOrNull(dpsPrisonerRestrictionId = dpsPrisonerRestrictionId, null)
          dpsApi.stubGetPrisonerRestrictionOrNull(
            prisonerRestrictionId = dpsPrisonerRestrictionId,
            prisonerRestriction().copy(
              prisonerRestrictionId = dpsPrisonerRestrictionId,
            ),
          )
          nomisApi.stubCreatePrisonerRestriction(offenderNo = offenderNo, createPrisonerRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePrisonerRestrictionMappingFollowedBySuccess()
          publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId = dpsPrisonerRestrictionId.toString(), offenderNo = offenderNo)
        }

        @Test
        fun `will send telemetry for initial failure`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-restriction-mapping-create-failed"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will eventually send telemetry for success`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-restriction-create-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("prisoner-restriction-create-success"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$offenderNo/restriction")))
          }
        }
      }

      @Nested
      @DisplayName("when mapping service detects a duplicate mapping")
      inner class DuplicateMappingFailure {
        private val dpsPrisonerRestrictionId = 1234567L
        private val nomisRestrictionId = 7654321L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsPrisonerRestrictionIdOrNull(dpsPrisonerRestrictionId = dpsPrisonerRestrictionId, null)
          dpsApi.stubGetPrisonerRestrictionOrNull(
            prisonerRestrictionId = dpsPrisonerRestrictionId,
            prisonerRestriction().copy(
              prisonerRestrictionId = dpsPrisonerRestrictionId,
            ),
          )
          nomisApi.stubCreatePrisonerRestriction(offenderNo = offenderNo, createPrisonerRestrictionResponse().copy(id = nomisRestrictionId))
          mappingApi.stubCreatePrisonerRestrictionMapping(
            error = DuplicateMappingErrorResponse(
              moreInfo = DuplicateErrorContentObject(
                duplicate = PrisonerRestrictionMappingDto(
                  dpsId = dpsPrisonerRestrictionId.toString(),
                  nomisId = 999999,
                  offenderNo = offenderNo,
                  mappingType = PrisonerRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
                existing = PrisonerRestrictionMappingDto(
                  dpsId = dpsPrisonerRestrictionId.toString(),
                  nomisId = nomisRestrictionId,
                  offenderNo = offenderNo,
                  mappingType = PrisonerRestrictionMappingDto.MappingType.DPS_CREATED,
                ),
              ),
              errorCode = 1409,
              status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
              userMessage = "Duplicate mapping",
            ),
          )
          publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId = dpsPrisonerRestrictionId.toString(), offenderNo = offenderNo)
        }

        @Test
        fun `will send telemetry for duplicate`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-prisoner-restriction-duplicate"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will create the restriction in NOMIS once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("to-nomis-synch-prisoner-restriction-duplicate"),
              any(),
              isNull(),
            )
            nomisApi.verify(1, postRequestedFor(urlEqualTo("/prisoners/$offenderNo/restriction")))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.updated")
  inner class RestrictionUpdated {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction update")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishUpdatePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-update-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("personal-relationships-api.prisoner-restriction.deleted")
  inner class RestrictionDelete {

    @Nested
    @DisplayName("when NOMIS is the origin of a Restriction delete")
    inner class WhenNomisUpdated {

      @BeforeEach
      fun setUp() {
        publishDeletePrisonerRestrictionDomainEvent(prisonerRestrictionId = "12345", offenderNo = "A1234KT", source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("prisoner-restriction-delete-ignored"),
          eq(mapOf("dpsRestrictionId" to "12345", "offenderNo" to "A1234KT")),
          isNull(),
        )
      }
    }
  }

  @Suppress("SameParameterValue")
  private fun publishCreatePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.created") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
    }
  }

  private fun publishUpdatePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.updated") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
    }
  }

  private fun publishDeletePrisonerRestrictionDomainEvent(prisonerRestrictionId: String, offenderNo: String, source: String = "DPS") {
    with("personal-relationships-api.prisoner-restriction.deleted") {
      publishDomainEvent(eventType = this, payload = prisonerRestrictionMessagePayload(eventType = this, prisonerRestrictionId = prisonerRestrictionId, offenderNo = offenderNo, source = source))
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

fun prisonerRestrictionMessagePayload(
  eventType: String,
  prisonerRestrictionId: String,
  source: String = "DPS",
  offenderNo: String = "A1234KT",
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "prisonerRestrictionId": "$prisonerRestrictionId",
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
