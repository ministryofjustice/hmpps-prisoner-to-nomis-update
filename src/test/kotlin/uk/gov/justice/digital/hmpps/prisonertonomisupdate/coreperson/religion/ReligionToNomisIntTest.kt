package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

class ReligionToNomisIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
  @Autowired private val mappingApi: ReligionMappingApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("core-person-record.prison.religion.created")
  inner class ReligionCreated {
    private val prisonNumber = "A1234BC"
    private val cprReligionId = "e312a74d-ca98-4fbc-b212-608bc41558e7"
    private val nomisBeliefId = 98765L

    @Nested
    @DisplayName("when NOMIS is the origin of a Religion create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishReligionCreatedDomainEvent(prisonNumber, cprReligionId, source = "nomis")
        waitForAnyProcessingToComplete("core-person-religion-create-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("core-person-religion-create-ignored"),
          check {
            assertThat(it).containsEntry("prisonNumber", prisonNumber)
            assertThat(it).containsEntry("cprReligionId", cprReligionId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when CPR is the origin of a Religion create")
    inner class WhenCprCreated {
      @Nested
      @DisplayName("when all goes ok")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetReligionMapping(cprReligionId, mapping = null)
          CorePersonCprApiExtension.corePersonCprApi.stubGetReligion(prisonNumber, cprReligionId)
          nomisApi.stubInsertReligion(prisonNumber, beliefId = nomisBeliefId)
          mappingApi.stubCreateReligionMapping()

          publishReligionCreatedDomainEvent(prisonNumber, cprReligionId)
          waitForAnyProcessingToComplete("core-person-religion-create-success")
        }

        @Test
        fun `will call CPR to get the religion details`() {
          CorePersonCprApiExtension.corePersonCprApi.verify(
            getRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/religion/$cprReligionId")),
          )
        }

        @Test
        fun `will create the religion in NOMIS`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religion"))
              .withRequestBodyJsonPath("beliefCode", "JEHV")
              .withRequestBodyJsonPath("startDate", "2024-01-01")
              .withRequestBodyJsonPath("comments", "Updated religion"),
          )
        }

        @Test
        fun `will create a mapping for the religion`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion"))
              .withRequestBodyJsonPath("cprId", cprReligionId)
              .withRequestBodyJsonPath("nomisId", nomisBeliefId)
              .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
          )
        }

        @Test
        fun `will send success telemetry with religion identifiers`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-religion-create-success"),
            check {
              assertThat(it).containsEntry("prisonNumber", prisonNumber)
              assertThat(it).containsEntry("cprReligionId", cprReligionId)
              assertThat(it).containsEntry("nomisBeliefId", nomisBeliefId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetReligionMapping(cprReligionId, mapping = null)
          CorePersonCprApiExtension.corePersonCprApi.stubGetReligion(prisonNumber, cprReligionId)
          nomisApi.stubInsertReligion(prisonNumber, beliefId = nomisBeliefId)
          mappingApi.stubCreateReligionMappingFollowedBySuccess()

          publishReligionCreatedDomainEvent(prisonNumber, cprReligionId)
          waitForAnyProcessingToComplete("core-person-religion-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-religion-mapping-create-failed"),
            check {
              assertThat(it).containsEntry("cprReligionId", cprReligionId)
            },
            isNull(),
          )
        }

        @Test
        fun `will create the religion in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/religion")))
        }

        @Test
        fun `will try to create the mapping twice`() {
          mappingApi.verify(2, postRequestedFor(urlPathEqualTo("/mapping/core-person-religion/religion")))
        }

        @Test
        fun `will eventually send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-religion-create-success"),
            check {
              assertThat(it).containsEntry("cprReligionId", cprReligionId)
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun publishReligionCreatedDomainEvent(prisonNumber: String, cprReligionId: String, source: String = "core-person-record") {
    val eventType = "core-person-record.prison.religion.created"
    val payload = """
      {
        "eventType": "$eventType",
        "additionalInformation": { "cprReligionId": "$cprReligionId" },
        "personReference": {
          "identifiers": [{ "type": "prisonNumber", "value": "$prisonNumber" }]
        }
      }
    """.trimIndent()

    awsSnsClient.publish(
      PublishRequest.builder()
        .topicArn(topicArn)
        .message(payload)
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String").stringValue(eventType).build(),
            "eventSource" to MessageAttributeValue.builder().dataType("String").stringValue(source).build(),
          ),
        )
        .build(),
    ).get()
  }
}
