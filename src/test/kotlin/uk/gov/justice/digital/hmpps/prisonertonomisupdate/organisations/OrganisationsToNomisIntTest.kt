package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto

class OrganisationsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var organisationsNomisApi: OrganisationsNomisApiMockServer

  @Autowired
  private lateinit var organisationsMappingApi: OrganisationsMappingApiMockServer

  @Nested
  @DisplayName("person.organisation.deleted")
  inner class OrganisationDeleted {
    @Nested
    @DisplayName("when NOMIS is the origin of the Organisation delete")
    inner class WhenNomisDeleted {

      @BeforeEach
      fun setup() {
        publishDeleteOrganisationDomainEvent(source = OrganisationSource.NOMIS)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing it ignored the update`() {
        verify(telemetryClient).trackEvent(
          eq("organisation-deleted-ignored"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will not try to delete the Organisation in NOMIS`() {
        organisationsNomisApi.verify(
          0,
          deleteRequestedFor(anyUrl()),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the Organisation delete")
    inner class WhenDpsDeleted {
      @Nested
      @DisplayName("when no mapping found")
      inner class WhenMappingDoesNotExist {
        @BeforeEach
        fun setUp() {
          organisationsMappingApi.stubGetByDpsOrganisationId(HttpStatus.NOT_FOUND)
          publishDeleteOrganisationDomainEvent()
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will ignore request since delete may have happened already by previous event`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-deleted-skipped"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when mapping is found")
      inner class HappyPath {
        private val dpsOrganisationId = "565643"
        private val nomisOrganisationId = 123456L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          organisationsMappingApi.stubGetByDpsOrganisationId(
            dpsOrganisationId,
            OrganisationsMappingDto(
              dpsId = dpsOrganisationId,
              nomisId = nomisOrganisationId,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            ),
          )
          organisationsNomisApi.stubDeleteCorporateOrganisation(corporateId = nomisOrganisationId)
          organisationsMappingApi.stubDeleteByDpsOrganisationId(dpsOrganisationId = dpsOrganisationId)
          publishDeleteOrganisationDomainEvent(organisationId = dpsOrganisationId, offenderNo = offenderNo)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the delete`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-deleted-success"),
            any(),
            isNull(),
          )
        }

        @Test
        fun `telemetry will contain key facts about the deleted organisation`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-deleted-success"),
            check {
              assertThat(it).containsEntry("dpsOrganisationId", dpsOrganisationId)
              assertThat(it).containsEntry("nomisOrganisationId", nomisOrganisationId.toString())
            },
            isNull(),
          )
        }

        @Test
        fun `will call the mapping service to get the NOMIS organisation id`() {
          organisationsMappingApi.verify(getRequestedFor(urlMatching("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
        }

        @Test
        fun `will delete the organisation in NOMIS`() {
          organisationsNomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$nomisOrganisationId")))
        }

        @Test
        fun `will delete the organisation mapping`() {
          organisationsMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
        }
      }

      @Nested
      @DisplayName("when mapping delete fails")
      inner class WhenMappingDeleteFails {
        private val dpsOrganisationId = "565643"
        private val nomisOrganisationId = 123456L
        private val offenderNo = "A1234KT"

        @BeforeEach
        fun setUp() {
          organisationsMappingApi.stubGetByDpsOrganisationId(
            dpsOrganisationId,
            OrganisationsMappingDto(
              dpsId = dpsOrganisationId,
              nomisId = nomisOrganisationId,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            ),
          )
          organisationsNomisApi.stubDeleteCorporateOrganisation(corporateId = nomisOrganisationId)
          organisationsMappingApi.stubDeleteByDpsOrganisationId(status = HttpStatus.INTERNAL_SERVER_ERROR)
          publishDeleteOrganisationDomainEvent(organisationId = dpsOrganisationId, offenderNo = offenderNo)
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("organisation-deleted-success"),
              any(),
              isNull(),
            )
          }
        }

        @Test
        fun `will delete the organisation in NOMIS`() {
          organisationsNomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$nomisOrganisationId")))
        }

        @Test
        fun `will try delete the organisation mapping once and ignore failure`() {
          organisationsMappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
          verify(telemetryClient).trackEvent(
            eq("organisation-mapping-deleted-failed"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  private fun publishDeleteOrganisationDomainEvent(
    offenderNo: String = "A1234KT",
    organisationId: String = "565643",
    source: OrganisationSource = OrganisationSource.DPS,
  ) {
    publishOrganisationDomainEvent("organisations-api.organisation.deleted", offenderNo, organisationId, source)
  }

  private fun publishOrganisationDomainEvent(
    eventType: String,
    offenderNo: String,
    organisationId: String,
    source: OrganisationSource,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          organisationMessagePayload(
            eventType = eventType,
            offenderNo = offenderNo,
            organisationId = organisationId,
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

fun organisationMessagePayload(
  eventType: String,
  offenderNo: String,
  organisationId: String,
  source: OrganisationSource,
) = //language=JSON
  """
    {
      "eventType":"$eventType", 
      "detailUrl":"https://somecallback", 
      "additionalInformation": {
        "source": "${source.name}",
        "organisationId": "$organisationId"
      },
      "personReference": {
        "identifiers": [
          {
            "type" : "NOMS", "value": "$offenderNo"
          }
        ]
      }
    }
    """
