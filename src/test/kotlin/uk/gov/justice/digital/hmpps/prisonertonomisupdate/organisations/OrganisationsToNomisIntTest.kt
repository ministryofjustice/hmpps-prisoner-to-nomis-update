package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationTypes
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationWeb
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporatePhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateWebAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

class OrganisationsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: OrganisationsNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: OrganisationsMappingApiMockServer

  private val dpsApi = OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer

  @Nested
  inner class Organisation {
    @Nested
    @DisplayName("organisations-api.organisation.created")
    inner class OrganisationCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateOrganisationDomainEvent(organisationId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsId = 1234567L
          private val nomisId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationIdOrNull(dpsOrganisationId = dpsId, null)
            dpsApi.stubGetSyncOrganisation(
              organisationId = dpsId,
              organisation().copy(
                organisationId = dpsId,
              ),
            )
            nomisApi.stubCreateCorporate()
            mappingApi.stubCreateOrganisationMapping()
            publishCreateOrganisationDomainEvent(organisationId = dpsId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the organisation created`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-create-success"),
              check {
                assertThat(it).containsEntry("organisationId", dpsId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get organisation details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation/$dpsId")))
          }

          @Test
          fun `will create the organisation in NOMIS`() {
            nomisApi.verify(postRequestedFor(urlEqualTo("/corporates")))
          }

          @Test
          fun `the created  will contain details of the DPS contact `() {
            nomisApi.verify(
              postRequestedFor(anyUrl())
                .withRequestBodyJsonPath("name", "Test organisation")
                .withRequestBodyJsonPath("active", true)
                .withRequestBodyJsonPath("caseloadId", "MDI")
                .withRequestBodyJsonPath("comment", "some comments")
                .withRequestBodyJsonPath("programmeNumber", "prog")
                .withRequestBodyJsonPath("vatNumber", "123 34"),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/corporate/organisation"))
                .withRequestBodyJsonPath("dpsId", "$dpsId")
                .withRequestBodyJsonPath("nomisId", dpsId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsId = 1234567L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationIdOrNull(dpsOrganisationId = dpsId, null)
            dpsApi.stubGetSyncOrganisation(
              organisationId = dpsId,
              organisation().copy(organisationId = dpsId),
            )
            nomisApi.stubCreateCorporate()
            mappingApi.stubCreateOrganisationMappingFollowedBySuccess()
            publishCreateOrganisationDomainEvent(organisationId = dpsId.toString())
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the organisation in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsId = 1234567L
          private val nomisId = 7654321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationIdOrNull(dpsOrganisationId = dpsId, null)
            dpsApi.stubGetSyncOrganisation(
              organisationId = dpsId,
              organisation().copy(
                organisationId = dpsId,
              ),
            )
            nomisApi.stubCreateCorporate()
            mappingApi.stubCreateOrganisationMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OrganisationsMappingDto(
                    dpsId = dpsId.toString(),
                    nomisId = 999999,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = OrganisationsMappingDto(
                    dpsId = dpsId.toString(),
                    nomisId = nomisId,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateOrganisationDomainEvent(organisationId = dpsId.toString())
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the organisation in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation.updated")
    inner class OrganisationUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationDomainEvent(organisationId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationId(
              dpsOrganisationId = organisationId,
              OrganisationsMappingDto(
                dpsId = organisationId.toString(),
                nomisId = organisationId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetSyncOrganisation(
              organisationId = organisationId,
              organisation().copy(
                organisationId = organisationId,
              ),
            )
            nomisApi.stubUpdateCorporate(corporateId = organisationId)
            publishUpdateOrganisationDomainEvent(organisationId = organisationId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the  updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-update-success"),
              check {
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get organisation details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation/$organisationId")))
          }

          @Test
          fun `will update the organisation in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId")))
          }

          @Test
          fun `the updated  will contain details of the DPS contact `() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("name", "Test organisation")
                .withRequestBodyJsonPath("active", true)
                .withRequestBodyJsonPath("caseloadId", "MDI")
                .withRequestBodyJsonPath("comment", "some comments")
                .withRequestBodyJsonPath("programmeNumber", "prog")
                .withRequestBodyJsonPath("vatNumber", "123 34"),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation.deleted")
    inner class OrganisationDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteOrganisationDomainEvent(organisationId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationIdOrNull(
              dpsOrganisationId = organisationId,
              OrganisationsMappingDto(
                dpsId = organisationId.toString(),
                nomisId = organisationId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteCorporate(corporateId = organisationId)
            mappingApi.stubDeleteByNomisOrganisationId(nomisOrganisationId = organisationId)
            publishDeleteOrganisationDomainEvent(organisationId = organisationId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the  deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-delete-success"),
              check {
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the organisation in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$organisationId")))
          }

          @Test
          fun `will delete the organisation mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/organisation/nomis-organisation-id/$organisationId")))
          }
        }

        @Nested
        @DisplayName("Organisation mapping already deleted")
        inner class MappingMissing {
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsOrganisationIdOrNull(dpsOrganisationId = organisationId, null)
            publishDeleteOrganisationDomainEvent(organisationId = organisationId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the organisation deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-delete-skipped"),
              check {
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateOrganisationDomainEvent(organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation.created") {
        publishDomainEvent(
          eventType = this,
          payload = organisationMessagePayload(organisationId = organisationId, eventType = this, source = source),
        )
      }
    }

    private fun publishUpdateOrganisationDomainEvent(organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationMessagePayload(organisationId = organisationId, eventType = this, source = source),
        )
      }
    }

    private fun publishDeleteOrganisationDomainEvent(organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = organisationMessagePayload(
            eventType = this,
            source = source,
            organisationId = organisationId,
          ),
        )
      }
    }

    fun organisationMessagePayload(
      eventType: String,
      source: String = "DPS",
      organisationId: String = "87654",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "source": "$source"
      }
    }
    """
  }

  @Nested
  inner class Types {
    @Nested
    @DisplayName("organisations-api.organisation-types.updated")
    inner class OrganisationTypeUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation type update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationTypeDomainEvent(organisationId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-types-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation type update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            dpsApi.stubGetSyncOrganisationTypes(
              organisationId = organisationId,
              organisationTypes(),
            )
            nomisApi.stubUpdateCorporateTypes(corporateId = organisationId)
            publishUpdateOrganisationTypeDomainEvent(organisationId = organisationId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-types-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the type updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-types-update-success"),
              check {
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get type details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-types/$organisationId")))
          }

          @Test
          fun `will update the type in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId/type")))
          }

          @Test
          fun `the updated type will contain details of the DPS contact type`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("typeCodes[0]", "some type"),
            )
          }
        }
      }
    }

    private fun publishUpdateOrganisationTypeDomainEvent(organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation-types.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationTypeMessagePayload(eventType = this, organisationId = organisationId, source = source),
        )
      }
    }

    fun organisationTypeMessagePayload(
      eventType: String,
      organisationId: String,
      source: String = "DPS",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "identifier": "$organisationId",
        "source": "$source"
      }
    }
    """
  }

  @Nested
  inner class Addresses {
    @Nested
    @DisplayName("organisations-api.organisation-address.created")
    inner class OrganisationAddressCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation address create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateOrganisationAddressDomainEvent(addressId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-address-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation address create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsAddressId = 1234567L
          private val nomisAddressId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressIdOrNull(dpsAddressId = dpsAddressId, null)
            dpsApi.stubGetSyncOrganisationAddress(
              organisationAddressId = dpsAddressId,
              organisationAddress().copy(
                organisationAddressId = dpsAddressId,
                organisationId = organisationId,
                street = "my street",
              ),
            )
            nomisApi.stubCreateCorporateAddress(
              corporateId = organisationId,
              createCorporateAddressResponse().copy(id = nomisAddressId),
            )
            mappingApi.stubCreateAddressMapping()
            publishCreateOrganisationAddressDomainEvent(addressId = dpsAddressId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the address created`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-create-success"),
              check {
                assertThat(it).containsEntry("dpsAddressId", dpsAddressId.toString())
                assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get address details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-address/$dpsAddressId")))
          }

          @Test
          fun `will create the address in NOMIS`() {
            nomisApi.verify(postRequestedFor(urlEqualTo("/corporates/$organisationId/address")))
          }

          @Test
          fun `the created address will contain details of the DPS contact address`() {
            nomisApi.verify(
              postRequestedFor(anyUrl())
                .withRequestBodyJsonPath("noFixedAddress", false)
                .withRequestBodyJsonPath("primaryAddress", false)
                .withRequestBodyJsonPath("mailAddress", true)
                .withRequestBodyJsonPath("startDate", "2022-01-01")
                .withRequestBodyJsonPath("isServices", false)
                .withRequestBodyJsonPath("typeCode", "aType")
                .withRequestBodyJsonPath("flat", "4a")
                .withRequestBodyJsonPath("premise", "something")
                .withRequestBodyJsonPath("street", "my street")
                .withRequestBodyJsonPath("locality", "an area")
                .withRequestBodyJsonPath("postcode", "S1 4UP")
                .withRequestBodyJsonPath("cityCode", "a city")
                .withRequestBodyJsonPath("countyCode", "a country")
                .withRequestBodyJsonPath("countryCode", "UK")
                .withRequestBodyJsonPath("comment", "some comments")
                .withRequestBodyJsonPath("businessHours", "9-5")
                .withRequestBodyJsonPath("contactPersonName", "Joe Bloggs"),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/corporate/address"))
                .withRequestBodyJsonPath("dpsId", "$dpsAddressId")
                .withRequestBodyJsonPath("nomisId", nomisAddressId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsAddressId = 1234567L
          private val nomisAddressId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressIdOrNull(dpsAddressId = dpsAddressId, null)
            dpsApi.stubGetSyncOrganisationAddress(
              organisationAddressId = dpsAddressId,
              organisationAddress().copy(
                organisationAddressId = dpsAddressId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateAddress(
              corporateId = organisationId,
              createCorporateAddressResponse().copy(id = nomisAddressId),
            )
            mappingApi.stubCreateAddressMappingFollowedBySuccess()
            publishCreateOrganisationAddressDomainEvent(addressId = dpsAddressId.toString())
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-address-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-address-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the address in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-address-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/address")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsAddressId = 1234567L
          private val nomisAddressId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressIdOrNull(dpsAddressId = dpsAddressId, null)
            dpsApi.stubGetSyncOrganisationAddress(
              organisationAddressId = dpsAddressId,
              organisationAddress().copy(
                organisationAddressId = dpsAddressId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateAddress(
              organisationId,
              createCorporateAddressResponse().copy(id = nomisAddressId),
            )
            mappingApi.stubCreateAddressMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OrganisationsMappingDto(
                    dpsId = dpsAddressId.toString(),
                    nomisId = 999999,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = OrganisationsMappingDto(
                    dpsId = dpsAddressId.toString(),
                    nomisId = nomisAddressId,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateOrganisationAddressDomainEvent(addressId = dpsAddressId.toString())
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-address-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the address in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-address-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/address")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-address.updated")
    inner class OrganisationAddressUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation address update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationAddressDomainEvent(addressId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-address-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation address update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsAddressId = 1234567L
          private val nomisAddressId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressId(
              dpsAddressId = dpsAddressId,
              OrganisationsMappingDto(
                dpsId = dpsAddressId.toString(),
                nomisId = nomisAddressId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetSyncOrganisationAddress(
              organisationAddressId = dpsAddressId,
              organisationAddress().copy(
                organisationAddressId = dpsAddressId,
                organisationId = organisationId,
                street = "my street",
              ),
            )
            nomisApi.stubUpdateCorporateAddress(corporateId = organisationId, addressId = nomisAddressId)
            publishUpdateOrganisationAddressDomainEvent(addressId = dpsAddressId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the address updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-update-success"),
              check {
                assertThat(it).containsEntry("dpsAddressId", dpsAddressId.toString())
                assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get address details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-address/$dpsAddressId")))
          }

          @Test
          fun `will update the address in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId/address/$nomisAddressId")))
          }

          @Test
          fun `the updated address will contain details of the DPS contact address`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("noFixedAddress", false)
                .withRequestBodyJsonPath("primaryAddress", false)
                .withRequestBodyJsonPath("mailAddress", true)
                .withRequestBodyJsonPath("startDate", "2022-01-01")
                .withRequestBodyJsonPath("isServices", false)
                .withRequestBodyJsonPath("typeCode", "aType")
                .withRequestBodyJsonPath("flat", "4a")
                .withRequestBodyJsonPath("premise", "something")
                .withRequestBodyJsonPath("street", "my street")
                .withRequestBodyJsonPath("locality", "an area")
                .withRequestBodyJsonPath("postcode", "S1 4UP")
                .withRequestBodyJsonPath("cityCode", "a city")
                .withRequestBodyJsonPath("countyCode", "a country")
                .withRequestBodyJsonPath("countryCode", "UK")
                .withRequestBodyJsonPath("comment", "some comments")
                .withRequestBodyJsonPath("businessHours", "9-5")
                .withRequestBodyJsonPath("contactPersonName", "Joe Bloggs"),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-address.deleted")
    inner class OrganisationAddressDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation address delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteOrganisationAddressDomainEvent(addressId = "12345", source = "NOMIS", organisationId = "38383")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-address-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation address delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsAddressId = 1234567L
          private val nomisAddressId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressIdOrNull(
              dpsAddressId = dpsAddressId,
              OrganisationsMappingDto(
                dpsId = dpsAddressId.toString(),
                nomisId = nomisAddressId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteCorporateAddress(corporateId = organisationId, addressId = nomisAddressId)
            mappingApi.stubDeleteByNomisAddressId(nomisAddressId = nomisAddressId)
            publishDeleteOrganisationAddressDomainEvent(
              addressId = dpsAddressId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the address deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-delete-success"),
              check {
                assertThat(it).containsEntry("dpsAddressId", dpsAddressId.toString())
                assertThat(it).containsEntry("nomisAddressId", nomisAddressId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the address in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$organisationId/address/$nomisAddressId")))
          }

          @Test
          fun `will delete the address mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/address/nomis-address-id/$nomisAddressId")))
          }
        }

        @Nested
        @DisplayName("Address mapping already deleted")
        inner class AddressMappingMissing {
          private val dpsAddressId = 1234567L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsAddressIdOrNull(dpsAddressId = dpsAddressId, null)
            publishDeleteOrganisationAddressDomainEvent(
              addressId = dpsAddressId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the address deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-address-delete-skipped"),
              check {
                assertThat(it).containsEntry("dpsAddressId", dpsAddressId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateOrganisationAddressDomainEvent(addressId: String, source: String = "DPS") {
      with("organisations-api.organisation-address.created") {
        publishDomainEvent(
          eventType = this,
          payload = organisationAddressMessagePayload(eventType = this, addressId = addressId, source = source),
        )
      }
    }

    private fun publishUpdateOrganisationAddressDomainEvent(addressId: String, source: String = "DPS") {
      with("organisations-api.organisation-address.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationAddressMessagePayload(eventType = this, addressId = addressId, source = source),
        )
      }
    }

    private fun publishDeleteOrganisationAddressDomainEvent(
      addressId: String,
      organisationId: String,
      source: String = "DPS",
    ) {
      with("organisations-api.organisation-address.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = organisationAddressMessagePayload(
            eventType = this,
            addressId = addressId,
            organisationId = organisationId,
            source = source,
          ),
        )
      }
    }

    fun organisationAddressMessagePayload(
      eventType: String,
      addressId: String,
      source: String = "DPS",
      organisationId: String = "87654",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "identifier": "$addressId",
        "source": "$source"
      }
    }
    """
  }

  @Nested
  inner class Phones {
    @Nested
    @DisplayName("organisations-api.organisation-phone.created")
    inner class OrganisationPhoneCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation phone create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateOrganisationPhoneDomainEvent(phoneId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-phone-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation phone create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsPhoneId = 1234567L
          private val nomisPhoneId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneIdOrNull(dpsPhoneId = dpsPhoneId, null)
            dpsApi.stubGetSyncOrganisationPhone(
              organisationPhoneId = dpsPhoneId,
              organisationPhone().copy(
                organisationPhoneId = dpsPhoneId,
                organisationId = organisationId,
                phoneNumber = "07973 555 5555",
                phoneType = "MOB",
                extNumber = "x555",
              ),
            )
            nomisApi.stubCreateCorporatePhone(
              corporateId = organisationId,
              createCorporatePhoneResponse().copy(id = nomisPhoneId),
            )
            mappingApi.stubCreatePhoneMapping()
            publishCreateOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the phone created`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-create-success"),
              check {
                assertThat(it).containsEntry("dpsPhoneId", dpsPhoneId.toString())
                assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get phone details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-phone/$dpsPhoneId")))
          }

          @Test
          fun `will create the phone in NOMIS`() {
            nomisApi.verify(postRequestedFor(urlEqualTo("/corporates/$organisationId/phone")))
          }

          @Test
          fun `the created phone will contain details of the DPS contact phone`() {
            nomisApi.verify(
              postRequestedFor(anyUrl())
                .withRequestBodyJsonPath("number", "07973 555 5555")
                .withRequestBodyJsonPath("typeCode", "MOB")
                .withRequestBodyJsonPath("extension", "x555"),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/corporate/phone"))
                .withRequestBodyJsonPath("dpsId", "$dpsPhoneId")
                .withRequestBodyJsonPath("nomisId", nomisPhoneId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsPhoneId = 1234567L
          private val nomisPhoneId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneIdOrNull(dpsPhoneId = dpsPhoneId, null)
            dpsApi.stubGetSyncOrganisationPhone(
              organisationPhoneId = dpsPhoneId,
              organisationPhone().copy(
                organisationPhoneId = dpsPhoneId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporatePhone(
              corporateId = organisationId,
              createCorporatePhoneResponse().copy(id = nomisPhoneId),
            )
            mappingApi.stubCreatePhoneMappingFollowedBySuccess()
            publishCreateOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString())
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-phone-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-phone-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the phone in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-phone-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/phone")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsPhoneId = 1234567L
          private val nomisPhoneId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneIdOrNull(dpsPhoneId = dpsPhoneId, null)
            dpsApi.stubGetSyncOrganisationPhone(
              organisationPhoneId = dpsPhoneId,
              organisationPhone().copy(
                organisationPhoneId = dpsPhoneId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporatePhone(organisationId, createCorporatePhoneResponse().copy(id = nomisPhoneId))
            mappingApi.stubCreatePhoneMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OrganisationsMappingDto(
                    dpsId = dpsPhoneId.toString(),
                    nomisId = 999999,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = OrganisationsMappingDto(
                    dpsId = dpsPhoneId.toString(),
                    nomisId = nomisPhoneId,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString())
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-phone-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the phone in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-phone-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/phone")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-phone.updated")
    inner class OrganisationPhoneUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation phone update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationPhoneDomainEvent(phoneId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-phone-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation phone update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsPhoneId = 1234567L
          private val nomisPhoneId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneId(
              dpsPhoneId = dpsPhoneId,
              OrganisationsMappingDto(
                dpsId = dpsPhoneId.toString(),
                nomisId = nomisPhoneId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetSyncOrganisationPhone(
              organisationPhoneId = dpsPhoneId,
              organisationPhone().copy(
                organisationPhoneId = dpsPhoneId,
                organisationId = organisationId,
                phoneNumber = "07973 555 5555",
                phoneType = "MOB",
                extNumber = "x555",
              ),
            )
            nomisApi.stubUpdateCorporatePhone(corporateId = organisationId, phoneId = nomisPhoneId)
            publishUpdateOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the phone updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-update-success"),
              check {
                assertThat(it).containsEntry("dpsPhoneId", dpsPhoneId.toString())
                assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get phone details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-phone/$dpsPhoneId")))
          }

          @Test
          fun `will update the phone in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId/phone/$nomisPhoneId")))
          }

          @Test
          fun `the updated phone will contain details of the DPS contact phone`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("number", "07973 555 5555")
                .withRequestBodyJsonPath("typeCode", "MOB")
                .withRequestBodyJsonPath("extension", "x555"),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-phone.deleted")
    inner class OrganisationPhoneDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation phone delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteOrganisationPhoneDomainEvent(phoneId = "12345", source = "NOMIS", organisationId = "38383")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-phone-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation phone delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsPhoneId = 1234567L
          private val nomisPhoneId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneIdOrNull(
              dpsPhoneId = dpsPhoneId,
              OrganisationsMappingDto(
                dpsId = dpsPhoneId.toString(),
                nomisId = nomisPhoneId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteCorporatePhone(corporateId = organisationId, phoneId = nomisPhoneId)
            mappingApi.stubDeleteByNomisPhoneId(nomisPhoneId = nomisPhoneId)
            publishDeleteOrganisationPhoneDomainEvent(
              phoneId = dpsPhoneId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the phone deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-delete-success"),
              check {
                assertThat(it).containsEntry("dpsPhoneId", dpsPhoneId.toString())
                assertThat(it).containsEntry("nomisPhoneId", nomisPhoneId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the phone in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$organisationId/phone/$nomisPhoneId")))
          }

          @Test
          fun `will delete the phone mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/phone/nomis-phone-id/$nomisPhoneId")))
          }
        }

        @Nested
        @DisplayName("Phone mapping already deleted")
        inner class PhoneMappingMissing {
          private val dpsPhoneId = 1234567L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsPhoneIdOrNull(dpsPhoneId = dpsPhoneId, null)
            publishDeleteOrganisationPhoneDomainEvent(
              phoneId = dpsPhoneId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the phone deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-phone-delete-skipped"),
              check {
                assertThat(it).containsEntry("dpsPhoneId", dpsPhoneId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateOrganisationPhoneDomainEvent(phoneId: String, source: String = "DPS") {
      with("organisations-api.organisation-phone.created") {
        publishDomainEvent(
          eventType = this,
          payload = organisationPhoneMessagePayload(eventType = this, phoneId = phoneId, source = source),
        )
      }
    }

    private fun publishUpdateOrganisationPhoneDomainEvent(phoneId: String, source: String = "DPS") {
      with("organisations-api.organisation-phone.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationPhoneMessagePayload(eventType = this, phoneId = phoneId, source = source),
        )
      }
    }

    private fun publishDeleteOrganisationPhoneDomainEvent(
      phoneId: String,
      organisationId: String,
      source: String = "DPS",
    ) {
      with("organisations-api.organisation-phone.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = organisationPhoneMessagePayload(
            eventType = this,
            phoneId = phoneId,
            organisationId = organisationId,
            source = source,
          ),
        )
      }
    }

    fun organisationPhoneMessagePayload(
      eventType: String,
      phoneId: String,
      source: String = "DPS",
      organisationId: String = "87654",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "identifier": "$phoneId",
        "source": "$source"
      }
    }
    """
  }

  @Nested
  inner class Emails {
    @Nested
    @DisplayName("organisations-api.organisation-email.created")
    inner class OrganisationEmailCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation email create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateOrganisationEmailDomainEvent(emailId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-email-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation email create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsEmailId = 1234567L
          private val nomisEmailId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailIdOrNull(dpsEmailId = dpsEmailId, null)
            dpsApi.stubGetSyncOrganisationEmail(
              organisationEmailId = dpsEmailId,
              organisationEmail().copy(
                organisationEmailId = dpsEmailId,
                organisationId = organisationId,
                emailAddress = "07973@somewhere.com",
              ),
            )
            nomisApi.stubCreateCorporateEmail(
              corporateId = organisationId,
              createCorporateEmailResponse().copy(id = nomisEmailId),
            )
            mappingApi.stubCreateEmailMapping()
            publishCreateOrganisationEmailDomainEvent(emailId = dpsEmailId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the email created`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-create-success"),
              check {
                assertThat(it).containsEntry("dpsEmailId", dpsEmailId.toString())
                assertThat(it).containsEntry("nomisEmailId", nomisEmailId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get email details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-email/$dpsEmailId")))
          }

          @Test
          fun `will create the email in NOMIS`() {
            nomisApi.verify(postRequestedFor(urlEqualTo("/corporates/$organisationId/email")))
          }

          @Test
          fun `the created email will contain details of the DPS contact email`() {
            nomisApi.verify(
              postRequestedFor(anyUrl())
                .withRequestBodyJsonPath("email", "07973@somewhere.com"),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/corporate/email"))
                .withRequestBodyJsonPath("dpsId", "$dpsEmailId")
                .withRequestBodyJsonPath("nomisId", nomisEmailId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsEmailId = 1234567L
          private val nomisEmailId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailIdOrNull(dpsEmailId = dpsEmailId, null)
            dpsApi.stubGetSyncOrganisationEmail(
              organisationEmailId = dpsEmailId,
              organisationEmail().copy(
                organisationEmailId = dpsEmailId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateEmail(
              corporateId = organisationId,
              createCorporateEmailResponse().copy(id = nomisEmailId),
            )
            mappingApi.stubCreateEmailMappingFollowedBySuccess()
            publishCreateOrganisationEmailDomainEvent(emailId = dpsEmailId.toString())
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-email-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-email-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the email in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-email-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/email")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsEmailId = 1234567L
          private val nomisEmailId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailIdOrNull(dpsEmailId = dpsEmailId, null)
            dpsApi.stubGetSyncOrganisationEmail(
              organisationEmailId = dpsEmailId,
              organisationEmail().copy(
                organisationEmailId = dpsEmailId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateEmail(organisationId, createCorporateEmailResponse().copy(id = nomisEmailId))
            mappingApi.stubCreateEmailMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OrganisationsMappingDto(
                    dpsId = dpsEmailId.toString(),
                    nomisId = 999999,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = OrganisationsMappingDto(
                    dpsId = dpsEmailId.toString(),
                    nomisId = nomisEmailId,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateOrganisationEmailDomainEvent(emailId = dpsEmailId.toString())
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-email-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the email in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-email-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/email")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-email.updated")
    inner class OrganisationEmailUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation email update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationEmailDomainEvent(emailId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-email-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation email update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsEmailId = 1234567L
          private val nomisEmailId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailId(
              dpsEmailId = dpsEmailId,
              OrganisationsMappingDto(
                dpsId = dpsEmailId.toString(),
                nomisId = nomisEmailId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetSyncOrganisationEmail(
              organisationEmailId = dpsEmailId,
              organisationEmail().copy(
                organisationEmailId = dpsEmailId,
                organisationId = organisationId,
                emailAddress = "07973@somewhere.com",
              ),
            )
            nomisApi.stubUpdateCorporateEmail(corporateId = organisationId, emailId = nomisEmailId)
            publishUpdateOrganisationEmailDomainEvent(emailId = dpsEmailId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the email updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-update-success"),
              check {
                assertThat(it).containsEntry("dpsEmailId", dpsEmailId.toString())
                assertThat(it).containsEntry("nomisEmailId", nomisEmailId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get email details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-email/$dpsEmailId")))
          }

          @Test
          fun `will update the email in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId/email/$nomisEmailId")))
          }

          @Test
          fun `the updated email will contain details of the DPS contact email`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("email", "07973@somewhere.com"),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-email.deleted")
    inner class OrganisationEmailDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation email delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteOrganisationEmailDomainEvent(emailId = "12345", source = "NOMIS", organisationId = "38383")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-email-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation email delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsEmailId = 1234567L
          private val nomisEmailId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailIdOrNull(
              dpsEmailId = dpsEmailId,
              OrganisationsMappingDto(
                dpsId = dpsEmailId.toString(),
                nomisId = nomisEmailId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteCorporateEmail(corporateId = organisationId, emailId = nomisEmailId)
            mappingApi.stubDeleteByNomisEmailId(nomisEmailId = nomisEmailId)
            publishDeleteOrganisationEmailDomainEvent(
              emailId = dpsEmailId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the email deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-delete-success"),
              check {
                assertThat(it).containsEntry("dpsEmailId", dpsEmailId.toString())
                assertThat(it).containsEntry("nomisEmailId", nomisEmailId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the email in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$organisationId/email/$nomisEmailId")))
          }

          @Test
          fun `will delete the email mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/email/nomis-internet-address-id/$nomisEmailId")))
          }
        }

        @Nested
        @DisplayName("Email mapping already deleted")
        inner class EmailMappingMissing {
          private val dpsEmailId = 1234567L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsEmailIdOrNull(dpsEmailId = dpsEmailId, null)
            publishDeleteOrganisationEmailDomainEvent(
              emailId = dpsEmailId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the email deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-email-delete-skipped"),
              check {
                assertThat(it).containsEntry("dpsEmailId", dpsEmailId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateOrganisationEmailDomainEvent(emailId: String, source: String = "DPS") {
      with("organisations-api.organisation-email.created") {
        publishDomainEvent(
          eventType = this,
          payload = organisationEmailMessagePayload(eventType = this, emailId = emailId, source = source),
        )
      }
    }

    private fun publishUpdateOrganisationEmailDomainEvent(emailId: String, source: String = "DPS") {
      with("organisations-api.organisation-email.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationEmailMessagePayload(eventType = this, emailId = emailId, source = source),
        )
      }
    }

    private fun publishDeleteOrganisationEmailDomainEvent(
      emailId: String,
      organisationId: String,
      source: String = "DPS",
    ) {
      with("organisations-api.organisation-email.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = organisationEmailMessagePayload(
            eventType = this,
            emailId = emailId,
            organisationId = organisationId,
            source = source,
          ),
        )
      }
    }

    fun organisationEmailMessagePayload(
      eventType: String,
      emailId: String,
      source: String = "DPS",
      organisationId: String = "87654",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "identifier": "$emailId",
        "source": "$source"
      }
    }
    """
  }

  @Nested
  inner class WebAddresses {
    @Nested
    @DisplayName("organisations-api.organisation-web.created")
    inner class OrganisationWebCreated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation web create")
      inner class WhenNomisCreated {

        @BeforeEach
        fun setUp() {
          publishCreateOrganisationWebDomainEvent(webId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-web-create-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation web create")
      inner class WhenDpsCreated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsWebId = 1234567L
          private val nomisWebId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebIdOrNull(dpsWebId = dpsWebId, null)
            dpsApi.stubGetSyncOrganisationWeb(
              organisationWebId = dpsWebId,
              organisationWeb().copy(
                organisationWebAddressId = dpsWebId,
                organisationId = organisationId,
                webAddress = "07973.somewhere.com",
              ),
            )
            nomisApi.stubCreateCorporateWebAddress(
              corporateId = organisationId,
              createCorporateWebAddressResponse().copy(id = nomisWebId),
            )
            mappingApi.stubCreateWebMapping()
            publishCreateOrganisationWebDomainEvent(webId = dpsWebId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the create`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-create-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the web created`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-create-success"),
              check {
                assertThat(it).containsEntry("dpsWebId", dpsWebId.toString())
                assertThat(it).containsEntry("nomisWebId", nomisWebId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get web details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-web/$dpsWebId")))
          }

          @Test
          fun `will create the web in NOMIS`() {
            nomisApi.verify(postRequestedFor(urlEqualTo("/corporates/$organisationId/web-address")))
          }

          @Test
          fun `the created web will contain details of the DPS contact web`() {
            nomisApi.verify(
              postRequestedFor(anyUrl())
                .withRequestBodyJsonPath("webAddress", "07973.somewhere.com"),
            )
          }

          @Test
          fun `will create a mapping between the NOMIS and DPS ids`() {
            mappingApi.verify(
              postRequestedFor(urlEqualTo("/mapping/corporate/web"))
                .withRequestBodyJsonPath("dpsId", "$dpsWebId")
                .withRequestBodyJsonPath("nomisId", nomisWebId)
                .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
            )
          }
        }

        @Nested
        @DisplayName("when mapping service fails once")
        inner class MappingFailure {
          private val dpsWebId = 1234567L
          private val nomisWebId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebIdOrNull(dpsWebId = dpsWebId, null)
            dpsApi.stubGetSyncOrganisationWeb(
              organisationWebId = dpsWebId,
              organisationWeb().copy(
                organisationWebAddressId = dpsWebId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateWebAddress(
              corporateId = organisationId,
              createCorporateWebAddressResponse().copy(id = nomisWebId),
            )
            mappingApi.stubCreateWebMappingFollowedBySuccess()
            publishCreateOrganisationWebDomainEvent(webId = dpsWebId.toString())
          }

          @Test
          fun `will send telemetry for initial failure`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-web-mapping-create-failed"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will eventually send telemetry for success`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-web-create-success"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the web in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("organisation-web-create-success"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/web-address")))
            }
          }
        }

        @Nested
        @DisplayName("when mapping service detects a duplicate mapping")
        inner class DuplicateMappingFailure {
          private val dpsWebId = 1234567L
          private val nomisWebId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebIdOrNull(dpsWebId = dpsWebId, null)
            dpsApi.stubGetSyncOrganisationWeb(
              organisationWebId = dpsWebId,
              organisationWeb().copy(
                organisationWebAddressId = dpsWebId,
                organisationId = organisationId,
              ),
            )
            nomisApi.stubCreateCorporateWebAddress(
              organisationId,
              createCorporateWebAddressResponse().copy(id = nomisWebId),
            )
            mappingApi.stubCreateWebMapping(
              error = DuplicateMappingErrorResponse(
                moreInfo = DuplicateErrorContentObject(
                  duplicate = OrganisationsMappingDto(
                    dpsId = dpsWebId.toString(),
                    nomisId = 999999,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                  existing = OrganisationsMappingDto(
                    dpsId = dpsWebId.toString(),
                    nomisId = nomisWebId,
                    mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
                  ),
                ),
                errorCode = 1409,
                status = DuplicateMappingErrorResponse.Status._409_CONFLICT,
                userMessage = "Duplicate mapping",
              ),
            )
            publishCreateOrganisationWebDomainEvent(webId = dpsWebId.toString())
          }

          @Test
          fun `will send telemetry for duplicate`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-web-duplicate"),
                any(),
                isNull(),
              )
            }
          }

          @Test
          fun `will create the web in NOMIS once`() {
            await untilAsserted {
              verify(telemetryClient).trackEvent(
                eq("to-nomis-synch-organisation-web-duplicate"),
                any(),
                isNull(),
              )
              nomisApi.verify(1, postRequestedFor(urlEqualTo("/corporates/$organisationId/web-address")))
            }
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-web.updated")
    inner class OrganisationWebUpdated {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation web update")
      inner class WhenNomisUpdated {

        @BeforeEach
        fun setUp() {
          publishUpdateOrganisationWebDomainEvent(webId = "12345", source = "NOMIS")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-web-update-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation web update")
      inner class WhenDpsUpdated {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsWebId = 1234567L
          private val nomisWebId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebId(
              dpsWebId = dpsWebId,
              OrganisationsMappingDto(
                dpsId = dpsWebId.toString(),
                nomisId = nomisWebId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            dpsApi.stubGetSyncOrganisationWeb(
              organisationWebId = dpsWebId,
              organisationWeb().copy(
                organisationWebAddressId = dpsWebId,
                organisationId = organisationId,
                webAddress = "07973.somewhere.com",
              ),
            )
            nomisApi.stubUpdateCorporateWebAddress(corporateId = organisationId, webAddressId = nomisWebId)
            publishUpdateOrganisationWebDomainEvent(webId = dpsWebId.toString())
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the update`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-update-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the web updated`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-update-success"),
              check {
                assertThat(it).containsEntry("dpsWebId", dpsWebId.toString())
                assertThat(it).containsEntry("nomisWebId", nomisWebId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will call back to DPS to get web details`() {
            dpsApi.verify(getRequestedFor(urlEqualTo("/sync/organisation-web/$dpsWebId")))
          }

          @Test
          fun `will update the web in NOMIS`() {
            nomisApi.verify(putRequestedFor(urlEqualTo("/corporates/$organisationId/web-address/$nomisWebId")))
          }

          @Test
          fun `the updated web will contain details of the DPS contact web`() {
            nomisApi.verify(
              putRequestedFor(anyUrl())
                .withRequestBodyJsonPath("webAddress", "07973.somewhere.com"),
            )
          }
        }
      }
    }

    @Nested
    @DisplayName("organisations-api.organisation-web.deleted")
    inner class OrganisationWebDeleted {

      @Nested
      @DisplayName("when NOMIS is the origin of an organisation web delete")
      inner class WhenNomisDeleted {

        @BeforeEach
        fun setUp() {
          publishDeleteOrganisationWebDomainEvent(webId = "12345", source = "NOMIS", organisationId = "38383")
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will send telemetry event showing the ignore`() {
          verify(telemetryClient).trackEvent(
            eq("organisation-web-delete-ignored"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      @DisplayName("when DPS is the origin of an organisation web delete")
      inner class WhenDpsDeleted {
        @Nested
        @DisplayName("when all goes ok")
        inner class HappyPath {
          private val dpsWebId = 1234567L
          private val nomisWebId = 7654321L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebIdOrNull(
              dpsWebId = dpsWebId,
              OrganisationsMappingDto(
                dpsId = dpsWebId.toString(),
                nomisId = nomisWebId,
                mappingType = OrganisationsMappingDto.MappingType.MIGRATED,
              ),
            )
            nomisApi.stubDeleteCorporateWebAddress(corporateId = organisationId, webAddressId = nomisWebId)
            mappingApi.stubDeleteByNomisWebId(nomisWebId = nomisWebId)
            publishDeleteOrganisationWebDomainEvent(
              webId = dpsWebId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `will send telemetry event showing the delete`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-delete-success"),
              any(),
              isNull(),
            )
          }

          @Test
          fun `telemetry will contain key facts about the web deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-delete-success"),
              check {
                assertThat(it).containsEntry("dpsWebId", dpsWebId.toString())
                assertThat(it).containsEntry("nomisWebId", nomisWebId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }

          @Test
          fun `will delete the web in NOMIS`() {
            nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$organisationId/web-address/$nomisWebId")))
          }

          @Test
          fun `will delete the web mapping`() {
            mappingApi.verify(deleteRequestedFor(urlPathEqualTo("/mapping/corporate/web/nomis-internet-address-id/$nomisWebId")))
          }
        }

        @Nested
        @DisplayName("Web mapping already deleted")
        inner class WebMappingMissing {
          private val dpsWebId = 1234567L
          private val organisationId = 54321L

          @BeforeEach
          fun setUp() {
            mappingApi.stubGetByDpsWebIdOrNull(dpsWebId = dpsWebId, null)
            publishDeleteOrganisationWebDomainEvent(
              webId = dpsWebId.toString(),
              organisationId = organisationId.toString(),
            )
            waitForAnyProcessingToComplete()
          }

          @Test
          fun `telemetry will contain key facts about the web deleted`() {
            verify(telemetryClient).trackEvent(
              eq("organisation-web-delete-skipped"),
              check {
                assertThat(it).containsEntry("dpsWebId", dpsWebId.toString())
                assertThat(it).containsEntry("organisationId", organisationId.toString())
              },
              isNull(),
            )
          }
        }
      }
    }

    private fun publishCreateOrganisationWebDomainEvent(webId: String, source: String = "DPS") {
      with("organisations-api.organisation-web.created") {
        publishDomainEvent(
          eventType = this,
          payload = organisationWebMessagePayload(eventType = this, webId = webId, source = source),
        )
      }
    }

    private fun publishUpdateOrganisationWebDomainEvent(webId: String, source: String = "DPS") {
      with("organisations-api.organisation-web.updated") {
        publishDomainEvent(
          eventType = this,
          payload = organisationWebMessagePayload(eventType = this, webId = webId, source = source),
        )
      }
    }

    private fun publishDeleteOrganisationWebDomainEvent(webId: String, organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation-web.deleted") {
        publishDomainEvent(
          eventType = this,
          payload = organisationWebMessagePayload(
            eventType = this,
            webId = webId,
            organisationId = organisationId,
            source = source,
          ),
        )
      }
    }

    fun organisationWebMessagePayload(
      eventType: String,
      webId: String,
      source: String = "DPS",
      organisationId: String = "87654",
    ) = //language=JSON
      """
    {
      "eventType":"$eventType", 
      "additionalInformation": {
        "organisationId": "$organisationId",
        "identifier": "$webId",
        "source": "$source"
      }
    }
    """
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
