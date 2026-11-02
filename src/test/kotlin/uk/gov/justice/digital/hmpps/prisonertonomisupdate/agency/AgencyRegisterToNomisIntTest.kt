package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyEmailAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.agencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.hospitalAgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyAddressesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyEmailAddressesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyPhoneNumbersResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyAddressDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyEmailDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.legacyAgencyPhoneDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyEmailAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyPhoneNumberRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumber
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyPhoneNumbersRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateAgencyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody

class AgencyRegisterToNomisIntTest(
  @Autowired
  private val nomisApi: AgencyNomisApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = AgencyRegistersDpsApiExtension.agencyRegistersApi

  @Nested
  @DisplayName("register.court.inserted")
  inner class CourtInserted {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtInsertedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = courtId))
          publishCourtInsertedEvent(courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(courtId)
          assertThat(request.description).isEqualTo("Sheffield Crown Ct")
          assertThat(request.typeCode).isEqualTo("CRT")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Crown Court")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-created-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["nomisAgencyId"]).isEqualTo(courtId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.amended")
  inner class CourtAmended {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtAmendedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = courtId, response = agencyResponse().copy(agencyId = courtId))
          publishCourtAmendedEvent(courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Crown Ct")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Crown Court")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["nomisAgencyId"]).isEqualTo(courtId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.deleted")
  inner class CourtDeleted {
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtDeletedEvent(courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$courtId")))
        verify(telemetryClient).trackEvent(
          eq("court-deleted-ignored"),
          check { assertThat(it["courtId"]).isEqualTo(courtId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = courtId)
        publishCourtDeletedEvent(courtId = courtId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$courtId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("court-deleted-success"),
          check { assertThat(it["courtId"]).isEqualTo(courtId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.court.email.inserted")
  inner class CourtEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishCourtEmailInsertedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(courtId, legacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))))
          nomisApi.stubCreateAgencyEmail(agencyId = courtId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishCourtEmailInsertedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-created-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.email.amended")
  inner class CourtEmailAmended {
    val dpsEmailId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtEmailAmendedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("court.one@justice.gov.uk", "court.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = courtId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishCourtEmailAmendedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.email.deleted")
  inner class CourtEmailDeleted {
    val dpsEmailId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtEmailDeletedEvent(emailId = dpsEmailId, courtId = courtId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-email-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("court.one@justice.gov.uk", "court.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = courtId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishCourtEmailDeletedEvent(emailId = dpsEmailId, courtId = courtId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-email-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.phone.inserted")
  inner class CourtPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.inserted", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = courtId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishCourtPhoneEvent("register.court.phone.inserted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$courtId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-created-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.phone.amended")
  inner class CourtPhoneAmended {
    val dpsPhoneId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.amended", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishCourtPhoneEvent("register.court.phone.amended", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.phone.deleted")
  inner class CourtPhoneDeleted {
    val dpsPhoneId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-phone-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = courtId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishCourtPhoneEvent("register.court.phone.deleted", courtId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("court-phone-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.inserted")
  inner class CourtAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.inserted", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-created-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = courtId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
        }

        private fun insertAddress(addressLine1: String?, addressLine2: String?) {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another court address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = addressLine1,
                  addressLine2 = addressLine2,
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishCourtAddressEvent("register.court.address.inserted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        private fun assertNomisAddress(expected: CreateAgencyAddressRequest) {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expected)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
        }

        @Nested
        inner class BothAddressLinesNull {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = null, addressLine2 = null)
          }

          @Test
          fun `will create only the inserted address in NOMIS with no street or locality`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = null, street = null, locality = null))
          }
        }

        @Nested
        inner class NeitherAddressLineContainsCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "The Law Courts", addressLine2 = "50 West Bar")
          }

          @Test
          fun `will create only the inserted address in NOMIS with street and locality`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = null, street = "The Law Courts", locality = "50 West Bar"))
          }

          @Test
          fun `will send telemetry event showing success`() {
            verify(telemetryClient).trackEvent(
              eq("court-address-created-success"),
              check {
                assertThat(it["courtId"]).isEqualTo(courtId)
                assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
                assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
              },
              isNull(),
            )
          }
        }

        @Nested
        inner class AddressLine1ContainsOneComma {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "The Law Courts, 50 West Bar", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = null, premise = "The Law Courts", street = "50 West Bar", locality = "Sheffield City Centre"))
          }
        }

        @Nested
        inner class AddressLine1ContainsTwoCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "Floor 2, The Law Courts, 50 West Bar", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between flat, premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar", locality = "Sheffield City Centre"))
          }
        }

        @Nested
        inner class AddressLine1ContainsThreeCommas {
          @BeforeEach
          fun setUp() {
            insertAddress(addressLine1 = "Floor 2, The Law Courts, 50 West Bar, Justice Area", addressLine2 = "Sheffield City Centre")
          }

          @Test
          fun `will split address line 1 between flat, premise and street`() {
            assertNomisAddress(expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar, Justice Area", locality = "Sheffield City Centre"))
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.amended")
  inner class CourtAddressAmended {
    val dpsAddressId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.amended", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-updated-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Law Courts",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                address,
                address.copy(id = 2, addressLine1 = " The Law Courts , 50 West Bar "),
                address.copy(id = 3, addressLine1 = " Floor 2 , The Law Courts , 50 West Bar "),
                address.copy(id = 4, addressLine1 = "Floor 2, The Law Courts, 50 West Bar, Justice Area"),
                legacyAgencyAddressDto().copy(id = 5, addressLine1 = null, addressLine2 = null, town = null, county = null, postcode = null, country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(
              addresses = (1L..5L).map { agencyAddress().copy(id = it) },
            ),
          )
          publishCourtAddressEvent("register.court.address.amended", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            expectedAddress,
            expectedAddress.copy(premise = "The Law Courts", street = "50 West Bar"),
            expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar"),
            expectedAddress.copy(flat = "Floor 2", premise = "The Law Courts", street = "50 West Bar, Justice Area"),
            UpdateAgencyAddress(),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-updated-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2, 3, 4, 5")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.court.address.deleted")
  inner class CourtAddressDeleted {
    val dpsAddressId = 12345L
    val courtId = "SHEFCC"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("court-address-deleted-ignored"),
          check {
            assertThat(it["courtId"]).isEqualTo(courtId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$courtId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$courtId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = courtId,
            response = legacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Law Courts", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another court address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Law Courts", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another court address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = courtId, response = legacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = courtId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishCourtAddressEvent("register.court.address.deleted", courtId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$courtId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("court-address-deleted-success"),
            check {
              assertThat(it["courtId"]).isEqualTo(courtId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.inserted")
  inner class HospitalInserted {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishHospitalInsertedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = hospitalAgencyResponse().copy(agencyId = hospitalId))
          publishHospitalInsertedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(hospitalId)
          assertThat(request.description).isEqualTo("Hallamshire Hospital")
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Hallamshire Hospital, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["nomisAgencyId"]).isEqualTo(hospitalId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.amended")
  inner class HospitalAmended {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishHospitalAmendedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = hospitalId, response = hospitalAgencyResponse().copy(agencyId = hospitalId))
          publishHospitalAmendedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId")), jsonMapper)
          assertThat(request.description).isEqualTo("Hallamshire Hospital")
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Hallamshire Hospital, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.courtTypeCode).isEqualTo("CC")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["nomisAgencyId"]).isEqualTo(hospitalId)
            },
            isNull(),
          )
        }

        @Test
        fun `will update NOMIS when the agency type changes to HOSPITAL`() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(agencyType = LegacyAgencyType.HOSPITAL))
          publishHospitalAmendedEvent(hospitalId = hospitalId)
          waitForAnyProcessingToComplete(times = 2)

          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId")), jsonMapper)
          assertThat(request.typeCode).isEqualTo("HOSPITAL")
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.deleted")
  inner class HospitalDeleted {
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalDeletedEvent(hospitalId = hospitalId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$hospitalId")))
        verify(telemetryClient).trackEvent(
          eq("hospital-deleted-ignored"),
          check { assertThat(it["hospitalId"]).isEqualTo(hospitalId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = hospitalId)
        publishHospitalDeletedEvent(hospitalId = hospitalId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$hospitalId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-deleted-success"),
          check { assertThat(it["hospitalId"]).isEqualTo(hospitalId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.inserted")
  inner class HospitalPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.inserted", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = hospitalId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishHospitalPhoneEvent("register.hospital.phone.inserted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.amended")
  inner class HospitalPhoneAmended {
    val dpsPhoneId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.amended", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishHospitalPhoneEvent("register.hospital.phone.amended", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.phone.deleted")
  inner class HospitalPhoneDeleted {
    val dpsPhoneId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-phone-deleted-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = hospitalId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishHospitalPhoneEvent("register.hospital.phone.deleted", hospitalId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-phone-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.inserted")
  inner class HospitalAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.inserted", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-created-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = hospitalId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another hospital address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Law Courts",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishHospitalAddressEvent("register.hospital.address.inserted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-created-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.amended")
  inner class HospitalAddressAmended {
    val dpsAddressId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.amended", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-updated-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Law Courts",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Law Courts",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishHospitalAddressEvent("register.hospital.address.amended", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-updated-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.hospital.address.deleted")
  inner class HospitalAddressDeleted {
    val dpsAddressId = 12345L
    val hospitalId = "HHOSP1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("hospital-address-deleted-ignored"),
          check {
            assertThat(it["hospitalId"]).isEqualTo(hospitalId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$hospitalId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = hospitalId,
            response = hospitalLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Law Courts", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another hospital address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Law Courts", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another hospital address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = hospitalId, response = hospitalLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = hospitalId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishHospitalAddressEvent("register.hospital.address.deleted", hospitalId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$hospitalId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("hospital-address-deleted-success"),
            check {
              assertThat(it["hospitalId"]).isEqualTo(hospitalId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.inserted")
  inner class PoliceCustodySuiteInserted {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteInsertedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = policeCustodySuiteId))
          publishPoliceCustodySuiteInsertedEvent(policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(policeCustodySuiteId)
          assertThat(request.description).isEqualTo("Sheffield Custody Suite")
          assertThat(request.typeCode).isEqualTo("POLICE")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Custody Suite, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["nomisAgencyId"]).isEqualTo(policeCustodySuiteId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.amended")
  inner class PoliceCustodySuiteAmended {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAmendedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = policeCustodySuiteId, response = agencyResponse().copy(agencyId = policeCustodySuiteId))
          publishPoliceCustodySuiteAmendedEvent(policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Custody Suite")
          assertThat(request.typeCode).isEqualTo("POLICE")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Custody Suite, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["nomisAgencyId"]).isEqualTo(policeCustodySuiteId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.deleted")
  inner class PoliceCustodySuiteDeleted {
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteDeletedEvent(policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")))
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-deleted-ignored"),
          check { assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = policeCustodySuiteId)
        publishPoliceCustodySuiteDeletedEvent(policeCustodySuiteId = policeCustodySuiteId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-deleted-success"),
          check { assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.email.inserted")
  inner class PoliceCustodySuiteEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailInsertedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            policeCustodySuiteId,
            policeCustodySuiteLegacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))),
          )
          nomisApi.stubCreateAgencyEmail(agencyId = policeCustodySuiteId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishPoliceCustodySuiteEmailInsertedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.email.amended")
  inner class PoliceCustodySuiteEmailAmended {
    val dpsEmailId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailAmendedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishPoliceCustodySuiteEmailAmendedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.email.deleted")
  inner class PoliceCustodySuiteEmailDeleted {
    val dpsEmailId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteEmailDeletedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-email-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishPoliceCustodySuiteEmailDeletedEvent(emailId = dpsEmailId, policeCustodySuiteId = policeCustodySuiteId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-email-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.phone.inserted")
  inner class PoliceCustodySuitePhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.inserted", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = policeCustodySuiteId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.inserted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.phone.amended")
  inner class PoliceCustodySuitePhoneAmended {
    val dpsPhoneId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.amended", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.amended", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.phone.deleted")
  inner class PoliceCustodySuitePhoneDeleted {
    val dpsPhoneId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-phone-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = policeCustodySuiteId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishPoliceCustodySuitePhoneEvent("register.policecustodysuite.phone.deleted", policeCustodySuiteId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-phone-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.inserted")
  inner class PoliceCustodySuiteAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.inserted", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-created-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = policeCustodySuiteId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Custody Suite",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.inserted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-created-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.amended")
  inner class PoliceCustodySuiteAddressAmended {
    val dpsAddressId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.amended", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-updated-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Custody Suite",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.amended", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-updated-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.policecustodysuite.address.deleted")
  inner class PoliceCustodySuiteAddressDeleted {
    val dpsAddressId = 12345L
    val policeCustodySuiteId = "SHEFPOL"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("police-custody-suite-address-deleted-ignored"),
          check {
            assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$policeCustodySuiteId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = policeCustodySuiteId,
            response = policeCustodySuiteLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Custody Suite", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Custody Suite", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another custody suite address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = policeCustodySuiteId, response = policeCustodySuiteLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = policeCustodySuiteId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishPoliceCustodySuiteAddressEvent("register.policecustodysuite.address.deleted", policeCustodySuiteId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$policeCustodySuiteId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("police-custody-suite-address-deleted-success"),
            check {
              assertThat(it["policeCustodySuiteId"]).isEqualTo(policeCustodySuiteId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.inserted")
  inner class ApprovedPremisesInserted {
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishApprovedPremisesInsertedEvent(approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-created-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = approvedPremisesId, response = approvedPremisesLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = approvedPremisesId))
          publishApprovedPremisesInsertedEvent(approvedPremisesId = approvedPremisesId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(approvedPremisesId)
          assertThat(request.description).isEqualTo("Sheffield Approved Premises")
          assertThat(request.typeCode).isEqualTo("APPR")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Approved Premises, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-created-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["nomisAgencyId"]).isEqualTo(approvedPremisesId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.amended")
  inner class ApprovedPremisesAmended {
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishApprovedPremisesAmendedEvent(approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-updated-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = approvedPremisesId, response = approvedPremisesLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = approvedPremisesId, response = agencyResponse().copy(agencyId = approvedPremisesId))
          publishApprovedPremisesAmendedEvent(approvedPremisesId = approvedPremisesId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Approved Premises")
          assertThat(request.typeCode).isEqualTo("APPR")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Approved Premises, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-updated-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["nomisAgencyId"]).isEqualTo(approvedPremisesId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.deleted")
  inner class ApprovedPremisesDeleted {
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesDeletedEvent(approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$approvedPremisesId")))
        verify(telemetryClient).trackEvent(
          eq("approved-premises-deleted-ignored"),
          check { assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = approvedPremisesId)
        publishApprovedPremisesDeletedEvent(approvedPremisesId = approvedPremisesId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$approvedPremisesId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-deleted-success"),
          check { assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.email.inserted")
  inner class ApprovedPremisesEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishApprovedPremisesEmailInsertedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-email-created-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            approvedPremisesId,
            approvedPremisesLegacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))),
          )
          nomisApi.stubCreateAgencyEmail(agencyId = approvedPremisesId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishApprovedPremisesEmailInsertedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-email-created-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.email.amended")
  inner class ApprovedPremisesEmailAmended {
    val dpsEmailId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesEmailAmendedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-email-updated-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = approvedPremisesId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishApprovedPremisesEmailAmendedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-email-updated-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.email.deleted")
  inner class ApprovedPremisesEmailDeleted {
    val dpsEmailId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesEmailDeletedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-email-deleted-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = approvedPremisesId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishApprovedPremisesEmailDeletedEvent(emailId = dpsEmailId, approvedPremisesId = approvedPremisesId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-email-deleted-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.phone.inserted")
  inner class ApprovedPremisesPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.inserted", approvedPremisesId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-phone-created-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = approvedPremisesId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.inserted", approvedPremisesId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-phone-created-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.phone.amended")
  inner class ApprovedPremisesPhoneAmended {
    val dpsPhoneId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.amended", approvedPremisesId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-phone-updated-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = approvedPremisesId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.amended", approvedPremisesId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-phone-updated-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.phone.deleted")
  inner class ApprovedPremisesPhoneDeleted {
    val dpsPhoneId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.deleted", approvedPremisesId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-phone-deleted-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = approvedPremisesId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.deleted", approvedPremisesId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-phone-deleted-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = approvedPremisesId, response = approvedPremisesLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = approvedPremisesId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishApprovedPremisesPhoneEvent("register.approvedpremises.phone.deleted", approvedPremisesId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-phone-deleted-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.address.inserted")
  inner class ApprovedPremisesAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesAddressEvent("register.approvedpremises.address.inserted", approvedPremisesId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-address-created-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = approvedPremisesId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Custody Suite",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishApprovedPremisesAddressEvent("register.approvedpremises.address.inserted", approvedPremisesId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-address-created-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.address.amended")
  inner class ApprovedPremisesAddressAmended {
    val dpsAddressId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesAddressEvent("register.approvedpremises.address.amended", approvedPremisesId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-address-updated-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Custody Suite",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = approvedPremisesId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishApprovedPremisesAddressEvent("register.approvedpremises.address.amended", approvedPremisesId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-address-updated-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.approvedpremises.address.deleted")
  inner class ApprovedPremisesAddressDeleted {
    val dpsAddressId = 12345L
    val approvedPremisesId = "SHEFAPP"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishApprovedPremisesAddressEvent("register.approvedpremises.address.deleted", approvedPremisesId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("approved-premises-address-deleted-ignored"),
          check {
            assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$approvedPremisesId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = approvedPremisesId,
            response = approvedPremisesLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Custody Suite", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = approvedPremisesId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishApprovedPremisesAddressEvent("register.approvedpremises.address.deleted", approvedPremisesId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Custody Suite", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another custody suite address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-address-deleted-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = approvedPremisesId, response = approvedPremisesLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = approvedPremisesId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishApprovedPremisesAddressEvent("register.approvedpremises.address.deleted", approvedPremisesId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$approvedPremisesId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("approved-premises-address-deleted-success"),
            check {
              assertThat(it["approvedPremisesId"]).isEqualTo(approvedPremisesId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.inserted")
  inner class OtherAgencyInserted {
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishOtherAgencyInsertedEvent(otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-created-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = otherAgencyId, response = otherAgencyLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = otherAgencyId))
          publishOtherAgencyInsertedEvent(otherAgencyId = otherAgencyId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(otherAgencyId)
          assertThat(request.description).isEqualTo("Prisoner Escort Custody Services")
          assertThat(request.typeCode).isEqualTo("PECS")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Prisoner Escort Custody Services, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-created-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["nomisAgencyId"]).isEqualTo(otherAgencyId)
            },
            isNull(),
          )
        }

        @Test
        fun `will map every supported DPS agency type to the correct NOMIS type code`() {
          var eventCount = 1 // the inserted event published in setUp
          mapOf(
            LegacyAgencyType.PROBATION_CRC to "CRC",
            LegacyAgencyType.AIRPORT to "AIRPORT",
            LegacyAgencyType.VOLUNTARY_HOSTEL to "HOST",
            LegacyAgencyType.IMMIGRATION_DETENTION_CENTRE to "IMDC",
            LegacyAgencyType.OUTSIDE to "OUT",
            LegacyAgencyType.PECS to "PECS",
            LegacyAgencyType.PSYCHIATRIC_CARE to "PSY",
            LegacyAgencyType.CHILDREN_SECURE_HOME to "SCH",
            LegacyAgencyType.SECURE_TRAINING_CENTRE to "STC",
            LegacyAgencyType.YOT to "YOT",
            LegacyAgencyType.FOREIGN_NATIONAL_PRISON to "FNP",
          ).forEach { (agencyType, typeCode) ->
            dpsApi.stubGetAgency(agencyId = otherAgencyId, response = otherAgencyLegacyAgencyDto().copy(agencyType = agencyType))
            nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = otherAgencyId))
            publishOtherAgencyInsertedEvent(otherAgencyId = otherAgencyId)
            eventCount += 1
            waitForAnyProcessingToComplete(times = eventCount)

            val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
            assertThat(request.typeCode).isEqualTo(typeCode)
          }
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.amended")
  inner class OtherAgencyAmended {
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishOtherAgencyAmendedEvent(otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-updated-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = otherAgencyId, response = otherAgencyLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = otherAgencyId, response = agencyResponse().copy(agencyId = otherAgencyId))
          publishOtherAgencyAmendedEvent(otherAgencyId = otherAgencyId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId")), jsonMapper)
          assertThat(request.description).isEqualTo("Prisoner Escort Custody Services")
          assertThat(request.typeCode).isEqualTo("PECS")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Prisoner Escort Custody Services, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-updated-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["nomisAgencyId"]).isEqualTo(otherAgencyId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.deleted")
  inner class OtherAgencyDeleted {
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyDeletedEvent(otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$otherAgencyId")))
        verify(telemetryClient).trackEvent(
          eq("other-agency-deleted-ignored"),
          check { assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = otherAgencyId)
        publishOtherAgencyDeletedEvent(otherAgencyId = otherAgencyId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$otherAgencyId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-deleted-success"),
          check { assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.email.inserted")
  inner class OtherAgencyEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishOtherAgencyEmailInsertedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-email-created-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            otherAgencyId,
            otherAgencyLegacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))),
          )
          nomisApi.stubCreateAgencyEmail(agencyId = otherAgencyId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishOtherAgencyEmailInsertedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$otherAgencyId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-email-created-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.email.amended")
  inner class OtherAgencyEmailAmended {
    val dpsEmailId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyEmailAmendedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-email-updated-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = otherAgencyId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishOtherAgencyEmailAmendedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-email-updated-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.email.deleted")
  inner class OtherAgencyEmailDeleted {
    val dpsEmailId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyEmailDeletedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-email-deleted-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = otherAgencyId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishOtherAgencyEmailDeletedEvent(emailId = dpsEmailId, otherAgencyId = otherAgencyId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-email-deleted-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.phone.inserted")
  inner class OtherAgencyPhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyPhoneEvent("register.otheragencies.phone.inserted", otherAgencyId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-phone-created-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = otherAgencyId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishOtherAgencyPhoneEvent("register.otheragencies.phone.inserted", otherAgencyId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-phone-created-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.phone.amended")
  inner class OtherAgencyPhoneAmended {
    val dpsPhoneId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyPhoneEvent("register.otheragencies.phone.amended", otherAgencyId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-phone-updated-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = otherAgencyId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishOtherAgencyPhoneEvent("register.otheragencies.phone.amended", otherAgencyId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-phone-updated-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.phone.deleted")
  inner class OtherAgencyPhoneDeleted {
    val dpsPhoneId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyPhoneEvent("register.otheragencies.phone.deleted", otherAgencyId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-phone-deleted-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = otherAgencyId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishOtherAgencyPhoneEvent("register.otheragencies.phone.deleted", otherAgencyId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-phone-deleted-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = otherAgencyId, response = otherAgencyLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = otherAgencyId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishOtherAgencyPhoneEvent("register.otheragencies.phone.deleted", otherAgencyId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-phone-deleted-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.address.inserted")
  inner class OtherAgencyAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyAddressEvent("register.otheragencies.address.inserted", otherAgencyId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-address-created-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = otherAgencyId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Custody Suite",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishOtherAgencyAddressEvent("register.otheragencies.address.inserted", otherAgencyId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-address-created-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.address.amended")
  inner class OtherAgencyAddressAmended {
    val dpsAddressId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyAddressEvent("register.otheragencies.address.amended", otherAgencyId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-address-updated-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Custody Suite",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = otherAgencyId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishOtherAgencyAddressEvent("register.otheragencies.address.amended", otherAgencyId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-address-updated-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.otheragencies.address.deleted")
  inner class OtherAgencyAddressDeleted {
    val dpsAddressId = 12345L
    val otherAgencyId = "OTHAG1"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishOtherAgencyAddressEvent("register.otheragencies.address.deleted", otherAgencyId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("other-agency-address-deleted-ignored"),
          check {
            assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$otherAgencyId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = otherAgencyId,
            response = otherAgencyLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Custody Suite", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = otherAgencyId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishOtherAgencyAddressEvent("register.otheragencies.address.deleted", otherAgencyId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Custody Suite", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another custody suite address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-address-deleted-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = otherAgencyId, response = otherAgencyLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = otherAgencyId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishOtherAgencyAddressEvent("register.otheragencies.address.deleted", otherAgencyId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$otherAgencyId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("other-agency-address-deleted-success"),
            check {
              assertThat(it["otherAgencyId"]).isEqualTo(otherAgencyId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.inserted")
  inner class ProbationOfficeInserted {
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishProbationOfficeInsertedEvent(probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-created-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = probationOfficeId, response = probationOfficeLegacyAgencyDto())
          nomisApi.stubCreateAgency(response = agencyResponse().copy(agencyId = probationOfficeId))
          publishProbationOfficeInsertedEvent(probationOfficeId = probationOfficeId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create agency in NOMIS`() {
          val request: CreateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency")), jsonMapper)
          assertThat(request.agencyId).isEqualTo(probationOfficeId)
          assertThat(request.description).isEqualTo("Sheffield Probation Office")
          assertThat(request.typeCode).isEqualTo("COMM")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Probation Office, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-created-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["nomisAgencyId"]).isEqualTo(probationOfficeId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.amended")
  inner class ProbationOfficeAmended {
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishProbationOfficeAmendedEvent(probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-updated-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = probationOfficeId, response = probationOfficeLegacyAgencyDto())
          nomisApi.stubUpdateAgency(agencyId = probationOfficeId, response = agencyResponse().copy(agencyId = probationOfficeId))
          publishProbationOfficeAmendedEvent(probationOfficeId = probationOfficeId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: UpdateAgencyRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId")), jsonMapper)
          assertThat(request.description).isEqualTo("Sheffield Probation Office")
          assertThat(request.typeCode).isEqualTo("COMM")
          assertThat(request.active).isTrue()
          assertThat(request.localAuthorityCodes).containsExactly("00CG")
          assertThat(request.longDescription).isEqualTo("Sheffield Probation Office, Sheffield")
          assertThat(request.contactName).isEqualTo("JANE SMITH")
          assertThat(request.disabilityAccessCode).isEqualTo("WC")
          assertThat(request.areaCode).isEqualTo("52")
          assertThat(request.subAreaCode).isEqualTo("SHEFF")
          assertThat(request.regionCode).isEqualTo("NEAST")
          assertThat(request.nomsRegionCode).isEqualTo("YOHUM")
          assertThat(request.cjitCode).isEqualTo("C00SH00")
          assertThat(request.payrollRegionCode).isEqualTo("YP")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-updated-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["nomisAgencyId"]).isEqualTo(probationOfficeId)
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.deleted")
  inner class ProbationOfficeDeleted {
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeDeletedEvent(probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will ignore the event`() {
        nomisApi.verify(0, deleteRequestedFor(urlEqualTo("/agency/$probationOfficeId")))
        verify(telemetryClient).trackEvent(
          eq("probation-office-deleted-ignored"),
          check { assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId) },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @BeforeEach
      fun setUp() {
        nomisApi.stubDeleteAgency(agencyId = probationOfficeId)
        publishProbationOfficeDeletedEvent(probationOfficeId = probationOfficeId)
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will delete agency in NOMIS`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/agency/$probationOfficeId")))
      }

      @Test
      fun `will send telemetry event showing success`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-deleted-success"),
          check { assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId) },
          isNull(),
        )
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.email.inserted")
  inner class ProbationOfficeEmailInserted {
    val dpsEmailId = 12345L
    val nomisEmailId = 8765L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {

      @BeforeEach
      fun setUp() {
        publishProbationOfficeEmailInsertedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-email-created-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {

      @Nested
      inner class HappyPath {

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            probationOfficeId,
            probationOfficeLegacyAgencyDto().copy(emailAddresses = listOf(legacyAgencyEmailDto().copy(id = dpsEmailId, address = "test@justice.gov.uk"))),
          )
          nomisApi.stubCreateAgencyEmail(agencyId = probationOfficeId, response = createAgencyEmailResponse().copy(id = nomisEmailId))
          publishProbationOfficeEmailInsertedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will update NOMIS`() {
          val request: CreateAgencyEmailAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$probationOfficeId/emails")), jsonMapper)
          assertThat(request.emailAddress).isEqualTo("test@justice.gov.uk")
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-email-created-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailId"]).isEqualTo(nomisEmailId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.email.amended")
  inner class ProbationOfficeEmailAmended {
    val dpsEmailId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeEmailAmendedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-email-updated-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = probationOfficeId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishProbationOfficeEmailAmendedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-email-updated-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.email.deleted")
  inner class ProbationOfficeEmailDeleted {
    val dpsEmailId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeEmailDeletedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-email-deleted-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
          },
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      private val emails = listOf("custody.one@justice.gov.uk", "custody.two@justice.gov.uk")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                legacyAgencyEmailDto().copy(id = index + 1L, address = address)
              },
            ),
          )
          nomisApi.stubUpdateAgencyEmailAddresses(
            agencyId = probationOfficeId,
            response = updateAgencyEmailAddressesResponse().copy(
              emailAddresses = emails.mapIndexed { index, address ->
                agencyEmailAddress().copy(id = index + 1L, emailAddress = address)
              },
            ),
          )
          publishProbationOfficeEmailDeletedEvent(emailId = dpsEmailId, probationOfficeId = probationOfficeId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS email addresses`() {
          val request: UpdateAgencyEmailAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/emails")), jsonMapper)
          assertThat(request.emailAddresses).containsExactlyElementsOf(emails)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-email-deleted-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsEmailId"]).isEqualTo(dpsEmailId.toString())
              assertThat(it["nomisEmailIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.phone.inserted")
  inner class ProbationOfficePhoneInserted {
    val dpsPhoneId = 12345L
    val nomisPhoneId = 8765L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishProbationOfficePhoneEvent("register.probationoffice.phone.inserted", probationOfficeId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-phone-created-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              phoneNumbers = listOf(legacyAgencyPhoneDto(), legacyAgencyPhoneDto().copy(id = dpsPhoneId, number = "0114 555 1234")),
            ),
          )
          nomisApi.stubCreateAgencyPhone(agencyId = probationOfficeId, response = createAgencyPhoneResponse().copy(id = nomisPhoneId))
          publishProbationOfficePhoneEvent("register.probationoffice.phone.inserted", probationOfficeId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted phone in NOMIS`() {
          val request: CreateAgencyPhoneNumberRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")), jsonMapper)
          assertThat(request).isEqualTo(CreateAgencyPhoneNumberRequest(number = "0114 555 1234", typeCode = "BUS"))
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-phone-created-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneId"]).isEqualTo(nomisPhoneId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.phone.amended")
  inner class ProbationOfficePhoneAmended {
    val dpsPhoneId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishProbationOfficePhoneEvent("register.probationoffice.phone.amended", probationOfficeId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-phone-updated-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = dpsPhoneId + index, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = probationOfficeId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishProbationOfficePhoneEvent("register.probationoffice.phone.amended", probationOfficeId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-phone-updated-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.phone.deleted")
  inner class ProbationOfficePhoneDeleted {
    val dpsPhoneId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishProbationOfficePhoneEvent("register.probationoffice.phone.deleted", probationOfficeId, dpsPhoneId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-phone-deleted-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      val phones = listOf("0114 555 1234", "0114 555 5678")

      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              phoneNumbers = phones.mapIndexed { index, number -> legacyAgencyPhoneDto().copy(id = index + 1L, number = number) },
            ),
          )
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = probationOfficeId,
            response = updateAgencyPhoneNumbersResponse().copy(
              phoneNumbers = phones.mapIndexed { index, number -> agencyPhoneNumber().copy(id = index + 1L, number = number) },
            ),
          )
          publishProbationOfficePhoneEvent("register.probationoffice.phone.deleted", probationOfficeId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).containsExactlyElementsOf(phones.map { UpdateAgencyPhoneNumber(number = it, typeCode = "BUS") })
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-phone-deleted-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastPhoneDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = probationOfficeId, response = probationOfficeLegacyAgencyDto().copy(phoneNumbers = emptyList()))
          nomisApi.stubUpdateAgencyPhoneNumbers(
            agencyId = probationOfficeId,
            response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()),
          )
          publishProbationOfficePhoneEvent("register.probationoffice.phone.deleted", probationOfficeId, dpsPhoneId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS phone numbers`() {
          val request: UpdateAgencyPhoneNumbersRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/phones")), jsonMapper)
          assertThat(request.phoneNumbers).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no phone ids`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-phone-deleted-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsPhoneId"]).isEqualTo(dpsPhoneId.toString())
              assertThat(it["nomisPhoneIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.address.inserted")
  inner class ProbationOfficeAddressInserted {
    val dpsAddressId = 12345L
    val nomisAddressId = 8765L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisCreated {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeAddressEvent("register.probationoffice.address.inserted", probationOfficeId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-address-created-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, postRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsCreated {
      @Nested
      inner class HappyPath {
        private val expectedAddress = CreateAgencyAddressRequest(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          nomisApi.stubCreateAgencyAddress(agencyId = probationOfficeId, response = createAgencyAddressResponse().copy(id = nomisAddressId))
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = null, country = null),
                legacyAgencyAddressDto().copy(
                  id = dpsAddressId,
                  addressLine1 = "The Custody Suite",
                  addressLine2 = "50 West Bar",
                  postcode = "S3 8PH",
                  town = "Sheffield",
                  county = "South Yorkshire",
                  country = "England",
                ),
              ),
            ),
          )
          publishProbationOfficeAddressEvent("register.probationoffice.address.inserted", probationOfficeId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will create only the inserted address in NOMIS`() {
          val request: CreateAgencyAddressRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")), jsonMapper)
          assertThat(request).isEqualTo(expectedAddress)
          nomisApi.verify(1, postRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")))
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-address-created-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressId"]).isEqualTo(nomisAddressId.toString())
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.address.amended")
  inner class ProbationOfficeAddressAmended {
    val dpsAddressId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisAmended {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeAddressEvent("register.probationoffice.address.amended", probationOfficeId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-address-updated-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsAmended {
      @Nested
      inner class HappyPath {
        private val address = legacyAgencyAddressDto().copy(
          id = dpsAddressId,
          addressLine1 = "The Custody Suite",
          addressLine2 = "50 West Bar",
          postcode = "S3 8PH",
          town = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )
        private val expectedAddress = UpdateAgencyAddress(
          street = "The Custody Suite",
          locality = "50 West Bar",
          postcode = "S3 8PH",
          city = "Sheffield",
          county = "South Yorkshire",
          country = "England",
        )

        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(addresses = listOf(address)),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = probationOfficeId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1))),
          )
          publishProbationOfficeAddressEvent("register.probationoffice.address.amended", probationOfficeId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh all NOMIS addresses using the same mapping as creation`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(expectedAddress)
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-address-updated-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1")
            },
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  @DisplayName("register.probationoffice.address.deleted")
  inner class ProbationOfficeAddressDeleted {
    val dpsAddressId = 12345L
    val probationOfficeId = "SHEFPRB"

    @Nested
    @DisplayName("when NOMIS is the origin of the event")
    inner class WhenNomisDeleted {
      @BeforeEach
      fun setUp() {
        publishProbationOfficeAddressEvent("register.probationoffice.address.deleted", probationOfficeId, dpsAddressId, source = "NOMIS")
        waitForAnyProcessingToComplete()
      }

      @Test
      fun `will send telemetry event showing the event is ignored`() {
        verify(telemetryClient).trackEvent(
          eq("probation-office-address-deleted-ignored"),
          check {
            assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
            assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will not call DPS or update NOMIS`() {
        dpsApi.verify(0, getRequestedFor(urlEqualTo("/legacy/$probationOfficeId")))
        nomisApi.verify(0, putRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")))
      }
    }

    @Nested
    @DisplayName("when DPS is the origin of the event")
    inner class WhenDpsDeleted {
      @Nested
      inner class HappyPath {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(
            agencyId = probationOfficeId,
            response = probationOfficeLegacyAgencyDto().copy(
              addresses = listOf(
                legacyAgencyAddressDto().copy(id = 1, addressLine1 = "The Custody Suite", addressLine2 = null, town = null, county = null, postcode = "S3 8PH", country = null),
                legacyAgencyAddressDto().copy(id = 2, addressLine1 = "Another custody suite address", addressLine2 = null, town = null, county = null, postcode = "S1 2AB", country = null),
              ),
            ),
          )
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = probationOfficeId,
            response = updateAgencyAddressesResponse().copy(addresses = listOf(agencyAddress().copy(id = 1), agencyAddress().copy(id = 2))),
          )
          publishProbationOfficeAddressEvent("register.probationoffice.address.deleted", probationOfficeId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will refresh NOMIS with the remaining addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")), jsonMapper)
          assertThat(request.addresses).containsExactly(
            UpdateAgencyAddress(street = "The Custody Suite", postcode = "S3 8PH"),
            UpdateAgencyAddress(street = "Another custody suite address", postcode = "S1 2AB"),
          )
        }

        @Test
        fun `will send telemetry event showing success`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-address-deleted-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEqualTo("1, 2")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class LastAddressDeleted {
        @BeforeEach
        fun setUp() {
          dpsApi.stubGetAgency(agencyId = probationOfficeId, response = probationOfficeLegacyAgencyDto().copy(addresses = emptyList()))
          nomisApi.stubUpdateAgencyAddresses(
            agencyId = probationOfficeId,
            response = updateAgencyAddressesResponse().copy(addresses = emptyList()),
          )
          publishProbationOfficeAddressEvent("register.probationoffice.address.deleted", probationOfficeId, dpsAddressId)
          waitForAnyProcessingToComplete()
        }

        @Test
        fun `will remove all NOMIS addresses`() {
          val request: UpdateAgencyAddressesRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/agency/$probationOfficeId/addresses")), jsonMapper)
          assertThat(request.addresses).isEmpty()
        }

        @Test
        fun `will send telemetry event showing success with no address ids`() {
          verify(telemetryClient).trackEvent(
            eq("probation-office-address-deleted-success"),
            check {
              assertThat(it["probationOfficeId"]).isEqualTo(probationOfficeId)
              assertThat(it["dpsAddressId"]).isEqualTo(dpsAddressId.toString())
              assertThat(it["nomisAddressIds"]).isEmpty()
            },
            isNull(),
          )
        }
      }
    }
  }

  private fun hospitalLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.HOSPITAL,
    name = "Hallamshire Hospital",
    description = "Hallamshire Hospital, Sheffield",
  )

  private fun policeCustodySuiteLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.POLICE_CUSTODY_SUITE,
    name = "Sheffield Custody Suite",
    description = "Sheffield Custody Suite, Sheffield",
    courtTypeCode = null,
  )

  private fun approvedPremisesLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.APPROVED_PREMISES,
    name = "Sheffield Approved Premises",
    description = "Sheffield Approved Premises, Sheffield",
    courtTypeCode = null,
  )

  private fun otherAgencyLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.PECS,
    name = "Prisoner Escort Custody Services",
    description = "Prisoner Escort Custody Services, Sheffield",
    courtTypeCode = null,
  )

  private fun probationOfficeLegacyAgencyDto() = legacyAgencyDto().copy(
    agencyType = LegacyAgencyType.PROBATION_OFFICE,
    name = "Sheffield Probation Office",
    description = "Sheffield Probation Office, Sheffield",
    courtTypeCode = null,
  )

  private fun publishCourtAddressEvent(
    eventType: String,
    courtId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = CourtAddressEvent(
        eventType = eventType,
        additionalInformation = CourtAddressAdditionalInformation(courtId = courtId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishCourtPhoneEvent(
    eventType: String,
    courtId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = CourtPhoneEvent(
        eventType = eventType,
        additionalInformation = CourtPhoneAdditionalInformation(courtId = courtId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishCourtInsertedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.inserted",
      payload = CourtEvent(
        eventType = "register.court.inserted",
        additionalInformation = CourtAdditionalInformation(
          courtId = courtId,
          source = source,
        ),
      ),
    )
  }

  private fun publishCourtAmendedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.amended",
      payload = CourtEvent(
        eventType = "register.court.amended",
        additionalInformation = CourtAdditionalInformation(
          courtId = courtId,
          source = source,
        ),
      ),
    )
  }

  private fun publishCourtDeletedEvent(
    courtId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.court.deleted",
      payload = CourtEvent(
        eventType = "register.court.deleted",
        additionalInformation = CourtAdditionalInformation(courtId = courtId, source = source),
      ),
    )
  }

  private fun publishHospitalAddressEvent(
    eventType: String,
    hospitalId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = HospitalAddressEvent(
        eventType = eventType,
        additionalInformation = HospitalAddressAdditionalInformation(hospitalId = hospitalId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishHospitalPhoneEvent(
    eventType: String,
    hospitalId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = HospitalPhoneEvent(
        eventType = eventType,
        additionalInformation = HospitalPhoneAdditionalInformation(hospitalId = hospitalId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishHospitalInsertedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.inserted",
      payload = HospitalEvent(
        eventType = "register.hospital.inserted",
        additionalInformation = HospitalAdditionalInformation(
          hospitalId = hospitalId,
          source = source,
        ),
      ),
    )
  }

  private fun publishHospitalAmendedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.amended",
      payload = HospitalEvent(
        eventType = "register.hospital.amended",
        additionalInformation = HospitalAdditionalInformation(
          hospitalId = hospitalId,
          source = source,
        ),
      ),
    )
  }

  private fun publishHospitalDeletedEvent(
    hospitalId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.hospital.deleted",
      payload = HospitalEvent(
        eventType = "register.hospital.deleted",
        additionalInformation = HospitalAdditionalInformation(hospitalId = hospitalId, source = source),
      ),
    )
  }

  private fun publishCourtEmailInsertedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishCourtEmailAmendedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishCourtEmailDeletedEvent(
    courtId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.court.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = CourtEmailEvent(
          eventType = this,
          additionalInformation = CourtEmailAdditionalInformation(
            courtId = courtId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishPoliceCustodySuiteAddressEvent(
    eventType: String,
    policeCustodySuiteId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = PoliceCustodySuiteAddressEvent(
        eventType = eventType,
        additionalInformation = PoliceCustodySuiteAddressAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuitePhoneEvent(
    eventType: String,
    policeCustodySuiteId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = PoliceCustodySuitePhoneEvent(
        eventType = eventType,
        additionalInformation = PoliceCustodySuitePhoneAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuiteInsertedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.inserted",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.inserted",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(
          policeCustodySuiteId = policeCustodySuiteId,
          source = source,
        ),
      ),
    )
  }

  private fun publishPoliceCustodySuiteAmendedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.amended",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.amended",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(
          policeCustodySuiteId = policeCustodySuiteId,
          source = source,
        ),
      ),
    )
  }

  private fun publishPoliceCustodySuiteDeletedEvent(
    policeCustodySuiteId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.policecustodysuite.deleted",
      payload = PoliceCustodySuiteEvent(
        eventType = "register.policecustodysuite.deleted",
        additionalInformation = PoliceCustodySuiteAdditionalInformation(policeCustodySuiteId = policeCustodySuiteId, source = source),
      ),
    )
  }

  private fun publishPoliceCustodySuiteEmailInsertedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishPoliceCustodySuiteEmailAmendedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishPoliceCustodySuiteEmailDeletedEvent(
    policeCustodySuiteId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.policecustodysuite.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = PoliceCustodySuiteEmailEvent(
          eventType = this,
          additionalInformation = PoliceCustodySuiteEmailAdditionalInformation(
            policeCustodySuiteId = policeCustodySuiteId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishApprovedPremisesAddressEvent(
    eventType: String,
    approvedPremisesId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = ApprovedPremisesAddressEvent(
        eventType = eventType,
        additionalInformation = ApprovedPremisesAddressAdditionalInformation(approvedPremisesId = approvedPremisesId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishApprovedPremisesPhoneEvent(
    eventType: String,
    approvedPremisesId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = ApprovedPremisesPhoneEvent(
        eventType = eventType,
        additionalInformation = ApprovedPremisesPhoneAdditionalInformation(approvedPremisesId = approvedPremisesId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishApprovedPremisesInsertedEvent(
    approvedPremisesId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.approvedpremises.inserted",
      payload = ApprovedPremisesEvent(
        eventType = "register.approvedpremises.inserted",
        additionalInformation = ApprovedPremisesAdditionalInformation(
          approvedPremisesId = approvedPremisesId,
          source = source,
        ),
      ),
    )
  }

  private fun publishApprovedPremisesAmendedEvent(
    approvedPremisesId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.approvedpremises.amended",
      payload = ApprovedPremisesEvent(
        eventType = "register.approvedpremises.amended",
        additionalInformation = ApprovedPremisesAdditionalInformation(
          approvedPremisesId = approvedPremisesId,
          source = source,
        ),
      ),
    )
  }

  private fun publishApprovedPremisesDeletedEvent(
    approvedPremisesId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.approvedpremises.deleted",
      payload = ApprovedPremisesEvent(
        eventType = "register.approvedpremises.deleted",
        additionalInformation = ApprovedPremisesAdditionalInformation(approvedPremisesId = approvedPremisesId, source = source),
      ),
    )
  }

  private fun publishApprovedPremisesEmailInsertedEvent(
    approvedPremisesId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.approvedpremises.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = ApprovedPremisesEmailEvent(
          eventType = this,
          additionalInformation = ApprovedPremisesEmailAdditionalInformation(
            approvedPremisesId = approvedPremisesId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishApprovedPremisesEmailAmendedEvent(
    approvedPremisesId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.approvedpremises.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = ApprovedPremisesEmailEvent(
          eventType = this,
          additionalInformation = ApprovedPremisesEmailAdditionalInformation(
            approvedPremisesId = approvedPremisesId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishApprovedPremisesEmailDeletedEvent(
    approvedPremisesId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.approvedpremises.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = ApprovedPremisesEmailEvent(
          eventType = this,
          additionalInformation = ApprovedPremisesEmailAdditionalInformation(
            approvedPremisesId = approvedPremisesId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishOtherAgencyAddressEvent(
    eventType: String,
    otherAgencyId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = OtherAgencyAddressEvent(
        eventType = eventType,
        additionalInformation = OtherAgencyAddressAdditionalInformation(otherAgencyId = otherAgencyId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishOtherAgencyPhoneEvent(
    eventType: String,
    otherAgencyId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = OtherAgencyPhoneEvent(
        eventType = eventType,
        additionalInformation = OtherAgencyPhoneAdditionalInformation(otherAgencyId = otherAgencyId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishOtherAgencyInsertedEvent(
    otherAgencyId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.otheragencies.inserted",
      payload = OtherAgencyEvent(
        eventType = "register.otheragencies.inserted",
        additionalInformation = OtherAgencyAdditionalInformation(
          otherAgencyId = otherAgencyId,
          source = source,
        ),
      ),
    )
  }

  private fun publishOtherAgencyAmendedEvent(
    otherAgencyId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.otheragencies.amended",
      payload = OtherAgencyEvent(
        eventType = "register.otheragencies.amended",
        additionalInformation = OtherAgencyAdditionalInformation(
          otherAgencyId = otherAgencyId,
          source = source,
        ),
      ),
    )
  }

  private fun publishOtherAgencyDeletedEvent(
    otherAgencyId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.otheragencies.deleted",
      payload = OtherAgencyEvent(
        eventType = "register.otheragencies.deleted",
        additionalInformation = OtherAgencyAdditionalInformation(otherAgencyId = otherAgencyId, source = source),
      ),
    )
  }

  private fun publishOtherAgencyEmailInsertedEvent(
    otherAgencyId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.otheragencies.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = OtherAgencyEmailEvent(
          eventType = this,
          additionalInformation = OtherAgencyEmailAdditionalInformation(
            otherAgencyId = otherAgencyId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishOtherAgencyEmailAmendedEvent(
    otherAgencyId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.otheragencies.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = OtherAgencyEmailEvent(
          eventType = this,
          additionalInformation = OtherAgencyEmailAdditionalInformation(
            otherAgencyId = otherAgencyId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishOtherAgencyEmailDeletedEvent(
    otherAgencyId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.otheragencies.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = OtherAgencyEmailEvent(
          eventType = this,
          additionalInformation = OtherAgencyEmailAdditionalInformation(
            otherAgencyId = otherAgencyId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishProbationOfficeAddressEvent(
    eventType: String,
    probationOfficeId: String,
    addressId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = ProbationOfficeAddressEvent(
        eventType = eventType,
        additionalInformation = ProbationOfficeAddressAdditionalInformation(probationOfficeId = probationOfficeId, addressId = addressId, source = source),
      ),
    )
  }

  private fun publishProbationOfficePhoneEvent(
    eventType: String,
    probationOfficeId: String,
    phoneId: Long,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = eventType,
      payload = ProbationOfficePhoneEvent(
        eventType = eventType,
        additionalInformation = ProbationOfficePhoneAdditionalInformation(probationOfficeId = probationOfficeId, phoneId = phoneId, source = source),
      ),
    )
  }

  private fun publishProbationOfficeInsertedEvent(
    probationOfficeId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.probationoffice.inserted",
      payload = ProbationOfficeEvent(
        eventType = "register.probationoffice.inserted",
        additionalInformation = ProbationOfficeAdditionalInformation(
          probationOfficeId = probationOfficeId,
          source = source,
        ),
      ),
    )
  }

  private fun publishProbationOfficeAmendedEvent(
    probationOfficeId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.probationoffice.amended",
      payload = ProbationOfficeEvent(
        eventType = "register.probationoffice.amended",
        additionalInformation = ProbationOfficeAdditionalInformation(
          probationOfficeId = probationOfficeId,
          source = source,
        ),
      ),
    )
  }

  private fun publishProbationOfficeDeletedEvent(
    probationOfficeId: String,
    source: String = "DPS",
  ) {
    publishDomainEvent(
      eventType = "register.probationoffice.deleted",
      payload = ProbationOfficeEvent(
        eventType = "register.probationoffice.deleted",
        additionalInformation = ProbationOfficeAdditionalInformation(probationOfficeId = probationOfficeId, source = source),
      ),
    )
  }

  private fun publishProbationOfficeEmailInsertedEvent(
    probationOfficeId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.probationoffice.email.inserted") {
      publishDomainEvent(
        eventType = this,
        payload = ProbationOfficeEmailEvent(
          eventType = this,
          additionalInformation = ProbationOfficeEmailAdditionalInformation(
            probationOfficeId = probationOfficeId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishProbationOfficeEmailAmendedEvent(
    probationOfficeId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.probationoffice.email.amended") {
      publishDomainEvent(
        eventType = this,
        payload = ProbationOfficeEmailEvent(
          eventType = this,
          additionalInformation = ProbationOfficeEmailAdditionalInformation(
            probationOfficeId = probationOfficeId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishProbationOfficeEmailDeletedEvent(
    probationOfficeId: String,
    emailId: Long,
    source: String = "DPS",
  ) {
    with("register.probationoffice.email.deleted") {
      publishDomainEvent(
        eventType = this,
        payload = ProbationOfficeEmailEvent(
          eventType = this,
          additionalInformation = ProbationOfficeEmailAdditionalInformation(
            probationOfficeId = probationOfficeId,
            emailId = emailId,
            source = source,
          ),
        ),
      )
    }
  }

  private fun publishDomainEvent(
    eventType: String,
    payload: Any,
  ) {
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(jsonMapper.writeValueAsString(payload))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }
}
