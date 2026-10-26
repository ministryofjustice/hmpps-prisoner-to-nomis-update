package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.address

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension.Companion.corePersonCprApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactMappingApiMockServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonAddressUsage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.prisonerAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime

class CorePersonAddressToNomisIntTest(
  @Autowired private val nomisApi: CorePersonNomisApiMockServer,
  @Autowired private val mappingApi: CorePersonContactMappingApiMockServer,
) : SqsIntegrationTestBase() {

  @Nested
  @DisplayName("core-person-record.prison.address.created")
  inner class AddressCreated {
    private val prisonNumber = "A1234BC"
    private val cprAddressId = "11111111-2222-3333-4444-555555555555"
    private val nomisAddressId = 54321L

    @Nested
    @DisplayName("when NOMIS is the origin of an address create")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishAddressCreatedDomainEvent(prisonNumber, cprAddressId, source = "nomis")
        waitForAnyProcessingToComplete("core-person-address-create-ignored")
      }

      @Test
      fun `will send telemetry event showing the ignore`() {
        verify(telemetryClient).trackEvent(
          eq("core-person-address-create-ignored"),
          check {
            assertThat(it).containsEntry("prisonNumber", prisonNumber)
            assertThat(it).containsEntry("cprAddressId", cprAddressId)
          },
          isNull(),
        )
      }

      @Test
      fun `will not create the address in NOMIS`() {
        nomisApi.verify(0, postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address")))
      }
    }

    @Nested
    @DisplayName("when CPR is the origin of an address create")
    inner class WhenCprCreated {
      @Nested
      @DisplayName("when mapping already exists")
      inner class MappingExists {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetAddressMapping(cprAddressId)

          publishAddressCreatedDomainEvent(prisonNumber, cprAddressId)
          waitForAnyProcessingToComplete("core-person-address-create-duplicate")
        }

        @Test
        fun `will not create the address in NOMIS`() {
          nomisApi.verify(0, postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address")))
        }
      }

      @Nested
      @DisplayName("happy path")
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetAddressMapping(cprAddressId, mapping = null)
          corePersonCprApi.stubGetPrisonerAddress(prisonNumber, cprAddressId)
          nomisApi.stubCreateOffenderAddress(prisonNumber, addressId = nomisAddressId)
          mappingApi.stubCreateAddressMapping()

          publishAddressCreatedDomainEvent(prisonNumber, cprAddressId)
          waitForAnyProcessingToComplete("core-person-address-create-success")
        }

        @Test
        fun `will call CPR to get the address details`() {
          corePersonCprApi.verify(
            getRequestedFor(urlPathEqualTo("/syscon-sync/person/$prisonNumber/address/$cprAddressId")),
          )
        }

        @Test
        fun `will create the address in NOMIS`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address"))
              .withRequestBodyJsonPath("primaryAddress", true)
              .withRequestBodyJsonPath("mailAddress", false)
              .withRequestBodyJsonPath("flat", "Sub building 2")
              .withRequestBodyJsonPath("premise", "Big House")
              .withRequestBodyJsonPath("street", "Petty France")
              .withRequestBodyJsonPath("locality", "Westminster")
              .withRequestBodyJsonPath("postcode", "SW1H 9AJ")
              .withRequestBodyJsonPath("city", "London")
              .withRequestBodyJsonPath("county", "Greater London")
              .withRequestBodyJsonPath("countryCode", "GBR")
              .withRequestBodyJsonPath("noFixedAddress", false)
              .withRequestBodyJsonPath("comment", "Some comment")
              .withRequestBodyJsonPath("startDate", "2020-02-26")
              .withRequestBodyJsonPath("endDate", "2023-07-15")
              .withRequestBodyJsonPath("addressUsages.length()", 0),
          )
        }

        @Test
        fun `will create a mapping for the address`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
              .withRequestBodyJsonPath("cprId", cprAddressId)
              .withRequestBodyJsonPath("nomisId", nomisAddressId)
              .withRequestBodyJsonPath("nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("mappingType", "CPR_CREATED")
              .withRequestBodyJsonPath("addressUsageMappings.length()", 0),
          )
        }

        @Test
        fun `will send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-address-create-success"),
            check {
              assertThat(it).containsEntry("prisonNumber", prisonNumber)
              assertThat(it).containsEntry("cprAddressId", cprAddressId)
              assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when the address has usages")
      inner class WithAddressUsages {
        private val cprAddressUsageId1 = "aaaaaaaa-2222-3333-4444-555555555555"
        private val cprAddressUsageId2 = "bbbbbbbb-2222-3333-4444-555555555555"

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetAddressMapping(cprAddressId, mapping = null)
          corePersonCprApi.stubGetPrisonerAddress(
            prisonNumber,
            cprAddressId,
            prisonerAddress().copy(
              addressUsage = listOf(
                addressUsage(cprAddressUsageId1, PrisonAddressUsage.AddressUsageCode.HOME),
                addressUsage(cprAddressUsageId2, PrisonAddressUsage.AddressUsageCode.HDC),
              ),
            ),
          )
          nomisApi.stubCreateOffenderAddress(prisonNumber, addressId = nomisAddressId)
          mappingApi.stubCreateAddressMapping()

          publishAddressCreatedDomainEvent(prisonNumber, cprAddressId)
          waitForAnyProcessingToComplete("core-person-address-create-success")
        }

        @Test
        fun `will create the address in NOMIS with the address usages`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address"))
              .withRequestBodyJsonPath("addressUsages.length()", 2)
              .withRequestBodyJsonPath("addressUsages[0]", "HOME")
              .withRequestBodyJsonPath("addressUsages[1]", "HDC"),
          )
        }

        @Test
        fun `will create address usage mappings along with the address mapping`() {
          mappingApi.verify(
            postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
              .withRequestBodyJsonPath("cprId", cprAddressId)
              .withRequestBodyJsonPath("nomisId", nomisAddressId)
              .withRequestBodyJsonPath("addressUsageMappings.length()", 2)
              .withRequestBodyJsonPath("addressUsageMappings[0].cprId", cprAddressUsageId1)
              .withRequestBodyJsonPath("addressUsageMappings[0].nomisId", nomisAddressId)
              .withRequestBodyJsonPath("addressUsageMappings[0].addressUsageCode", "HOME")
              .withRequestBodyJsonPath("addressUsageMappings[0].nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("addressUsageMappings[0].mappingType", "CPR_CREATED")
              .withRequestBodyJsonPath("addressUsageMappings[1].cprId", cprAddressUsageId2)
              .withRequestBodyJsonPath("addressUsageMappings[1].nomisId", nomisAddressId)
              .withRequestBodyJsonPath("addressUsageMappings[1].addressUsageCode", "HDC")
              .withRequestBodyJsonPath("addressUsageMappings[1].nomisPrisonNumber", prisonNumber)
              .withRequestBodyJsonPath("addressUsageMappings[1].mappingType", "CPR_CREATED"),
          )
        }
      }

      @Nested
      @DisplayName("when country code differs between CPR and NOMIS")
      inner class CountryCodeTranslation {
        @BeforeEach
        fun setUp() {
          mappingApi.stubGetAddressMapping(cprAddressId, mapping = null)
          corePersonCprApi.stubGetPrisonerAddress(
            prisonNumber,
            cprAddressId,
            prisonerAddress().copy(countryCode = PrisonAddress.CountryCode.IMN, isMail = null),
          )
          nomisApi.stubCreateOffenderAddress(prisonNumber, addressId = nomisAddressId)
          mappingApi.stubCreateAddressMapping()

          publishAddressCreatedDomainEvent(prisonNumber, cprAddressId)
          waitForAnyProcessingToComplete("core-person-address-create-success")
        }

        @Test
        fun `will translate the country code and default mail address`() {
          nomisApi.verify(
            postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address"))
              .withRequestBodyJsonPath("countryCode", "IOM")
              .withRequestBodyJsonPath("mailAddress", false),
          )
        }
      }

      @Nested
      @DisplayName("when mapping service fails once")
      inner class MappingFailure {
        private val cprAddressUsageId = "aaaaaaaa-2222-3333-4444-555555555555"

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetAddressMapping(cprAddressId, mapping = null)
          corePersonCprApi.stubGetPrisonerAddress(
            prisonNumber,
            cprAddressId,
            prisonerAddress().copy(addressUsage = listOf(addressUsage(cprAddressUsageId, PrisonAddressUsage.AddressUsageCode.HOME))),
          )
          nomisApi.stubCreateOffenderAddress(prisonNumber, addressId = nomisAddressId)
          mappingApi.stubCreateAddressMappingFollowedBySuccess()

          publishAddressCreatedDomainEvent(prisonNumber, cprAddressId)
          waitForAnyProcessingToComplete("core-person-address-create-success")
        }

        @Test
        fun `will send telemetry for initial failure`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-address-mapping-create-failed"),
            check {
              assertThat(it).containsEntry("cprAddressId", cprAddressId)
            },
            isNull(),
          )
        }

        @Test
        fun `will create the address in NOMIS once`() {
          nomisApi.verify(1, postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/address")))
        }

        @Test
        fun `will try to create the mapping twice`() {
          mappingApi.verify(2, postRequestedFor(urlPathEqualTo("/mapping/core-person/address")))
        }

        @Test
        fun `will include the address usage mappings on both attempts`() {
          mappingApi.verify(
            2,
            postRequestedFor(urlPathEqualTo("/mapping/core-person/address"))
              .withRequestBodyJsonPath("addressUsageMappings[0].cprId", cprAddressUsageId)
              .withRequestBodyJsonPath("addressUsageMappings[0].nomisId", nomisAddressId)
              .withRequestBodyJsonPath("addressUsageMappings[0].addressUsageCode", "HOME")
              .withRequestBodyJsonPath("addressUsageMappings[0].mappingType", "CPR_CREATED"),
          )
        }

        @Test
        fun `will eventually send success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("core-person-address-create-success"),
            check {
              assertThat(it).containsEntry("cprAddressId", cprAddressId)
              assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun addressUsage(cprAddressUsageId: String, code: PrisonAddressUsage.AddressUsageCode) = PrisonAddressUsage(
    addressUsageCode = code,
    isActive = true,
    createDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
    createUserId = "ME",
    cprAddressUsageId = cprAddressUsageId,
  )

  private fun publishAddressCreatedDomainEvent(prisonNumber: String, cprAddressId: String, source: String = "core-person-record") {
    val eventType = "core-person-record.prison.address.created"
    val payload = """
      {
        "eventType": "$eventType",
        "additionalInformation": { "cprAddressId": "$cprAddressId" },
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
