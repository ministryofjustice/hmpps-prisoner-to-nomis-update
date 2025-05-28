package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
import org.springframework.http.HttpStatus
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateErrorContentObject
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.organisationPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporatePhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath

class OrganisationsToNomisIntTest : SqsIntegrationTestBase() {
  @Autowired
  private lateinit var nomisApi: OrganisationsNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: OrganisationsMappingApiMockServer

  private val dpsApi = OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer

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
        nomisApi.verify(
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
          mappingApi.stubGetByDpsOrganisationId(HttpStatus.NOT_FOUND)
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

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsOrganisationId(
            dpsOrganisationId,
            OrganisationsMappingDto(
              dpsId = dpsOrganisationId,
              nomisId = nomisOrganisationId,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            ),
          )
          nomisApi.stubDeleteCorporateOrganisation(corporateId = nomisOrganisationId)
          mappingApi.stubDeleteByDpsOrganisationId(dpsOrganisationId = dpsOrganisationId)
          publishDeleteOrganisationDomainEvent(organisationId = dpsOrganisationId)
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
          mappingApi.verify(getRequestedFor(urlMatching("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
        }

        @Test
        fun `will delete the organisation in NOMIS`() {
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$nomisOrganisationId")))
        }

        @Test
        fun `will delete the organisation mapping`() {
          mappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
        }
      }

      @Nested
      @DisplayName("when mapping delete fails")
      inner class WhenMappingDeleteFails {
        private val dpsOrganisationId = "565643"
        private val nomisOrganisationId = 123456L

        @BeforeEach
        fun setUp() {
          mappingApi.stubGetByDpsOrganisationId(
            dpsOrganisationId,
            OrganisationsMappingDto(
              dpsId = dpsOrganisationId,
              nomisId = nomisOrganisationId,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            ),
          )
          nomisApi.stubDeleteCorporateOrganisation(corporateId = nomisOrganisationId)
          mappingApi.stubDeleteByDpsOrganisationId(status = HttpStatus.INTERNAL_SERVER_ERROR)
          publishDeleteOrganisationDomainEvent(organisationId = dpsOrganisationId)
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
          nomisApi.verify(deleteRequestedFor(urlEqualTo("/corporates/$nomisOrganisationId")))
        }

        @Test
        fun `will try delete the organisation mapping once and ignore failure`() {
          mappingApi.verify(deleteRequestedFor(urlEqualTo("/mapping/corporate/organisation/dps-organisation-id/$dpsOrganisationId")))
          verify(telemetryClient).trackEvent(
            eq("organisation-mapping-deleted-failed"),
            any(),
            isNull(),
          )
        }
      }
    }
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
            nomisApi.stubCreateCorporatePhone(corporateId = organisationId, createCorporatePhoneResponse().copy(id = nomisPhoneId))
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
            nomisApi.stubCreateCorporatePhone(corporateId = organisationId, createCorporatePhoneResponse().copy(id = nomisPhoneId))
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
            publishDeleteOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString(), organisationId = organisationId.toString())
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
            publishDeleteOrganisationPhoneDomainEvent(phoneId = dpsPhoneId.toString(), organisationId = organisationId.toString())
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
        publishDomainEvent(eventType = this, payload = organisationPhoneMessagePayload(eventType = this, phoneId = phoneId, source = source))
      }
    }
    private fun publishUpdateOrganisationPhoneDomainEvent(phoneId: String, source: String = "DPS") {
      with("organisations-api.organisation-phone.updated") {
        publishDomainEvent(eventType = this, payload = organisationPhoneMessagePayload(eventType = this, phoneId = phoneId, source = source))
      }
    }
    private fun publishDeleteOrganisationPhoneDomainEvent(phoneId: String, organisationId: String, source: String = "DPS") {
      with("organisations-api.organisation-phone.deleted") {
        publishDomainEvent(eventType = this, payload = organisationPhoneMessagePayload(eventType = this, phoneId = phoneId, organisationId = organisationId, source = source))
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

  private fun publishDeleteOrganisationDomainEvent(
    organisationId: String = "565643",
    source: OrganisationSource = OrganisationSource.DPS,
  ) {
    publishOrganisationDomainEvent("organisations-api.organisation.deleted", organisationId, source)
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

  private fun publishOrganisationDomainEvent(
    eventType: String,
    organisationId: String,
    source: OrganisationSource,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          organisationMessagePayload(
            eventType = eventType,
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
      }
    }
    """
